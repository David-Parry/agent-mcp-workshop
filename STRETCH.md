# Stretch goals

This branch is `complete` with all 19 stretch goals from the lessons built in: one commit per chapter, one for the agent chapter, and one for the bonus. Every commit passes `./gradlew clean build`, coverage gate included.

The lessons themselves are unchanged, so in a few places they describe `complete` rather than this branch. [Where this branch differs from the lessons](#where-this-branch-differs-from-the-lessons) lists those places.

## Trying it

```bash
./gradlew clean build
python3 lessons/presentation-3hr/verify/harness.py --chapter 07
./inspector/modern-probe.sh
./inspector/modern-mrtr.sh
./inspector/modern-tasks.sh
```

The MCP Inspector connects in either era. Each command holds its terminal:

```bash
cd inspector && ./run.sh                      # 2026-07-28: server/discover, then stateless requests
cd inspector && ./run.sh config-legacy.json   # a handshake revision: initialize first, no envelope
```

Every new test carries the `stretch` tag next to its chapter's tag, so `./gradlew chapterTest -Pchapter=05` runs chapter 5's lesson tests and its stretch tests together. The agent and bonus tests are tagged `agent` and `bonus`, and run with `./gradlew test`. The harness runs 30 scenarios; each stretch below names the ones that cover it.

## Switches

Four behaviours are configured from the environment, read once at startup. Set them in the `env` block of `inspector/config.json` or `lessons/mcp.json`, or export them before running the JAR. A value that cannot be read falls back to the default.

| Variable | Default | Effect |
| --- | --- | --- |
| `WORKSHOP_LIST_CHANGED_MS` | `30000` | How often each open stream hears `notifications/tools/list_changed`. `0` turns the timer off, and `tools.listChanged` goes back to `false`. |
| `WORKSHOP_REQUIRE_FORM_FOR_LISTEN` | off | `true` makes `subscriptions/listen` refuse a client that cannot show a form, with `-32021`. |
| `WORKSHOP_STATE_KEY` | a random key per process | The Base64 HMAC key that signs `requestState`, at least 32 bytes, so a state survives a restart. A shorter or invalid key is ignored. The key is never logged. |
| `WORKSHOP_ELICITATION_PORT` | `0`, any free port | A fixed loopback port for the URL-elicitation page. |

## Chapter 2

### Read the Caller's Trace Context

`JsonRpcMessageDeserializer.traceparent(request)` returns the W3C `traceparent` a caller sends in `params._meta`, or `null` when there is none. `IORouter.process` logs it with the method, for example `[API][RECEIVED] tools/call traceparent=00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`.

To try it, add a `traceparent` to the `_meta` of any request and look in `logs/` under the server's working directory.

## Chapter 3

### Keep a Stream Open

Discovery advertises `tools.listChanged: true`. A `subscriptions/listen` whose agreed filter is not empty stays open: the server acknowledges it and leaves the request unanswered. Every `WORKSHOP_LIST_CHANGED_MS`, each stream that asked for tool-list changes hears `notifications/tools/list_changed`, with the stream's id under `io.modelcontextprotocol/subscriptionId` in `_meta`. The list never really changes; the timer stands in for a server whose tools come and go.

A client ends a stream by cancelling its listen request, and the server forgets it without answering. When the server ends a stream itself, it answers the listen request with a closing result.

Lives in `StreamRegistry`, `Settings`, and `IORouter`. Harness: `subscriptions`, which runs with a 200 ms interval, and `list_changed_off`.

### Require a Client Capability

Off unless `WORKSHOP_REQUIRE_FORM_FOR_LISTEN=true`. Then a `subscriptions/listen` from a client that did not declare `elicitation.form` gets `-32021`, with `requiredCapabilities` in its `data`. The lesson's `requireFormElicitation` is in `IORouter` as written, behind the switch instead of undone afterwards.

Harness: `require_form_for_listen`.

## Chapter 4

### Page for real

`resources/list` serves pages of 20 behind an opaque cursor, read from the new `ResourcesListParams`. The last page has no `nextCursor`. A cursor this server never issued gets `-32602` with `Invalid cursor`. Every page carries the same TTL and `cacheScope`. Today that is 85 resources over five pages: the app and the skill file first, then the 83 javadoc pages.

Lives in `resources/Pagination`, which `skills/list` reuses. Harness: `resource_paging`.

### Complete what was typed

`completion/complete` for the search's `keyword` argument offers only the vocabulary words that start with what was typed, ignoring case. `total` is how many matched. At most 100 values come back, with `hasMore` set when more matched.

Lives in `tools/KeywordCompletions`. Harness: `completion_prefix`.

### Return structured content

`key_word_search` declares an `outputSchema`: `keyword`, `totalMatches`, and `files` as `{path, count}` items. A successful result carries matching `structuredContent` alongside the usual text content; an error result carries none.

Lives in `tools/SearchOutput` and `KeyWordSearch`. `Tool`, `AppTool`, and `ToolCallResult` gained the two fields; Gson omits nulls, so a result without them is unchanged on the wire. Harness: `structured_content`.

## Chapter 5

### Sign the state

`requestState` is sealed: the Base64url of the state's JSON, a dot, then the Base64url of its HMAC-SHA256. The JSON holds the `stage`, a `token` when the question was asked on a page, and the expiry under `exp`. The server checks the signature in constant time, and a state lasts 10 minutes. A state that fails either check counts as no state, so the server asks again as if it were the first attempt. Signing makes the state tamper-evident, not secret: anyone can still read it.

The key comes from `WORKSHOP_STATE_KEY`, or each process makes a random one.

Lives in `tools/StateSigner` and `SearchContinuation`. Harness: `sealed_state`. `./inspector/modern-mrtr.sh` now reads the state out of the first answer instead of writing its own.

### Ask two questions in one round

A call with neither `keyword` nor `directory` asks for both in one round, under `search_keyword` and `search_directory`. The state's stage, `keyword_and_directory`, records that both were asked, so the retry reads both answers. A task asks the same way, and one `tasks/update` answers both.

Lives in `tools/SearchQuestions` and `SearchContinuation`. Harness: `two_questions`.

### Ask with a URL

A client that declares only `elicitation.url` cannot show a form, so the server shows one itself. The question goes out with `mode: "url"` and an address like `http://127.0.0.1:<port>/elicit/<token>`. The JDK's built-in `HttpServer` serves that page. It starts with the first such question and listens on the loopback interface only, which is where a stdio server's user is.

When the user submits the page, the server keeps the answer under the token until the client retries with `action: "accept"` and the state, then hands it over once. A retry before the page was submitted is asked again.

Lives in `tools/ElicitationPages` and `IORouter`. `WORKSHOP_ELICITATION_PORT` fixes the port. Harness: `url_elicitation`.

## Chapter 6

### Offer the page only to hosts that can show it

`tools/list` describes the search as an `AppTool`, with the page's `resourceUri` in its `_meta`, only to a client whose `io.modelcontextprotocol/ui` extension lists `text/html;profile=mcp-app` in its `mimeTypes`. Every other client gets a plain `Tool`. Because the list now depends on who asks, `tools/list` is `cacheScope: private`. MCP Inspector 2.5.0 declares the UI extension by default, so it gets the page.

Lives in `IORouter`. Harness: `mcp_apps`.

### A tool only the page can call

`recent_searches` carries `visibility: ["app"]` in its `_meta.ui`, set through the new `AppMeta.of(uri, visibility)`, and is listed only to UI hosts. It returns the last 10 searches. The router keeps them in a bounded `RecentSearches`, so the tools themselves stay stateless.

Lives in `tools/RecentSearches`. Harness: `app_only_tool`.

### Make the page real

`mcp-app.html` talks to its host in JSON-RPC over `postMessage`. It sends `ui/initialize`, then `ui/notifications/initialized`, and renders from `ui/notifications/tool-input` and `ui/notifications/tool-result`, preferring `structuredContent`. Its Search and Recent buttons call `tools/call` through the host, and it reports its size with `ui/notifications/size-changed`. If the host runs the search as a task or asks for input, the page says so instead of rendering a result. Demo data appears only when the page is opened outside a host.

Lives in `src/main/resources/lesson/mcp-app.html`. To try it, run `key_word_search` in the Inspector and open its app.

## Chapter 7

### Stop the work when a task is cancelled

Once the store has marked a task cancelled, `tasks/cancel` interrupts the task's worker. `KeyWordSearch` checks for the interrupt between files, so the walk stops instead of finishing for nobody.

Harness: `task_cancel_stops_work`.

### Push status to a client that subscribes

`SubscriptionFilter` gained `taskIds`. A `subscriptions/listen` that names tasks is agreed to for the ids this server knows, and stays open. The stream hears the task as it stands, then every change, as `notifications/tasks`. Each push is the task's `tasks/get` snapshot without a `resultType`, plus the stream's `subscriptionId`. The stream closes once every task it names has finished, or when the client cancels the listen.

Lives in `TaskStore`, which tells the router of each change, and `IORouter`. Harness: `task_push`. Steps 3 and 4 of `./inspector/modern-tasks.sh` show it on the wire.

### Report progress in the status message

The tasks extension forbids `notifications/progress` for a task, so progress goes in the task's status message: `Searched N of M files`. It is reported for the first file, the last, and at most once every 500 ms in between. A client that polls sees it in `tasks/get`, and a stream hears it pushed.

Lives in `TaskStore.updateStatusMessage` and `KeyWordSearch.Progress`. Harness: `task_push`.

### Let a task expire

`TaskStore` reads the time from a `Clock`. A task lives for its `ttlMs`, five minutes from when it was created. After that, the next use of the store purges it, failing it first if it was still running, and any `tasks/*` call for it gets `-32602`, as for an id that was never issued.

Lives in `tasks/TaskStore`. `TaskStoreTest` covers it by moving the clock, since the harness would have to wait five minutes.

## Agent chapter

### Write Your Own Skill

`keyword-compare` searches for two keywords and sorts the files into those with both, only the first, and only the second. `keyword-trend` compares how often a keyword occurs in `src/main/java` and in `src/test/java`, pairing each `<Name>.java` with its `<Name>Test.java`. Both pass `directory` to `key_word_search`, and both are in each copy of the plugin: `keyword-audit-plugin/skills/` and `lessons/keyword-audit-plugin/skills/`.

To try them, start Claude Code with `./start_claude_mcp.sh`, as the agent chapter does, and run `/keyword-audit-plugin:keyword-compare` or `/keyword-audit-plugin:keyword-trend`.

### Serve the Skills over MCP

Discovery declares `io.modelcontextprotocol/skills` with no settings. `skills/list` and `skills/get` describe `keyword-search`: its frontmatter as JSON, and each of its files with a `sha256:` digest and a size in bytes. `resources/read` serves `skill://keyword-search/SKILL.md` as `text/markdown`, exactly the bytes the digest describes. `resources/list` lists the file right after the app.

Gradle copies that `SKILL.md` from `lessons/keyword-audit-plugin/skills/keyword-search` into the JAR, so the served skill and the plugin's cannot drift. There is no YAML library, so a small parser reads the flat frontmatter and refuses anything it cannot render exactly, rather than guess.

Lives in `skills/SkillCatalog`, the `Skill*` records in `spec/`, and `processResources` in `build.gradle`. Harness: `skills`.

## Bonus

### Accept Older Clients

`initialize` is answered for `2025-11-25`, `2025-06-18`, `2025-03-26`, and `2024-11-05`; a client asking for any other revision is offered `2025-11-25`. `notifications/initialized` is accepted, and a `ping` without the envelope is answered with `{}`.

After `initialize`, a request without the envelope is served with the capabilities the client declared in it, minus elicitation and tasks. So a legacy client's search runs in one hop: in the directory it names, or else in the server's working directory. A legacy client is promised no list changes, and is not offered the tasks extension.

A request that carries the envelope is still served statelessly, and `server/discover` still requires the envelope and lists only `2026-07-28`. The handshake revisions are agreed through `initialize`, never named in an envelope.

Lives in `legacy/LegacySession` and `IORouter`. Harness: `legacy`. `./inspector/modern-probe.sh` ends with the handshake, and `./run.sh config-legacy.json` connects the Inspector the legacy way.

## Where this branch differs from the lessons

The lessons describe `complete`. On this branch:

- **Chapter 3.** `tools.listChanged` is `true`, and a listen with something to hear stays open instead of closing at once. Set `WORKSHOP_LIST_CHANGED_MS=0` to get the chapter's behaviour back.
- **Chapters 3 and 5, the bonus chapter, the syllabus, and the slides.** They teach that `initialize`, `notifications/initialized`, and `ping` were removed and get `-32601`. Here a client on a handshake revision is answered, so a client that opens with `initialize`, as the bonus chapter says Cursor does, is no longer refused. A `ping` that carries the envelope is still `-32601`, and that is the example `modern-probe.sh` now sends.
- **Chapter 4.** `resources/list` is five pages, not one.
- **Chapter 5.** The chapter's `requestState` is the Base64 of `{"stage":"directory"}`. Here every state is signed, so one written like that no longer opens, and the server asks again.
- **Chapter 6.** The search is an app tool only for UI hosts, and `tools/list` is `private`, not `public`. The chapter's `printf` example declares no UI extension, so it gets the plain tool, without `_meta.ui`.
- **Chapter 7.** The server pushes task status to a stream that names the task, a cancel stops the search, and a task is gone five minutes after it was created.
- **Agent overview.** It says the workshop does not implement the Skills extension. This branch does.

What the stretch instructions leave out, and how this branch filled the gap:

- **Page for real.** No params record carried a `cursor`, so `ResourcesListParams` was added.
- **Return structured content.** `Tool`, `AppTool`, and `ToolCallResult` had no `outputSchema` or `structuredContent`; they were added.
- **Ask with a URL.** `ElicitationCreateParams` had no `url`. And a URL-mode answer never comes back in the client's retry, so the server has to hold it, keyed by the token in `requestState`. That cuts against the lessons' point that the client carries the state, and the lesson text should say so.
- **Serve the Skills over MCP.** The lesson does not mention the manifest the extension requires, frontmatter as JSON plus a digest and size for each file, or that `skills/list` and `skills/get` must be added to `UniqueKeys` and `INBOUND_METHODS`.
- **Make the page real.** The Inspector declares the tasks extension, so a `tools/call` from the page can come back as a task handle. The page says so instead of rendering.

Only Keep a Stream Open warns that it breaks existing checks. These did too, and were updated on this branch:

- **Page for real:** `aListThatFitsOnOnePageOffersNoCursorToANextOne`, `resourcesListServesTheJavadocPagesAndTheAppAlongside`, and the harness's `unknown_names` and `mcp_apps`.
- **Sign the state:** the format assertions in `SearchContinuationTest`, the harness's `encode_state` and the `mrtr_elicitation`, `tasks`, and `tasks_gated` scenarios that used it, and the hand-written state in `inspector/modern-mrtr.sh`.
- **Offer the page only to UI hosts:** `appToolsCarryTheResourceUriUnderTheNestedMetaKeyOnly` and the harness's `mcp_apps`.
- **Push status:** the harness `tasks` step that expected no task ids to be agreed.
- **Accept Older Clients:** `methodsRemovedByThisRevisionAreNotFoundRatherThanInvalid`, `initializeIsToldWhichVersionsThisServerSupports`, the harness's `removed_methods`, and `inspector/modern-probe.sh`.
