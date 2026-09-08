#!/usr/bin/env python3
from pathlib import Path
import json,re,sys,subprocess,shutil
root=Path(__file__).resolve().parents[1]
checks=[]
def c(n,x,d=""):checks.append((n,bool(x),d))
manifest=(root/"app/src/main/AndroidManifest.xml").read_text()
pm=(root/"app/src/main/java/com/arkforge/faith/PhoneIntakeManager.java").read_text()
main=(root/"app/src/main/java/com/arkforge/faith/MainActivity.java").read_text()
nb=(root/"app/src/main/java/com/arkforge/faith/NativeBridge.java").read_text()
app=(root/"app/src/main/assets/app.js").read_text()
grad=(root/"app/build.gradle").read_text()
c("All Files Access permission","android.permission.MANAGE_EXTERNAL_STORAGE" in manifest)
c("Runtime permission check","Environment.isExternalStorageManager()" in main)
c("Android permission settings","ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION" in main)
c("Automatic resume after permission","pendingImportAll" in main and "onResume()" in main)
c("Direct all-shared-storage scan","scanAllSharedStorage" in pm)
c("Multiple storage volumes","getExternalFilesDirs" in pm and "sharedStorageRoots" in pm)
c("Safety item limit","1,000,000-item safety limit" in pm)
c("No source delete/rename","renameTo(" not in pm and "file.delete()" not in pm)
c("Persisted provider trees","getPersistedUriPermissions" in main)
c("Import All bridge","importAllAccessible" in nb and "allFilesAccessStatus" in nb)
c("Import All main UI",'id="importAllAccessible"' in app)
c("Import All settings shortcut",'id="settingsImportAll"' in app)
c("Import All callback","onImportAll(payload)" in app)
m=re.search(r"versionCode\s+(\d+)",grad);c("V25.3+ version code",bool(m) and int(m.group(1))>=250300)
c("Import All lineage","import-all" in grad or "mirror-sweep" in grad)
if shutil.which("node"):
    p=subprocess.run(["node","--check",str(root/"app/src/main/assets/app.js")],capture_output=True,text=True)
    c("JavaScript syntax",p.returncode==0,(p.stderr or p.stdout)[-1000:])
bad=[x for x in checks if not x[1]]
out={"schema":"ark.import-all.qa.v25_3","passed":len(checks)-len(bad),"failed":len(bad),
     "checks":[{"name":n,"pass":ok,"detail":d} for n,ok,d in checks]}
(root/"qa").mkdir(exist_ok=True)
(root/"qa/V25_3_IMPORT_ALL_VERIFICATION.json").write_text(json.dumps(out,indent=2))
print(json.dumps(out,indent=2))
sys.exit(1 if bad else 0)
