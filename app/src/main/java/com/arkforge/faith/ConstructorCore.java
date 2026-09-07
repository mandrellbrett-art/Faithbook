package com.arkforge.faith;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/** Offline, deterministic helper. It never executes arbitrary shell commands. */
public final class ConstructorCore {
    private ConstructorCore(){}

    public static JSONObject ask(R10Database db,String message)throws Exception{
        String raw=message==null?"":message.trim();if(raw.isEmpty())throw new IllegalArgumentException("Message is required");String q=raw.toLowerCase(Locale.US);JSONObject out=new JSONObject();out.put("ok",true);out.put("mode","offline-local-constructor");
        if(q.equals("help")||q.contains("what can you do")){
            out.put("reply","I work on HeritageFaith's local managed database. Try: status; projects; files <project id>; search <words>; corpus <words>; continuity; verify cantus. I navigate indexed local evidence but do not run arbitrary shell commands or invent physical test results.");return out;
        }
        if(q.equals("status")||q.contains("system status")){
            JSONObject s=db.stats();out.put("data",s);out.put("reply","HeritageFaith local status: "+s.optLong("projects")+" projects, "+s.optLong("managed_files")+" managed files, "+s.optLong("records")+" active records, "+s.optLong("corpus_files")+" heritage-index entries, "+s.optLong("cantus_events")+" Cantus events. SQLite integrity: "+s.optString("db_integrity")+".");return out;
        }
        if(q.equals("projects")||q.contains("list projects")){
            JSONArray p=db.listProjects(false);out.put("data",p);out.put("reply","I found "+p.length()+" active local projects. Open Constructor or the managed-project view to inspect them.");return out;
        }
        if(q.startsWith("files ")){
            String id=raw.substring(raw.indexOf(' ')+1).trim();
            JSONObject project=db.getProject(id);
            if(project==null) throw new IllegalArgumentException("Project not found: "+id);
            JSONArray files=db.listManagedFiles(id,5000);out.put("data",files);
            out.put("reply","Constructor found "+files.length()+" managed file entries for “"+project.optString("name",id)+"”. Open the project in Constructor to inspect paths and evidence state.");return out;
        }
        if(q.equals("continuity")||q.contains("migration status")||q.contains("continuity status")){
            JSONObject m=db.migrationSummary();out.put("data",m);
            long unresolved=m.optLong("reference_only")+m.optLong("mismatches")+m.optLong("missing");
            out.put("reply","Continuity ledger: "+m.optLong("items")+" tracked items, "+unresolved+" unresolved/reference or mismatch items. Promotion stays blocked while continuity evidence is unresolved.");return out;
        }
        if(q.startsWith("verify cantus")||q.contains("check cantus")){
            JSONObject v=db.verifyCantus();out.put("data",v);out.put("reply",v.optBoolean("ok")?"Cantus hash chain verifies across "+v.optLong("events")+" append-only events.":"Cantus verification found "+v.optJSONArray("issues").length()+" chain issue(s). Open Cantus Logs before changing anything.");return out;
        }
        if(q.startsWith("search ")){
            String term=raw.substring(raw.indexOf(' ')+1).trim();JSONArray a=db.search(term,50);out.put("data",a);out.put("reply","Local search found "+a.length()+" project/record matches for “"+term+"”.");return out;
        }
        if(q.startsWith("corpus ")){
            String term=raw.substring(raw.indexOf(' ')+1).trim();JSONArray a=db.searchCorpus(term,100);out.put("data",a);out.put("reply","Heritage corpus search found "+a.length()+" indexed path matches for “"+term+"”.");return out;
        }
        if(q.startsWith("finish ")){
            out.put("reply","Use the My Work → Auto Finish button for the selected project. The native finisher deliberately requires an explicit project selection so a chat sentence cannot execute arbitrary code or mutate an unintended project.");return out;
        }
        JSONObject s=db.stats();out.put("reply","I am the offline Constructor inside HeritageFaith. I navigate the local project/file index and continuity/Cantus evidence without requiring Termux. Right now there are "+s.optLong("projects")+" projects and "+s.optLong("corpus_files")+" indexed heritage paths. For exact work, use status, projects, files <project id>, search <term>, corpus <term>, continuity, or verify cantus.");return out;
    }
}
