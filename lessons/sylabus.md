# MCP Server Workshop Syllabus

## Workshop Overview

This instructor-led workshop teaches you to build a Java-based Model Context Protocol (MCP) server from scratch. By the end, you'll have a fully functional MCP server that integrates with AI coding assistants like Claude Code, Cursor, and Windsurf — and you'll understand the protocol's full surface on revision 2026-07-28, the stateless revision that deleted the handshake, including the tasks and MCP Apps extensions.

**Duration**: 7 chapters + optional agent bonus (instructor-paced)
**Prerequisites**: Java fundamentals, familiarity with JSON
**Tools Required**: JDK 21+, Gradle, Node.js (for MCP Inspector)

---

## Chapter 1: MCP Transport Layer — The Foundation

**Branch**: `01-chapter`

### Objective
Build the bidirectional I/O infrastructure that enables communication between MCP clients and servers.

### What You'll Build
- `IOHandlerImpl` — manages input/output streams
- Event-driven listener architecture

### What You're Given
- `LogFileWriter` — thread-safe logging to a per-run file under `logs/`, already written; stdout is reserved for protocol messages
- `Server` — the lifecycle that registers the router as a listener and exits when stdin closes

### Key Concepts
- STDIO-based communication
- A blocking read loop on the main thread, with an atomic guard against a second reader
- Observer pattern for message distribution
- JSON serialization with Gson

### Tasks
1. Implement `startInputReader()` — read lines from `System.in`
2. Implement `emit()` — send JSON responses to `System.out`
3. Implement `publishLine()` — distribute input to listeners

### Verification
- `./gradlew chapterTest -Pchapter=01` is green: 58 tests, of which 13 in `IOHandlerImplTest` start red

### Outcome
A working transport layer that can read input, publish to listeners, and emit JSON responses.

---

## Chapter 2: Reading JSON-RPC Messages

**Branch**: `02-chapter`

### Objective
Turn each inbound line into a typed JSON-RPC message, and read the stateless envelope every request carries.

### What You'll Build
- `JsonRpcMessageDeserializer` — classification, typed params, and the `params._meta` envelope

### What You're Given
- The message records (`JsonRpcRequest`, `JsonRpcNotification`, `JsonRpcResponse`, `JsonRpcErrorResponse`, `Unknown`), `RequestEnvelope`, and the capability records it holds
- `RequestId` and `RequestIdTypeAdapter`, registered once in `McpGson`, so an id goes back out as the string or number it arrived as
- `MetaKeys` and `ErrorCodes` — the specification's key names and error numbers

### Key Concepts
- Structural typing: JSON-RPC has no type tag, so the fields present decide the shape
- `"jsonrpc": "2.0"` is required, and malformed JSON fails differently from well-formed JSON that is not a message, so chapter 3 can answer `-32700` and `-32600`
- Absent versus null, and the `"id": null` notification trap
- Requests flow from client to server only: a server MUST NOT initiate one
- Statelessness: every request re-declares its context in `params._meta`, whose reserved prefixes and unprefixed trace keys the specification defines

### Tasks
1. Implement `deserialize()` — set aside what is not a message, then classify the four shapes
2. Implement both `deserializeParams()` overloads and `convert()`
3. Implement `deserializeEnvelope()` and its helpers `metaObject()` and `asString()`

### Supporting Materials
- `02-message-class-diagram.md` — this chapter's classes
- `02-mcp-message-flow.md` — message sequence diagrams
- `02-mcp-json-communication-flow-diagram.md` — JSON examples

### Verification
- `./gradlew chapterTest -Pchapter=02` is green: 35 tests, of which the 22 in `JsonRpcMessageDeserializerTest` start red

### Outcome
Every inbound line becomes either a typed message or a clearly marked non-message, and every request's envelope can be read.

---

## Chapter 3: Discovery & Core Message Routing

**Branch**: `03-chapter`

### Objective
Implement `server/discover` and the per-request envelope that replaced the handshake.

### What You'll Build
- Fill `acknowledgeSubscription()`, `discoverResult()`, `envelopeFor()`, the request prelude, and the notification handler — those methods are empty; the types (`RequestId`, `McpGson`, `NotificationCancelledParams`) are already on the branch

### Key Concepts
- Statelessness: revision `2026-07-28` deleted `initialize`, `notifications/initialized`, and `ping`
- Per-request capability declaration in `params._meta`
- Protocol version negotiation *through errors*, since there is no handshake to negotiate in
- Type-safe parameter deserialization
- STDIO discipline (never write debug output to stdout)

### Tasks
1. Fill `acknowledgeSubscription()` — acknowledge `subscriptions/listen` with what the server honors, then close the stream
2. Fill `discoverResult()` and the `server/discover` shortcut (answered before any version check)
3. Fill `envelopeFor()` — `-32602` for an incomplete envelope, `-32022` (with `data.supported` and `data.requested`) for an unsupported revision
4. Build and test with MCP Inspector (`inspector/config.json` already has `"protocolEra": "modern"` as a sibling of `command`)
5. Fill the notification handler using Chapter 2's `deserializeParams` and the existing `NotificationCancelledParams`

### Verification
- MCP Inspector connects successfully with `"protocolEra": "modern"`, and the first message on the wire is `server/discover`, not `initialize`
- Discovery completes (green status) — note the **string** id on that first message
- `initialize` returns `-32601`, checked before the envelope so a legacy client is told the truth
- A request with no `_meta` returns `-32602`; one declaring `2025-11-25` returns `-32022`
- Cancellation notifications show reason text in logs

### Outcome
A server that answers discovery, validates every request's envelope, and rejects what it cannot honour in three diagnosable ways.

---

## Chapter 4: Resources, Tools & Prompts

**Branch**: `04-chapter`

### Objective
Implement the three core MCP capabilities to create a useful server.

### What You'll Build

#### Resources Capability
- `JavadocResources` helper class, given and read first
- `RESOURCES_LIST` handler (discovery)
- `RESOURCES_TEMPLATES_LIST` handler, answered with an empty list
- `RESOURCES_READ` handler (content retrieval)

#### Tools Capability
- `KeyWordSearch` tool, given and read first — a required `keyword` and an optional `directory`. In this chapter a call without a directory is a **tool-level error** (`isError`). Chapter 5 turns that into a round trip.
- `TOOLS_LIST` handler via `ToolsListResultBuilder` (not `AppToolBuilder`)
- `TOOLS_CALL` handler (tool execution)

#### Prompts Capability
- `PROMPTS_LIST` handler
- `PROMPTS_GET` handler
- `COMPLETION_COMPLETE` handler, suggesting values for the prompt's `keyword`

### Key Concepts
- Builder pattern for response construction
- JSON Schema for tool parameters
- Classpath resource loading
- Cache hints on every list and read result, and no `nextCursor` when there is one page
- Errors versus results: an unknown tool, prompt or resource is `-32602`; a tool that ran and failed is a result with `isError`
- Runtime context as a tool argument, resolved per call — there is no session to configure it in

### Verification
- The Inspector's Resources tab lists the Javadoc files
- The Tools tab shows `key_word_search` with a required `keyword` and an optional `directory`
- The Prompts tab shows `search_keyword`, and typing its `keyword` offers completions
- Calling the tool without a `directory` returns a clear tool-level error. Chapter 5 adds the round trip and the working-directory fallback.

### Outcome
A working MCP server with all three core capabilities — ready to be extended with experimental features.

---

## Chapter 5: Extensions & Multi Round-Trip Requests

**Branch**: `05-chapter`

### Objective
See where extensions are declared, though this chapter declares none, then ask the user for a missing search directory in the only way a server still can — by answering the call with a question.

### What You'll Build
- `SearchContinuation` — the exchange's stage, base64-encoded into the opaque `requestState`
- **The MRTR loop**: `TOOLS_CALL` hands the search to `handleKeywordSearch`, which answers `resultType: "input_required"`, embedding an `elicitation/create` form
- `elicitedDirectory` — reads the user's answer off the retry, which may also be a decline or a cancel
- Given, and read first: the `extensions` map in `ServerCapabilities` with `withExtension()`, `InputRequiredResult` and `InputRequest` — an embedded method call with no `jsonrpc` and no `id`

### Key Concepts
- Extension identifier format (`{vendor-prefix}/{extension-name}`)
- A server MUST NOT send a request; modern clients silently discard inbound ones
- The retry is a **brand new request with a brand new id**, not a response — correlated only by the echoed `requestState`
- Statelessness forces the continuation onto the wire: the server has nowhere to keep it
- `requestState` comes back from the client, which can rewrite it, so it holds only the stage; the keyword comes back with the retried call's arguments
- An empty `"elicitation":{}` declares form, as it did before there were modes
- Always leave a one-hop path — MRTR is optional for clients, so the directory is also an optional tool argument
- Removed (SEP-2575) is not deprecated (SEP-2577): `initialize` and its family are physically gone, whereas roots, sampling, and logging still work, and clients keep declaring roots and sampling — this server just does not build on them

### Verification
- The `server/discover` result still has **no** UI or tasks extension — those are Chapters 6 and 7
- Nothing is asked at startup — a server has no way to ask
- `tools/call key_word_search` **with** a `directory` argument completes in one hop
- **Without** it, the answer is `input_required` embedding an `elicitation/create` with `mode: "form"`, and the client re-sends under a new id once the user has filled it in
- Declining the form falls back to the server's working directory rather than failing
- `elicitation/create` sent to the server is `-32601`: it only ever appears embedded in a result

### Outcome
A server that asks for what it needs, exactly when it needs it, without ever sending a request, and is ready to declare the extensions Chapters 6 and 7 add.

---

## Chapter 6: MCP Apps — Interactive UI

**Branch**: `06-chapter`

### Objective
Give the existing tool a page of its own, which a host that supports MCP Apps renders inside the conversation.

### What You'll Build
- `AppMeta.of` — the page's address under both the canonical `_meta.ui.resourceUri` and the deprecated flat `ui/resourceUri`
- `AppToolBuilder.withResourceUri(...)` and `build()`, which refuses a tool with no name, description, page or input schema — there is no `withExecution` / `taskSupport`
- Given, and read first: the records `UiMeta`, `AppMeta`, `AppTool` and `AppToolsListResult`; the router's `io.modelcontextprotocol/ui` declaration; the `TOOLS_LIST`, `RESOURCES_LIST` and `RESOURCES_READ` cases that send the tool and serve `ui://keyword-search/mcp-app.html` from the classpath; and the demo page itself

### Key Concepts
- Pattern: **Tool + Resource + `_meta` = interactive app**
- MCP Apps is a Stable extension, revision `2026-01-26`, and adds no protocol methods
- The server declares the extension with no settings; `mimeTypes` is the host's, the page types it can render
- `visibility` says who may see and call a tool, the model or the app, not where the page is shown
- MIME type `text/html;profile=mcp-app` is required for a page
- Sandboxed iframes and a strict default Content Security Policy for security
- Progressive enhancement — tools still work in text-only clients, so the tool always returns text

### Verification
- The `server/discover` result declares `"io.modelcontextprotocol/ui": {}`
- `tools/list` returns the tool with both `_meta.ui.resourceUri` and `_meta["ui/resourceUri"]`
- `resources/list` advertises the `ui://` URI
- `resources/read` returns the HTML under `text/html;profile=mcp-app`
- The Inspector's Apps tab lists `key_word_search`, and **Open App** renders the demo page with its sample rows

### Outcome
A tool that returns plain text to every client, and carries a page that a host supporting MCP Apps renders beside the result.

---

## Chapter 7: Tasks — Call Now, Fetch Later

**Branch**: `07-chapter`

### Objective
Implement the **`io.modelcontextprotocol/tasks` extension**, at the Beta stage and headed for core — turn a slow `tools/call` into an operation the client polls, answers and cancels.

### What You'll Build
- `TaskStore` — a framework-free, in-memory store: `create`, `complete`, `fail`, `requireInput`, `applyInput`, `awaitInput` and `cancel`
- `runSearchAsTaskThatAsks` — a search that asks for its directory as the task's `input_required` status
- The `tasks/get`, `tasks/update` and `tasks/cancel` cases
- Given, and read first: the records `Task`, `TaskStatus`, `TaskResult`, `TasksGetParams`, `TasksUpdateParams` and `TasksCancelParams`; the extension declared under `capabilities.extensions` in the `server/discover` result; the `tools/call` branch that returns an **unsolicited** `resultType: "task"` handle to a client that declared the extension; and the `-32021` refusal of every task method, and of a listen for task status, to a client that did not

### Key Concepts
- **The server decides, not the client.** There is no `task: { ttl }` opt-in any more — a server may answer any call with a handle, provided the client declared the extension *on that request*
- The lifecycle state machine: `working → input_required ⇄ working → completed | failed | cancelled`, and a terminal task never changes status again
- Polling replaced blocking: `tasks/result` and `tasks/list` are gone, and the client polls `tasks/get` at `pollIntervalMs`
- A task that needs input asks through its status, and `tasks/update` delivers the `inputResponses`
- `tasks/update` and `tasks/cancel` are acknowledged with an empty result: answers the task is not waiting for are ignored, and cancelling a finished task leaves it finished
- `notifications/tasks` goes only to a client that subscribed with `subscriptions/listen`; this server sends none, and the client polls
- `ttlMs` / `pollIntervalMs` naming, and the TTL is the server's alone

### Verification
- The `server/discover` result carries `extensions["io.modelcontextprotocol/tasks"]`
- `tools/list` shows no `execution` field — `Tool.execution` / `taskSupport` was removed
- A `tools/call` from a task-declaring client returns `resultType: "task"` in milliseconds
- The same call from a client that did *not* declare the extension runs inline and returns `complete`
- `tasks/get` reports `working` then `completed`, carrying the result
- A call with no directory reaches `input_required`, and `tasks/update` resumes it
- `tasks/update` and `tasks/cancel` answer with an empty acknowledgement, and a finished task that is cancelled stays `completed`
- A task method, or a listen for task status, from a client that did not declare the extension gets `-32021`
- No `notifications/tasks` arrives for a client that did not subscribe

### Outcome
A server that implements the call-now / fetch-later pattern at the protocol level — the same pattern used by production batch APIs and long-running ML jobs.

---

## Bonus: Agent Chapter — Live LLM Integration & Supervised Workflow

**Branch**: `complete`, with the lessons in `lessons/agent-instructions.md` and `lessons/bonus-live-llm-instructions.md`

### Objective
Connect your MCP server to a real AI coding assistant and use it in conversation, then layer on a Claude Code plugin that supervises a multi-step workflow against the server.

### What You'll Do
1. Register the server with your chosen client, from `lessons/mcp.json`
2. Verify tool discovery
3. Have real conversations using your tool
4. Build a Claude Code plugin with four composable skills and a 6-step audit workflow that pauses at an approval checkpoint — note that these are host-installed skills, not the `io.modelcontextprotocol/skills` extension, which serves the same `SKILL.md` artifacts over the protocol instead

### Supported Clients
The client must speak MCP revision `2026-07-28`, because the server refuses the `initialize` handshake of earlier revisions.

| Client | Configuration Location |
|--------|------------------------|
| Claude Code 2.1.292 or later | `./start_claude_mcp.sh`, which passes `lessons/mcp.json` with `--mcp-config` |
| Cursor | Settings → MCP, once it speaks `2026-07-28` |
| Windsurf | `~/.codeium/windsurf/mcp_config.json` |
| Cline | VS Code extension settings |

### Example Conversations
- "Search for 'deprecated' and tell me which file has the most occurrences"
- "Find all files containing 'test' and summarize the testing approach"
- "Compare occurrences of 'error' vs 'exception' in the codebase"

### Outcome
Your MCP server running with a live LLM, and a supervised plugin demonstrating safe agent orchestration on top of it.

---

## Workshop Summary

| Chapter | Topic | Key Deliverable |
|---------|-------|-----------------|
| 1 | Transport Layer | Line-oriented I/O with event publishing |
| 2 | Reading JSON-RPC Messages | `JsonRpcMessageDeserializer`: the four shapes, typed params and the `_meta` envelope |
| 3 | Discovery & Routing | Working `server/discover` and envelope validation |
| 4 | Resources, Tools, Prompts | Three core capabilities wired up |
| 5 | Extensions & Round Trips | Namespaced extensions + Multi Round-Trip Requests |
| 6 | MCP Apps | Interactive HTML UI inside the conversation |
| 7 | Tasks | Deferred-execution lifecycle (call-now / fetch-later) |
| Bonus | Agent / Live LLM | Server connected to a real assistant + supervised plugin |

## What You'll Have Built

By completing this workshop, you'll have:

- A production-ready MCP server in Java
- Understanding of the Model Context Protocol specification through 2026-07-28
- Experience with MCP Inspector for testing every flow — one-hop calls, round trips, MCP Apps, tasks, and subscriptions
- A custom `key_word_search` tool that takes a directory argument, **or** asks for one over a round trip, **or** falls back to its working directory
- Integration with your preferred AI coding assistant

## Next Steps After Workshop

- Add additional tools to your server, deciding per call whether each one hands back a task handle
- Implement custom resources for your domain
- Create specialized prompts for your workflows
- Push task status to clients that subscribe with `subscriptions/listen`
- Share your MCP server with your team
