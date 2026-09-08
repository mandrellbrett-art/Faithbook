#!/usr/bin/env python3
from pathlib import Path
import json,subprocess,shutil,sys
root=Path(__file__).resolve().parents[1]
checks=[]
def c(n,x,d=""):checks.append((n,bool(x),d))
db=(root/"app/src/main/java/com/arkforge/faith/R10Database.java").read_text()
mf=(root/"app/src/main/java/com/arkforge/faith/MirrorFilter.java").read_text()
main=(root/"app/src/main/java/com/arkforge/faith/MainActivity.java").read_text()
nb=(root/"app/src/main/java/com/arkforge/faith/NativeBridge.java").read_text()
app=(root/"app/src/main/assets/app.js").read_text()
grad=(root/"app/build.gradle").read_text()
c("DB v10","DB_VERSION = 10" in db)
c("Mirror schema","CREATE TABLE IF NOT EXISTS mirror_triage" in db)
c("Additive DB migration","oldVersion < 10" in db)
c("Mirror filter class",(root/"app/src/main/java/com/arkforge/faith/MirrorFilter.java").is_file())
c("No source deletion","delete(" not in mf and "renameTo(" not in mf)
c("Narrow family conflict rules","DAD_CONFLICT" in mf and "FAMILY_CONFLICT" in mf)
c("Exit planning rule","EXIT_RELOCATION_PLANNING" in mf)
c("High emotion goes to review","HIGH_EMOTION" in mf and 'disposition="REVIEW"' in mf)
c("Text sampling bounded","TEXT_SAMPLE_BYTES" in mf and "MAX_TEXT_FILE" in mf)
c("Import All auto mirror","MirrorFilter.sweep(this,db,false)" in main)
c("Mirror bridge","mirrorSummary" in nb and "mirrorSearch" in nb and "mirrorSet" in nb)
c("Mirror route","case 'mirror':return renderMirror();" in app)
c("Mirror archive UI","Longitudinal Mirror" in app and "Past me is evidence, not authority" in app)
c("Manual overrides","Keep in present" in app and "Add reflection" in app)
c("Present global search excludes mirror archive","m.disposition<>'MIRROR_ARCHIVE'" in db)
c("V25.4 code","versionCode 250400" in grad)
c("V25.4 name","25.4.0-mirror-sweep" in grad)
if shutil.which("node"):
    p=subprocess.run(["node","--check",str(root/"app/src/main/assets/app.js")],capture_output=True,text=True)
    c("JavaScript syntax",p.returncode==0,(p.stderr or p.stdout)[-1000:])
bad=[x for x in checks if not x[1]]
out={"schema":"ark.mirror-sweep.qa.v25_4","passed":len(checks)-len(bad),"failed":len(bad),
"checks":[{"name":n,"pass":ok,"detail":d} for n,ok,d in checks]}
(root/"qa").mkdir(exist_ok=True)
(root/"qa/V25_4_MIRROR_SWEEP_VERIFICATION.json").write_text(json.dumps(out,indent=2))
print(json.dumps(out,indent=2));sys.exit(1 if bad else 0)
