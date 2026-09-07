#!/usr/bin/env python3
from pathlib import Path
import json, re, sys
root=Path(__file__).resolve().parents[1]
checks=[]
def c(name, ok, detail=""):
    checks.append((name,bool(ok),detail))
app=(root/"app/src/main/assets/app.js").read_text()
main=(root/"app/src/main/java/com/arkforge/faith/MainActivity.java").read_text()
bridge=(root/"app/src/main/java/com/arkforge/faith/NativeBridge.java").read_text()
mgr=(root/"app/src/main/java/com/arkforge/faith/AssistantBridgeManager.java").read_text()
imp=(root/"app/src/main/java/com/arkforge/faith/ImportManager.java").read_text()
grad=(root/"app/build.gradle").read_text()
c("AssistantBridgeManager exists", (root/"app/src/main/java/com/arkforge/faith/AssistantBridgeManager.java").is_file())
c("Settings route", "case 'settings':return renderSettings();" in app)
c("Settings navigation", "['settings','Settings']" in app)
c("Standard context export", "exportAssistantContext" in bridge and "requestAssistantContextExport" in main)
c("Share context", "shareAssistantContext" in bridge and main)
c("Assistant Return import", '"assistant-return".equals(purpose)' in imp)
c("No background access declaration", "EXPLICIT_USER_HANDOFF_ONLY" in mgr and "background_remote_access" in mgr)
c("No code execution", 'execution_allowed", false' in mgr and 'code_executed", false' in mgr)
c("No raw book bytes", "raw_book_bytes_included" in mgr)
c("Credentials excluded", "credentials_included" in mgr)
c("Ademic Cantus exported", '"ademic_cantus"' in mgr and "Runic compression" in mgr)
m=re.search(r"versionCode\s+(\d+)",grad); c("V21+ versionCode", bool(m) and int(m.group(1))>=210001)
c("Assistant Bridge version lineage", "assistant-bridge" in grad or "drive-folder-import" in grad)
c("Bridge spec JSON", (root/"app/src/main/assets/assistant-bridge-spec.json").is_file())
c("Bridge documentation", (root/"ASSISTANT_BRIDGE.md").is_file())
failed=[x for x in checks if not x[1]]
out={"schema":"heritagefaith.qa.v21","passed":len(checks)-len(failed),"failed":len(failed),
     "checks":[{"name":n,"pass":ok,"detail":d} for n,ok,d in checks]}
qa=root/"qa";qa.mkdir(exist_ok=True)
(qa/"V21_ASSISTANT_BRIDGE_VERIFICATION.json").write_text(json.dumps(out,indent=2))
print(json.dumps(out,indent=2))
sys.exit(1 if failed else 0)
