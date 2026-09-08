#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

SEED="${SEED_ROOT:-$HOME/Seed}"
APP="${TF_R10_APP:-$SEED/thunderforge-r10-unified-verified}"
BIN="${TF_SEED_BIN:-$SEED/bin}"
KSTATE="${TF_KERNEL_COMPUTER_STATE:-$HOME/Thunderforge-Atlas/kernel-computer-r2}"
STAMP="$(date +%Y%m%d_%H%M%S)"
BACK="${TF_INTEGRATION_BACKUP:-$HOME/PrivateWorks-Backups/homebase-kernel-constructor-r1-$STAMP}"
TEST_ONLY="${TF_INTEGRATION_TEST_ONLY:-0}"

say(){ printf '%s\n' "$*"; }
die(){ say "ERROR: $*" >&2; exit 1; }

say "============================================================"
say " HOME BASE R10 · KERNEL COMPUTER R2 + CONSTRUCTOR · R1"
say "============================================================"

[ -d "$APP" ] || die "R10 Home Base not found: $APP"
for f in index.html app.js styles.css sw.js r10_bridge.py; do
  [ -f "$APP/$f" ] || die "Missing R10 file: $APP/$f"
done
mkdir -p "$BIN" "$BACK"

if [ "$TEST_ONLY" != "1" ] && [ -x "$BIN/thunderforge-r10-stop" ]; then
  "$BIN/thunderforge-r10-stop" >/dev/null 2>&1 || true
fi

cp -a "$APP/index.html" "$APP/app.js" "$APP/sw.js" "$APP/r10_bridge.py" "$BACK/"
[ -f "$APP/kernel-constructor-r1.js" ] && cp -a "$APP/kernel-constructor-r1.js" "$BACK/" || true
[ -f "$APP/kernel-constructor-r1.css" ] && cp -a "$APP/kernel-constructor-r1.css" "$BACK/" || true
[ -f "$APP/kernel_constructor_bridge_r1.py" ] && cp -a "$APP/kernel_constructor_bridge_r1.py" "$BACK/" || true
say "Backup: $BACK"

cat > "$APP/kernel_constructor_bridge_r1.py" <<'PY'
from __future__ import annotations
import http.client
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import time
from urllib.parse import parse_qs, urlparse

HOME = Path.home()
SEED_ROOT = Path(os.environ.get("TF_SEED_ROOT", HOME / "Seed"))
KERNEL_STATE = Path(os.environ.get("TF_KERNEL_COMPUTER_STATE", HOME / "Thunderforge-Atlas" / "kernel-computer-r2"))
R10_PORT = int(os.environ.get("TF_R10_PORT", "8941"))
MAX_CHAT = 12000


def _read_json_file(path: Path):
    if not path.is_file():
        return None
    if path.stat().st_size > 32 * 1024 * 1024:
        raise ValueError("JSON state file exceeds 32 MiB limit")
    return json.loads(path.read_text(encoding="utf-8"))


def _kernel_node(kernel_id: str):
    kernel_id = str(kernel_id or "").strip()
    if not kernel_id or len(kernel_id) > 200:
        return None
    db_path = KERNEL_STATE / "kernel-computer.sqlite3"
    if not db_path.is_file():
        return None
    db = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True, timeout=5)
    db.row_factory = sqlite3.Row
    try:
        row = db.execute("SELECT * FROM nodes WHERE kernel_id=?", (kernel_id,)).fetchone()
        if not row:
            return None
        result = dict(row)
        if "tags_json" in result:
            try: result["tags"] = json.loads(result.pop("tags_json"))
            except Exception: result["tags"] = []
        trait = db.execute("SELECT * FROM traits WHERE kernel_id=?", (kernel_id,)).fetchone()
        result["traits"] = json.loads(trait["traits_json"]) if trait else None
        result["parents"] = [dict(item) for item in db.execute(
            "SELECT l.parent_id,l.relation,l.score,n.statement,n.origin FROM lineage l JOIN nodes n ON n.kernel_id=l.parent_id WHERE l.child_id=? ORDER BY l.parent_id",
            (kernel_id,),
        )]
        result["children"] = [dict(item) for item in db.execute(
            "SELECT l.child_id,l.relation,l.score,n.statement,n.origin FROM lineage l JOIN nodes n ON n.kernel_id=l.child_id WHERE l.parent_id=? ORDER BY l.child_id LIMIT 100",
            (kernel_id,),
        )]
        result["reviews"] = [dict(item) for item in db.execute(
            "SELECT decision,note,reviewed_at FROM reviews WHERE kernel_id=? ORDER BY reviewed_at DESC", (kernel_id,)
        )]
        return result
    finally:
        db.close()


def _candidate_constructor_ports():
    seen = set()
    out = []
    run_dirs = [SEED_ROOT / "run", HOME / "Thunderforge-Atlas" / "run"]
    preferred = ["farwater-homebase.port", "homebase.port", "homebase-144.port", "constructor.port"]
    for run in run_dirs:
        for name in preferred:
            p = run / name
            if p.is_file():
                try:
                    port = int(p.read_text().strip())
                    if 1 <= port <= 65535 and port != R10_PORT and port not in seen:
                        seen.add(port); out.append(port)
                except Exception:
                    pass
        if run.is_dir():
            for p in sorted(run.glob("*.port")):
                try:
                    port = int(p.read_text().strip())
                    if 1 <= port <= 65535 and port != R10_PORT and port not in seen:
                        seen.add(port); out.append(port)
                except Exception:
                    pass
    for port in (8880, 8791):
        if port != R10_PORT and port not in seen:
            seen.add(port); out.append(port)
    return out[:40]


def _constructor_request(method: str, path: str, payload=None, timeout=8):
    body = None
    headers = {"Connection": "close"}
    if payload is not None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
        headers["Content-Length"] = str(len(body))
    for port in _candidate_constructor_ports():
        conn = None
        try:
            conn = http.client.HTTPConnection("127.0.0.1", port, timeout=timeout)
            conn.request(method, path, body=body, headers=headers)
            response = conn.getresponse()
            raw = response.read(2 * 1024 * 1024).decode("utf-8", "replace")
            status = response.status
            try:
                obj = json.loads(raw) if raw else {}
            except Exception:
                obj = {"ok": False, "error": "Constructor returned non-JSON data"}
            if status == 404 and path.startswith("/api/constructor/"):
                continue
            if isinstance(obj, dict):
                obj.setdefault("constructor_port", port)
            return status, obj
        except Exception:
            continue
        finally:
            try:
                if conn: conn.close()
            except Exception:
                pass
    return 503, {"ok": False, "online": False, "error": "Constructor service is not running."}


def _read_request_json(handler):
    try:
        n = int(handler.headers.get("Content-Length", "0") or 0)
    except Exception:
        n = 0
    if n < 0 or n > 65536:
        raise ValueError("Request too large")
    raw = handler.rfile.read(n) if n else b"{}"
    obj = json.loads(raw or b"{}")
    if not isinstance(obj, dict):
        raise ValueError("JSON object required")
    return obj


def _start_constructor():
    log_dir = KERNEL_STATE / "logs"
    log_dir.mkdir(parents=True, exist_ok=True)
    log_path = log_dir / "constructor-homebase-bridge.log"
    stack = SEED_ROOT / "bin" / "constructor-stack-start"
    core = SEED_ROOT / "bin" / "constructor-core-start-r30"
    farwater = SEED_ROOT / "bin" / "farwater-start"
    commands = []
    if stack.is_file() and os.access(stack, os.X_OK):
        commands = [[str(stack)]]
    elif core.is_file() and os.access(core, os.X_OK):
        if farwater.is_file() and os.access(farwater, os.X_OK):
            commands.append([str(farwater)])
        commands.append([str(core)])
    if not commands:
        return {"ok": False, "error": "Constructor lifecycle helpers were not found."}
    with log_path.open("ab", buffering=0) as log:
        for cmd in commands:
            subprocess.Popen(cmd, cwd=str(HOME), stdin=subprocess.DEVNULL, stdout=log, stderr=log,
                             start_new_session=True, close_fds=True)
    return {"ok": True, "started": True, "log": str(log_path), "commands": [Path(x[0]).name for x in commands]}


def install_into_handler(Handler):
    if getattr(Handler, "_kernel_constructor_r1", False):
        return Handler
    original_get = Handler.do_GET
    original_post = Handler.do_POST

    def do_GET(self):
        u = urlparse(self.path)
        route = u.path
        if route == "/api/r10/kernel/field":
            try:
                data = _read_json_file(KERNEL_STATE / "web-data" / "computer.json")
                if data is None:
                    return self.send_json({"ok": False, "error": "Kernel Computer field is not built yet."}, 503)
                return self.send_json(data)
            except Exception as e:
                return self.send_json({"ok": False, "error": f"{type(e).__name__}: {e}"}, 500)
        if route == "/api/r10/kernel/status":
            try:
                data = _read_json_file(KERNEL_STATE / "exports" / "STATUS.json")
                return self.send_json(data if data is not None else {"ok": False, "error": "Kernel status unavailable"}, 200 if data is not None else 503)
            except Exception as e:
                return self.send_json({"ok": False, "error": f"{type(e).__name__}: {e}"}, 500)
        if route == "/api/r10/kernel/node":
            kernel_id = parse_qs(u.query).get("id", [""])[0]
            try:
                node = _kernel_node(kernel_id)
                return self.send_json(node if node is not None else {"ok": False, "error": "Kernel not found"}, 200 if node is not None else 404)
            except Exception as e:
                return self.send_json({"ok": False, "error": f"{type(e).__name__}: {e}"}, 500)
        if route == "/api/r10/constructor/status":
            status, obj = _constructor_request("GET", "/api/constructor/status", timeout=3)
            if isinstance(obj, dict): obj["homebase_bridge"] = True
            return self.send_json(obj, 200 if status < 500 else 200)
        return original_get(self)

    def do_POST(self):
        u = urlparse(self.path)
        route = u.path
        if route == "/api/r10/constructor/start":
            try:
                result = _start_constructor()
                return self.send_json(result, 200 if result.get("ok") else 503)
            except Exception as e:
                return self.send_json({"ok": False, "error": f"{type(e).__name__}: {e}"}, 500)
        if route == "/api/r10/constructor/chat":
            try:
                obj = _read_request_json(self)
                message = str(obj.get("message", "")).strip()
                if not message:
                    return self.send_json({"ok": False, "error": "Message is required"}, 400)
                if len(message) > MAX_CHAT:
                    return self.send_json({"ok": False, "error": f"Message limited to {MAX_CHAT} characters"}, 413)
                payload = {"message": message, "include_terminal_context": False}
                status, reply = _constructor_request("POST", "/api/constructor/chat", payload=payload, timeout=90)
                return self.send_json(reply, 200 if status == 200 else (503 if status >= 500 else status))
            except ValueError as e:
                return self.send_json({"ok": False, "error": str(e)}, 400)
            except Exception as e:
                return self.send_json({"ok": False, "error": f"{type(e).__name__}: {e}"}, 500)
        return original_post(self)

    Handler.do_GET = do_GET
    Handler.do_POST = do_POST
    Handler._kernel_constructor_r1 = True
    return Handler
PY

cat > "$APP/kernel-constructor-r1.css" <<'CSS'
.kc-shell{display:grid;grid-template-columns:minmax(0,1.4fr) minmax(300px,.8fr);gap:14px;align-items:start}
.kc-full{grid-column:1/-1}.kc-metrics{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}.kc-metric{padding:14px;border:1px solid var(--line,#31506a);border-radius:14px;background:rgba(7,21,38,.55)}.kc-metric strong{display:block;font-size:1.45rem;color:#efd077}.kc-metric span{display:block;font-size:.78rem;letter-spacing:.08em;text-transform:uppercase;opacity:.72;margin-top:4px}.kc-status{display:inline-flex;align-items:center;gap:7px}.kc-dot{width:9px;height:9px;border-radius:50%;background:#6ee7a8;box-shadow:0 0 0 3px rgba(110,231,168,.12)}.kc-dot.off{background:#b5bdc8;box-shadow:none}.kc-field-wrap{overflow-x:auto;padding-bottom:6px}.kc-field{display:grid;grid-template-columns:minmax(124px,1.7fr) repeat(12,minmax(34px,1fr));gap:5px;min-width:620px;align-items:center}.kc-axis{font-size:.72rem;opacity:.78;text-transform:lowercase}.kc-band{text-align:center;font-size:.68rem;opacity:.55}.kc-cell{min-height:34px;border:1px solid rgba(83,142,184,.32);border-radius:8px;background:rgba(34,91,130,calc(.08 + var(--kc-heat,0)*.55));color:inherit;font-size:.66rem}.kc-cell.active{outline:2px solid #d6b55a;outline-offset:1px}.kc-work{display:grid;grid-template-columns:minmax(0,1.1fr) minmax(250px,.9fr);gap:12px}.kc-list{display:grid;gap:7px;max-height:520px;overflow:auto;padding-right:2px}.kc-node{width:100%;text-align:left;padding:10px 12px;border:1px solid rgba(117,151,175,.24);border-radius:11px;background:rgba(8,24,40,.55);color:inherit}.kc-node.active{border-color:#d6b55a}.kc-node small{display:block;opacity:.65;margin-bottom:4px}.kc-detail{min-height:160px}.kc-traits{display:grid;grid-template-columns:1fr auto;gap:6px 12px;font-size:.78rem}.kc-ctor{position:sticky;top:8px}.kc-chat{display:grid;gap:8px;max-height:430px;overflow:auto;margin:10px 0}.kc-msg{padding:10px 12px;border:1px solid rgba(117,151,175,.22);border-radius:12px;background:rgba(10,27,43,.62);white-space:pre-wrap}.kc-msg.user{border-color:rgba(214,181,90,.45)}.kc-msg b{display:block;font-size:.7rem;letter-spacing:.08em;text-transform:uppercase;margin-bottom:5px;opacity:.7}.kc-actions{display:flex;flex-wrap:wrap;gap:8px}.kc-compose textarea{min-height:100px;resize:vertical}.kc-note{font-size:.76rem;opacity:.72}.kc-offline{padding:10px;border:1px dashed rgba(181,189,200,.4);border-radius:10px}.kc-loading{padding:18px;text-align:center;opacity:.72}
@media(max-width:900px){.kc-shell,.kc-work{grid-template-columns:1fr}.kc-ctor{position:static}.kc-metrics{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media(max-width:430px){.kc-metrics{grid-template-columns:1fr 1fr}.kc-metric{padding:11px}.kc-actions>*{flex:1 1 145px}}
CSS

cat > "$APP/kernel-constructor-r1.js" <<'JS'
'use strict';

async function kernel(){
  setHead('Kernel Computer R2','Golden Field 144 · candidate computation, provenance, and Constructor assistance.','THUNDERFORGE · KERNEL COMPUTER R2');
  view.innerHTML=`<article class="card full"><div class="kc-loading">Loading Kernel Computer R2…</div></article>`;
  const api=async(path,options={})=>{
    const r=await fetch(path,{cache:'no-store',...options});
    let j={};try{j=await r.json()}catch{j={ok:false,error:'Invalid local response'}}
    if(!r.ok)throw new Error(j.error||`Local request failed (${r.status})`);return j;
  };
  let data=null,ctor={ok:false},selected=null,cell=null;
  try{data=await api('/api/r10/kernel/field')}catch(e){view.innerHTML=`<article class="card full"><h3>Kernel Computer unavailable</h3><p class="muted">${esc(e.message)}</p><p class="tiny">Install or start Kernel Computer R2, then refresh this page.</p></article>`;return}
  try{ctor=await api('/api/r10/constructor/status')}catch(_e){ctor={ok:false}}
  const s=data.status||{},axes=data.axes||[],nodes=Array.isArray(data.nodes)?data.nodes:[],cells=(Array.isArray(data.cells)&&data.cells.length===144)?data.cells:axes.flatMap((axis,ai)=>Array.from({length:12},(_,band)=>({cell:ai*12+band,axis,band,count:0})));
  const num=v=>Number(v||0).toLocaleString();
  const truth=s.truth_boundary||'Computation produces candidates; candidates are not facts.';
  view.innerHTML=`<div class="kc-shell">
    <article class="card kc-full"><div class="split"><div><div class="eyebrow">GOLDEN FIELD 144</div><h3>Kernel Computer R2</h3><p class="muted">${esc(truth)}</p></div><div class="row"><span class="badge good">LOCAL</span><button class="secondary" id="kcRefresh" type="button">Refresh</button></div></div><div class="kc-metrics" id="kcMetrics"></div></article>
    <article class="card kc-full"><div class="split"><div><div class="eyebrow">REGISTER SPACE</div><h3>12 traits × 12 bands</h3></div><div class="tiny" id="kcCellReadout">Tap a cell to filter</div></div><div class="kc-field-wrap"><div class="kc-field" id="kcField"></div></div></article>
    <article class="card"><div class="split"><div><h3>Working memory</h3><div class="meta" id="kcCount"></div></div><button class="secondary" id="kcClear" type="button">Clear cell</button></div><input id="kcSearch" type="search" placeholder="Search statement, domain, tag" style="margin-top:10px"><div class="kc-work" style="margin-top:10px"><div class="kc-list" id="kcList"></div><div class="kc-detail" id="kcDetail"><div class="empty">Select a kernel to inspect it.</div></div></div></article>
    <aside class="card kc-ctor"><div class="split"><div><div class="eyebrow">CONSTRUCTOR</div><h3>Your local helper</h3></div><span class="kc-status"><i class="kc-dot ${ctor.ok?'':'off'}" id="kcCtorDot"></i><span id="kcCtorState">${ctor.ok?'ONLINE':'OFFLINE'}</span></span></div><p class="kc-note">Constructor can explain a selected kernel, look for weaknesses, and suggest a next test. It does not silently promote candidates or rewrite projects.</p><div id="kcCtorOffline" class="kc-offline" ${ctor.ok?'hidden':''}><div class="muted">Constructor is not running.</div><button class="secondary" id="kcCtorStart" type="button" style="margin-top:8px">Start Constructor</button></div><div class="kc-chat" id="kcChat"><div class="kc-msg"><b>Constructor</b>I’m here to help with the Kernel Computer. Select a kernel or ask a question.</div></div><div class="kc-actions"><button class="secondary" id="kcExplain" type="button">Explain selected</button><button class="secondary" id="kcWeak" type="button">Check weaknesses</button><button class="secondary" id="kcNext" type="button">Suggest next test</button></div><form class="kc-compose" id="kcCtorForm" style="margin-top:10px"><label for="kcCtorInput">Ask Constructor</label><textarea id="kcCtorInput" maxlength="8000" placeholder="What should we test next?"></textarea><label class="tiny"><input id="kcUseContext" type="checkbox" checked> Include selected kernel context</label><div class="row" style="margin-top:8px"><button class="primary" id="kcSend" type="submit">Ask Constructor</button><button class="secondary" id="kcSpeak" type="button">Read last reply</button></div></form></aside>
    <article class="card kc-full"><div class="split"><div><h3>Growth cycles</h3><div class="meta">Reviewable history only · no automatic fact promotion</div></div></div><div class="list" id="kcCycles"></div></article>
  </div>`;

  $('#kcMetrics').innerHTML=[
    [s.nodes_total,'all kernels'],[s.origins?.R1,'R1 memory'],[s.origins?.R2,'R2 grown'],[s.origins?.WEB,'web claims'],[s.cycles_total,'cycles'],[s.tensions_total,'tensions']
  ].map(([v,l])=>`<div class="kc-metric"><strong>${num(v)}</strong><span>${esc(l)}</span></div>`).join('');

  const max=Math.max(1,...cells.map(c=>Number(c.count||0)));
  const renderField=()=>{
    let h='<div></div>'+Array.from({length:12},(_,b)=>`<div class="kc-band">${b+1}</div>`).join('');
    axes.forEach((axis,ai)=>{h+=`<div class="kc-axis">${esc(String(axis).replaceAll('_',' '))}</div>`;for(let b=0;b<12;b++){const c=cells[ai*12+b]||{cell:ai*12+b,axis,band:b,count:0};const heat=.06+.88*Math.log1p(Number(c.count||0))/Math.log1p(max);h+=`<button class="kc-cell ${cell===c.cell?'active':''}" style="--kc-heat:${heat.toFixed(3)}" data-kc-cell="${c.cell}" type="button" aria-label="${esc(axis)} band ${b+1}, ${num(c.count)} kernels">${c.count?num(c.count):''}</button>`}});$('#kcField').innerHTML=h;$('#kcField').querySelectorAll('[data-kc-cell]').forEach(b=>b.addEventListener('click',()=>{cell=Number(b.dataset.kcCell);const c=cells[cell];$('#kcCellReadout').textContent=`${String(c.axis).replaceAll('_',' ')} · band ${Number(c.band)+1} · ${num(c.count)} kernels`;renderField();renderNodes()}));
  };
  const filtered=()=>{const q=$('#kcSearch').value.trim().toLowerCase();return nodes.filter(n=>(cell===null||Number(n.cell)===cell)&&(!q||[n.statement,n.domain,...(n.tags||[])].join(' ').toLowerCase().includes(q)))};
  const renderNodes=()=>{const rows=filtered();$('#kcCount').textContent=`${num(rows.length)} shown from ${num(nodes.length)}-kernel console window`;$('#kcList').innerHTML=rows.slice(0,180).map(n=>`<button class="kc-node ${selected?.kernel_id===n.kernel_id?'active':''}" data-kc-id="${esc(n.kernel_id)}" type="button"><small>${esc(n.origin)} · G${esc(n.generation)} · ${esc(n.operator||n.kernel_kind||'kernel')} · ${Math.round(Number(n.confidence||0)*100)}%</small>${esc(n.statement)}</button>`).join('')||'<div class="empty">No kernels match this view.</div>';$('#kcList').querySelectorAll('[data-kc-id]').forEach(b=>b.addEventListener('click',()=>selectNode(b.dataset.kcId)))};
  const selectNode=async id=>{selected=nodes.find(n=>n.kernel_id===id)||null;try{selected=await api('/api/r10/kernel/node?id='+encodeURIComponent(id))}catch(_e){};renderNodes();if(!selected)return;const traits=selected.traits||{};$('#kcDetail').innerHTML=`<div class="eyebrow">PROVENANCE INSPECTOR</div><h3>${esc(selected.kernel_id)}</h3><p>${esc(selected.statement)}</p><div class="tiny">${esc(selected.truth_status||'candidate')} · ${esc(selected.evidence_state||'unverified')} · ${esc(selected.domain||'')}</div><div class="kc-traits" style="margin-top:10px">${Object.entries(traits).map(([k,v])=>`<span>${esc(k.replaceAll('_',' '))}</span><b>${Math.round(Number(v||0)*100)}</b>`).join('')}</div><h4>Parents</h4><div class="tiny">${(selected.parents||[]).length?(selected.parents||[]).map(p=>esc(typeof p==='string'?p:(p.parent_id||''))).join('<br>'):'Atomic/source kernel'}</div>`};
  const ctx=()=>selected?`Selected Kernel Computer R2 context:\nID: ${selected.kernel_id}\nOrigin: ${selected.origin||''}\nGeneration: ${selected.generation??''}\nTruth status: ${selected.truth_status||''}\nEvidence state: ${selected.evidence_state||''}\nDomain: ${selected.domain||''}\nStatement: ${selected.statement||''}\n\n`:'';
  let lastReply='';
  const appendMsg=(who,text,klass='')=>{const d=document.createElement('div');d.className='kc-msg '+klass;d.innerHTML=`<b>${esc(who)}</b>${esc(text)}`;$('#kcChat').appendChild(d);$('#kcChat').scrollTop=$('#kcChat').scrollHeight};
  const setCtor=online=>{$('#kcCtorDot').classList.toggle('off',!online);$('#kcCtorState').textContent=online?'ONLINE':'OFFLINE';$('#kcCtorOffline').hidden=online};
  const ask=async text=>{text=String(text||'').trim();if(!text)return;appendMsg('You',text,'user');$('#kcSend').disabled=true;try{const use=$('#kcUseContext').checked;const message=(use?ctx():'')+text;const r=await api('/api/r10/constructor/chat',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({message})});lastReply=String(r.reply||r.answer||r.message||'Constructor returned no reply.');appendMsg('Constructor',lastReply);setCtor(true)}catch(e){appendMsg('Constructor',e.message);setCtor(false)}finally{$('#kcSend').disabled=false}};
  $('#kcCtorForm').addEventListener('submit',e=>{e.preventDefault();const t=$('#kcCtorInput').value;$('#kcCtorInput').value='';ask(t)});
  $('#kcExplain').addEventListener('click',()=>selected?ask('Explain this kernel in plain English. Separate what is known from what is only a candidate, and tell me what would verify it.'):toast('Select a kernel first.'));
  $('#kcWeak').addEventListener('click',()=>selected?ask('Stress-test this kernel. Identify contradictions, weak assumptions, missing evidence, and the safest way to falsify it.'):toast('Select a kernel first.'));
  $('#kcNext').addEventListener('click',()=>ask(selected?'Suggest one concrete next test for this selected kernel. Keep it bounded, reversible, and evidence-producing.':'Suggest the highest-value next review or test for the current Kernel Computer field.'));
  $('#kcCtorStart').addEventListener('click',async()=>{const b=$('#kcCtorStart');b.disabled=true;b.textContent='Starting…';try{await api('/api/r10/constructor/start',{method:'POST',headers:{'Content-Type':'application/json'},body:'{}'});for(let i=0;i<12;i++){await new Promise(r=>setTimeout(r,700));try{const st=await api('/api/r10/constructor/status');if(st.ok){ctor=st;setCtor(true);appendMsg('Constructor','Constructor is online.');break}}catch(_e){}}}catch(e){appendMsg('Constructor',e.message)}finally{b.disabled=false;b.textContent='Start Constructor'}});
  $('#kcSpeak').addEventListener('click',()=>{if(!lastReply)return toast('No Constructor reply yet');if('speechSynthesis' in window){speechSynthesis.cancel();speechSynthesis.speak(new SpeechSynthesisUtterance(lastReply))}else toast('Speech is not available in this browser')});
  $('#kcSearch').addEventListener('input',renderNodes);$('#kcClear').addEventListener('click',()=>{cell=null;$('#kcCellReadout').textContent='Tap a cell to filter';renderField();renderNodes()});$('#kcRefresh').addEventListener('click',()=>go('kernel'));
  $('#kcCycles').innerHTML=(data.cycles||[]).map(c=>`<div class="item split"><div><strong>${esc(c.status||'cycle')}</strong><div class="meta">${num(c.accepted)} grown · ${num(c.rejected)} gated</div></div><span class="tiny">${esc(c.cycle_id||'')}</span></div>`).join('')||'<div class="empty">No growth cycles yet.</div>';
  renderField();renderNodes();
}
JS

python - "$APP" <<'PYPATCH'
from pathlib import Path
import sys
root=Path(sys.argv[1])
index=root/'index.html'; app=root/'app.js'; bridge=root/'r10_bridge.py'; sw=root/'sw.js'

s=index.read_text(encoding='utf-8')
if 'kernel-constructor-r1.css' not in s:
    anchor='<link rel="stylesheet" href="styles.css">'
    if anchor not in s: raise SystemExit('index.html stylesheet anchor missing')
    s=s.replace(anchor,anchor+'\n  <link rel="stylesheet" href="kernel-constructor-r1.css">',1)
if 'kernel-constructor-r1.js' not in s:
    anchor='  <script src="app.js"></script>'
    if anchor not in s: raise SystemExit('index.html app.js anchor missing')
    s=s.replace(anchor,'  <script src="kernel-constructor-r1.js"></script>\n'+anchor,1)
index.write_text(s,encoding='utf-8')

s=app.read_text(encoding='utf-8')
if "['kernel','Kernel Computer']" not in s:
    old="['home','Home'],['mywork','My Work'],['search','Search']"
    new="['home','Home'],['mywork','My Work'],['kernel','Kernel Computer'],['search','Search']"
    if old not in s: raise SystemExit('app.js NAV anchor missing')
    s=s.replace(old,new,1)
if 'home,mywork,kernel,search' not in s:
    old='const ROUTES={home,mywork,search,'
    if old not in s: raise SystemExit('app.js ROUTES anchor missing')
    s=s.replace(old,'const ROUTES={home,mywork,kernel,search,',1)
if "appTile('kernel','Kernel Computer'" not in s:
    old="${appTile('mywork','My Work','197K FILE CORPUS · LINEAGE','⌘','gold')}${appTile('projects','Projects','PLAN · BUILD · LAUNCH','◆','blue')}"
    new="${appTile('mywork','My Work','197K FILE CORPUS · LINEAGE','⌘','gold')}${appTile('kernel','Kernel Computer','COMPUTE · VERIFY · CONSTRUCT','K','amber')}${appTile('projects','Projects','PLAN · BUILD · LAUNCH','◆','blue')}"
    if old not in s: raise SystemExit('app.js home tile anchor missing')
    s=s.replace(old,new,1)
app.write_text(s,encoding='utf-8')

s=bridge.read_text(encoding='utf-8')
marker='# THUNDERFORGE_KERNEL_CONSTRUCTOR_BRIDGE_R1\nimport kernel_constructor_bridge_r1 as _kernel_constructor_bridge_r1\n_kernel_constructor_bridge_r1.install_into_handler(Handler)\n\n'
if 'THUNDERFORGE_KERNEL_CONSTRUCTOR_BRIDGE_R1' not in s:
    anchor='def main():'
    if anchor not in s: raise SystemExit('r10_bridge.py main anchor missing')
    s=s.replace(anchor,marker+anchor,1)
bridge.write_text(s,encoding='utf-8')

s=sw.read_text(encoding='utf-8')
s=s.replace("const CACHE='thunderforge-r10-unified-verified-candidate-v1';","const CACHE='thunderforge-r10-unified-verified-candidate-kernel-constructor-r1';")
if "'./kernel-constructor-r1.js'" not in s:
    old="'./','./index.html','./styles.css','./heritage.js'"
    new="'./','./index.html','./styles.css','./kernel-constructor-r1.css','./kernel-constructor-r1.js','./heritage.js'"
    if old not in s: raise SystemExit('sw.js CORE anchor missing')
    s=s.replace(old,new,1)
sw.write_text(s,encoding='utf-8')
print('R10 UI + bridge patch: PASS')
PYPATCH

python -m py_compile "$APP/r10_bridge.py" "$APP/kernel_constructor_bridge_r1.py"
if command -v node >/dev/null 2>&1; then
  node --check "$APP/app.js"
  node --check "$APP/kernel-constructor-r1.js"
  node --check "$APP/sw.js"
fi

cat > "$BIN/thunderforge-homebase-kernel-open" <<'OPEN'
#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail
SEED="${SEED_ROOT:-$HOME/Seed}"
PORT="${TF_R10_PORT:-8941}"
"$SEED/bin/thunderforge-r10-start"
URL="http://127.0.0.1:$PORT/index.html?r10-candidate=1#kernel"
if command -v termux-open-url >/dev/null 2>&1; then
  termux-open-url "$URL"
else
  am start -a android.intent.action.VIEW -d "$URL" >/dev/null 2>&1 || true
  echo "Open: $URL"
fi
OPEN
chmod 700 "$BIN/thunderforge-homebase-kernel-open"

if [ "$TEST_ONLY" = "1" ]; then
  say "TEST-ONLY PATCH: PASS"
  exit 0
fi

"$BIN/thunderforge-r10-start"
PORT="${TF_R10_PORT:-8941}"
python - "$PORT" <<'PYVERIFY'
import json,sys,urllib.request
p=int(sys.argv[1])
def get(path):
    with urllib.request.urlopen(f'http://127.0.0.1:{p}{path}',timeout=8) as r:
        return r.status,json.load(r)
h,j=get('/api/r10/health'); assert h==200 and j.get('bridge')=='r10'
h,k=get('/api/r10/kernel/field'); assert h==200 and isinstance(k.get('cells'),list) and len(k.get('cells'))==144
h,c=get('/api/r10/constructor/status'); assert h==200
print('R10 bridge: PASS')
print('Kernel Computer field: PASS · 144 cells')
print('Constructor:', 'ONLINE' if c.get('ok') else 'READY TO START FROM UI')
PYVERIFY

say ""
say "INSTALL: PASS"
say "Kernel Computer R2 is now a first-class Home Base route."
say "Constructor is docked inside it as the local helper."
say "Open with:"
say "  thunderforge-homebase-kernel-open"
say "or refresh Home Base and tap KERNEL COMPUTER."
say "Rollback source backup: $BACK"
