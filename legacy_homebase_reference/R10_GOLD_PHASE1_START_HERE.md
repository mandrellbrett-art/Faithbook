# Thunderforge R10 Gold — Phase 1: Continuity Migration

This phase is the bridge from the current Termux/Home Base lineage into standalone Android R10 without silently losing project history.

## What was implemented now

The standalone source was upgraded with:

- native **Continuity** screen;
- structured migration ZIP import;
- SHA-256 verification of supplied payload bytes;
- migration runs/items stored in SQLite;
- exact-byte vs reference-only vs hash-mismatch state;
- search by original path/family/version/status;
- open migrated payload files through Android;
- Continuity Lock embedded in the app;
- unresolved migration counts in runtime stats;
- Cantus events for migration begin/finish;
- R10 Gold promotion remains blocked while continuity is unresolved.

Source verification currently passes **16/16** checks, including 144-route structure, Cantus hash chain, continuity import/UI, safe ZIP handling, no arbitrary-command API, and no localhost/Termux runtime requirement.

## Step 1 — one-time legacy export on the phone

Put `EXPORT_THUNDERFORGE_R10_CONTINUITY_FROM_TERMUX.sh` in Android Downloads, then in the **existing** Termux installation run:

```bash
bash ~/storage/downloads/EXPORT_THUNDERFORGE_R10_CONTINUITY_FROM_TERMUX.sh
```

It creates:

- `Thunderforge_R10_Migration_Bundle_<timestamp>.zip`
- matching `.zip.sha256`

The exporter is copy-only. It does not delete the old Home Base/Termux data. Small/medium portable source/data/log/save/database files are included as exact payload bytes. Large ROMs and other files that are not duplicated remain in the manifest as reference-only with paths/hashes when available.

**Do not uninstall Termux or delete R9/R10 legacy data after export.**

## Step 2 — build/install standalone R10 Continuity V2

Open `Thunderforge_R10_Standalone_Continuity_V2_Source.zip` in Android Studio, sync Gradle, and build the APK.

This execution environment does not contain an Android SDK/Gradle toolchain, so an APK has not been falsely claimed here.

## Step 3 — import continuity bundle

In the standalone app:

1. Open **Continuity**.
2. Tap **Import continuity migration ZIP**.
3. Select the bundle created in Step 1.
4. Let the app verify and index it.
5. Search the continuity ledger for Home Base, Garden, Cantus, Constructor, Kernel, Chrono, CIVIS and other critical families.

Reference-only items stay visible. A hash mismatch stays visible. Neither is silently treated as nonexistent.

## Step 4 — reconciliation before R10 Gold

Next we compare old-vs-new counts and then run the physical-phone acceptance sweep: Projects, Files, Cantus, Production, Library, Games/saves, Garden/saves, Constructor, Kernel 144/144, Farm Time, Calendar, Maps, Medical, Communications, backup/restore, offline persistence, Fold layouts and rollback.

Only after that does R10 Gold become canonical.
