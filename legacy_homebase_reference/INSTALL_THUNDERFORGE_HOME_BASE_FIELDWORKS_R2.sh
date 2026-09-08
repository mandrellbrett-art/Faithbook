#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

PACKAGE="${1:-$HOME/storage/downloads/Thunderforge_Home_Base_Fieldworks_R2.zip}"
SEED="${SEED_ROOT:-$HOME/Seed}"
APP="${TF_R10_APP:-$SEED/thunderforge-r10-unified-verified}"
BIN="${TF_SEED_BIN:-$SEED/bin}"
STATE="${TF_FIELDWORKS_STATE:-$HOME/Thunderforge-Atlas/fieldworks-r2}"
KERNEL_STATE="${TF_KERNEL_COMPUTER_STATE:-$HOME/Thunderforge-Atlas/kernel-computer-r2}"
BACKUP_ROOT="${TF_FIELDWORKS_BACKUP_ROOT:-$HOME/PrivateWorks-Backups}"
STAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP="$BACKUP_ROOT/homebase-fieldworks-r2-pre-$STAMP"
mkdir -p "$SEED"
TMP="$(mktemp -d "$SEED/.fieldworks-r2-install.XXXXXX")"

cleanup(){ case "$TMP" in "$SEED"/.fieldworks-r2-install.*) rm -rf -- "$TMP";; esac; }
trap cleanup EXIT
say(){ printf '%s\n' "$*"; }
die(){ say "ERROR: $*" >&2; exit 1; }

say "============================================================"
say " THUNDERFORGE HOME BASE · FIELDWORKS R2"
say " SAME-ORIGIN KERNEL COMPUTER + NORTHSTAR CONDUCTOR"
say "============================================================"
say "Package: $PACKAGE"
[ -f "$PACKAGE" ] || die "Package not found: $PACKAGE"
[ -d "$APP" ] || die "Home Base R10 not found: $APP"
for file in index.html app.js styles.css sw.js manifest.webmanifest r10_bridge.py; do [ -f "$APP/$file" ] || die "Home Base file missing: $APP/$file"; done
command -v python >/dev/null 2>&1 || pkg install -y python
mkdir -p "$BIN" "$STATE" "$BACKUP/files"

python - "$PACKAGE" "$TMP" <<'PY'
import sys,zipfile
from pathlib import Path,PurePosixPath
package=Path(sys.argv[1]);target=Path(sys.argv[2]);required={'Thunderforge_Home_Base_Fieldworks_R2/fieldworks_bridge_r2.py','Thunderforge_Home_Base_Fieldworks_R2/fieldworks-r2.js','Thunderforge_Home_Base_Fieldworks_R2/fieldworks-r2.css','Thunderforge_Home_Base_Fieldworks_R2/patch_homebase.py','Thunderforge_Home_Base_Fieldworks_R2/SHA256SUMS.txt','Thunderforge_Home_Base_Fieldworks_R2/termux/thunderforge-fieldworks-start','Thunderforge_Home_Base_Fieldworks_R2/termux/thunderforge-fieldworks-open','Thunderforge_Home_Base_Fieldworks_R2/termux/thunderforge-fieldworks-status','Thunderforge_Home_Base_Fieldworks_R2/termux/thunderforge-fieldworks-accept','Thunderforge_Home_Base_Fieldworks_R2/termux/thunderforge-fieldworks-rollback'}
with zipfile.ZipFile(package) as archive:
    bad=archive.testzip()
    if bad:raise SystemExit(f'ZIP integrity failed at {bad}')
    names=archive.namelist();missing=sorted(required.difference(names))
    if missing:raise SystemExit('Package members missing: '+', '.join(missing))
    for name in names:
        path=PurePosixPath(name)
        if path.is_absolute() or '..' in path.parts or '\\' in name:raise SystemExit(f'Unsafe archive member: {name}')
    archive.extractall(target)
print('Package integrity and member safety: PASS')
PY

SOURCE="$TMP/Thunderforge_Home_Base_Fieldworks_R2"
python - "$SOURCE" <<'PY'
import hashlib,sys
from pathlib import Path,PurePosixPath
root=Path(sys.argv[1]).resolve();manifest=root/'SHA256SUMS.txt';checked=0
for number,line in enumerate(manifest.read_text(encoding='utf-8').splitlines(),1):
    if not line.strip():continue
    expected,relative=line.split(None,1);relative=relative.strip().lstrip('*');posix=PurePosixPath(relative)
    if posix.is_absolute() or '..' in posix.parts or '\\' in relative:raise SystemExit(f'Unsafe checksum path on line {number}: {relative}')
    candidate=(root/relative).resolve();candidate.relative_to(root)
    if not candidate.is_file():raise SystemExit(f'Checksummed file missing: {relative}')
    if hashlib.sha256(candidate.read_bytes()).hexdigest()!=expected.lower():raise SystemExit(f'Checksum mismatch: {relative}')
    checked+=1
if checked<8:raise SystemExit('Internal manifest is unexpectedly small')
print(f'Internal SHA-256 manifest: PASS ({checked} files)')
PY

if [ -x "$BIN/thunderforge-r10-stop" ]; then "$BIN/thunderforge-r10-stop" >/dev/null 2>&1 || true; fi

python - "$APP" "$BACKUP" <<'PY'
import hashlib,json,shutil,sys,time
from pathlib import Path
app=Path(sys.argv[1]).resolve();backup=Path(sys.argv[2]).resolve();names=['index.html','app.js','sw.js','manifest.webmanifest','r10_bridge.py','fieldworks_bridge_r2.py','fieldworks-r2.js','fieldworks-r2.css','patch_homebase.py','FIELDWORKS_R2_INSTALL_INFO.json']
items=[]
for name in names:
    source=app/name;entry={'relative':name,'existed':source.is_file()}
    if source.is_file():
        destination=backup/'files'/name;destination.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,destination);entry['sha256']=hashlib.sha256(source.read_bytes()).hexdigest()
    items.append(entry)
manifest={'schema':'thunderforge.fieldworks-r2.rollback.v1','created_at':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'app':str(app),'files':items}
(backup/'rollback-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
print(f'Rollback backup: {backup}')
PY

for file in fieldworks_bridge_r2.py fieldworks-r2.js fieldworks-r2.css patch_homebase.py; do cp "$SOURCE/$file" "$APP/$file"; done
python "$APP/patch_homebase.py" "$APP"
python -m py_compile "$APP/r10_bridge.py" "$APP/fieldworks_bridge_r2.py" "$APP/patch_homebase.py"
if command -v node >/dev/null 2>&1; then node --check "$APP/app.js"; node --check "$APP/fieldworks-r2.js"; node --check "$APP/sw.js"; fi

for name in thunderforge-fieldworks-start thunderforge-fieldworks-open thunderforge-fieldworks-status thunderforge-fieldworks-accept thunderforge-fieldworks-rollback; do cp "$SOURCE/termux/$name" "$BIN/$name"; chmod 700 "$BIN/$name"; done

python - "$APP" "$BACKUP" "$STATE/latest-install.json" "$PACKAGE" "$KERNEL_STATE" <<'PY'
import hashlib,json,sys,time
from pathlib import Path
app=Path(sys.argv[1]).resolve();backup=Path(sys.argv[2]).resolve();marker=Path(sys.argv[3]);package=Path(sys.argv[4]);kernel_state=Path(sys.argv[5]).resolve()
value={'schema':'thunderforge.fieldworks-r2.install.v1','installed_at':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'app':str(app),'backup':str(backup),'package_sha256':hashlib.sha256(package.read_bytes()).hexdigest(),'kernel_database':str(kernel_state/'kernel-computer.sqlite3'),'r1_mode':'READ_ONLY','arbitrary_commands':False,'truth_boundary':'Computation produces candidates. Novelty, repetition, or scale never promotes a candidate into a fact.'}
marker.parent.mkdir(parents=True,exist_ok=True);marker.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8');(app/'FIELDWORKS_R2_INSTALL_INFO.json').write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
PY

if [ -f "$KERNEL_STATE/kernel-computer.sqlite3" ]; then say "Kernel Computer R2 database: PRESENT"; else say "WARNING: Kernel Computer R2 database is not present yet. The Home Base route is installed, but data will remain unavailable until R2 is installed."; fi

bash "$BIN/thunderforge-fieldworks-start"
python - "${TF_R10_PORT:-8941}" <<'PY'
import http.client,json,sys
port=int(sys.argv[1])
def get(path):
    connection=http.client.HTTPConnection('127.0.0.1',port,timeout=8)
    try:connection.request('GET',path,headers={'Connection':'close'});response=connection.getresponse();return response.status,json.loads(response.read())
    finally:connection.close()
status,health=get('/api/r10/fieldworks/health');assert status==200 and health.get('bridge')=='fieldworks-r2'
status,field=get('/api/r10/fieldworks/field')
if health.get('kernel',{}).get('present'):assert status==200 and len((field.get('field') or {}).get('cells') or [])==144
print('Same-origin Fieldworks bridge: PASS')
print('Kernel field:', 'PASS · 144 cells' if status==200 else 'WAITING FOR R2 DATA')
print('Arbitrary command execution: DISABLED')
PY

say
say "Installed additively into the current Home Base R10 with an exact rollback backup."
say "R9, R10 project data, production logs, Kernel R1, and Kernel R2 data were not rewritten."
say "Open:     $BIN/thunderforge-fieldworks-open"
say "Status:   $BIN/thunderforge-fieldworks-status"
say "Accept:   $BIN/thunderforge-fieldworks-accept"
say "Rollback: $BIN/thunderforge-fieldworks-rollback"
