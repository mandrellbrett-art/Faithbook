#!/usr/bin/env python3
from pathlib import Path
import json, re, sys
root=Path(__file__).resolve().parents[1]
checks=[]
def c(name, ok, detail=""):
    checks.append((name,bool(ok),detail))
assets=root/"app/src/main/assets"
app=(assets/"app.js").read_text()
main=(root/"app/src/main/java/com/arkforge/faith/MainActivity.java").read_text()
bridge=(root/"app/src/main/java/com/arkforge/faith/NativeBridge.java").read_text()
ledger=json.loads((assets/"feature-ledger.json").read_text())
sources=json.loads((assets/"private-library-sources.json").read_text())
unity=json.loads((assets/"homebase-unity-spec.json").read_text())
c("private mode", ledger.get("release_state")=="PRIVATE_SINGLE_USER_ANDROID_SOURCE")
c("six drive shelves", len(sources.get("sources",[]))==6)
c("drive shelves private-only", all(x.get("redistribution")=="DO_NOT_BUNDLE_OR_REDISTRIBUTE" for x in sources["sources"]))
c("home base route", "case 'homebase':return renderHomeBase()" in app)
for r in ["mywork","files","games","production","engineering","garden","farm","cantus","kernel","systems","continuity"]:
    c("route "+r, f"case '{r}':" in app)
c("generic record tools", "renderGenericTool(tool[0],tool[1],tool[2])" in app)
c("multi-book action", "library-batch" in app)
c("multi-book picker", 'Intent.EXTRA_ALLOW_MULTIPLE' in main)
c("multi-book callback", "onNativeImportBatch" in app and "onNativeImportBatch" in main)
c("private source accessor", "privateLibrarySources" in bridge)
c("unity accessor", "homeBaseUnitySpec" in bridge)
c("ademic cantus route", "cantuslab" in app)
c("ademic enabled", unity.get("ademic_cantus")=="ENABLED")
c("package continuity", "package com.arkforge.faith;" in main)
c("not for sale", json.loads((assets/"PUBLIC_RELEASE_BOUNDARY.json").read_text()).get("store_distribution")=="NOT_FOR_SALE")
c("constructor present", (root/"app/src/main/java/com/arkforge/faith/ConstructorCore.java").is_file())
c("auto finish present", (root/"app/src/main/java/com/arkforge/faith/AutoFinishManager.java").is_file())
c("backup present", (root/"app/src/main/java/com/arkforge/faith/BackupManager.java").is_file())
c("continuity present", (root/"app/src/main/java/com/arkforge/faith/ContinuityManager.java").is_file())
failed=[x for x in checks if not x[1]]
out={"schema":"heritagefaith.qa.v20","product":"HeritageFaith","version":"20.0.0-united-private",
     "passed":len(checks)-len(failed),"failed":len(failed),
     "checks":[{"name":n,"ok":ok,"detail":d} for n,ok,d in checks]}
(root/"qa/V20_PRIVATE_SOURCE_VERIFICATION.json").write_text(json.dumps(out,indent=2))
print(json.dumps(out,indent=2))
sys.exit(1 if failed else 0)
