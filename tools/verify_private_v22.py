#!/usr/bin/env python3
from pathlib import Path
import json, re, sys
root=Path(__file__).resolve().parents[1]
checks=[]
def c(name, ok, detail=""): checks.append((name,bool(ok),detail))
main=(root/"app/src/main/java/com/arkforge/faith/MainActivity.java").read_text()
bridge=(root/"app/src/main/java/com/arkforge/faith/NativeBridge.java").read_text()
imp=(root/"app/src/main/java/com/arkforge/faith/DriveFolderImporter.java").read_text()
app=(root/"app/src/main/assets/app.js").read_text()
grad=(root/"app/build.gradle").read_text()
c("DriveFolderImporter exists",(root/"app/src/main/java/com/arkforge/faith/DriveFolderImporter.java").is_file())
c("ACTION_OPEN_DOCUMENT_TREE","ACTION_OPEN_DOCUMENT_TREE" in main)
c("persistable tree permission","takePersistableUriPermission" in main and "FLAG_GRANT_PREFIX_URI_PERMISSION" in main)
c("recursive traversal","buildChildDocumentsUriUsingTree" in imp and "walk(context" in imp)
c("Google Drive compatible SAF wording","Google Drive" in imp)
c("supported book filter","BOOK_EXTENSIONS" in imp and '"pdf"' in imp and '"epub"' in imp)
c("private only provenance",'PRIVATE_USER_OWNED_OR_AUTHORIZED' in imp and 'PRIVATE_ONLY' in imp)
c("folder import JS bridge","importLibraryFolder" in bridge)
c("settings folder button","assistantImportFolder" in app and "Import entire Drive / document folder" in app)
c("folder callback","onNativeFolderImport" in app)
m=re.search(r"versionCode\s+(\d+)",grad); c("V22+ versionCode", bool(m) and int(m.group(1))>=220001)
c("Drive folder import lineage","drive-folder-import" in grad or "reader-index-patent-archive" in grad)
c("V21 bridge preserved",(root/"app/src/main/java/com/arkforge/faith/AssistantBridgeManager.java").is_file())
c("Ademic Cantus preserved",(root/"ADEMIC_CANTUS.md").is_file() and "Ademic Cantus + Resonance" in app and "Runic compression" in app)
failed=[x for x in checks if not x[1]]
out={"schema":"heritagefaith.qa.v22","passed":len(checks)-len(failed),"failed":len(failed),
     "checks":[{"name":n,"pass":ok,"detail":d} for n,ok,d in checks]}
qa=root/"qa";qa.mkdir(exist_ok=True)
(qa/"V22_DRIVE_FOLDER_IMPORT_VERIFICATION.json").write_text(json.dumps(out,indent=2))
print(json.dumps(out,indent=2))
sys.exit(1 if failed else 0)
