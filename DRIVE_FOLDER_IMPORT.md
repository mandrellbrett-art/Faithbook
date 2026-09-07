# HeritageFaith V22 — Recursive Drive Folder Import

HeritageFaith can now import a whole folder selected through Android's Storage Access Framework.

## What this means

1. Open **Settings**.
2. Tap **Import entire Drive / document folder**.
3. In Android's system folder picker, choose **Google Drive**.
4. Select one of the six book folders and choose **Use this folder**.
5. HeritageFaith recursively walks that folder and copies supported book/document files into its private managed storage.
6. Repeat once for each of the six folders.

The app receives only the folder trees you explicitly authorize. Android persists the read permission for that tree.

## Supported book/document extensions

PDF, EPUB, MOBI, AZW/AZW3, DJVU, FB2, TXT, Markdown, RTF, DOC/DOCX, ODT, HTML/HTM, CBZ/CBR.

Other files are left untouched and reported as `SKIPPED_UNSUPPORTED`.

## Provenance

Every imported item records:
- source document URI
- source tree URI
- relative path
- source folder name
- SHA-256 where available from the normal managed-file importer
- byte count
- `PRIVATE_USER_OWNED_OR_AUTHORIZED`
- `PRIVATE_ONLY`

## Important

This is additive and non-destructive. It does not delete or modify the Google Drive originals.
