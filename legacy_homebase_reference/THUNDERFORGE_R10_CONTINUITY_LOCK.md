# Thunderforge R10 Continuity Lock

**Status:** ACTIVE  
**Target:** Thunderforge R10 Gold  
**Migration mode:** additive / read-only first  
**Rule:** A platform move is **not complete** while critical information, features, history, saves, provenance, files, routes, or rollback capability are missing.

## Core rule

The old platform remains intact until the new platform proves parity. A missing item must appear as **NOT MIGRATED**, **MISSING**, or **REVIEW**. It must never disappear from the interface and be mistaken for something that never existed.

## Critical continuity domains

- Projects / My Work / Workspaces
- Universal Files / imports / exports / exact-byte originals
- Production jobs / travelers / revisions / lots / tests / failures
- Cantus logs / audit history / provenance
- Saved items / archive / chats / notes
- Library / books / user documents
- Games library / manifests / user-owned ROM references / saves / save states
- Garden Immortals / First Light source lineage / saves / autonomy-rest policy
- Constructor
- Kernel Computer / 144 Circuit
- Artifact Studio / Design Studio / Blueprints-CAD
- Material Canon / Physics Vault / Constraint Graph
- Failure Taxonomy / Lifecycle Registry / Provenance Index
- CIVIS Gear / Chrono-Compass / manufacturing packages
- Calendar / ICS
- Maps / GeoJSON / GPS records
- Medical organizer
- Communications / phone-SMS-email handoffs / voice-note records
- Images
- Farm Time
- Argus / Catalog Zero / network-dependent tools
- App settings / route state / permissions state where exportable
- 93-module historical parity classification
- 144/144 route registry and render contract
- Backups / rollback packages / release manifests / SHA-256 evidence

## Required migration package

Every future platform move carries these artifacts together:

- `THUNDERFORGE_R10_CONTINUITY_LOCK.json`
- `FEATURE_REGISTRY.json`
- `ROUTE_REGISTRY.json`
- `DATASET_MANIFEST.csv`
- `FILE_HASHES.sha256`
- Cantus export/database backup
- project version graph
- game/save manifest
- Garden lineage + saves manifest
- production/audit manifest
- migration report
- rollback report
- physical-phone acceptance report

## Promotion rule

A new platform does **not** become canonical because it installs, opens, or looks polished.

Promotion requires:

1. Snapshot and hash the predecessor.
2. Export without deleting originals.
3. Import read-only first.
4. Compare counts and identifiers.
5. Compare exact-byte hashes where possible.
6. Preserve Cantus/audit chronology.
7. Preserve 93-module historical parity information.
8. Verify 144/144 routes.
9. Verify Files, Library, Games, Garden, Production, Logs, Projects, Auto Finish, Farm Time.
10. Verify persistence after restart and offline use.
11. Verify device adapters and permissions.
12. Verify backup/restore.
13. Verify rollback.
14. Only then mark the new platform **PROMOTED**.

## User-facing behavior

If something has not migrated, R10 should say so plainly and show:

- what it is,
- where the last known copy lived,
- which version it belonged to,
- its evidence status,
- what is needed to restore it.

Nothing important is allowed to vanish merely because the runtime or platform changed.

## Platform architecture rule

The standalone Android app should use one canonical data model. Compatibility adapters may read older Home Base / Farwater / Grovenaut / Thunderforge data, but the UI must not create several competing sources of truth.

## Cantus rule

Every migration/import/repair/promotion/rollback operation writes a Cantus event. Migration history itself is part of the preserved project history.

## Final acceptance

“Finished” means a normal person can install Thunderforge, find their work, open it, use it, save it, recover it, understand its status, and move to a later platform without losing its history.
