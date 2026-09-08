#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

# Home Base R10 Integration Pack
# Additive/non-destructive: creates a new R10 control center beside existing Home Base/Fieldworks.
# It never exposes an arbitrary shell-command API.

ROOT="${HOME}/Seed/homebase-r10"
BIN="${HOME}/Seed/bin"
DATA="${ROOT}/data"
LOGS="${ROOT}/logs"
INBOX="${ROOT}/inbox"
EXPORTS="${ROOT}/exports"
PORT="${HOMEBASE_R10_PORT:-8950}"

mkdir -p "$ROOT" "$BIN" "$DATA" "$LOGS" "$INBOX" "$EXPORTS"

cat > "$ROOT/server.py" <<'PY'
#!/usr/bin/env python3
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path
from urllib.parse import urlparse, parse_qs
import json, os, re, shutil, subprocess, time, zipfile

HOME = Path.home()
ROOT = HOME / "Seed" / "homebase-r10"
DATA = ROOT / "data"
INBOX = ROOT / "inbox"
EXPORTS = ROOT / "exports"
LOGS = ROOT / "logs"
PORT = int(os.environ.get("HOMEBASE_R10_PORT", "8950"))

SAFE_ROOTS = [
    HOME / "Seed",
    HOME / "storage" / "downloads",
    HOME / "storage" / "shared" / "Documents",
    HOME / "storage" / "shared" / "Download",
    HOME / "storage" / "shared" / "Games",
]
ROM_EXTS = {
    ".nds",".3ds",".cia",".gba",".gb",".gbc",".sfc",".smc",".nes",".n64",".z64",
    ".iso",".chd",".cue",".bin",".pbp",".rvz",".wbfs",".gcz",".wad",".zip",".7z"
}

def now():
    return time.strftime("%Y-%m-%d %H:%M:%S")

def is_within(path: Path, roots=SAFE_ROOTS):
    try:
        rp = path.expanduser().resolve()
    except Exception:
        return False
    for root in roots:
        try:
            rr = root.resolve()
            if rp == rr or rr in rp.parents:
                return True
        except Exception:
            pass
    return False

def clean_name(name):
    name = Path(name).name
    return re.sub(r"[^A-Za-z0-9._() +\-\[\]]", "_", name)[:180] or "imported_file"

def scan_games(limit=600):
    found = []
    roots = [
        HOME/"storage"/"downloads",
        HOME/"storage"/"shared"/"Download",
        HOME/"storage"/"shared"/"Games",
        HOME/"Seed"/"games",
        HOME/"Seed",
    ]
    seen = set()
    for root in roots:
        if not root.exists():
            continue
        try:
            for p in root.rglob("*"):
                if len(found) >= limit:
                    break
                if not p.is_file() or p.suffix.lower() not in ROM_EXTS:
                    continue
                rp = str(p.resolve())
                if rp in seen:
                    continue
                seen.add(rp)
                found.append({
                    "name": p.name,
                    "path": rp,
                    "ext": p.suffix.lower(),
                    "size_mb": round(p.stat().st_size/1024/1024, 1),
                })
        except Exception:
            continue
    found.sort(key=lambda x: x["name"].lower())
    return found

def project_score(p):
    markers = [
        "package.json","pyproject.toml","requirements.txt","src","app","README.md",
        "index.html","main.py","Makefile",".git","Cargo.toml","build.gradle"
    ]
    score = sum(1 for m in markers if (p/m).exists())
    if any(k in p.name.lower() for k in ("project","garden","homebase","fieldworks","thunderforge")):
        score += 1
    return score

def scan_projects(limit=240):
    roots = [HOME/"Seed", HOME/"storage"/"downloads"]
    out, seen = [], set()
    for root in roots:
        if not root.exists():
            continue
        try:
            candidates = [root] + [x for x in root.iterdir() if x.is_dir()]
        except Exception:
            candidates = []
        for p in candidates:
            try:
                rp = str(p.resolve())
                if rp in seen:
                    continue
                seen.add(rp)
                score = project_score(p)
                if score >= 2:
                    out.append({
                        "name": p.name,
                        "path": rp,
                        "score": score,
                        "modified": time.strftime("%Y-%m-%d %H:%M", time.localtime(p.stat().st_mtime)),
                    })
            except Exception:
                pass
            if len(out) >= limit:
                break
    out.sort(key=lambda x: (-x["score"], x["name"].lower()))
    return out

def scan_files(base=None, limit=400):
    p = Path(base).expanduser() if base else HOME/"Seed"
    if not is_within(p):
        p = HOME/"Seed"
    items = []
    try:
        for x in p.iterdir():
            try:
                items.append({
                    "name": x.name,
                    "path": str(x.resolve()),
                    "dir": x.is_dir(),
                    "size": None if x.is_dir() else x.stat().st_size,
                    "modified": time.strftime("%Y-%m-%d %H:%M", time.localtime(x.stat().st_mtime)),
                })
            except Exception:
                pass
    except Exception:
        pass
    items.sort(key=lambda x: (not x["dir"], x["name"].lower()))
    return {"base": str(p.resolve()), "items": items[:limit]}

def module_status():
    checks = [
        ("Home Base seed", HOME/"Seed"),
        ("Fieldworks opener", HOME/"Seed"/"bin"/"thunderforge-fieldworks-open"),
        ("Home Base opener", HOME/"Seed"/"bin"/"thunderforge-homebase-open"),
        ("Games area", HOME/"Seed"/"games"),
        ("Garden Immortals", HOME/"Seed"/"grovenaut-r29"/"games"/"garden-immortals"),
        ("Downloads", HOME/"storage"/"downloads"),
        ("R10 inbox", INBOX),
        ("R10 exports", EXPORTS),
    ]
    return [{"name": name, "ok": p.exists(), "path": str(p)} for name, p in checks]

def inspect_project(path):
    p = Path(path).expanduser()
    if not is_within(p) or not p.is_dir():
        return {"ok": False, "error": "Project path is outside allowed roots or is not a directory."}
    report = {"ok": True, "project": str(p.resolve()), "time": now(), "markers": {}, "checks": []}
    markers = ["package.json","pyproject.toml","requirements.txt","README.md","index.html","main.py","Makefile","Cargo.toml","build.gradle"]
    for m in markers:
        report["markers"][m] = (p/m).exists()

    pyfiles = list(p.rglob("*.py"))[:300]
    py_bad = []
    import py_compile
    for f in pyfiles:
        try:
            py_compile.compile(str(f), doraise=True)
        except Exception as e:
            py_bad.append({"file": str(f), "error": str(e)[:300]})
    report["checks"].append({
        "name": "python-compile",
        "count": len(pyfiles),
        "failures": py_bad[:30],
        "ok": not py_bad,
    })

    jsonfiles = list(p.rglob("*.json"))[:300]
    json_bad = []
    for f in jsonfiles:
        try:
            json.loads(f.read_text(errors="replace"))
        except Exception as e:
            json_bad.append({"file": str(f), "error": str(e)[:300]})
    report["checks"].append({
        "name": "json-parse",
        "count": len(jsonfiles),
        "failures": json_bad[:30],
        "ok": not json_bad,
    })

    files = [f for f in p.rglob("*") if f.is_file()]
    report["file_count"] = len(files)
    total = 0
    for f in files:
        try:
            total += f.stat().st_size
        except Exception:
            pass
    report["size_mb"] = round(total/1024/1024, 2)
    return report

def finish_project(path):
    p = Path(path).expanduser()
    report = inspect_project(path)
    if not report.get("ok"):
        return report

    inv = []
    for f in list(p.rglob("*"))[:5000]:
        try:
            if f.is_file():
                inv.append(str(f.relative_to(p)))
        except Exception:
            pass

    report["inventory_count"] = len(inv)
    out = p/"R10_AUTOFINISH_REPORT.json"
    out.write_text(json.dumps(report, indent=2, ensure_ascii=False))
    invout = p/"R10_FILE_INVENTORY.txt"
    invout.write_text("\n".join(inv))

    stamp = time.strftime("%Y%m%d_%H%M%S")
    zpath = EXPORTS/f"{clean_name(p.name)}_r10_snapshot_{stamp}.zip"
    total = 0
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        for f in p.rglob("*"):
            if not f.is_file():
                continue
            try:
                if f.stat().st_size > 8*1024*1024:
                    continue
                if f.suffix.lower() in {".mp4",".mkv",".iso",".chd",".zip",".7z",".apk",".obb"}:
                    continue
                z.write(f, f.relative_to(p))
                total += 1
                if total >= 2500:
                    break
            except Exception:
                pass

    report["generated"] = [str(out), str(invout), str(zpath)]
    report["message"] = (
        "Safe finish completed: validation, inventory, report, and recovery snapshot. "
        "No arbitrary project code was executed."
    )
    return report

def open_external(path):
    p = Path(path).expanduser()
    if not is_within(p) or not p.exists():
        return {"ok": False, "error": "Path is outside allowed roots or missing."}
    if shutil.which("termux-open"):
        cmd = ["termux-open", str(p)]
    elif shutil.which("xdg-open"):
        cmd = ["xdg-open", str(p)]
    else:
        return {
            "ok": False,
            "error": "No Android opener found. Install/configure Termux:API for termux-open, or open the file manually."
        }
    try:
        subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        return {"ok": True, "message": "Sent to Android app chooser/default app.", "path": str(p)}
    except Exception as e:
        return {"ok": False, "error": str(e)}

def extract_import(path):
    p = Path(path)
    if p.suffix.lower() != ".zip":
        return {"ok": False, "error": "Only ZIP extraction is supported by the safe importer."}
    dest = INBOX/(p.stem+"_extracted")
    dest.mkdir(parents=True, exist_ok=True)
    try:
        with zipfile.ZipFile(p) as z:
            for m in z.infolist():
                target = (dest/m.filename).resolve()
                if dest.resolve() not in target.parents and target != dest.resolve():
                    return {"ok": False, "error": "ZIP contains an unsafe path."}
            z.extractall(dest)
        return {"ok": True, "path": str(dest)}
    except Exception as e:
        return {"ok": False, "error": str(e)}

HTML = r'''<!doctype html>
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Home Base R10</title>
<style>
:root{font-family:system-ui,-apple-system,sans-serif;color-scheme:dark;background:#0b0e13;color:#eef2f7}
body{margin:0;background:#0b0e13}.wrap{max-width:1100px;margin:auto;padding:16px}
h1{font-size:24px;margin:0 0 6px}.sub{color:#aeb8c7;margin-bottom:14px}
.tabs{display:flex;gap:8px;flex-wrap:wrap;position:sticky;top:0;background:#0b0e13;padding:8px 0;z-index:2}
button,.btn,input{font:inherit}.tab,.btn{border:1px solid #334155;background:#161c26;color:#eef2f7;border-radius:12px;padding:11px 14px;min-height:44px}
.tab.active{background:#263348}.panel{display:none}.panel.active{display:block}
.card{border:1px solid #273244;background:#111722;border-radius:14px;padding:14px;margin:10px 0}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:10px}
.row{display:flex;gap:8px;align-items:center;flex-wrap:wrap}.grow{flex:1;min-width:0}
.small{font-size:13px;color:#9eabbc}.ok{color:#7fe0a1}.bad{color:#ff9f9f}
input[type=text]{width:100%;box-sizing:border-box;background:#0d131c;border:1px solid #334155;color:#fff;border-radius:10px;padding:11px}
pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#090d13;border-radius:10px;padding:12px;max-height:420px;overflow:auto}
.item{padding:10px 0;border-bottom:1px solid #202b3a}.item:last-child{border:0}
.drop{border:2px dashed #3b4b61;border-radius:16px;padding:22px;text-align:center}
.badge{font-size:12px;border:1px solid #334155;border-radius:99px;padding:3px 8px;color:#b9c5d5}
a{color:#9ec5ff}.hero{display:flex;gap:10px;flex-wrap:wrap;margin:12px 0}
.hero .btn{font-weight:700}
</style>
<div class="wrap" id="app">
  <h1>Home Base R10 — Integration Center</h1>
  <div class="sub">Games • Files • Imports • Safe Auto-Finish • Missing-System Recovery</div>

  <div class="hero">
    <button class="btn" id="refreshAll">Refresh everything</button>
    <button class="btn" id="openFieldworks">Open Fieldworks R2</button>
  </div>

  <div class="tabs" role="tablist">
    <button class="tab active" data-tab="status">Status</button>
    <button class="tab" data-tab="games">Games</button>
    <button class="tab" data-tab="files">Files</button>
    <button class="tab" data-tab="import">Import</button>
    <button class="tab" data-tab="projects">Projects</button>
    <button class="tab" data-tab="logs">Activity</button>
  </div>

  <section id="status" class="panel active"><div id="statusBody" class="grid"></div></section>

  <section id="games" class="panel">
    <div class="card">
      <b>Game launcher</b>
      <div class="small">Scans common ROM/game folders. Play/Open hands the file to Android so an installed emulator can claim it.</div>
    </div>
    <div id="gamesBody"></div>
  </section>

  <section id="files" class="panel">
    <div class="card">
      <label>Folder path<input id="filePath" type="text" value="~/Seed"></label>
      <div class="row" style="margin-top:8px">
        <button class="btn" id="browseBtn">Browse</button>
        <button class="btn" id="upBtn">Up</button>
      </div>
    </div>
    <div id="filesBody"></div>
  </section>

  <section id="import" class="panel">
    <div class="card drop">
      <b>Input files to Home Base</b>
      <p class="small">Choose a file from Android. It is copied into ~/Seed/homebase-r10/inbox. ZIPs can be safely extracted without executing code.</p>
      <input id="upload" type="file">
      <div id="uploadMsg" class="small"></div>
    </div>
    <div id="inboxBody"></div>
  </section>

  <section id="projects" class="panel">
    <div class="card">
      <b>Safe Auto-Finish</b>
      <p class="small">R10 validates Python/JSON, inventories the project, writes a report, and makes a recovery snapshot. It does not expose or execute arbitrary shell commands.</p>
    </div>
    <div id="projectsBody"></div>
  </section>

  <section id="logs" class="panel">
    <div class="card"><pre id="activity">R10 ready.</pre></div>
  </section>
</div>

<script>
const $=s=>document.querySelector(s), $$=s=>[...document.querySelectorAll(s)];
const activity=$("#activity");
function log(x){
  activity.textContent=`[${new Date().toLocaleTimeString()}] ${typeof x==="string"?x:JSON.stringify(x,null,2)}\n`+activity.textContent;
}
async function api(url,opt){
  const r=await fetch(url,opt);
  const j=await r.json();
  if(!r.ok) throw new Error(j.error||r.statusText);
  return j;
}
function esc(s){
  return String(s).replace(/[&<>"']/g,m=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[m]));
}
function pathParam(p){return encodeURIComponent(p)}

$$(".tab").forEach(b=>b.addEventListener("click",()=>{
  $$(".tab").forEach(x=>x.classList.remove("active"));
  $$(".panel").forEach(x=>x.classList.remove("active"));
  b.classList.add("active");
  $("#"+b.dataset.tab).classList.add("active");
}));

async function loadStatus(){
  const j=await api("/api/status");
  $("#statusBody").innerHTML=j.modules.map(x=>`
    <div class="card">
      <div class="${x.ok?"ok":"bad"}"><b>${x.ok?"✓":"○"} ${esc(x.name)}</b></div>
      <div class="small">${esc(x.path)}</div>
    </div>`).join("")+
    `<div class="card"><b>Detected games</b><div>${j.game_count}</div></div>
     <div class="card"><b>Detected projects</b><div>${j.project_count}</div></div>`;
}

async function loadGames(){
  const j=await api("/api/games");
  $("#gamesBody").innerHTML=j.games.length?j.games.map(g=>`
    <div class="card item"><div class="row">
      <div class="grow"><b>${esc(g.name)}</b><div class="small">${esc(g.path)} • ${g.size_mb} MB</div></div>
      <button class="btn play" data-p="${esc(g.path)}">Play/Open</button>
    </div></div>`).join(""):`<div class="card">No ROMs found in the scanned folders yet.</div>`;
  $$(".play").forEach(b=>b.addEventListener("click",async()=>{
    try{log(await api("/api/open?path="+pathParam(b.dataset.p),{method:"POST"}))}
    catch(e){log(e.message)}
  }));
}

async function browse(path){
  const j=await api("/api/files?path="+pathParam(path||$("#filePath").value));
  $("#filePath").value=j.base;
  $("#filesBody").innerHTML=j.items.map(x=>`
    <div class="card item"><div class="row">
      <div class="grow"><b>${x.dir?"📁":"📄"} ${esc(x.name)}</b>
      <div class="small">${esc(x.modified)}${x.size!=null?" • "+Math.round(x.size/1024)+" KB":""}</div></div>
      ${x.dir?`<button class="btn dir" data-p="${esc(x.path)}">Open folder</button>`:`<button class="btn ext" data-p="${esc(x.path)}">Open</button>`}
    </div></div>`).join("");
  $$(".dir").forEach(b=>b.addEventListener("click",()=>browse(b.dataset.p)));
  $$(".ext").forEach(b=>b.addEventListener("click",async()=>{
    try{log(await api("/api/open?path="+pathParam(b.dataset.p),{method:"POST"}))}
    catch(e){log(e.message)}
  }));
}

async function loadInbox(){
  const j=await api("/api/inbox");
  $("#inboxBody").innerHTML=j.items.map(x=>`
    <div class="card"><div class="row">
      <div class="grow"><b>${esc(x.name)}</b><div class="small">${esc(x.path)}</div></div>
      ${x.name.toLowerCase().endsWith(".zip")?`<button class="btn extract" data-p="${esc(x.path)}">Extract safely</button>`:""}
    </div></div>`).join("")||`<div class="card">Inbox is empty.</div>`;
  $$(".extract").forEach(b=>b.addEventListener("click",async()=>{
    try{log(await api("/api/extract?path="+pathParam(b.dataset.p),{method:"POST"}));loadInbox()}
    catch(e){log(e.message)}
  }));
}

async function loadProjects(){
  const j=await api("/api/projects");
  $("#projectsBody").innerHTML=j.projects.map(p=>`
    <div class="card"><div class="row">
      <div class="grow"><b>${esc(p.name)}</b><div class="small">${esc(p.path)} • marker score ${p.score}</div></div>
      <button class="btn inspect" data-p="${esc(p.path)}">Inspect</button>
      <button class="btn finish" data-p="${esc(p.path)}">Safe Finish</button>
    </div></div>`).join("")||`<div class="card">No project folders detected.</div>`;
  $$(".inspect").forEach(b=>b.addEventListener("click",async()=>{
    try{log(await api("/api/inspect?path="+pathParam(b.dataset.p),{method:"POST"}))}
    catch(e){log(e.message)}
  }));
  $$(".finish").forEach(b=>b.addEventListener("click",async()=>{
    try{
      b.disabled=true;b.textContent="Working…";
      log(await api("/api/finish?path="+pathParam(b.dataset.p),{method:"POST"}))
    }catch(e){log(e.message)}
    finally{b.disabled=false;b.textContent="Safe Finish"}
  }));
}

$("#browseBtn").addEventListener("click",()=>browse());
$("#upBtn").addEventListener("click",()=>{
  let p=$("#filePath").value.replace(/\/+$/,"").split("/");
  p.pop();
  browse(p.join("/")||"/");
});
$("#openFieldworks").addEventListener("click",()=>window.open("http://127.0.0.1:8941/index.html?fieldworks-r2=1#fieldworks","_blank"));
$("#refreshAll").addEventListener("click",async()=>{
  try{
    await Promise.all([loadStatus(),loadGames(),loadInbox(),loadProjects(),browse($("#filePath").value)]);
    log("Refreshed all R10 indexes.");
  }catch(e){log(e.message)}
});
$("#upload").addEventListener("change",async e=>{
  const f=e.target.files[0];
  if(!f)return;
  const msg=$("#uploadMsg");
  msg.textContent="Importing…";
  try{
    const r=await api("/api/import?name="+encodeURIComponent(f.name),{
      method:"POST",
      headers:{"Content-Type":"application/octet-stream"},
      body:await f.arrayBuffer()
    });
    msg.textContent="Imported: "+r.path;
    log(r);
    loadInbox();
  }catch(err){
    msg.textContent=err.message;
    log(err.message);
  }
});

Promise.all([loadStatus(),loadGames(),loadInbox(),loadProjects(),browse("~/Seed")])
  .then(()=>log("R10 indexes loaded."))
  .catch(e=>log(e.message));
</script>'''

class Handler(BaseHTTPRequestHandler):
    server_version = "HomeBaseR10/1.0"

    def send_json(self, obj, status=200):
        b = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    def send_html(self, s):
        b = s.encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    def qs(self):
        return parse_qs(urlparse(self.path).query)

    def do_GET(self):
        u = urlparse(self.path)
        if u.path in ("/", "/index.html"):
            return self.send_html(HTML)
        if u.path == "/api/status":
            return self.send_json({
                "modules": module_status(),
                "game_count": len(scan_games()),
                "project_count": len(scan_projects()),
                "time": now(),
            })
        if u.path == "/api/games":
            return self.send_json({"games": scan_games()})
        if u.path == "/api/projects":
            return self.send_json({"projects": scan_projects()})
        if u.path == "/api/files":
            return self.send_json(scan_files(self.qs().get("path", ["~/Seed"])[0]))
        if u.path == "/api/inbox":
            items = []
            for p in INBOX.iterdir():
                try:
                    items.append({"name": p.name, "path": str(p.resolve()), "dir": p.is_dir()})
                except Exception:
                    pass
            items.sort(key=lambda x: x["name"].lower())
            return self.send_json({"items": items})
        return self.send_json({"error": "Not found"}, 404)

    def do_POST(self):
        u = urlparse(self.path)
        q = self.qs()
        try:
            if u.path == "/api/open":
                return self.send_json(open_external(q.get("path", [""])[0]))
            if u.path == "/api/inspect":
                return self.send_json(inspect_project(q.get("path", [""])[0]))
            if u.path == "/api/finish":
                return self.send_json(finish_project(q.get("path", [""])[0]))
            if u.path == "/api/extract":
                return self.send_json(extract_import(q.get("path", [""])[0]))
            if u.path == "/api/import":
                name = clean_name(q.get("name", ["imported_file"])[0])
                length = int(self.headers.get("Content-Length", "0"))
                if length <= 0 or length > 2*1024*1024*1024:
                    return self.send_json({"error": "File is empty or over the 2 GB single-upload limit."}, 400)
                dest = INBOX/name
                stem, suf = dest.stem, dest.suffix
                i = 1
                while dest.exists():
                    dest = INBOX/f"{stem}_{i}{suf}"
                    i += 1
                remaining = length
                with dest.open("wb") as f:
                    while remaining:
                        chunk = self.rfile.read(min(1024*1024, remaining))
                        if not chunk:
                            break
                        f.write(chunk)
                        remaining -= len(chunk)
                if remaining:
                    try:
                        dest.unlink()
                    except Exception:
                        pass
                    return self.send_json({"error": "Upload ended early."}, 400)
                return self.send_json({"ok": True, "path": str(dest), "bytes": length})
        except Exception as e:
            return self.send_json({"error": str(e)}, 500)
        return self.send_json({"error": "Not found"}, 404)

    def log_message(self, fmt, *args):
        try:
            with (LOGS/"server.log").open("a") as f:
                f.write(f"{now()} {self.address_string()} {fmt % args}\n")
        except Exception:
            pass

if __name__ == "__main__":
    DATA.mkdir(parents=True, exist_ok=True)
    INBOX.mkdir(parents=True, exist_ok=True)
    EXPORTS.mkdir(parents=True, exist_ok=True)
    LOGS.mkdir(parents=True, exist_ok=True)
    print(f"Home Base R10: http://127.0.0.1:{PORT}")
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
PY

chmod +x "$ROOT/server.py"

cat > "$BIN/homebase-r10-open" <<'SH'
#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$HOME/Seed/homebase-r10"
PORT="${HOMEBASE_R10_PORT:-8950}"
PID="$ROOT/data/server.pid"
mkdir -p "$ROOT/data" "$ROOT/logs"
if [ -f "$PID" ] && kill -0 "$(cat "$PID")" 2>/dev/null; then
  :
else
  nohup env HOMEBASE_R10_PORT="$PORT" python "$ROOT/server.py" >>"$ROOT/logs/stdout.log" 2>&1 &
  echo $! > "$PID"
  sleep 0.8
fi
URL="http://127.0.0.1:${PORT}/"
echo "Home Base R10: $URL"
if command -v termux-open-url >/dev/null 2>&1; then
  termux-open-url "$URL"
else
  echo "$URL"
fi
SH
chmod +x "$BIN/homebase-r10-open"

cat > "$BIN/homebase-r10-stop" <<'SH'
#!/data/data/com.termux/files/usr/bin/bash
PID="$HOME/Seed/homebase-r10/data/server.pid"
if [ -f "$PID" ]; then
  kill "$(cat "$PID")" 2>/dev/null || true
  rm -f "$PID"
fi
echo "Home Base R10 stopped."
SH
chmod +x "$BIN/homebase-r10-stop"

cat > "$ROOT/README_R10.txt" <<'TXT'
HOME BASE R10 INTEGRATION PACK

What it adds:
- Games: scans common ROM folders and hands a selected file to Android/emulator.
- Files: browses safe Home Base / Download / Documents roots and opens files via Android.
- Import: browser file picker uploads into ~/Seed/homebase-r10/inbox.
- ZIP intake: safe extraction with path-traversal protection.
- Projects: detects project folders.
- Safe Auto-Finish: validates Python and JSON, inventories files, writes R10_AUTOFINISH_REPORT.json,
  writes R10_FILE_INVENTORY.txt, and creates a recovery snapshot in ~/Seed/homebase-r10/exports.
- Status: shows whether Home Base, Fieldworks, Garden Immortals, games, downloads, and R10 areas exist.
- Fieldworks button: opens the existing Fieldworks R2 address from the acceptance report.

Safety:
- R10 is additive and does not overwrite the existing Home Base.
- No arbitrary shell-command API is exposed.
- Imported projects are not executed automatically.
- External opening requires termux-open (normally from Termux:API); otherwise files stay untouched.

Open:
  ~/Seed/bin/homebase-r10-open

Stop:
  ~/Seed/bin/homebase-r10-stop

Default address:
  http://127.0.0.1:8950/
TXT

echo
echo "============================================================"
echo " Home Base R10 Integration Pack installed"
echo "============================================================"
echo "Open it with:"
echo "  ~/Seed/bin/homebase-r10-open"
echo
echo "R10 address:"
echo "  http://127.0.0.1:${PORT}/"
echo
echo "Existing Fieldworks R2 remains untouched on port 8941."
