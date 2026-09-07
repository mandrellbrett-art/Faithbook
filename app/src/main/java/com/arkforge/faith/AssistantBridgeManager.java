package com.arkforge.faith;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Explicit, user-controlled bridge between the private HeritageFaith app and an external assistant.
 *
 * There is intentionally NO background remote access, socket server, hidden sync, credential export,
 * arbitrary command execution, or automatic upload. The user must choose Export/Share or Import.
 */
public final class AssistantBridgeManager {
    public static final String CONTEXT_SCHEMA = "heritagefaith.assistant_context.v1";
    public static final String RETURN_SCHEMA = "heritagefaith.assistant_return.v1";
    private static final String PREFS = "heritagefaith_assistant_bridge";
    private static final long MAX_RETURN_BYTES = 32L * 1024 * 1024;
    private static final int MAX_RETURN_RECORDS = 1000;

    private static final Set<String> ALLOWED_BOOL_SETTINGS = new HashSet<>();
    static {
        ALLOWED_BOOL_SETTINGS.add("include_projects");
        ALLOWED_BOOL_SETTINGS.add("include_file_index");
        ALLOWED_BOOL_SETTINGS.add("include_study_records");
        ALLOWED_BOOL_SETTINGS.add("include_private_records");
        ALLOWED_BOOL_SETTINGS.add("include_cantus_logs");
        ALLOWED_BOOL_SETTINGS.add("include_continuity");
        ALLOWED_BOOL_SETTINGS.add("include_library_catalog");
        ALLOWED_BOOL_SETTINGS.add("allow_return_import");
    }

    private AssistantBridgeManager() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean pref(Context c, String key, boolean fallback) {
        return prefs(c).getBoolean(key, fallback);
    }

    public static JSONObject settings(Context c) throws Exception {
        JSONObject o = new JSONObject();
        o.put("ok", true);
        o.put("access_model", "EXPLICIT_USER_HANDOFF_ONLY");
        o.put("background_remote_access", false);
        o.put("include_projects", pref(c, "include_projects", true));
        o.put("include_file_index", pref(c, "include_file_index", true));
        o.put("include_study_records", pref(c, "include_study_records", true));
        o.put("include_private_records", pref(c, "include_private_records", false));
        o.put("include_cantus_logs", pref(c, "include_cantus_logs", true));
        o.put("include_continuity", pref(c, "include_continuity", true));
        o.put("include_library_catalog", pref(c, "include_library_catalog", true));
        o.put("allow_return_import", pref(c, "allow_return_import", true));
        o.put("raw_book_bytes_in_context_bundle", false);
        o.put("credentials_exported", false);
        o.put("code_execution_from_return_bundle", false);
        return o;
    }

    public static JSONObject setSetting(Context c, String key, boolean value) throws Exception {
        if (!ALLOWED_BOOL_SETTINGS.contains(key)) throw new IllegalArgumentException("Unknown Assistant Bridge setting");
        prefs(c).edit().putBoolean(key, value).apply();
        JSONObject out = settings(c);
        out.put("changed", key);
        out.put("value", value);
        return out;
    }

    public static JSONObject exportToUri(Context c, R10Database db, Uri destination, String mode) throws Exception {
        JSONObject context = buildContext(c, db, mode);
        byte[] contextBytes = context.toString(2).getBytes(StandardCharsets.UTF_8);
        String contextSha = sha256(contextBytes);

        try (OutputStream raw = c.getContentResolver().openOutputStream(destination, "wt");
             ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(requireOut(raw)))) {
            writeEntry(zout, "assistant-context.json", contextBytes);
            writeEntry(zout, "assistant-context.sha256",
                    (contextSha + "  assistant-context.json\n").getBytes(StandardCharsets.UTF_8));
            writeEntry(zout, "README.txt", readme().getBytes(StandardCharsets.UTF_8));
        }

        db.log("assistant-bridge", "context-export", "PASS",
                "mode=" + normalizeMode(mode) + " sha256=" + contextSha + " -> " + destination);
        JSONObject out = new JSONObject();
        out.put("ok", true);
        out.put("kind", "assistant-context");
        out.put("mode", normalizeMode(mode));
        out.put("sha256", contextSha);
        out.put("bytes", contextBytes.length);
        out.put("uri", destination.toString());
        return out;
    }

    public static File createShareBundle(Context c, R10Database db, String mode) throws Exception {
        File dir = new File(c.getFilesDir(), "assistant_bridge");
        if (!dir.mkdirs() && !dir.isDirectory()) throw new IllegalStateException("Could not create Assistant Bridge directory");
        File out = new File(dir, "HeritageFaith_Assistant_Context_" + System.currentTimeMillis() + ".zip");
        JSONObject context = buildContext(c, db, mode);
        byte[] contextBytes = context.toString(2).getBytes(StandardCharsets.UTF_8);
        String contextSha = sha256(contextBytes);
        try (ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
            writeEntry(zout, "assistant-context.json", contextBytes);
            writeEntry(zout, "assistant-context.sha256",
                    (contextSha + "  assistant-context.json\n").getBytes(StandardCharsets.UTF_8));
            writeEntry(zout, "README.txt", readme().getBytes(StandardCharsets.UTF_8));
        }
        db.log("assistant-bridge", "context-share-create", "PASS",
                "mode=" + normalizeMode(mode) + " bytes=" + out.length() + " sha256=" + sha256File(out));
        return out;
    }

    public static JSONObject buildContext(Context c, R10Database db, String mode) throws Exception {
        boolean full = "full".equals(normalizeMode(mode));
        JSONObject source = db.exportJson();
        JSONObject root = new JSONObject();
        root.put("schema", CONTEXT_SCHEMA);
        root.put("product", "HeritageFaith");
        root.put("mode", full ? "FULL_PRIVATE_EXPLICIT" : "STANDARD_SCOPED");
        root.put("access_model", "EXPLICIT_USER_HANDOFF_ONLY");
        root.put("background_remote_access", false);
        root.put("credentials_included", false);
        root.put("raw_managed_file_bytes_included", false);
        root.put("raw_book_bytes_included", false);
        root.put("instructions",
                "This bundle is a user-authorized snapshot for analysis. It does not grant remote access to the phone or app.");
        root.put("settings", settings(c));

        if (pref(c, "include_projects", true) || full) {
            root.put("projects", source.optJSONArray("projects") == null ? new JSONArray() : source.getJSONArray("projects"));
        }

        if (pref(c, "include_file_index", true) || full) {
            root.put("managed_file_index", source.optJSONArray("files") == null ? new JSONArray() : source.getJSONArray("files"));
        }

        if (pref(c, "include_study_records", true) || full) {
            JSONArray records = source.optJSONArray("records");
            root.put("records", filterRecords(records, full || pref(c, "include_private_records", false)));
        }

        if (pref(c, "include_cantus_logs", true) || full) {
            root.put("cantus_verification", db.verifyCantus());
            root.put("cantus_recent", db.cantusLogs(full ? 1000 : 250));
            JSONObject cantus = new JSONObject();
            cantus.put("enabled", true);
            cantus.put("pipeline", new JSONArray()
                    .put("Source expression")
                    .put("Plain meaning")
                    .put("Ademic Cantus")
                    .put("Resonance")
                    .put("Runic compression")
                    .put("Plain-language expansion"));
            cantus.put("rule", "Cantus carries the meaning. Runes carry the Cantus. Neither replaces the source.");
            root.put("ademic_cantus", cantus);
        }

        if (pref(c, "include_continuity", true) || full) {
            root.put("continuity", db.migrationSummary());
        }

        if (pref(c, "include_library_catalog", true) || full) {
            root.put("private_library_sources", readAssetJson(c, "private-library-sources.json"));
        }

        JSONArray exclusions = new JSONArray();
        exclusions.put("Passwords, tokens, and credentials are never intentionally exported.");
        exclusions.put("Raw project/book file bytes are not included in Assistant Context bundles.");
        if (!full && !pref(c, "include_private_records", false)) {
            exclusions.put("Sensitive/private record categories are excluded unless explicitly enabled.");
        }
        root.put("exclusions", exclusions);
        root.put("assistant_return_schema", RETURN_SCHEMA);
        return root;
    }

    private static JSONArray filterRecords(JSONArray records, boolean includePrivate) throws Exception {
        JSONArray out = new JSONArray();
        if (records == null) return out;
        for (int i = 0; i < records.length(); i++) {
            JSONObject r = records.getJSONObject(i);
            String type = r.optString("type").toLowerCase(Locale.US);
            if (!includePrivate && sensitiveType(type)) continue;
            out.put(r);
        }
        return out;
    }

    private static boolean sensitiveType(String type) {
        String[] words = {"medical","health","ancestor","ancestry","family","prayer","contact","message",
                "sms","email","location","geo","private","identity","financial","legal"};
        for (String w : words) if (type.contains(w)) return true;
        return false;
    }

    public static JSONObject importReturnUri(Context c, R10Database db, Uri uri) throws Exception {
        if (!pref(c, "allow_return_import", true)) {
            throw new IllegalStateException("Assistant Return import is disabled in Settings");
        }
        String name = ImportManager.displayName(c, uri);
        JSONObject payload;
        if (name.toLowerCase(Locale.US).endsWith(".zip")) payload = readReturnZip(c, uri);
        else payload = new JSONObject(new String(readUri(c, uri, MAX_RETURN_BYTES), StandardCharsets.UTF_8));

        if (!RETURN_SCHEMA.equals(payload.optString("schema"))) {
            throw new IllegalArgumentException("Not a HeritageFaith Assistant Return bundle");
        }

        JSONArray records = payload.optJSONArray("records");
        int imported = 0;
        if (records != null) {
            if (records.length() > MAX_RETURN_RECORDS) throw new IllegalArgumentException("Assistant Return has too many records");
            for (int i = 0; i < records.length(); i++) {
                JSONObject r = records.getJSONObject(i);
                String type = safe(r.optString("type", "assistant_note"), "assistant_note");
                String title = safe(r.optString("title", "Assistant note"), "Assistant note");
                String body = r.optString("body", "");
                if (body.length() > 250000) throw new IllegalArgumentException("Assistant Return record is too large");
                JSONObject meta = r.optJSONObject("meta");
                if (meta == null) meta = new JSONObject();
                meta.put("assistant_return_import", true);
                meta.put("source_uri", uri.toString());
                meta.put("execution_allowed", false);
                db.addRecord(type, title, body, meta.toString());
                imported++;
            }
        }

        JSONArray proposals = payload.optJSONArray("proposals");
        int proposalsImported = 0;
        if (proposals != null) {
            if (proposals.length() > 500) throw new IllegalArgumentException("Too many Assistant Return proposals");
            for (int i = 0; i < proposals.length(); i++) {
                JSONObject p = proposals.getJSONObject(i);
                JSONObject meta = new JSONObject();
                meta.put("assistant_return_import", true);
                meta.put("proposal", true);
                meta.put("execution_allowed", false);
                meta.put("source_uri", uri.toString());
                db.addRecord("assistant_proposal",
                        safe(p.optString("title", "Assistant proposal"), "Assistant proposal"),
                        p.optString("body", p.toString(2)),
                        meta.toString());
                proposalsImported++;
            }
        }

        db.log("assistant-bridge", "return-import", "PASS",
                "records=" + imported + " proposals=" + proposalsImported + " execution=false");
        JSONObject out = new JSONObject();
        out.put("ok", true);
        out.put("kind", "assistant-return");
        out.put("records_imported", imported);
        out.put("proposals_imported", proposalsImported);
        out.put("code_executed", false);
        return out;
    }

    private static JSONObject readReturnZip(Context c, Uri uri) throws Exception {
        JSONObject found = null;
        try (InputStream raw = c.getContentResolver().openInputStream(uri);
             ZipInputStream zin = new ZipInputStream(new BufferedInputStream(requireIn(raw)))) {
            ZipEntry e;
            int entries = 0;
            while ((e = zin.getNextEntry()) != null) {
                entries++;
                if (entries > 1000) throw new IllegalArgumentException("Assistant Return ZIP has too many entries");
                String n = e.getName() == null ? "" : e.getName().replace('\\','/');
                if (n.contains("..")) throw new IllegalArgumentException("Unsafe ZIP path");
                if (!e.isDirectory() && (n.equals("assistant-return.json") || n.endsWith("/assistant-return.json"))) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    copyLimited(zin, b, MAX_RETURN_BYTES);
                    found = new JSONObject(new String(b.toByteArray(), StandardCharsets.UTF_8));
                }
                zin.closeEntry();
            }
        }
        if (found == null) throw new IllegalArgumentException("ZIP is missing assistant-return.json");
        return found;
    }

    private static byte[] readUri(Context c, Uri uri, long max) throws Exception {
        try (InputStream in = c.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream b = new ByteArrayOutputStream()) {
            copyLimited(requireIn(in), b, max);
            return b.toByteArray();
        }
    }

    private static JSONObject readAssetJson(Context c, String name) throws Exception {
        try (InputStream in = c.getAssets().open(name);
             ByteArrayOutputStream b = new ByteArrayOutputStream()) {
            copyLimited(in, b, 8L * 1024 * 1024);
            return new JSONObject(new String(b.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    private static void writeEntry(ZipOutputStream z, String name, byte[] bytes) throws Exception {
        z.putNextEntry(new ZipEntry(name));
        z.write(bytes);
        z.closeEntry();
    }

    private static String readme() {
        return "HeritageFaith Assistant Bridge\\n\\n"
                + "This bundle was created only after an explicit user action.\\n"
                + "It does NOT grant background access to the phone or HeritageFaith.\\n"
                + "The assistant can analyze only the files/data the user shares or places in an authorized connected source.\\n"
                + "Raw book/project bytes and credentials are not included in this context bundle.\\n\\n"
                + "To return structured notes to HeritageFaith, use schema: " + RETURN_SCHEMA + "\\n"
                + "Returned records are imported additively and are never executed as code.\\n";
    }

    private static String normalizeMode(String mode) {
        return "full".equalsIgnoreCase(mode == null ? "" : mode.trim()) ? "full" : "standard";
    }

    private static String safe(String s, String fallback) {
        String x = s == null ? "" : s.trim();
        return x.isEmpty() ? fallback : x;
    }

    private static void copyLimited(InputStream in, OutputStream out, long max) throws Exception {
        byte[] buf = new byte[65536];
        long total = 0;
        int r;
        while ((r = in.read(buf)) != -1) {
            total += r;
            if (total > max) throw new IllegalArgumentException("Assistant Bridge size safety limit exceeded");
            out.write(buf, 0, r);
        }
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        return hex(d.digest(data));
    }

    private static String sha256File(File f) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(new java.io.FileInputStream(f))) {
            byte[] b = new byte[65536];
            int r;
            while ((r = in.read(b)) != -1) d.update(b, 0, r);
        }
        return hex(d.digest());
    }

    private static String hex(byte[] h) {
        StringBuilder b = new StringBuilder();
        for (byte x : h) b.append(String.format(Locale.US, "%02x", x));
        return b.toString();
    }

    private static InputStream requireIn(InputStream i) {
        if (i == null) throw new IllegalArgumentException("Could not read selected Assistant Bridge file");
        return i;
    }

    private static OutputStream requireOut(OutputStream o) {
        if (o == null) throw new IllegalArgumentException("Could not create Assistant Bridge destination");
        return o;
    }
}
