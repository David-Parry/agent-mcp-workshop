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
- `LogFileWriter` — thread-safe logging to files
- Event-driven listener architecture

### Key Concepts
- STDIO-based communication
- Thread-safe input reading with `Scanner`
- Observer pattern for message distribution
- JSON serialization with Gson

### Tasks
1. Implement `startInputReader()` — read lines from `System.in`
2. Implement `publishLine()` — distribute input to listeners
3. Implement `emit()` — send JSON responses to `System.out`

### Outcome
A working transport layer that can read input, publish to listeners, and emit JSON responses.

---

## Chapter 2: JSON-RPC Protocol Review

**Branch**: `02-chapter`

### Objective
Understand the JSON-RPC 2.0 protocol that MCP uses for all communication.

### What You'll Learn
- JSON-RPC request/response structure
- Message types: Request, Response, Notification, Error
- MCP's extension of JSON-RPC
- Capability negotiation concepts

### Key Concepts
- Stateless request-response pattern
- Transport-agnostic protocol design
- Bidirectional communication
- Structured data exchange with schemas

### Supporting Materials
- `02-message-class-diagram.md` — class relationships
- `02-mcp-message-flow.md` — message sequence diagrams
- `02-mcp-json-communication-flow-diagram.md` — JSON examples

### Outcome
Understanding of how MCP messages are structured and exchanged.

---

## Chapter 3: Discovery & Core Message Routing

**Branch**: `03-chapter`

### Objective
Implement `server/discover` and the per-request envelope that replaced the handshake.

### What You'll Build
- `RequestId` plus a Gson `TypeAdapter` — a JSON-RPC id is a string *or* a number
- `server/discover` handler (versions, capabilities, identity, cache hints)
- `RequestEnvelope` validation — the three distinct rejections
- Notification deserialization infrastructure
- `NotificationCancelledParams` record

### Key Concepts
- Statelessness: revision `2026-07-28` deleted `initialize`, `notifications/initialized`, and `ping`
- Per-request capability declaration in `params._meta`
- Protocol version negotiation *through errors*, since there is no handshake to negotiate in
- Type-safe parameter deserialization
- STDIO discipline (never write debug output to stdout)

### Tasks
1. Add `RequestId` + `RequestIdTypeAdapter`, registered via the shared `McpGson` factory
2. Implement the `SERVER_DISCOVER` case in `IORouter`, answered before any version check
3. Implement `envelopeFor()` — `-32602` for an incomplete envelope, `-32022` (with `data.supported`) for an unsupported revision
4. Build and test with MCP Inspector in modern mode
5. Add `deserializeParams()` to `JsonRpcMessageDeserializer`
6. Create `NotificationCancelledParams` record and update the notification handler

### Verification
- MCP Inspector connects successfully with `"protocolEra": "modern"`
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
- `JavadocResources` helper class
- `RESOURCES_LIST` handler (discovery)
- `RESOURCES_READ` handler (content retrieval)

#### Tools Capability
- `KeyWordSearch` tool implementation — accepts a single `keyword` parameter; the search directory is supplied later via roots or elicitation (Ch 5), **not** as a tool argument
- `TOOLS_LIST` handler (tool discovery)
- `TOOLS_CALL` handler (tool execution)

#### Prompts Capability
- `PROMPTS_LIST` handler
- `PROMPTS_GET` handler

### Key Concepts
- Builder pattern for response construction
- JSON Schema for tool parameters
- Classpath resource loading
- Roots-based directory configuration (clean separation between tool args and runtime context)

### Verification
- "List Resources" shows Javadoc files
- "List Tools" shows `key_word_search` with one input field (`keyword`)
- "List Prompts" shows `search_keyword`
- Calling the tool without roots returns a clear error directing the user to either set MCP roots or accept the directory elicitation in Ch 5

### Outcome
A working MCP server with all three core capabilities — ready to be extended with experimental features.

---

## Chapter 5: Extensions & Multi Round-Trip Requests

**Branch**: `05-chapter`

### Objective
Wire up the MCP extension mechanism, then ask the user for a missing search directory in the only way a server still can — by answering the call with a question.

### What You'll Build
- `extensions` capability map in `ServerCapabilities`, alongside the surviving `experimental`
- `withExtension()` builder method
- `InputRequiredResult` and `InputRequest` — an embedded method call with no `jsonrpc` and no `id`
- **The MRTR loop**: `TOOLS_CALL` answers `resultType: "input_required"`, embedding `roots/list` and then escalating to `elicitation/create`
- `SearchContinuation` — the keyword and stage, base64-encoded into the opaque `requestState`

### Key Concepts
- Extension identifier format (`{vendor-prefix}/{extension-name}`)
- A server MUST NOT send a request; modern clients silently discard inbound ones
- The retry is a **brand new request with a brand new id**, not a response — correlated only by the echoed `requestState`
- Statelessness forces the continuation onto the wire: the server has nowhere to keep it
- Always leave a one-hop path — MRTR is optional for clients, so the directory is also an optional tool argument

### Verification
- The `server/discover` result carries `extensions: { "io.modelcontextprotocol/ui": {...}, "io.modelcontextprotocol/tasks": {} }`
- Nothing is asked at startup — a server has no way to ask
- `tools/call key_word_search` **with** a `directory` argument completes in one hop
- **Without** it, the first answer is `input_required` embedding `roots/list`, and the client re-sends under a new id
- Empty roots escalate to an embedded `elicitation/create` with `mode: "form"`
- Declining the form falls back to the server's working directory rather than failing

### Outcome
A server that uses the protocol's extensibility surface and asks for what it needs, exactly when it needs it, without ever sending a request.

---

## Chapter 6: MCP Apps — Interactive UI

**Branch**: `06-chapter`

### Objective
Extend the existing tool with an interactive HTML UI that renders inside the conversation.

### What You'll Build
- Three new spec records: `UiMeta`, `AppMeta`, `AppTool`
- `AppToolBuilder` with `withResourceUri(...)` and `withExecution(...)` (the latter advertises task support — used in Ch 7)
- Declare `io.modelcontextprotocol/apps` in experimental capabilities
- Serve `ui://keyword-search/mcp-app.html` from the classpath via `RESOURCES_READ`

### Key Concepts
- Pattern: **Tool + Resource + `_meta` = interactive app**
- MIME type `text/html;profile=mcp-app` signals an interactive app
- Sandboxed iframes for security
- Progressive enhancement — tools still work in text-only clients

### Verification
- `tools/list` returns the tool with `_meta.ui.resourceUri`
- `resources/list` advertises the `ui://` URI
- `resources/read` returns the HTML body
- Inspector's "Refresh Apps" renders the dashboard inside the chat

### Outcome
A tool that returns plain text **and** a rendered interactive dashboard, depending on host capability.

---

## Chapter 7: Tasks — Call Now, Fetch Later

**Branch**: `07-chapter`

### Objective
Implement the **`io.modelcontextprotocol/tasks` extension** — turn any expensive `tools/call` into a deferred, pollable, cancellable operation.

### What You'll Build
- Spec records: `Task`, `TaskStatus`, `TaskResult`, `TasksGetParams`, `TasksUpdateParams`
- `TaskStore` — framework-free in-memory state machine with status listeners
- The extension declared under `capabilities.extensions` in the `server/discover` result
- A `TOOLS_CALL` branch that returns an **unsolicited** `resultType: "task"` handle when the client declared the extension
- Three request handlers: `tasks/get`, `tasks/update`, `tasks/cancel`
- One new outbound notification: `notifications/tasks`

### Key Concepts
- **The server decides, not the client.** There is no `task: { ttl }` opt-in any more — a server may answer any call with a handle, provided the client declared the extension *on that request*
- The lifecycle state machine: `working → input_required ⇄ working → completed | failed | cancelled`
- Polling replaced blocking: `tasks/result` and `tasks/list` are gone, and the client polls `tasks/get` at `pollIntervalMs`
- `tasks/update` is the only reason `input_required` exists at the task level — it delivers `inputResponses` to a task that is waiting
- `ttlMs` / `pollIntervalMs` naming, and the terminal-state rule: a receiver MUST NOT move a terminal task back to `working`

### Verification
- The `server/discover` result carries `extensions["io.modelcontextprotocol/tasks"]`
- `tools/list` shows no `execution` field — `Tool.execution` / `taskSupport` was removed
- A `tools/call` from a task-declaring client returns `resultType: "task"` in ms
- The same call from a client that did *not* declare the extension runs inline and returns `complete`
- `tasks/get` reports `working` then `completed`, carrying the result
- `tasks/cancel` of a terminal task returns JSON-RPC error `-32602`
- `notifications/tasks` arrives on every status transition

### Outcome
A server that implements the call-now / fetch-later pattern at the protocol level — the same pattern used by production batch APIs and long-running ML jobs.

---

## Bonus: Agent Chapter — Live LLM Integration & Supervised Workflow

**Branch**: `agent-chapter`

### Objective
Connect your MCP server to a real AI coding assistant and use it in conversation, then layer on a Claude Code plugin that supervises a multi-step workflow against the server.

### What You'll Do
1. Create / register `mcp.json` for your chosen client
2. Verify tool discovery
3. Have real conversations using your tool
4. Build a Claude Code plugin with four composable skills and a 6-step audit workflow that pauses at an approval checkpoint

### Supported Clients
| Client | Configuration Location |
|--------|------------------------|
| Claude Code | Project `mcp.json` (auto-discovered) |
| Cursor | Settings → MCP Servers |
| Windsurf | `~/.codeium/windsurf/mcp_config.json` |
| Cline | VS Code extension settings |

### Example Conversations
- "Search for 'TODO' and tell me which file has the most occurrences"
- "Find all files containing 'test' and summarize the testing approach"
- "Compare occurrences of 'error' vs 'exception' in the codebase"

### Outcome
Your MCP server running with a live LLM, and a supervised plugin demonstrating safe agent orchestration on top of it.

---

## Workshop Summary

| Chapter | Topic | Key Deliverable |
|---------|-------|-----------------|
| 1 | Transport Layer | Line-oriented I/O with event publishing |
| 2 | Protocol Review | Understanding of JSON-RPC and MCP |
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

- Add additional tools to your server, advertising `execution.taskSupport` where appropriate
- Implement custom resources for your domain
- Create specialized prompts for your workflows
- Extend task augmentation to `sampling/createMessage` or `elicitation/create` (Ch 7 covers tools-only as the primary path)
- Share your MCP server with your team
