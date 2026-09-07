package com.arkforge.faith;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Metadata inventory importer for patents older than 70 years.
 *
 * Accepts CSV or NDJSON. The cutoff is calculated from the device date.
 * Rows without a usable grant date are skipped rather than guessed.
 */
public final class LegacyPatentImporter {
    private static final int BATCH = 1000;
    private static final long MAX_ROWS = 10_000_000L;

    private LegacyPatentImporter() {}

    public static String cutoffYmd() {
        return LocalDate.now().minusYears(70).format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    public static JSONObject importUri(Context context, R10Database db, Uri uri) throws Exception {
        String name = ImportManager.displayName(context, uri);
        String lower = name.toLowerCase(Locale.US);
        if (!(lower.endsWith(".csv") || lower.endsWith(".jsonl") || lower.endsWith(".ndjson"))) {
            throw new IllegalArgumentException("Legacy patent inventory expects CSV, JSONL, or NDJSON metadata.");
        }

        long seen = 0, imported = 0, tooNew = 0, noGrantDate = 0, invalid = 0;
        String cutoff = cutoffYmd();
        JSONArray batch = new JSONArray();

        try (InputStream raw = context.getContentResolver().openInputStream(uri);
             BufferedReader r = new BufferedReader(new InputStreamReader(raw, StandardCharsets.UTF_8), 128 * 1024)) {
            if (lower.endsWith(".csv")) {
                String first = r.readLine();
                if (first == null) throw new IllegalArgumentException("Patent CSV is empty.");
                List<String> header = csvLine(first);
                Map<String,Integer> h = headerMap(header);
                requireColumn(h, "publication_number", "publication number", "patent_number", "patent number");
                String line;
                while ((line = r.readLine()) != null) {
                    if (++seen > MAX_ROWS) throw new IllegalArgumentException("Patent import exceeded 10,000,000 row safety limit.");
                    if (line.isBlank()) continue;
                    try {
                        List<String> cells = csvLine(line);
                        JSONObject p = fromCsv(cells,h,name,uri.toString());
                        String date = p.optString("grant_date");
                        if (date.isEmpty()) { noGrantDate++; continue; }
                        if (date.compareTo(cutoff) >= 0) { tooNew++; continue; }
                        batch.put(p);
                        if (batch.length() >= BATCH) { db.upsertLegacyPatentBatch(batch); imported += batch.length(); batch = new JSONArray(); }
                    } catch (Exception ex) { invalid++; }
                }
            } else {
                String line;
                while ((line = r.readLine()) != null) {
                    if (++seen > MAX_ROWS) throw new IllegalArgumentException("Patent import exceeded 10,000,000 row safety limit.");
                    if (line.isBlank()) continue;
                    try {
                        JSONObject in = new JSONObject(line);
                        JSONObject p = normalize(in,name,uri.toString());
                        String date = p.optString("grant_date");
                        if (date.isEmpty()) { noGrantDate++; continue; }
                        if (date.compareTo(cutoff) >= 0) { tooNew++; continue; }
                        batch.put(p);
                        if (batch.length() >= BATCH) { db.upsertLegacyPatentBatch(batch); imported += batch.length(); batch = new JSONArray(); }
                    } catch (Exception ex) { invalid++; }
                }
            }
        }
        if (batch.length() > 0) { db.upsertLegacyPatentBatch(batch); imported += batch.length(); }

        db.addRecord("legacy_patent_source", name,
                "Imported patent inventory metadata filtered to grants older than 70 years.",
                new JSONObject()
                        .put("source_uri",uri.toString())
                        .put("cutoff",cutoff)
                        .put("rows_seen",seen)
                        .put("rows_imported",imported)
                        .put("rows_too_new",tooNew)
                        .put("rows_missing_grant_date",noGrantDate)
                        .put("rows_invalid",invalid)
                        .put("coverage_claim","SOURCE_DEPENDENT")
                        .toString());

        db.log("patents","legacy-inventory-import","PASS",
                name+" seen="+seen+" imported="+imported+" cutoff="+cutoff);

        JSONObject out = db.legacyPatentStats(cutoff);
        out.put("name",name);
        out.put("rows_seen",seen);
        out.put("rows_imported",imported);
        out.put("rows_too_new",tooNew);
        out.put("rows_missing_grant_date",noGrantDate);
        out.put("rows_invalid",invalid);
        out.put("message","Imported only patents whose grant date is older than the 70-year cutoff.");
        return out;
    }

    private static JSONObject fromCsv(List<String> c, Map<String,Integer> h, String source, String sourceUrl) throws Exception {
        JSONObject p = new JSONObject();
        p.put("publication_number", pick(c,h,"publication_number","publication number","patent_number","patent number"));
        p.put("title", pick(c,h,"title","invention_title","patent_title"));
        p.put("grant_date", ymd(pick(c,h,"grant_date","grant date","date_granted")));
        p.put("filing_date", ymd(pick(c,h,"filing_date","filing date","application_date")));
        p.put("priority_date", ymd(pick(c,h,"priority_date","priority date")));
        p.put("inventor", pick(c,h,"inventor","inventors","inventor_name"));
        p.put("assignee", pick(c,h,"assignee","assignees","organization","applicant"));
        p.put("jurisdiction", jurisdiction(pick(c,h,"jurisdiction","country_code","country","office"), p.optString("publication_number")));
        p.put("classification", pick(c,h,"classification","cpc","ipc","uspc","class"));
        p.put("source",source);
        p.put("source_url",sourceUrl);
        return p;
    }

    private static JSONObject normalize(JSONObject in, String source, String sourceUrl) throws Exception {
        JSONObject p = new JSONObject();
        String pub = first(in,"publication_number","patent_number","publicationNumber");
        p.put("publication_number",pub);
        p.put("title",first(in,"title","invention_title","patent_title"));
        p.put("grant_date",ymd(first(in,"grant_date","grantDate","date_granted")));
        p.put("filing_date",ymd(first(in,"filing_date","filingDate","application_date")));
        p.put("priority_date",ymd(first(in,"priority_date","priorityDate")));
        p.put("inventor",first(in,"inventor","inventors","inventor_name"));
        p.put("assignee",first(in,"assignee","assignees","applicant"));
        p.put("jurisdiction",jurisdiction(first(in,"jurisdiction","country_code","country"),pub));
        p.put("classification",first(in,"classification","cpc","ipc","uspc"));
        p.put("source",source);
        p.put("source_url",sourceUrl);
        return p;
    }

    private static String first(JSONObject o, String... keys) {
        for (String k: keys) {
            Object v = o.opt(k);
            if (v != null && v != JSONObject.NULL) {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty()) return s;
            }
        }
        return "";
    }

    private static String jurisdiction(String j, String publication) {
        String x = j == null ? "" : j.trim().toUpperCase(Locale.US);
        if (!x.isEmpty()) return x;
        String p = publication == null ? "" : publication.trim().toUpperCase(Locale.US);
        int dash = p.indexOf('-');
        if (dash > 0 && dash <= 3) return p.substring(0,dash);
        if (p.length() >= 2 && Character.isLetter(p.charAt(0)) && Character.isLetter(p.charAt(1))) return p.substring(0,2);
        return "";
    }

    private static String ymd(String raw) {
        if (raw == null) return "";
        String d = raw.replaceAll("[^0-9]","");
        if (d.length() >= 8) return d.substring(0,8);
        if (d.length() == 4) return d + "0101";
        return "";
    }

    private static Map<String,Integer> headerMap(List<String> h) {
        Map<String,Integer> m = new HashMap<>();
        for (int i=0;i<h.size();i++) m.put(h.get(i).trim().toLowerCase(Locale.US),i);
        return m;
    }

    private static void requireColumn(Map<String,Integer> h, String... names) {
        for (String n:names) if (h.containsKey(n)) return;
        throw new IllegalArgumentException("Patent CSV needs a publication_number or patent_number column.");
    }

    private static String pick(List<String> cells, Map<String,Integer> h, String... names) {
        for (String n:names) {
            Integer i = h.get(n);
            if (i != null && i >= 0 && i < cells.size()) {
                String v = cells.get(i).trim();
                if (!v.isEmpty()) return v;
            }
        }
        return "";
    }

    private static List<String> csvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        boolean quote = false;
        for (int i=0;i<line.length();i++) {
            char ch=line.charAt(i);
            if (ch=='"') {
                if (quote && i+1<line.length() && line.charAt(i+1)=='"') { b.append('"'); i++; }
                else quote=!quote;
            } else if (ch==',' && !quote) {
                out.add(b.toString()); b.setLength(0);
            } else b.append(ch);
        }
        out.add(b.toString());
        return out;
    }
}
