package com.arkforge.faith;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

public class R10Database extends SQLiteOpenHelper {
    public static final String DB_NAME = "arkforge-faith.db";
    private static final int DB_VERSION = 7;
    private final Context context;

    public R10Database(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context.getApplicationContext();
        setWriteAheadLoggingEnabled(true);
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE projects (" +
                "id TEXT PRIMARY KEY,name TEXT NOT NULL,description TEXT NOT NULL DEFAULT ''," +
                "family TEXT NOT NULL DEFAULT 'project',version TEXT NOT NULL DEFAULT 'R10'," +
                "parent_id TEXT,status TEXT NOT NULL DEFAULT 'active',root_path TEXT NOT NULL DEFAULT ''," +
                "source_uri TEXT NOT NULL DEFAULT '',evidence_state TEXT NOT NULL DEFAULT 'LOCAL_MANAGED'," +
                "created_at TEXT NOT NULL,updated_at TEXT NOT NULL,archived INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE managed_files (" +
                "id TEXT PRIMARY KEY,project_id TEXT NOT NULL,rel_path TEXT NOT NULL,name TEXT NOT NULL," +
                "size INTEGER NOT NULL DEFAULT 0,sha256 TEXT NOT NULL DEFAULT '',mime TEXT NOT NULL DEFAULT ''," +
                "kind TEXT NOT NULL DEFAULT 'file',source_uri TEXT NOT NULL DEFAULT '',created_at TEXT NOT NULL," +
                "UNIQUE(project_id,rel_path), FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE records (" +
                "id TEXT PRIMARY KEY,type TEXT NOT NULL,title TEXT NOT NULL,body TEXT NOT NULL DEFAULT ''," +
                "meta_json TEXT NOT NULL DEFAULT '{}',created_at TEXT NOT NULL,updated_at TEXT NOT NULL," +
                "archived INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE cantus_log (" +
                "seq INTEGER PRIMARY KEY AUTOINCREMENT,ts TEXT NOT NULL,module TEXT NOT NULL,action TEXT NOT NULL," +
                "status TEXT NOT NULL,detail TEXT NOT NULL,prev_hash TEXT NOT NULL,chain_hash TEXT NOT NULL)");
        db.execSQL("CREATE TABLE corpus_files (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,source_uri TEXT NOT NULL,path TEXT NOT NULL,size INTEGER NOT NULL DEFAULT -1," +
                "crc INTEGER NOT NULL DEFAULT -1,version_token TEXT NOT NULL DEFAULT '',family TEXT NOT NULL DEFAULT 'unplaced'," +
                "kind TEXT NOT NULL DEFAULT 'file',indexed_at TEXT NOT NULL)");
        db.execSQL("CREATE TABLE migration_runs (" +
                "id TEXT PRIMARY KEY,source_label TEXT NOT NULL DEFAULT '',source_uri TEXT NOT NULL DEFAULT ''," +
                "manifest_sha256 TEXT NOT NULL DEFAULT '',imported_at TEXT NOT NULL,item_count INTEGER NOT NULL DEFAULT 0," +
                "payload_count INTEGER NOT NULL DEFAULT 0,reference_count INTEGER NOT NULL DEFAULT 0,mismatch_count INTEGER NOT NULL DEFAULT 0," +
                "status TEXT NOT NULL DEFAULT 'IMPORTING',detail TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE migration_items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,run_id TEXT NOT NULL,original_path TEXT NOT NULL," +
                "payload_entry TEXT NOT NULL DEFAULT '',payload_path TEXT NOT NULL DEFAULT '',size INTEGER NOT NULL DEFAULT -1," +
                "sha256 TEXT NOT NULL DEFAULT '',kind TEXT NOT NULL DEFAULT 'file',family TEXT NOT NULL DEFAULT 'unplaced'," +
                "version_token TEXT NOT NULL DEFAULT '',migration_status TEXT NOT NULL DEFAULT 'REFERENCE_ONLY',detail TEXT NOT NULL DEFAULT ''," +
                "source_uri TEXT NOT NULL DEFAULT '',FOREIGN KEY(run_id) REFERENCES migration_runs(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX idx_migration_items_run ON migration_items(run_id,migration_status)");
        db.execSQL("CREATE INDEX idx_migration_items_path ON migration_items(original_path)");
        db.execSQL("CREATE TABLE bible_corpora (" +
                "id TEXT PRIMARY KEY,title TEXT NOT NULL,translation TEXT NOT NULL DEFAULT '',source_uri TEXT NOT NULL DEFAULT ''," +
                "sha256 TEXT NOT NULL DEFAULT '',license TEXT NOT NULL DEFAULT '',public_domain INTEGER NOT NULL DEFAULT 0," +
                "verse_count INTEGER NOT NULL DEFAULT 0,created_at TEXT NOT NULL,updated_at TEXT NOT NULL)");
        db.execSQL("CREATE TABLE bible_verses (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,corpus_id TEXT NOT NULL,book_no INTEGER NOT NULL DEFAULT 0," +
                "book TEXT NOT NULL,chapter INTEGER NOT NULL,verse INTEGER NOT NULL,text TEXT NOT NULL," +
                "UNIQUE(corpus_id,book,chapter,verse),FOREIGN KEY(corpus_id) REFERENCES bible_corpora(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX idx_bible_ref ON bible_verses(corpus_id,book,chapter,verse)");
        db.execSQL("CREATE INDEX idx_bible_text ON bible_verses(corpus_id,book,text)");
        db.execSQL("CREATE INDEX idx_records_type ON records(type,archived,updated_at)");
        db.execSQL("CREATE INDEX idx_projects_family ON projects(family,archived,updated_at)");
        db.execSQL("CREATE INDEX idx_files_project ON managed_files(project_id,rel_path)");
        db.execSQL("CREATE INDEX idx_corpus_path ON corpus_files(path)");
        db.execSQL("CREATE INDEX idx_corpus_family ON corpus_files(family,version_token)");
        logInternal(db, "system", "database-create", "PASS", "Faithbook local database created");
        seedSystemRecords(db);
        seedBibleLibraryRecord(db);
    }

    private void seedSystemRecords(SQLiteDatabase db) {
        ContentValues v = new ContentValues();
        String t = now();
        v.put("id", "r10-release-charter");
        v.put("type", "system");
        v.put("title", "R10 Release Charter");
        v.put("body", "Preserve originals; keep evidence and interpretation separate; do not fabricate physical proof; preserve Production/Logs/provenance; preserve Garden rest/autonomy; no arbitrary command API.");
        v.put("meta_json", "{\"source\":\"R9-to-R10 itinerary\",\"runtime\":\"standalone Android\"}");
        v.put("created_at", t); v.put("updated_at", t); v.put("archived", 0);
        db.insertWithOnConflict("records", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private void seedBibleLibraryRecord(SQLiteDatabase db) {
        String t = now();
        ContentValues book = new ContentValues();
        book.put("id", "library-arkforge-study-bible");
        book.put("type", "library");
        book.put("title", "Faithbook Scripture Workspace");
        book.put("body", "Jesus-centered personal study workspace. Import a public-domain or properly licensed Bible corpus, then add local notes, questions, resolution notes, commentary leaves, timelines, maps and church-group study records.");
        book.put("meta_json", "{\"medium\":\"digital_workspace\",\"content_state\":\"PUBLIC_DOMAIN_CORPUS_REQUIRED\",\"study_workspace\":true,\"page_notes\":true,\"questions\":true,\"resolution_notes\":true,\"church_group_book\":true,\"purpose\":\"Scripture study, prayer, theological research and church-group study.\",\"ark_category\":\"faith/scripture\"}");
        book.put("created_at", t); book.put("updated_at", t); book.put("archived", 0);
        db.insertWithOnConflict("records", null, book, SQLiteDatabase.CONFLICT_IGNORE);
        logInternal(db, "library", "arkforge-study-bible-register", "PASS", "Public clean-room Study Bible workspace registered; no owner-private Bible scan or copyrighted study edition bundled");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Public package com.arkforge.faith begins with a clean database.
        // Future public schema migrations belong here and must never import owner-private state implicitly.
    }

    public static String now() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    public synchronized String createProject(String name, String description, String family, String rootPath, String sourceUri) throws Exception {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) throw new IllegalArgumentException("Project name is required");
        String id = "prj_" + UUID.randomUUID().toString().replace("-", "");
        ContentValues v = new ContentValues();
        String t = now();
        v.put("id", id); v.put("name", clean); v.put("description", description == null ? "" : description.trim());
        v.put("family", safeText(family, "project")); v.put("version", "R10"); v.put("status", "active");
        v.put("root_path", rootPath == null ? "" : rootPath); v.put("source_uri", sourceUri == null ? "" : sourceUri);
        v.put("evidence_state", "LOCAL_MANAGED"); v.put("created_at", t); v.put("updated_at", t); v.put("archived", 0);
        getWritableDatabase().insertOrThrow("projects", null, v);
        log("projects", "create", "PASS", id + " · " + clean);
        return id;
    }

    public synchronized void updateProjectRoot(String id, String rootPath, String status) {
        ContentValues v = new ContentValues(); v.put("root_path", rootPath); v.put("updated_at", now());
        if (status != null) v.put("status", status);
        getWritableDatabase().update("projects", v, "id=?", new String[]{id});
    }

    public synchronized void updateProjectStatus(String id, String status) {
        ContentValues v = new ContentValues(); v.put("status", status); v.put("updated_at", now());
        getWritableDatabase().update("projects", v, "id=?", new String[]{id});
    }

    public synchronized JSONObject getProject(String id) throws Exception {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM projects WHERE id=?", new String[]{id})) {
            if (!c.moveToFirst()) return null;
            return cursorRow(c);
        }
    }

    public synchronized JSONArray listProjects(boolean archived) throws Exception {
        JSONArray a = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM projects WHERE archived=? ORDER BY updated_at DESC,name COLLATE NOCASE", new String[]{archived ? "1" : "0"})) {
            while (c.moveToNext()) a.put(cursorRow(c));
        }
        return a;
    }

    public synchronized String addRecord(String type, String title, String body, String metaJson) throws Exception {
        String cleanType = safeText(type, "note");
        String cleanTitle = title == null ? "" : title.trim();
        if (cleanTitle.isEmpty()) throw new IllegalArgumentException("Title is required");
        if (metaJson == null || metaJson.isBlank()) metaJson = "{}";
        new JSONObject(metaJson); // validation
        String id = "rec_" + UUID.randomUUID().toString().replace("-", "");
        String t = now();
        ContentValues v = new ContentValues();
        v.put("id", id); v.put("type", cleanType); v.put("title", cleanTitle); v.put("body", body == null ? "" : body);
        v.put("meta_json", metaJson); v.put("created_at", t); v.put("updated_at", t); v.put("archived", 0);
        getWritableDatabase().insertOrThrow("records", null, v);
        log(cleanType, "record-create", "PASS", id + " · " + cleanTitle);
        return id;
    }

    public synchronized void updateRecord(String id, String title, String body, String metaJson) throws Exception {
        if (metaJson == null || metaJson.isBlank()) metaJson = "{}";
        new JSONObject(metaJson);
        ContentValues v = new ContentValues(); v.put("title", title); v.put("body", body == null ? "" : body);
        v.put("meta_json", metaJson); v.put("updated_at", now());
        int n = getWritableDatabase().update("records", v, "id=?", new String[]{id});
        if (n == 0) throw new IllegalArgumentException("Record not found");
        log("records", "record-update", "PASS", id);
    }

    public synchronized void setRecordArchived(String id, boolean archived) {
        ContentValues v = new ContentValues(); v.put("archived", archived ? 1 : 0); v.put("updated_at", now());
        getWritableDatabase().update("records", v, "id=?", new String[]{id});
        log("archive", archived ? "archive" : "restore", "PASS", id);
    }

    public synchronized JSONArray listRecords(String type, boolean archived, int limit) throws Exception {
        JSONArray a = new JSONArray();
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM records WHERE type=? AND archived=? ORDER BY updated_at DESC LIMIT " + safeLimit,
                new String[]{type, archived ? "1" : "0"})) {
            while (c.moveToNext()) a.put(cursorRow(c));
        }
        return a;
    }

    public synchronized JSONArray search(String query, int limit) throws Exception {
        String q = "%" + (query == null ? "" : query.trim()) + "%";
        JSONArray out = new JSONArray();
        int safeLimit = Math.max(1, Math.min(limit, 200));
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 'project' AS source,id,name AS title,description AS body,updated_at FROM projects " +
                        "WHERE archived=0 AND (name LIKE ? OR description LIKE ? OR family LIKE ?) " +
                        "UNION ALL SELECT type,id,title,body,updated_at FROM records WHERE archived=0 AND (title LIKE ? OR body LIKE ? OR type LIKE ?) " +
                        "ORDER BY updated_at DESC LIMIT " + safeLimit,
                new String[]{q,q,q,q,q,q})) {
            while (c.moveToNext()) out.put(cursorRow(c));
        }
        return out;
    }

    public synchronized void addManagedFile(String projectId, String relPath, String name, long size, String sha256, String mime, String kind, String sourceUri) {
        ContentValues v = new ContentValues();
        v.put("id", "fil_" + UUID.randomUUID().toString().replace("-", "")); v.put("project_id", projectId);
        v.put("rel_path", relPath); v.put("name", name); v.put("size", size); v.put("sha256", sha256 == null ? "" : sha256);
        v.put("mime", mime == null ? "" : mime); v.put("kind", kind == null ? "file" : kind);
        v.put("source_uri", sourceUri == null ? "" : sourceUri); v.put("created_at", now());
        getWritableDatabase().insertWithOnConflict("managed_files", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized JSONArray listManagedFiles(String projectId, int limit) throws Exception {
        JSONArray a = new JSONArray(); int safeLimit = Math.max(1, Math.min(limit, 5000));
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM managed_files WHERE project_id=? ORDER BY rel_path COLLATE NOCASE LIMIT " + safeLimit,
                new String[]{projectId})) {
            while (c.moveToNext()) a.put(cursorRow(c));
        }
        return a;
    }

    public synchronized void beginCorpusIndex(String sourceUri) {
        getWritableDatabase().delete("corpus_files", "source_uri=?", new String[]{sourceUri});
    }

    public synchronized void addCorpusBatch(JSONArray batch) throws Exception {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            for (int i=0; i<batch.length(); i++) {
                JSONObject o = batch.getJSONObject(i); ContentValues v = new ContentValues();
                v.put("source_uri", o.optString("source_uri")); v.put("path", o.optString("path"));
                v.put("size", o.optLong("size", -1)); v.put("crc", o.optLong("crc", -1));
                v.put("version_token", o.optString("version_token")); v.put("family", o.optString("family", "unplaced"));
                v.put("kind", o.optString("kind", "file")); v.put("indexed_at", now());
                db.insert("corpus_files", null, v);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public synchronized JSONArray searchCorpus(String query, int limit) throws Exception {
        JSONArray a = new JSONArray(); String q = "%" + (query == null ? "" : query.trim()) + "%";
        int safeLimit = Math.max(1, Math.min(limit, 500));
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT path,size,crc,version_token,family,kind,indexed_at FROM corpus_files WHERE path LIKE ? OR family LIKE ? OR version_token LIKE ? ORDER BY path LIMIT " + safeLimit,
                new String[]{q,q,q})) {
            while (c.moveToNext()) a.put(cursorRow(c));
        }
        return a;
    }


    public synchronized String beginMigrationRun(String sourceLabel, String sourceUri, String manifestSha256, int itemCount) {
        String id = "mig_" + UUID.randomUUID().toString().replace("-", "");
        ContentValues v = new ContentValues();
        v.put("id", id); v.put("source_label", safeText(sourceLabel,"Continuity bundle"));
        v.put("source_uri", sourceUri == null ? "" : sourceUri); v.put("manifest_sha256", manifestSha256 == null ? "" : manifestSha256);
        v.put("imported_at", now()); v.put("item_count", itemCount); v.put("status", "IMPORTING");
        getWritableDatabase().insertOrThrow("migration_runs", null, v);
        log("continuity","migration-begin","PASS",id+" · items="+itemCount);
        return id;
    }

    public synchronized void addMigrationItem(String runId, JSONObject o) {
        ContentValues v = new ContentValues();
        v.put("run_id", runId); v.put("original_path", o.optString("original_path"));
        v.put("payload_entry", o.optString("payload_entry")); v.put("payload_path", o.optString("payload_path"));
        v.put("size", o.optLong("size", -1)); v.put("sha256", o.optString("sha256"));
        v.put("kind", o.optString("kind", "file")); v.put("family", o.optString("family", "unplaced"));
        v.put("version_token", o.optString("version_token")); v.put("migration_status", o.optString("migration_status", "REFERENCE_ONLY"));
        v.put("detail", o.optString("detail")); v.put("source_uri", o.optString("source_uri"));
        getWritableDatabase().insertOrThrow("migration_items", null, v);
    }

    public synchronized void finishMigrationRun(String runId, int payload, int references, int mismatches, String status, String detail) {
        ContentValues v = new ContentValues(); v.put("payload_count",payload); v.put("reference_count",references);
        v.put("mismatch_count",mismatches); v.put("status",safeText(status,"REVIEW")); v.put("detail",detail==null?"":detail);
        getWritableDatabase().update("migration_runs",v,"id=?",new String[]{runId});
        log("continuity","migration-finish",mismatches==0?"PASS":"REVIEW",runId+" payload="+payload+" reference="+references+" mismatch="+mismatches);
    }

    public synchronized JSONObject migrationSummary() throws Exception {
        JSONObject o = new JSONObject(); SQLiteDatabase db=getReadableDatabase();
        o.put("runs",scalar(db,"SELECT COUNT(*) FROM migration_runs"));
        o.put("items",scalar(db,"SELECT COUNT(*) FROM migration_items"));
        o.put("imported_bytes",scalar(db,"SELECT COUNT(*) FROM migration_items WHERE migration_status='IMPORTED_BYTES'"));
        o.put("reference_only",scalar(db,"SELECT COUNT(*) FROM migration_items WHERE migration_status LIKE 'REFERENCE_ONLY%' OR migration_status='NOT_MIGRATED'"));
        o.put("mismatches",scalar(db,"SELECT COUNT(*) FROM migration_items WHERE migration_status='HASH_MISMATCH'"));
        o.put("missing",scalar(db,"SELECT COUNT(*) FROM migration_items WHERE migration_status='MISSING'"));
        JSONArray runs=new JSONArray();
        try(Cursor c=db.rawQuery("SELECT * FROM migration_runs ORDER BY imported_at DESC LIMIT 50",null)){while(c.moveToNext())runs.put(cursorRow(c));}
        o.put("recent_runs",runs); return o;
    }

    public synchronized JSONArray searchMigrationItems(String query, int limit) throws Exception {
        JSONArray a=new JSONArray();String q="%"+(query==null?"":query.trim())+"%";int safeLimit=Math.max(1,Math.min(limit,500));
        try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM migration_items WHERE original_path LIKE ? OR family LIKE ? OR version_token LIKE ? OR migration_status LIKE ? ORDER BY id DESC LIMIT "+safeLimit,new String[]{q,q,q,q})){while(c.moveToNext())a.put(cursorRow(c));}
        return a;
    }

    public synchronized JSONObject getMigrationItem(long id) throws Exception {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM migration_items WHERE id=?",new String[]{String.valueOf(id)})){return c.moveToFirst()?cursorRow(c):null;}
    }


    public synchronized String beginBibleCorpus(String title,String translation,String sourceUri,String sha256,String license,boolean publicDomain) {
        String id="bcor_"+UUID.randomUUID().toString().replace("-","");
        String t=now(); ContentValues v=new ContentValues();
        v.put("id",id);v.put("title",safeText(title,"Imported Bible corpus"));v.put("translation",safeText(translation,"unknown"));
        v.put("source_uri",sourceUri==null?"":sourceUri);v.put("sha256",sha256==null?"":sha256);v.put("license",license==null?"":license);
        v.put("public_domain",publicDomain?1:0);v.put("verse_count",0);v.put("created_at",t);v.put("updated_at",t);
        getWritableDatabase().insertOrThrow("bible_corpora",null,v);
        log("bible","corpus-begin","PASS",id+" · "+title+" · "+translation);
        return id;
    }

    public synchronized void addBibleVerseBatch(String corpusId, JSONArray verses) throws Exception {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            for(int i=0;i<verses.length();i++){
                JSONObject o=verses.getJSONObject(i);ContentValues v=new ContentValues();
                v.put("corpus_id",corpusId);v.put("book_no",o.optInt("book_no",0));v.put("book",o.optString("book","Unknown"));
                v.put("chapter",o.optInt("chapter",0));v.put("verse",o.optInt("verse",0));v.put("text",o.optString("text",""));
                db.insertWithOnConflict("bible_verses",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    public synchronized void finishBibleCorpus(String corpusId,int verseCount) {
        ContentValues v=new ContentValues();v.put("verse_count",verseCount);v.put("updated_at",now());
        getWritableDatabase().update("bible_corpora",v,"id=?",new String[]{corpusId});
        log("bible","corpus-finish","PASS",corpusId+" · verses="+verseCount);
    }

    public synchronized JSONArray listBibleCorpora() throws Exception {
        JSONArray a=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM bible_corpora ORDER BY created_at DESC",null)){while(c.moveToNext())a.put(cursorRow(c));}
        return a;
    }

    public synchronized JSONArray bibleChapter(String corpusId,String book,int chapter) throws Exception {
        JSONArray a=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT book_no,book,chapter,verse,text FROM bible_verses WHERE corpus_id=? AND book=? AND chapter=? ORDER BY verse",new String[]{corpusId,book,String.valueOf(chapter)})){while(c.moveToNext())a.put(cursorRow(c));}
        return a;
    }

    public synchronized JSONArray searchBibleVerses(String corpusId,String query,int limit) throws Exception {
        JSONArray a=new JSONArray();int safe=Math.max(1,Math.min(limit,500));String q=query==null?"":query.trim();
        if(q.matches("(?i)^.+\\s+\\d+[:.]\\d+$")){
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)^(.+?)\\s+(\\d+)[:.](\\d+)$").matcher(q);
            if(m.find()){
                try(Cursor c=getReadableDatabase().rawQuery("SELECT book_no,book,chapter,verse,text FROM bible_verses WHERE corpus_id=? AND lower(book)=lower(?) AND chapter=? AND verse=? LIMIT "+safe,new String[]{corpusId,m.group(1).trim(),m.group(2),m.group(3)})){while(c.moveToNext())a.put(cursorRow(c));}
                return a;
            }
        }
        String like="%"+q+"%";
        try(Cursor c=getReadableDatabase().rawQuery("SELECT book_no,book,chapter,verse,text FROM bible_verses WHERE corpus_id=? AND (book LIKE ? OR text LIKE ?) ORDER BY book_no,chapter,verse LIMIT "+safe,new String[]{corpusId,like,like})){while(c.moveToNext())a.put(cursorRow(c));}
        return a;
    }

    public synchronized JSONObject stats() throws Exception {
        JSONObject o = new JSONObject(); SQLiteDatabase db = getReadableDatabase();
        o.put("projects", scalar(db, "SELECT COUNT(*) FROM projects WHERE archived=0"));
        o.put("records", scalar(db, "SELECT COUNT(*) FROM records WHERE archived=0"));
        o.put("archived_records", scalar(db, "SELECT COUNT(*) FROM records WHERE archived=1"));
        o.put("managed_files", scalar(db, "SELECT COUNT(*) FROM managed_files"));
        o.put("cantus_events", scalar(db, "SELECT COUNT(*) FROM cantus_log"));
        o.put("corpus_files", scalar(db, "SELECT COUNT(*) FROM corpus_files"));
        o.put("bible_corpora", scalar(db, "SELECT COUNT(*) FROM bible_corpora"));
        o.put("bible_verses", scalar(db, "SELECT COUNT(*) FROM bible_verses"));
        o.put("migration_runs", scalar(db, "SELECT COUNT(*) FROM migration_runs"));
        o.put("migration_items", scalar(db, "SELECT COUNT(*) FROM migration_items"));
        o.put("migration_unresolved", scalar(db, "SELECT COUNT(*) FROM migration_items WHERE migration_status!='IMPORTED_BYTES'"));
        o.put("db_integrity", integrity());
        return o;
    }

    private long scalar(SQLiteDatabase db, String sql) {
        try (Cursor c = db.rawQuery(sql, null)) { return c.moveToFirst() ? c.getLong(0) : 0; }
    }

    public synchronized String integrity() {
        try (Cursor c = getReadableDatabase().rawQuery("PRAGMA integrity_check", null)) {
            return c.moveToFirst() ? c.getString(0) : "unavailable";
        }
    }

    public synchronized void log(String module, String action, String status, String detail) {
        logInternal(getWritableDatabase(), safeText(module,"system"), safeText(action,"event"), safeText(status,"INFO"), detail == null ? "" : detail);
    }

    private void logInternal(SQLiteDatabase db, String module, String action, String status, String detail) {
        try {
            String prev = "GENESIS";
            try (Cursor c = db.rawQuery("SELECT chain_hash FROM cantus_log ORDER BY seq DESC LIMIT 1", null)) {
                if (c.moveToFirst()) prev = c.getString(0);
            }
            String ts = now();
            String canonical = ts + "\n" + module + "\n" + action + "\n" + status + "\n" + detail + "\n" + prev;
            String hash = sha256(canonical.getBytes(StandardCharsets.UTF_8));
            ContentValues v = new ContentValues(); v.put("ts",ts); v.put("module",module); v.put("action",action);
            v.put("status",status); v.put("detail",detail); v.put("prev_hash",prev); v.put("chain_hash",hash);
            db.insertOrThrow("cantus_log", null, v);
        } catch (Exception ignored) {}
    }

    public synchronized JSONArray cantusLogs(int limit) throws Exception {
        JSONArray a = new JSONArray(); int safeLimit = Math.max(1, Math.min(limit, 5000));
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT seq,ts,module,action,status,detail,prev_hash,chain_hash FROM cantus_log ORDER BY seq DESC LIMIT " + safeLimit, null)) {
            while (c.moveToNext()) a.put(cursorRow(c));
        }
        return a;
    }

    public synchronized JSONObject verifyCantus() throws Exception {
        SQLiteDatabase db = getReadableDatabase(); String expectedPrev = "GENESIS"; long count=0; JSONArray issues = new JSONArray();
        try (Cursor c = db.rawQuery("SELECT seq,ts,module,action,status,detail,prev_hash,chain_hash FROM cantus_log ORDER BY seq", null)) {
            while (c.moveToNext()) {
                long seq=c.getLong(0); String ts=c.getString(1),module=c.getString(2),action=c.getString(3),status=c.getString(4),detail=c.getString(5),prev=c.getString(6),hash=c.getString(7);
                String canonical = ts+"\n"+module+"\n"+action+"\n"+status+"\n"+detail+"\n"+prev;
                String calc=sha256(canonical.getBytes(StandardCharsets.UTF_8));
                if (!expectedPrev.equals(prev) || !calc.equals(hash)) {
                    JSONObject issue=new JSONObject(); issue.put("seq",seq); issue.put("expected_prev",expectedPrev); issue.put("stored_prev",prev); issue.put("stored_hash",hash); issue.put("calculated_hash",calc); issues.put(issue);
                }
                expectedPrev=hash; count++;
            }
        }
        JSONObject out=new JSONObject(); out.put("ok",issues.length()==0); out.put("events",count); out.put("issues",issues); out.put("head",expectedPrev); return out;
    }

    public synchronized JSONObject exportJson() throws Exception {
        JSONObject root = new JSONObject(); root.put("schema","thunderforge.r10.backup.v1"); root.put("exported_at",now());
        root.put("projects", tableJson("SELECT * FROM projects ORDER BY created_at"));
        root.put("files", tableJson("SELECT * FROM managed_files ORDER BY project_id,rel_path"));
        root.put("records", tableJson("SELECT * FROM records ORDER BY created_at"));
        root.put("cantus_verification", verifyCantus());
        root.put("corpus_summary", stats().getLong("corpus_files"));
        return root;
    }

    private JSONArray tableJson(String sql) throws Exception {
        JSONArray a = new JSONArray(); try(Cursor c=getReadableDatabase().rawQuery(sql,null)){while(c.moveToNext())a.put(cursorRow(c));} return a;
    }

    public synchronized JSONObject restoreJson(JSONObject backup) throws Exception {
        if (!"thunderforge.r10.backup.v1".equals(backup.optString("schema"))) throw new IllegalArgumentException("Not a Thunderforge R10 backup");
        SQLiteDatabase db=getWritableDatabase(); int projects=0,records=0; db.beginTransaction();
        try {
            JSONArray p=backup.optJSONArray("projects"); if(p!=null) for(int i=0;i<p.length();i++){JSONObject o=p.getJSONObject(i); ContentValues v=contentValues(o, new String[]{"id","name","description","family","version","parent_id","status","root_path","source_uri","evidence_state","created_at","updated_at","archived"}); long n=db.insertWithOnConflict("projects",null,v,SQLiteDatabase.CONFLICT_IGNORE); if(n!=-1)projects++;}
            JSONArray r=backup.optJSONArray("records"); if(r!=null) for(int i=0;i<r.length();i++){JSONObject o=r.getJSONObject(i); ContentValues v=contentValues(o,new String[]{"id","type","title","body","meta_json","created_at","updated_at","archived"}); long n=db.insertWithOnConflict("records",null,v,SQLiteDatabase.CONFLICT_IGNORE); if(n!=-1)records++;}
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
        log("backup","restore-merge","PASS","projects="+projects+" records="+records+"; Cantus source chain preserved separately");
        JSONObject out=new JSONObject();out.put("ok",true);out.put("projects_added",projects);out.put("records_added",records);return out;
    }

    private static ContentValues contentValues(JSONObject o,String[] keys) {
        ContentValues v=new ContentValues(); for(String k:keys){if(!o.has(k)||o.isNull(k))continue; Object x=o.opt(k); if(x instanceof Number)v.put(k,((Number)x).longValue()); else v.put(k,String.valueOf(x));} return v;
    }

    private static JSONObject cursorRow(Cursor c) throws Exception {
        JSONObject o=new JSONObject(); for(int i=0;i<c.getColumnCount();i++){String n=c.getColumnName(i); switch(c.getType(i)){case Cursor.FIELD_TYPE_NULL:o.put(n,JSONObject.NULL);break;case Cursor.FIELD_TYPE_INTEGER:o.put(n,c.getLong(i));break;case Cursor.FIELD_TYPE_FLOAT:o.put(n,c.getDouble(i));break;case Cursor.FIELD_TYPE_BLOB:o.put(n,"[blob]");break;default:o.put(n,c.getString(i));}} return o;
    }

    private static String safeText(String v,String fallback){if(v==null||v.trim().isEmpty())return fallback; return v.trim();}
    public static String sha256(byte[] data) throws Exception {MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] h=d.digest(data);StringBuilder b=new StringBuilder();for(byte x:h)b.append(String.format(Locale.US,"%02x",x));return b.toString();}
}
