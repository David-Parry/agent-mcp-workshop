# Deep Dive MCP Server Protocol — 25 Minute Talk

An interactive HTML deck for a **25-minute, demo-heavy** talk on the Model Context Protocol.
Roughly half the time is live in the MCP Inspector and the IDE, so these pages stay
deliberately thin — they are the map, not the talk.

Everything shown comes from one real captured session against the workshop server:
**MCP 2025-11-25**, `inspector-client` v0.22.0 against `agent-mcp-workshop` v0.0.1 over stdio.

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
| 2 – 5 | Overview | Four actors, one round trip |
| 5 – 8 | Flow Diagram | It is just JSON-RPC over stdio |
| 8 – 13 | **DEMO** — Inspector | Connect, then resources / tools / prompts live |
| 13 – 15 | Extensions | Server can ask the user a question mid-call |
| 15 – 18 | MCP Apps | A tool can return a UI, not just text |
| 18 – 21 | Tasks | Call now, fetch later |
| 21 – 24 | **DEMO** — Agent | Same tool, now inside a supervised audit |
| 24 – 25 | Wrap | Where the code and lessons live |

## Files

| File | Role |
|---|---|
| `index.html` | Home — card grid, run sheet, and session facts |
| `overview.html` | Protocol architecture with a Mermaid sequence diagram |
| `flow-diagram.html` | Full session sequence diagram, hover a message for its JSON |
| `extensions.html` | `experimental` capabilities and lazy elicitation |
| `mcp-apps.html` | `ui://` resources and `_meta.ui.resourceUri` |
| `tasks.html` | The tasks utility — lifecycle and the five new methods |
| `agent.html` | The plugin skills and the supervised audit workflow |
| `runtime-facts.html` | Reference card: versions, capabilities, tool and resource inventory |
| `trace-log.html` | Raw timestamped JSON-RPC trace with filters |
| `contact.html` | Links and where to find the code |
| `theme.css` / `theme.js` | Shared light-theme overrides and the toggle |

`trace-log.html` is the ground truth — every other page is a lens on that same traffic.

## Related Decks

Longer versions of the same material live alongside this one, with per-chapter IDE
demos and an agenda overlay: `../presentation-45min/` and `../presentation-3hr/`.
