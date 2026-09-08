# HeritageFaith V25 — Unified Core

V25 does not pretend that every historical byte has already been recovered. It changes the architecture so the app has one center.

## Unified Core

A single dashboard now covers:
- Projects / My Work
- managed files
- private records
- Phone Intake
- Continuity
- Scripture
- legacy patents
- indexed corpus metadata
- Kernel Computer R2
- 144 Route Atlas
- outstanding completion gates

## Global Search

The app now exposes one federated search API over:
- projects
- managed files
- records
- authorized phone-index items
- Continuity migration items
- imported Bible verses
- legacy patents
- corpus metadata

This is deliberately federated instead of copying a potentially multi-million-row patent archive into a second search table.

## Runtime repair

V25 restores missing `routeCard`, `projectCards`, `wireRouteCards`, and `wireProjectButtons` helpers so the Home Base and project-card surfaces are self-contained in `app.js`.

## Universal text viewer

Managed TXT/Markdown/JSON/XML/HTML/CSS/JS/TS/Python/Java/Kotlin/shell/YAML/TOML/Gradle/CSV/config/log files can be previewed inside HeritageFaith up to 2 MiB. Other formats continue to use Android viewers.

## 144 Route Atlas

The 12 × 12 runtime operation matrix is exposed as a dedicated Route Atlas. Historical canonical R10 bindings remain labeled unbound until recovered; V25 does not invent them.

## Still not complete

Phone-private app sandboxes, historical saves/packages, full PDF/EPUB internal rendering, emulator/game acceptance, bulk patent population, signed-release continuity, encrypted disaster recovery, and canonical 144 bindings still require their real data or device acceptance.
