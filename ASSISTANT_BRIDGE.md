# HeritageFaith V21 — Assistant Bridge

## Purpose

V21 adds explicit import/export settings so the installed private app can hand selected context to ChatGPT or another assistant without exposing the phone as a remotely accessible service.

## Access rule

**EXPLICIT USER HANDOFF ONLY.**

The bridge does not create a background server, open a remote-control port, export credentials, upload automatically, or let an assistant browse the phone.

## Context bundle

`heritagefaith.assistant_context.v1`

Standard mode can include:
- project index
- managed file metadata/hashes
- study records
- Ademic Cantus declaration and Cantus verification/logs
- continuity summary
- private library/source catalog

Sensitive records are excluded by default. Full private mode requires an explicit action.

Raw project/book bytes and credentials are never placed in the context bundle.

## Assistant Return

`heritagefaith.assistant_return.v1`

Assistant Return JSON/ZIP may contain records and proposals. Import is additive. Returned code is **never executed**.

Example:

```json
{
  "schema": "heritagefaith.assistant_return.v1",
  "records": [
    {
      "type": "assistant_note",
      "title": "Review note",
      "body": "A sourced note to preserve in HeritageFaith.",
      "meta": {"source": "ChatGPT review"}
    }
  ],
  "proposals": [
    {
      "title": "Possible next step",
      "body": "Review this before implementation."
    }
  ]
}
```

## Google Drive handoff

Use **Settings → Save standard context**. In Android's system document picker choose Google Drive and a folder you control. Once that Drive source is separately authorized to ChatGPT, the exported bundle can be retrieved there.

## Ademic Cantus

Ademic Cantus remains first-class and is represented in the exported context:
Source expression → Plain meaning → Ademic Cantus → Resonance → Runic compression → Plain-language expansion.
