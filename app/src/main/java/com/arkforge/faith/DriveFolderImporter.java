package com.arkforge.faith;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Recursive Storage Access Framework importer for a user-selected document tree.
 *
 * Works with providers exposed through Android's system picker, including Google Drive
 * when available on the device. HeritageFaith receives only the tree the user selects.
 */
public final class DriveFolderImporter {
    private static final int MAX_FILES = 5000;
    private static final int MAX_DEPTH = 40;

    private static final String[] BOOK_EXTENSIONS = {
            "pdf","epub","mobi","azw","azw3","djvu","fb2",
            "txt","md","rtf","doc","docx","odt","html","htm",
            "cbz","cbr"
    };

    private DriveFolderImporter() {}

    public static JSONObject importTree(Context context, R10Database db, Uri treeUri) throws Exception {
        String rootDocId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId);
        String folderName = displayName(context, rootDocUri);
        if (folderName == null || folderName.trim().isEmpty()) folderName = "Drive Library Folder";

        List<Entry> entries = new ArrayList<>();
        walk(context, treeUri, rootDocId, "", entries, 0);

        JSONArray items = new JSONArray();
        int imported = 0, skipped = 0, failed = 0;

        JSONObject sourceMeta = new JSONObject();
        sourceMeta.put("tree_uri", treeUri.toString());
        sourceMeta.put("provider", treeUri.getAuthority());
        sourceMeta.put("folder_name", folderName);
        sourceMeta.put("rights_status", "PRIVATE_USER_OWNED_OR_AUTHORIZED");
        sourceMeta.put("distribution", "PRIVATE_ONLY");
        sourceMeta.put("persisted_access", true);
        sourceMeta.put("recursive", true);
        db.addRecord("library_source", folderName,
                "Private recursive library folder authorized through Android's document picker.",
                sourceMeta.toString());

        for (Entry e : entries) {
            JSONObject item = new JSONObject();
            item.put("name", e.name);
            item.put("relative_path", e.relativePath);
            item.put("source_uri", e.uri.toString());

            if (!isBookLike(e.name)) {
                item.put("ok", true);
                item.put("status", "SKIPPED_UNSUPPORTED");
                items.put(item);
                skipped++;
                continue;
            }

            try {
                JSONObject importedProject = ImportManager.importUri(context, db, e.uri, "library");
                String title = importedProject.optString("name",
                        importedProject.optString("project_name", stripExtension(e.name)));

                JSONObject meta = new JSONObject();
                meta.put("project_id", importedProject.optString("id"));
                meta.put("filename", e.name);
                meta.put("relative_path", e.relativePath);
                meta.put("source_uri", e.uri.toString());
                meta.put("source_tree_uri", treeUri.toString());
                meta.put("source_folder", folderName);
                meta.put("sha256", importedProject.optString("sha256"));
                meta.put("bytes", importedProject.optLong("bytes"));
                meta.put("rights_status", "PRIVATE_USER_OWNED_OR_AUTHORIZED");
                meta.put("distribution", "PRIVATE_ONLY");
                meta.put("import_method", "SAF_RECURSIVE_FOLDER");
                db.addRecord("library", title,
                        "Private book imported recursively from a user-authorized Android document tree.",
                        meta.toString());

                item.put("ok", true);
                item.put("status", "IMPORTED");
                item.put("project_id", importedProject.optString("id"));
                item.put("sha256", importedProject.optString("sha256"));
                item.put("bytes", importedProject.optLong("bytes"));
                imported++;
            } catch (Exception ex) {
                item.put("ok", false);
                item.put("status", "FAILED");
                item.put("error", String.valueOf(ex.getMessage()));
                failed++;
                db.log("library", "folder-import-item", "FAIL",
                        e.relativePath + " · " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
            items.put(item);
        }

        JSONObject result = new JSONObject();
        result.put("ok", failed == 0);
        result.put("folder_name", folderName);
        result.put("tree_uri", treeUri.toString());
        result.put("files_seen", entries.size());
        result.put("books_imported", imported);
        result.put("skipped_unsupported", skipped);
        result.put("failed", failed);
        result.put("items", items);

        db.log("library", "folder-import", failed == 0 ? "PASS" : "PARTIAL",
                folderName + " files=" + entries.size() + " imported=" + imported +
                        " skipped=" + skipped + " failed=" + failed);
        return result;
    }

    private static void walk(Context context, Uri treeUri, String parentDocId, String relPrefix,
                             List<Entry> out, int depth) throws Exception {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("Folder nesting is too deep");
        if (out.size() > MAX_FILES) throw new IllegalArgumentException("Folder contains more than " + MAX_FILES + " files");

        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId);
        String[] projection = new String[] {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };

        try (Cursor c = context.getContentResolver().query(childrenUri, projection, null, null, null)) {
            if (c == null) throw new IllegalArgumentException("The selected provider did not expose this folder");
            int idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);

            while (c.moveToNext()) {
                String docId = c.getString(idCol);
                String name = c.getString(nameCol);
                String mime = c.getString(mimeCol);
                if (name == null || name.trim().isEmpty()) name = "unnamed";
                String rel = relPrefix.isEmpty() ? name : relPrefix + "/" + name;

                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    walk(context, treeUri, docId, rel, out, depth + 1);
                } else {
                    Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
                    out.add(new Entry(docUri, name, rel, mime));
                    if (out.size() > MAX_FILES)
                        throw new IllegalArgumentException("Folder contains more than " + MAX_FILES + " files");
                }
            }
        }
    }

    private static String displayName(Context context, Uri uri) {
        String[] projection = { DocumentsContract.Document.COLUMN_DISPLAY_NAME };
        try (Cursor c = context.getContentResolver().query(uri, projection, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean isBookLike(String name) {
        String ext = extension(name);
        for (String x : BOOK_EXTENSIONS) if (x.equals(ext)) return true;
        return false;
    }

    private static String extension(String name) {
        int i = name == null ? -1 : name.lastIndexOf('.');
        return i >= 0 ? name.substring(i + 1).toLowerCase(Locale.US) : "";
    }

    private static String stripExtension(String name) {
        int i = name == null ? -1 : name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : (name == null ? "Imported book" : name);
    }

    private static final class Entry {
        final Uri uri;
        final String name;
        final String relativePath;
        final String mime;
        Entry(Uri uri, String name, String relativePath, String mime) {
            this.uri = uri; this.name = name; this.relativePath = relativePath; this.mime = mime;
        }
    }
}
