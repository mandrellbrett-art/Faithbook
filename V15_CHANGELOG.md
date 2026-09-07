# Arkforge Faith V15 — Scripture Reader

## Added
- Download-once, offline 73-book Original Douay-Rheims Scripture reader.
- Source: `janvier-s/original-douay-rheims`, CC0 1.0 / public domain.
- The three historical Vulgate appendix texts present in the source repository are excluded from the default 73-book Catholic canon.
- Full book/chapter navigation and local full-text search after installation.
- Original source footnotes and cross-references surfaced by the reader when present.
- Verse-linked notes and questions.
- Reviewed red-letter overlay remains a separate annotation layer; source text is never rewritten.
- Arthur Context links chapter text to starter people, peoples, places, objects and timeline records.
- Explicit boundary: this is not the Ignatius RSV-2CE Study Bible and contains no Ignatius study notes.

## Runtime boundary
The source archive does not embed the 73 remote JSON files. On the user's explicit **Install Scripture** action, the Android app downloads the CC0 book JSON directly from the repository's `raw.githubusercontent.com` paths, validates each JSON book, and caches it in app-private storage. Existing personal data is not deleted or replaced.

## Release status
Source-level candidate only. A signed AAB, physical-phone acceptance, store policy review, and final rights review remain required before release.
