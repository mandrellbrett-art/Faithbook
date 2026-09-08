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
            out.put("reply","I work on HeritageFaith's local managed database. Try: core; global <words>; status; projects; files <project id>; search <words>; corpus <words>; phone status; phone <words>; homebase; routes; continuity; verify cantus. I navigate indexed local evidence but do not run arbitrary shell commands or invent physical test results.");return out;
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
        if(q.equals("core")||q.equals("unified core")){
            JSONObject s=db.globalSummary();out.put("data",s);
            out.put("reply","Unified Core sees "+s.optLong("projects")+" projects, "+s.optLong("managed_files")+" managed files, "+s.optLong("records")+" records, "+s.optLong("phone_items")+" phone-index items, "+s.optLong("legacy_patents")+" legacy patents, and "+s.optLong("bible_verses")+" indexed Bible verses.");return out;
        }
        if(q.startsWith("global ")){
            String term=raw.substring(raw.indexOf(' ')+1).trim();JSONArray a=db.globalSearch(term,200);out.put("data",a);
            out.put("reply","Unified search found "+a.length()+" results across projects, files, records, phone intake, continuity, Scripture, patents and corpus metadata.");return out;
        }
        if(q.equals("routes")||q.equals("route atlas")){
            out.put("reply","The runtime has a 12 × 12 operation matrix, but the historical canonical 144-route registry still contains unbound placeholders. Use the Route Atlas in the app to keep that gap visible rather than inventing bindings.");return out;
        }
        if(q.equals("phone status")||q.equals("phone")){JSONObject s=db.phoneIntakeSummary();out.put("data",s);out.put("reply","Phone intake: "+s.optLong("items")+" indexed, "+s.optLong("copied")+" copied, "+s.optLong("homebase_hits")+" Home Base-family hits, "+s.optLong("unavailable")+" Android-inaccessible.");return out;}
        if(q.startsWith("phone ")){String term=raw.substring(raw.indexOf(' ')+1).trim();JSONArray a=db.searchPhoneIntake(term,200);out.put("data",a);out.put("reply","Phone index found "+a.length()+" matches for “"+term+"”.");return out;}
        if(q.equals("homebase")){JSONObject s=db.phoneIntakeSummary();out.put("data",s);out.put("reply","Home Base runtime is present, but historical data stays IMPORT REQUIRED until Phone Intake or Continuity proves it. Phone intake currently shows "+s.optLong("homebase_hits")+" Home Base-family hits.");return out;}
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
