# 3-Hour Walkthrough Verification — Run Notes

Verification run of `lessons/presentation-3hr/walkthrough.md` on branch
`three-hour-workshop` (clean tree at `a243662`). Each step is verified
red-then-green: the check is run before the paste (expected to fail) and after
(expected to pass). Steps 4-33 use the stdio JSON-RPC harness in
`lessons/presentation-3hr/verify/harness.py`.

| Scenario | Walkthrough steps |
|---|---|
| gradle `IOHandlerImplTest` | 1-3 |
| handshake | 6 |
| ping_sampling | 7 |
| roots | 8 |
| cancelled | 9 |
| resources_list | 11 |
| resources_read | 12 |
| tools_list | 14 |
| tools_call | 15 |
| prompts_list | 16 |
| prompts_get | 17 |
| completion | 18 |
| elicit_capability | 19 |
| elicit_defer | 21 |
| elicit_resume | 22 |
| apps_capability | 23 |
| apps_tools_list | 24 |
| apps_resources_list | 25 |
| apps_resources_read | 26 |
| tasks_capability | 29 |
| tasks_tools_list | 30 |
| task_call | 28+31 |
| tasks_get_result | 32 |
| tasks_list_cancel | 33 |

Steps 4, 5, 10, 13, 20, 27, 28 are enabling steps with no externally observable
behavior of their own; they are verified by compilation (`./gradlew build`) and
covered by the first dependent scenario.

---

## Run log

### Section 1 — Transport (Steps 1-3)

- RED (before any paste): `./gradlew test --tests "...IOHandlerImplTest"` — 13 tests, 2 succeeded, 11 failed.
- STEP 1 applied (Scanner read loop): still 2/13 passing — reader tests need publishLine (Step 2) to observe lines. Expected.
- STEP 2 applied (publishLine loop): 10/13 passing — remaining 3 failures are emit tests.
- STEP 3 applied (emit body): GREEN — 13/13 passing. `./gradlew clean build` BUILD SUCCESSFUL, JAR produced.

### Section 2 — Handshake and Routing (Steps 4-9)

- RED `handshake`: timed out, no traffic (Runner.main empty). Confirmed.
- STEPS 4+5+6 applied (wiring, deserializer bodies, INITIALIZE): `handshake` GREEN.
  Note: `withDefaultCapabilities()` already advertises a `tasks` capability at Step 6 — worth knowing for Step 29 red/green (the observable change there is the hasTasks flag behavior, not the capability blob).
- RED `ping_sampling`: timed out on ping response. STEP 7 applied: GREEN (empty ping result + `sampling/createMessage` id=-2000 observed).
- RED `roots`: no `roots/list` request after notifications/initialized. STEP 8 applied: GREEN (request id=-1000 emitted on initialized and on roots/list_changed; log shows "populated 1 root(s)").
  Harness bug fixed along the way: log filename pattern is `agent-mcp-workshop-<pid>-<ts>.log` (pid first), harness had it reversed.
- RED `cancelled`: notification hit the default branch, reason never deserialized. STEP 9 applied (cp NotificationCancelledParams.java + handler): GREEN.
- Regression sweep (handshake, ping_sampling, roots, cancelled): all PASS.

### Section 3 — Resources and Tools (Steps 10-15)

- RED `resources_list`: timed out (no handler). STEPS 10+11 applied (cp JavadocResources.java, import, RESOURCES_LIST): GREEN — 60+ javadoc resources listed, nextCursor="pageNext".
- RED `resources_read`: timed out. STEP 12 applied: GREEN (javadoc content returned; bogus URI returns isError=true result).
- RED `tools_list`: timed out. STEPS 13+14 applied (cp KeyWordSearch.java, import, TOOLS_LIST): GREEN — key_word_search advertised with description + inputSchema.
- RED `tools_call`: timed out (roots flow worked, call unanswered). STEP 15 applied: GREEN — keyword_count=3 returned from temp dir root; unknown tool returns error-shaped result.
- Regression sweep (all 8 scenarios so far): all PASS.

### Section 4 — Prompts and Completion (Steps 16-18)

- RED `prompts_list`, `prompts_get`, `completion`: all timed out (no handlers).
- STEPS 16+17+18 applied as written: all three GREEN (search_keyword prompt with required keyword arg; prompts/get folds arguments into KEY_WORD_MESSAGE; completion offers java/the/and, total=3, hasMore=true).

### Section 5 — Elicitation (Steps 19-22)

- RED `elicit_capability`: no experimental capability in initialize result. STEP 19 applied (flag + INITIALIZE replacement): GREEN.
- RED `elicit_defer`: tools/call with empty roots answered immediately with error result, no elicitation request. STEPS 20+21 applied (reserved id, pending state, sendElicitationMessage, TOOLS_CALL replacement, executeKeyWordSearchCall): GREEN — elicitation/create id=-4000 emitted with directory-required schema, tools/call response correctly withheld.
- RED `elicit_resume`: accept answer never resumed the parked call. STEP 22 applied (else-if response branch + ELICITATION_CREATE_MESSAGE case): GREEN — deferred call resumed with keyword_count=3; client-initiated elicitation/create acknowledged with empty object.
- Regression: `tools_call` (Step 15 path with roots present) still PASS.

### Section 6 — MCP Apps (Steps 23-26)

- RED `apps_capability` and `apps_tools_list`: no apps experimental capability, no `_meta.ui.resourceUri` on the tool.
- STEP 23 applied (one added line in INITIALIZE): `apps_capability` GREEN.
- STEP 24 applied (TOOLS_LIST replaced with AppTool version): `apps_tools_list` GREEN.
- RED `apps_resources_list`: no ui:// resource. STEP 25 applied (RESOURCES_LIST replaced): GREEN — ui://keyword-search/mcp-app.html advertised with mcp-app MIME type.
- RED `apps_resources_read`: ui:// read fell through to the classpath path (mimeType text/html, error text). STEP 26 applied (RESOURCES_READ replaced): GREEN — bundled lesson/mcp-app.html served with text/html;profile=mcp-app; javadoc reads unaffected.
- Regression (apps_*, resources_list, resources_read, elicit_resume): all PASS.

### Section 7 — Tasks (Steps 27-33)

- RED `tasks_tools_list` (no execution.taskSupport) and `task_call` (task-augmented call answered synchronously with plain content). Confirmed.
- STEPS 27+28 applied (cp TaskStore.java, import, store field, hasTasks flag, status listener, relatedTaskMeta, sendTaskStatusNotification): `./gradlew build` GREEN (enabling step — no external behavior yet).
- STEP 29 applied (final INITIALIZE): `tasks_capability` GREEN. As predicted in Section 2 notes, the capability blob itself was already present via `withDefaultCapabilities()`; the meaningful change is the `hasTasks` flag, which gates Step 31 behavior (verified by `task_call` red→green below).
- RED `tasks_tools_list`. STEP 30 applied (final TOOLS_LIST): GREEN — execution.taskSupport="optional".
- RED `task_call`. STEP 31 applied (executeKeyWordSearchCall replaced + runToolAsTask): GREEN — immediate CreateTaskResult with related-task meta, then working→completed status notification after the 4s demo sleep.
- RED `tasks_get_result`: tasks/get timed out. STEP 32 applied (TASKS_GET/TASKS_RESULT + emitTaskResult): GREEN — non-blocking snapshot, blocking result with related-task meta, unknown taskId → -32602.
- RED `tasks_list_cancel`: tasks/list timed out. STEP 33 applied: GREEN — list returns store contents; cancel of a fresh task returns cancelled snapshot; second cancel and unknown taskId both → -32602.

### Final full pass

- `rg ">>> STEP" src/` — no markers remain.
- `./gradlew clean build` — BUILD SUCCESSFUL.
- All 23 harness scenarios (`harness.py all`) — PASS.

## Result

All 33 walkthrough steps verified red-then-green in a single uninterrupted pass
from the clean branch tip `a243662`. No fix to the walkthrough, the stubs, or
the prepared files was needed, so no revert-and-restart cycle was required.

The only fix made during the run was to the harness itself (log filename
pattern `agent-mcp-workshop-<pid>-<ts>.log`, pid before timestamp) — a bug in
the verification tooling, not in the workshop material.

## Pre-existing observations (outside walkthrough scope, present on clean a243662)

1. `LogFileWriterTest` has 2 failing tests on the untouched branch tip
   ("Should create log file with process ID in name", "Should fallback to temp
   directory when logs directory is not writable") — the log filename now
   embeds a timestamp (`agent-mcp-workshop-<pid>-<ts>.log`), and the tests
   still expect the old `<name>-<pid>.log` pattern.
2. `ServerTest.ioHandler_addListener_called` calls `Server.start()` with a null
   latch; `keepRunning()` reaches `System.exit(0)` and silently kills the
   Gradle test executor JVM. Depending on execution order this either hides
   the remaining tests (test summary "1 tests, 1 skipped", build passes) or
   lets the LogFileWriterTest failures surface first (build fails). This makes
   `./gradlew clean build` / `./gradlew test` outcomes order-dependent.
   The walkthrough's own verification commands (scoped `IOHandlerImplTest`
   runs and `clean build` in the order the script prescribes) are unaffected.
