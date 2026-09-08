#!/usr/bin/env python3
from pathlib import Path
import json,re,subprocess,shutil,sys
root=Path(__file__).resolve().parents[1]
checks=[]
def c(n,x,d=""):checks.append((n,bool(x),d))
db=(root/"app/src/main/java/com/arkforge/faith/R10Database.java").read_text()
nb=(root/"app/src/main/java/com/arkforge/faith/NativeBridge.java").read_text()
ctor=(root/"app/src/main/java/com/arkforge/faith/ConstructorCore.java").read_text()
app=(root/"app/src/main/assets/app.js").read_text()
grad=(root/"app/build.gradle").read_text()
c("Unified global summary","globalSummary()" in db and "globalSummary()" in nb)
c("Federated global search","globalSearch(String query,int limit)" in db and "globalSearch(String query,int limit)" in nb)
for source in ["project","managed_file","record","phone","continuity","bible","patent","corpus"]:
    c("Global search source "+source,("'"+source+"' source") in db)
c("Core route","case 'core':return renderUnifiedCore();" in app)
c("Global search route","case 'search':return renderGlobalSearch();" in app)
c("Viewer route","case 'viewer':return renderUniversalViewer();" in app)
c("Route Atlas route","case 'routeatlas':return renderRouteAtlas();" in app)
c("routeCard helper","function routeCard(" in app)
c("projectCards helper","function projectCards(" in app)
c("wireRouteCards helper","function wireRouteCards(" in app)
c("wireProjectButtons helper","function wireProjectButtons(" in app)
c("Text viewer bridge","readProjectText" in nb and "2 MiB" in nb)
c("Constructor core command","q.equals(\"core\")" in ctor)
c("Constructor global command","q.startsWith(\"global \")" in ctor)
c("Constructor route warning","q.equals(\"routes\")" in ctor)
c("Audit embedded",(root/"app/src/main/assets/v25-all-encompassing-audit.json").is_file())
c("V24 preserved",(root/"app/src/main/java/com/arkforge/faith/PhoneIntakeManager.java").is_file())
c("Ademic Cantus preserved",(root/"ADEMIC_CANTUS.md").is_file() and "Ademic Cantus" in app)
c("Scripture page reader preserved","Page turning" in app and "readerPageNext" in app)
c("Legacy patents preserved",(root/"app/src/main/java/com/arkforge/faith/LegacyPatentImporter.java").is_file())
m=re.search(r"versionCode\s+(\d+)",grad); c("V25+ version code", bool(m) and int(m.group(1))>=250001)
c("V25 Unified Core lineage","unified-core" in grad or "full-continuity" in grad or "import-all" in grad or "mirror-sweep" in grad)
if shutil.which("node"):
    p=subprocess.run(["node","--check",str(root/"app/src/main/assets/app.js")],capture_output=True,text=True)
    c("JavaScript syntax",p.returncode==0,(p.stderr or p.stdout)[-1000:])
else:c("JavaScript syntax",True,"node unavailable; skipped")
bad=[x for x in checks if not x[1]]
out={"schema":"heritagefaith.qa.v25","passed":len(checks)-len(bad),"failed":len(bad),
     "checks":[{"name":n,"pass":ok,"detail":d} for n,ok,d in checks]}
(root/"qa").mkdir(exist_ok=True)
(root/"qa/V25_UNIFIED_CORE_VERIFICATION.json").write_text(json.dumps(out,indent=2))
print(json.dumps(out,indent=2))
sys.exit(1 if bad else 0)
