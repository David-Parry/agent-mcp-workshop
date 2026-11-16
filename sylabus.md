# MCP Server Workshop Syllabus

## Workshop Overview

This instructor-led workshop teaches you to build a Java-based Model Context Protocol (MCP) server from scratch. By the end, you'll have a fully functional MCP server that connects to AI coding assistants that speak MCP revision `2026-07-28`, such as Claude Code 2.1.292 or later — and you'll understand the protocol's full surface on that revision, the stateless one that deleted the handshake, including the tasks and MCP Apps extensions.

**Duration**: 7 chapters + optional agent bonus (instructor-paced)
**Prerequisites**: Java fundamentals, familiarity with JSON
**Tools Required**: Git, JDK 21 or later, Node.js 22+ with npm 10.9+, MCP Inspector 2.5.0, and Python 3 for the protocol harness (Chapter 3 on). Gradle comes with the repository as `./gradlew`. Run `./verification.sh` on `trunk` before class.

Each chapter is a branch, and it is finished when `./gradlew chapterTest -Pchapter=0N` is green, using that chapter's number. From Chapter 3 on, also run the harness, which starts the built JAR and drives it over stdio the way a client would: `./gradlew shadowJar`, then `python3 lessons/presentation-3hr/verify/harness.py --chapter 0N`.

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
Every inbound line becomes a typed message, an `Unknown` (well-formed JSON that is not a message) or a parse exception (text that is not JSON), which Chapter 3 answers with `-32600` and `-32700` respectively, and every request's envelope can be read.

---

## Chapter 3: Discovery & Core Message Routing

**Branch**: `03-chapter`

### Objective
Implement `server/discover` and the per-request envelope that replaced the handshake.

### What You'll Build
- Fill `acknowledgeSubscription()`, the notification handler, `discoverResult()`, `envelopeFor()` and the request prelude in `IORouter` — each holds a `// Chapter 03:` comment, and `envelopeFor()` and `discoverResult()` also throw `UnsupportedOperationException` until you replace them; the types (`RequestId`, `McpGson`, `NotificationCancelledParams`) are already on the branch

### Key Concepts
- Statelessness: revision `2026-07-28` deleted `initialize`, `notifications/initialized`, and `ping`
- Per-request capability declaration in `params._meta`
- Protocol version negotiation *through errors*, since there is no handshake to negotiate in
- Type-safe parameter deserialization
- STDIO discipline (never write debug output to stdout)

### Tasks
1. Fill `acknowledgeSubscription()` — acknowledge `subscriptions/listen` with what the server honors, then close the stream
2. Fill the notification handler using Chapter 2's `deserializeParams` and the existing `NotificationCancelledParams`
3. Fill `discoverResult()` with `DiscoverResultBuilder`
4. Fill `envelopeFor()` — `-32602` for an incomplete envelope, `-32022` (with `data.supported` and `data.requested`) for an unsupported revision
5. Fill the request prelude in `process(JsonRpcRequest)` — `-32601` for any method outside `INBOUND_METHODS`, checked first; then `envelopeFor()`, where `server/discover` skips only the version check and is then answered
6. Run the tests and the harness, then try it in the MCP Inspector with `cd inspector && ./run.sh` (`inspector/config.json` already has `"protocolEra": "modern"` as a sibling of `command`)

### Supporting Materials
- `03-class-diagram.md` — the classes the router works with
- `03-sequence-diagram.md` — what the server does with each kind of line

### Verification
- `./gradlew chapterTest -Pchapter=03` is green: 49 tests, of which 21 start red
- The harness passes: `python3 lessons/presentation-3hr/verify/harness.py --chapter 03`
- MCP Inspector connects with `"protocolEra": "modern"`, and the first message on the wire is `server/discover`, not `initialize`
- Discovery completes: the probe's id is the **string** `"server-discover-probe-1"` and comes back as a string, and the capabilities list prompts, resources, tools and completions
- Until Chapter 4, the Tools, Resources and Prompts tabs show `Method not found` (`-32601`) for their lists
- `./inspector/modern-probe.sh`, run from a second terminal, shows `initialize` refused with `-32601` (checked before the envelope, so a legacy client is told the truth), a `subscriptions/listen` with no `_meta` refused with `-32602`, one declaring `2025-11-25` refused with `-32022`, and a line that is not JSON and one that is not a message answered with `-32700` and `-32600`
- The cancellation the probe sends shows its reason text in the newest log under `inspector/logs/`

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

### Supporting Materials
- `04-mcp-class-diagram.md` — the classes this chapter's handlers use
- `04-mcp-message-flow.md` — message flows for resources, tools, prompts and completion

### Verification
- `./gradlew chapterTest -Pchapter=04` is green: 141 tests, of which the 22 in `IORouterTest` start red
- The harness passes: `python3 lessons/presentation-3hr/verify/harness.py --chapter 04`
- The Inspector's Resources tab lists the Javadoc files
- The Tools tab shows `key_word_search` with a required `keyword` and an optional `directory`
- The Prompts tab shows `search_keyword`, and typing its `keyword` offers completions
- Calling the tool without a `directory` returns a clear tool-level error. Chapter 5 adds the round trip and the working-directory fallback.

### Outcome
A working MCP server with all three core capabilities — ready for Chapter 5's extensions and multi round-trip requests.

---

## Chapter 5: Extensions & Multi Round-Trip Requests

**Branch**: `05-chapter`

### Objective
See where extensions are declared, though this chapter declares none, then ask the user for a missing search directory in the only way a server still can — by answering the call with a question.

### What You'll Build
- `SearchContinuation.awaitingDirectory()`, `encode()` and `decode()` — the exchange's stage, carried in the opaque `requestState` as URL-safe Base64 JSON with no padding; `decode()` returns null for anything it cannot read, which the server treats as a first attempt
- **The MRTR loop**: replace Chapter 4's `TOOLS_CALL` case so it hands the search to `handleKeywordSearch`, which answers `resultType: "input_required"`, embedding an `elicitation/create` form
- `elicitedDirectory` — reads the user's answer off the retry, which may also be a decline or a cancel
- Given, and read first: the `extensions` map in `ServerCapabilities`, which `DiscoverResultBuilder.withExtension()` fills (Chapter 6 makes the first call), `InputRequiredResult` and `InputRequest` — an embedded method call with no `jsonrpc` and no `id`

### Key Concepts
- Extension identifier format (`{vendor-prefix}/{extension-name}`)
- A server MUST NOT send a request; modern clients silently discard inbound ones
- The retry is a **brand new request with a brand new id**, not a response: it repeats the original arguments, echoes `requestState` unchanged, and carries each answer in `inputResponses` under the key its question was asked under (`search_directory`)
- Statelessness forces the continuation onto the wire: the server has nowhere to keep it
- `requestState` comes back from the client, which can rewrite it, so it holds only the stage; the keyword comes back with the retried call's arguments
- An empty `"elicitation":{}` declares form, as it did before there were modes
- Always leave a one-hop path — MRTR is optional for clients, so the directory is also an optional tool argument
- Removed (SEP-2575) is not deprecated (SEP-2577): `initialize`, `ping`, `logging/setLevel` and the other removed methods are physically gone and answer `-32601`, whereas the roots, sampling, and logging features still work (the log level is now each request's `logLevel`), and clients keep declaring roots and sampling — this server just does not build on them

### Verification
- `./gradlew chapterTest -Pchapter=05` is green: 36 tests, of which 27 start red (9 in `SearchContinuationTest`, 18 in `IORouterTest`); 4 already pass through Chapter 4's `TOOLS_CALL` case and must stay green when you replace it
- The harness passes: `python3 lessons/presentation-3hr/verify/harness.py --chapter 05`, and `./inspector/modern-mrtr.sh` walks the round trip by hand
- The `server/discover` result still has **no** UI or tasks extension — those are Chapters 6 and 7
- Nothing is asked at startup — a server has no way to ask
- `tools/call key_word_search` **with** a `directory` argument completes in one hop
- **Without** it, a client that declares form elicitation gets `input_required` embedding an `elicitation/create` with `mode: "form"`, and re-sends under a new id once the user has filled it in; a client that cannot show a form is not asked and gets the working-directory search
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
- MCP Apps is a Stable extension, revision `2026-01-26`, and adds no methods between client and server — the page and its host talk over `postMessage`
- The server declares the extension with no settings; `mimeTypes` is the host's, the page types it can render
- `visibility` says who may see and call a tool, the model or the app, not where the page is shown
- MIME type `text/html;profile=mcp-app` is required for a page
- Sandboxed iframes and a strict default Content Security Policy for security
- Progressive enhancement — tools still work in text-only clients, so the tool always returns text

### Verification
- `./gradlew chapterTest -Pchapter=06` is green: 17 tests, of which 13 start red (9 in `AppToolBuilderTest`, a nested class in `BuildersCoverageTest`; 3 in `IORouterTest`; 1 in `SpecTypesCoverageTest`)
- The harness passes: `python3 lessons/presentation-3hr/verify/harness.py --chapter 06`
- The `server/discover` result declares `"io.modelcontextprotocol/ui": {}`
- `tools/list` returns the tool with both `_meta.ui.resourceUri` and `_meta["ui/resourceUri"]`
- `resources/list` advertises the `ui://` URI
- `resources/read` returns the HTML under `text/html;profile=mcp-app`
- The Inspector's Apps tab lists `key_word_search`; give it a `keyword` and a `directory`, and **Open App** renders the demo page with its sample rows whatever you searched for — the real result is the `tools/call` reply in the Protocol panel

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
- The lifecycle state machine: `working ⇄ input_required`, and from either one to `completed`, `failed` or `cancelled`; a terminal task never changes status again
- Polling replaced blocking: `tasks/result` and `tasks/list` are removed and answer `-32601`, and the client polls `tasks/get` at `pollIntervalMs`
- **A handle or a question, never both**: `resultType` holds one value, so the handler commits to the task handle before the tool works out what it is missing, and the question moves onto the task as its `input_required` status
- A task that needs input asks through its status, and `tasks/update` delivers the `inputResponses`
- `tasks/update` and `tasks/cancel` are acknowledged with an empty result: answers the task is not waiting for are ignored, and cancelling a finished task leaves it finished
- `notifications/tasks` goes only to a client that subscribed with `subscriptions/listen`; this server sends none, and the client polls
- `ttlMs` / `pollIntervalMs` naming, and the TTL is the server's alone

### Verification
- `./gradlew chapterTest -Pchapter=07` is green: 74 tests, of which 58 start red (23 in `IORouterTest`, 35 in `TaskStoreTest`)
- `./gradlew clean build` passes, which on this branch includes the coverage check the finished server must meet, and so does the harness: `python3 lessons/presentation-3hr/verify/harness.py --chapter 07`
- `./inspector/modern-tasks.sh` walks one task by hand: the handle, `input_required`, `tasks/update`, `completed`, a cancel that leaves it `completed`, then `-32021` and `-32602`
- The `server/discover` result carries `extensions["io.modelcontextprotocol/tasks"]`
- `tools/list` shows no `execution` field — `Tool.execution` / `taskSupport` was removed
- A `tools/call` from a task-declaring client returns `resultType: "task"` in milliseconds
- The same call from a client that did *not* declare the extension runs inline and returns `complete`
- `tasks/get` reports `working` then `completed`, carrying the result
- A call with no directory, from a client that declares form elicitation, reaches `input_required`, and `tasks/update` resumes it
- `tasks/update` and `tasks/cancel` answer with an empty acknowledgement, and a finished task that is cancelled stays `completed`
- A task method, or a listen for task status, from a client that did not declare the extension gets `-32021`
- No `notifications/tasks` arrives for a client that did not subscribe

### Outcome
A server that implements the call-now / fetch-later pattern at the protocol level — the same pattern used by production batch APIs and long-running ML jobs.

---

## Bonus: Agent Chapter — Live LLM Integration & Supervised Workflow

**Branch**: `complete`, with the lessons in `lessons/bonus-live-llm-instructions.md` and `lessons/agent-instructions.md`

### Objective
Connect your MCP server to a real AI coding assistant and use it in conversation, then layer on a Claude Code plugin that supervises a multi-step workflow against the server.

### Before You Start
The bonus runs on the finished server, so start from `complete` and build it:

```bash
./clean-checkout.sh complete
./gradlew clean build
```

`clean-checkout.sh` runs `git clean -fdx`, so commit or copy your own work first. `complete` already ships the finished plugin: run `rm -rf keyword-audit-plugin` before the plugin steps, so that you build it yourself.

### What You'll Do
1. Register the server with your chosen client, from `lessons/mcp.json`
2. Verify tool discovery
3. Have real conversations using your tool
4. Scaffold a Claude Code plugin, copy in and read its four composable skills from `lessons/keyword-audit-plugin/`, then run the 6-step audit workflow, which pauses at an approval checkpoint before the final synthesis step — note that these are host-installed skills, not the `io.modelcontextprotocol/skills` extension, which serves the same `SKILL.md` artifacts over the protocol instead

### Supported Clients
The client must speak MCP revision `2026-07-28`, because the server refuses the `initialize` handshake of earlier revisions.

| Client | Configuration Location |
|--------|------------------------|
| Claude Code 2.1.292 or later | `./start_claude_mcp.sh`, which writes `lessons/mcp.json` with `{YOUR_BASE}` filled in to `.mcp-resolved.json`, passes it with `--mcp-config`, and deletes it on exit |
| Cursor | Not yet: the version checked opens with `initialize`, which the server refuses. Once it speaks `2026-07-28`, Settings → MCP |
| Windsurf | If it speaks `2026-07-28`: `~/.codeium/windsurf/mcp_config.json` |
| Cline | If it speaks `2026-07-28`: VS Code extension settings |

For any client but Claude Code, copy the entry from `lessons/mcp.json` and replace `{YOUR_BASE}` with the absolute path of your checkout.

### Example Conversations
The prompts from `lessons/bonus-live-llm-instructions.md`:
- Search for the keyword "mcp" in this project and tell me which file has the most occurrences.
- Which files in src/main/java mention "deprecated", and what is being deprecated?
- Compare the occurrence of "test" vs "spec" across the codebase. What does this tell us about the testing approach?

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
| 5 | Extensions & Round Trips | Multi Round-Trip Requests: the server asks for a missing directory by answering the call |
| 6 | MCP Apps | Interactive HTML UI inside the conversation |
| 7 | Tasks | Deferred-execution lifecycle (call-now / fetch-later) |
| Bonus | Agent / Live LLM | Server connected to a real assistant + supervised plugin |

## What You'll Have Built

By completing this workshop, you'll have:

- A working, test-covered MCP server in Java, deliberately framework-free: stdio transport, in-memory tasks, no authorization
- Understanding of the Model Context Protocol specification through 2026-07-28
- Experience testing every flow: the MCP Inspector for one-hop calls, round trips, MCP Apps and tasks, and the probe scripts and harness for subscriptions and error paths
- A custom `key_word_search` tool that takes a directory argument, **or** asks for one over a round trip, **or** falls back to its working directory
- Integration with an AI coding assistant that speaks MCP `2026-07-28`, such as Claude Code 2.1.292 or later

## Next Steps After Workshop

- Add additional tools to your server, deciding per call whether each one hands back a task handle
- Implement custom resources for your domain
- Create specialized prompts for your workflows
- Push task status to clients that subscribe with `subscriptions/listen`
- Accept older clients that still open with `initialize`, such as today's Cursor (a stretch in `lessons/bonus-live-llm-instructions.md`)
- Serve the plugin's skills over MCP with the `io.modelcontextprotocol/skills` extension (a stretch in `lessons/agent-instructions.md`)
- Share your MCP server with your team
