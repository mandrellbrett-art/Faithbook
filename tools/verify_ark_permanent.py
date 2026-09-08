#!/usr/bin/env python3
from pathlib import Path
import json, re, shutil, subprocess, sys
root=Path(__file__).resolve().parents[1]
checks=[]
def c(n,x,d=""): checks.append((n,bool(x),d))
grad=(root/"app/build.gradle").read_text()
strings=(root/"app/src/main/res/values/strings.xml").read_text()
app=(root/"app/src/main/assets/app.js").read_text()
wf=(root/".github/workflows/ark-release.yml").read_text()
c("new side-by-side package","applicationId 'com.arkforge.ark'" in grad)
c("ARK launcher label",'<string name="app_name">ARK</string>' in strings)
c("ARK V25 version code","versionCode 250101" in grad)
c("ARK V25 version name","25.1.0-ark-unified-core" in grad)
c("release signing config","ARK_KEYSTORE_PATH" in grad and "signingConfig signingConfigs.arkRelease" in grad)
c("signed release workflow","assembleRelease" in wf and "ARK_KEYSTORE_B64" in wf)
c("certificate verification step","apksigner" in wf)
c("side-by-side migration notice","com.arkforge.ark" in app and "Side-by-side migration build" in app)
c("Unified Core preserved","renderUnifiedCore" in app)
c("Phone Intake preserved","renderPhoneIntake" in app)
c("Kernel preserved","Kernel Computer R2" in app)
c("Ademic Cantus preserved","Ademic Cantus" in app)
c("Scripture page reader preserved","Page turning" in app and "readerPageNext" in app)
c("Legacy patents preserved",(root/"app/src/main/java/com/arkforge/faith/LegacyPatentImporter.java").is_file())
if shutil.which("node"):
    p=subprocess.run(["node","--check",str(root/"app/src/main/assets/app.js")],capture_output=True,text=True)
    c("JavaScript syntax",p.returncode==0,(p.stderr or p.stdout)[-1000:])
bad=[x for x in checks if not x[1]]
out={"schema":"ark.v25.permanent.qa.v1","passed":len(checks)-len(bad),"failed":len(bad),
     "checks":[{"name":n,"pass":ok,"detail":d} for n,ok,d in checks]}
(root/"qa").mkdir(exist_ok=True)
(root/"qa/ARK_V25_PERMANENT_VERIFICATION.json").write_text(json.dumps(out,indent=2))
print(json.dumps(out,indent=2))
sys.exit(1 if bad else 0)
