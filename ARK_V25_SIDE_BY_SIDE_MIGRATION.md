# ARK V25 — Permanent Side-by-Side Migration

This build uses a new Android package ID:

`com.arkforge.ark`

The old app remains:

`com.arkforge.faith`

That means Android can install ARK V25 **beside** the old HeritageFaith/ARK without requiring the lost V23 signing key.

Permanent signing certificate SHA-256:

`CC:45:40:DC:8C:DA:C1:29:B9:5A:D6:67:DD:51:F0:97:16:97:08:82:16:69:74:97:CA:E2:AC:D4:01:A7:5F:5A`

## Migration order

1. Install ARK V25 beside the old app.
2. Export a full backup from the old app.
3. In ARK: Settings → Restore / merge HeritageFaith backup.
4. Run Home Base → Phone Intake → INDEX ONLY.
5. Import Continuity / Kernel / project packages as found.
6. Compare counts, hashes, saves, projects and unresolved gaps.
7. Keep the old app until acceptance passes.

The permanent signing key is never committed into Git. The provided one-command installer places it in a private Termux directory and uploads it to GitHub Actions as encrypted repository secrets.
