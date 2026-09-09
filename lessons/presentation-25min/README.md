# Deep Dive MCP Server Protocol — 25 Minute Talk

An interactive HTML deck for a **25-minute, demo-heavy** talk on the Model Context Protocol.
Roughly half the time is live in the MCP Inspector and the IDE, so these pages stay
deliberately thin — they are the map, not the talk.

Everything shown comes from real captured traffic against the workshop server:
**MCP 2026-07-28**, `inspector-cli` v2.5.0 against `agent-mcp-workshop` v0.0.1 over stdio.
That revision is **stateless** — there is no `initialize` handshake and no session, so every
request carries the protocol version and the client's capabilities in its own `_meta`.

## Usage

```bash
open index.html
```

Every page carries the same nav bar, plus a **Light/Dark** toggle for projector or
bright-room conditions (the choice persists in `localStorage`).

## Run Sheet

| Time | Segment | Point to land |
|---|---|---|
| 0 – 2 | Why MCP (`index.html`) | One protocol instead of N bespoke integrations |
| 2 – 5 | Overview | Four actors, one round trip — and no session |
| 5 – 8 | Flow Diagram | It is just JSON-RPC over stdio |
| 8 – 13 | **DEMO** — Inspector | Discover, then resources / tools / prompts live |
| 13 – 15 | Extensions | The server asks a question by answering the call |
| 15 – 18 | MCP Apps | A tool can return a UI, not just text |
| 18 – 21 | Tasks | Call now, fetch later |
| 21 – 24 | **DEMO** — Agent | Same tool, now inside a supervised audit |
| 24 – 25 | Wrap | Where the code and lessons live |

## Files

| File | Role |
|---|---|
| `index.html` | Home — card grid, run sheet, and the facts about the captured traffic |
| `overview.html` | Protocol architecture, plus why "stateless" drives everything else |
| `flow-diagram.html` | Full sequence diagram, hover a message for its JSON |
| `extensions.html` | `extensions` capabilities and Multi Round-Trip Requests |
| `mcp-apps.html` | `ui://` resources and the two `_meta` resource-uri keys |
| `tasks.html` | The tasks extension — lifecycle and what survived the move |
| `agent.html` | The plugin skills and the supervised audit workflow |
| `runtime-facts.html` | Reference card: revision, envelope, capabilities, error codes, inventory |
| `trace-log.html` | Raw timestamped JSON-RPC trace with filters |
| `contact.html` | Links and where to find the code |
| `theme.css` / `theme.js` | Shared light-theme overrides and the toggle |

`trace-log.html` is the ground truth — every other page is a lens on that same traffic.

## Related Decks

Longer versions of the same material live alongside this one, with per-chapter IDE
demos and an agenda overlay: `../presentation-45min/` and `../presentation-3hr/`.
