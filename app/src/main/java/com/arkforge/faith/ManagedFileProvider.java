package com.arkforge.faith;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

/** Read-only provider for files under this app's private files directory. */
public class ManagedFileProvider extends ContentProvider {
    public static Uri uriForFile(Context context, File file) {
        try {
            File root = context.getFilesDir().getCanonicalFile();
            File target = file.getCanonicalFile();
            String rootPath = root.getPath() + File.separator;
            if (!target.getPath().startsWith(rootPath)) {
                throw new IllegalArgumentException("File is outside Thunderforge managed storage");
            }
            String rel = target.getPath().substring(rootPath.length());
            return new Uri.Builder()
                    .scheme("content")
                    .authority(context.getPackageName() + ".files")
                    .appendPath("managed")
                    .appendPath(rel)
                    .build();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        if (getContext() == null || uri == null || uri.getPathSegments().size() < 2 ||
                !"managed".equals(uri.getPathSegments().get(0))) {
            throw new FileNotFoundException("Invalid managed file URI");
        }
        String rel = uri.getPathSegments().get(1);
        try {
            File root = getContext().getFilesDir().getCanonicalFile();
            File target = new File(root, rel).getCanonicalFile();
            if (!target.getPath().startsWith(root.getPath() + File.separator) || !target.isFile()) {
                throw new FileNotFoundException("Managed file not found");
            }
            return target;
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) {
        try {
            String ext = MimeTypeMap.getFileExtensionFromUrl(resolve(uri).getName()).toLowerCase(Locale.US);
            String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            return type != null ? type : "application/octet-stream";
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        try {
            File f = resolve(uri);
            String[] cols = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
            MatrixCursor c = new MatrixCursor(cols, 1);
            Object[] row = new Object[cols.length];
            for (int i = 0; i < cols.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
                else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
                else row[i] = null;
            }
            c.addRow(row);
            return c;
        } catch (Exception e) {
            return null;
        }
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only provider");
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("read only"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
