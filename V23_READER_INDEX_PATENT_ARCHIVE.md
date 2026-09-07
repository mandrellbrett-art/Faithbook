# HeritageFaith V23 — Reader, Library Index, and Legacy Patent Archive

## What V23 adds

### 1. Better private-library indexing
Imported book records are indexed by:
- title
- inferred/recorded author
- format
- source shelf
- relative Drive path
- byte size
- SHA-256 when available
- duplicate-hash indicator

The Library page adds search, author/format/shelf filters and sorting.

### 2. Real page-mode Scripture reader
The built-in 73-book Original Douay-Rheims reader now supports:
- page-turn mode or continuous scroll
- previous/next page inside a chapter
- swipe left/right in page mode
- chapter navigation
- persistent reading settings
- verses-per-page settings
- text-size settings
- verse numbering toggle
- red-letter annotation toggle
- chapter-page numbering
- subtle reduced-motion-aware page-turn animation

Scripture source text remains unchanged. Pagination is a display layer.

### 3. Legacy Patent Inventory — 70+ years
V23 adds a scalable SQLite inventory table for historical patent metadata.

Cutoff rule:
`grant date < current device date minus 70 years`.

Rows with unknown grant dates are skipped rather than guessed.

Supported bulk inventory input:
- CSV
- JSONL
- NDJSON

Search fields:
- publication/patent number
- title
- inventor
- assignee
- classification
- jurisdiction

Coverage is always source-dependent. HeritageFaith never claims that the world's complete patent corpus is present unless a complete source manifest is actually imported.

### 4. Additive DB migration
Database version 8 adds the legacy patent table without replacing existing Home Base, books, notes, Continuity, Constructor, or Ademic Cantus data.
