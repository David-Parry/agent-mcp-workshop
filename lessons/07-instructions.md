# Chapter 07: Tasks — Step-By-Step

## What You Are Building

You will extend the workshop server so a `tools/call` can be answered with a **task handle** instead of a result: the server returns `resultType: "task"` immediately, runs the work in the background, and then services `tasks/get`, `tasks/update`, and `tasks/cancel`, plus the `notifications/tasks` push.

Everything you add is additive — the inline `tools/call` path from Chapter 5 keeps working unchanged, and is still what a client without the extension gets.

## What Changed on 2026-07-28

Tasks were reshaped more than any other part of the protocol. Before writing code, it is worth having the list in one place, because almost every step below is a consequence of one of these:

| Before | Now | Why |
|--------|-----|-----|
| top-level `tasks` capability slot | `extensions["io.modelcontextprotocol/tasks"]` | tasks left the core protocol |
| client opts in with `params.task` | **server decides** | servers MUST ignore an inbound `task` field |
| `tasks/result` (blocking) | **removed** — poll `tasks/get` | a blocking call needs a session to block in |
| `tasks/list` | **removed** | a listing would leak other callers' task ids |
| — | `tasks/update` (**new**) | delivers input to a waiting task |
| `ttl` / `pollInterval` | `ttlMs` / `pollIntervalMs` | units in the name |
| `notifications/tasks/status` | `notifications/tasks` | the `notifications/tasks/` prefix is reserved |
| `_meta["…/related-task"]` wrapper | flat `taskId` | correlation by id, not by wrapper |
| `tool.execution.taskSupport` | **removed** | the client no longer chooses |

That `tasks/list` removal deserves a note: with sessions gone, there is no session to scope a task to. A task id's only access control is the entropy of its UUID, so anything that enumerates ids is a leak.

---

## Part 1: The spec records are already on the branch

`Task`, `TaskStatus`, `TaskResult`, `TasksGetParams`, `TasksUpdateParams`, and `TasksCancelParams` already exist under `src/main/java/com/workshop/mcp/spec/`. Read them; you do not create them. What you fill is `TaskStore` and the hollowed `TASKS_*` cases.

Note `ttlMs` and `pollIntervalMs` on `Task`. The old names carried no units and were renamed outright.

### `Task.java` — the store's view

```java
public record Task(
    String taskId,
    String status,
    String statusMessage,
    String createdAt,
    String lastUpdatedAt,
    Long ttlMs,
    Long pollIntervalMs
) {}
```

Note `ttlMs` and `pollIntervalMs`. The old names carried no units and were renamed outright.

### `TaskStatus.java`

```java
public enum TaskStatus {
    WORKING("working"),
    INPUT_REQUIRED("input_required"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELLED("cancelled");

    private final String value;
    TaskStatus(String value) { this.value = value; }
    public String getValue() { return value; }
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
    public static TaskStatus fromValue(String value) {
        if (value == null) return null;
        for (TaskStatus s : values()) {
            if (s.value.equalsIgnoreCase(value)) return s;
        }
        return null;
    }
}
```

### `TaskResult.java` — the wire projection

One flat record serves three different messages, distinguished by `resultType` and by which trailing payload fields are present:

```java
public record TaskResult(
    String resultType,
    String taskId,
    String status,
    String statusMessage,
    String createdAt,
    String lastUpdatedAt,
    Long ttlMs,
    Long pollIntervalMs,
    Object result,
    JsonRpcError error,
    Map<String, InputRequest> inputRequests
) {
    public static TaskResult handle(Task task) { /* resultType: "task" */ }

    public static TaskResult detail(Task task, Object result, JsonRpcError error,
                                    Map<String, InputRequest> inputRequests) { /* resultType: "complete" */ }

    public static TaskResult notification(Task task, Object result, JsonRpcError error) { /* no resultType */ }
}
```

- **`handle(...)`** answers `tools/call` with `resultType: "task"` — "the work is running, poll for it"
- **`detail(...)`** answers `tasks/get` with `resultType: "complete"` — the poll itself succeeded, whatever the task's own status is — plus the payload the status calls for: `result` when completed, `error` when failed, `inputRequests` when input is required
- **`notification(...)`** forms the params of `notifications/tasks`, which are task fields rather than a result and so carry **no** `resultType`

Fields left null are omitted from the JSON, which is what lets one record cover all three. There is no `CreateTaskResult` and no `related-task` `_meta` wrapper; the shape is flat and `taskId` does the correlating.

### Per-method params

```java
public record TasksGetParams(String taskId) {}
public record TasksCancelParams(String taskId) {}
public record TasksUpdateParams(String taskId, Map<String, Object> inputResponses) {}
```

There is no `TasksResultParams` and no `TasksListParams`, because there are no such methods any more.

These records name only the fields they read. The `_meta` envelope is still required on every one of these requests — `envelopeFor()` validates it before the switch is reached — but it is read off the raw request rather than through these records.

---

## Part 2: Declare the Extension

There is nothing to add to `ServerCapabilities` — Chapter 5 already gave it its `extensions` map, and tasks go in it like any other extension.

**Action Required**: `discoverResult()` is already on `IORouter.java` at **line 329**. Add `withTasksExtension()` to that builder chain:

```java
private DiscoverResult discoverResult() {
    DiscoverResultBuilder builder = DiscoverResultBuilder
            .builder()
            .withDefaultCapabilities()
            .withExtension(MetaKeys.UI_EXTENSION, /* … */)
            .withInstructions("…")
            .withCacheHints(LIST_TTL_MILLIS, CacheScope.PUBLIC)
            .withDefaultServerInfo();
    if (TASK_HANDLES_ENABLED) {
        // Declaring the extension here is what permits a task handle at all;
        // a client that does not see it will not poll.
        builder.withTasksExtension();
    }
    return builder.build();
}
```

Where `withTasksExtension()` is just a named shortcut:

```java
public DiscoverResultBuilder withTasksExtension() {
    return withExtension(MetaKeys.TASKS_EXTENSION, Map.of());
}
```

And `TASK_HANDLES_ENABLED` is a constant, so the workshop can demonstrate the same call in both shapes:

```java
private static final boolean TASK_HANDLES_ENABLED = true;
```

Client-side, there is likewise no new capability record to write. `ClientCapabilities.extensions` already exists, and the check is a map lookup:

```java
public boolean hasExtension(String identifier) {
    return extensions != null && extensions.containsKey(identifier);
}
```

---

## Part 3: There Is No `hasTasks` Field

The same rule as Chapter 5 applies, for the same reason. Whether a client can poll a task is a fact about **the request being answered**, not about a connection:

```java
public boolean supportsTasks() {
    return clientCapabilities != null
           && clientCapabilities.hasExtension(MetaKeys.TASKS_EXTENSION);
}
```

**Action Required**: Fill `answersWithTask` at **line 443** of `IORouter.java` (or confirm it already matches):

```java
private boolean answersWithTask(RequestEnvelope envelope) {
    return TASK_HANDLES_ENABLED && envelope.supportsTasks();
}
```

Two rules are baked into that one line:

1. **The server decides.** There is no `params.task` opt-in any more, and a server MUST ignore one if a legacy client sends it. `TASK_HANDLES_ENABLED` is this server's decision.
2. **Never hand a handle to a client that cannot poll.** The client must have declared the extension **on the request being answered**. Handing a task to a client that will not poll strands the work forever.

---

## Part 4: Create the `TaskStore`

Under `src/main/java/com/workshop/mcp/tasks/`, add `TaskStore.java`. It is deliberately framework-free — no executor pool, no database — to match the workshop's raw-protocol style.

Key responsibilities:

1. **Generate task IDs** as random UUIDs. The extension requires them to be unique and *unguessable* when authorization is unavailable. This server is stdio-only with no authorization, so that entropy is the only access control there is — which matters more now that there is no session to scope a task to.
2. **Track per-task state** in a `ConcurrentHashMap`, with each entry's mutable state guarded by its own monitor.
3. **Expose `create / get / detail / complete / fail / cancel`**, plus the input-handling trio below.
4. **Fire status listeners** on every transition, outside the entry lock so a listener cannot deadlock back into the store.

### The input-required trio

These three are what make `tasks/update` reachable, and they are easy to get wrong:

```java
/** Publishes what the task needs and moves it to INPUT_REQUIRED. */
public Task requireInput(String taskId, Map<String, InputRequest> inputRequests) { /* … */ }

/** Blocks the task's own work until an answer arrives. */
public Map<String, Object> awaitInput(String taskId, long timeoutMillis) { /* … */ }

/** Delivers tasks/update input and moves the task back to WORKING. */
public Task applyInput(String taskId, Map<String, Object> inputResponses) { /* … */ }
```

The split between `requireInput` and `awaitInput` is the important part. `requireInput` returns *immediately*, because the thread calling it is the one answering a `tasks/get` poll and must not be held. The work itself then waits in `awaitInput` until a `tasks/update` arrives **on a different thread** and calls `applyInput`, which notifies the waiter.

Note also that `applyInput` only retains answers the task actually asked for:

```java
Map<String, Object> accepted = new HashMap<>();
if (inputResponses != null && entry.inputRequests != null) {
    for (String key : entry.inputRequests.keySet()) {
        Object answer = inputResponses.get(key);
        if (answer != null) {
            accepted.put(key, answer);
        }
    }
}
```

The keys are chosen by this server and must not be reused across a task's lifetime, so anything unrecognised is dropped rather than trusted.

See `src/main/java/com/workshop/mcp/tasks/TaskStore.java` — the class is already on the branch. Fill the hollowed methods:
- `create` — **line 68**
- `complete` — **line 111**
- `fail` — **line 122**
- `requireInput` — **line 136**
- `applyInput` — **line 156**
- `awaitInput` — **line 187**
- `cancel` — **line 220**

---

## Part 5: Add the Method Constants

**Action Required**: In `src/main/java/com/workshop/mcp/spec/UniqueKeys.java`, the three methods and the notification are already declared (`TASKS_GET` at **line 129**, `TASKS_UPDATE` at **line 139**, `TASKS_CANCEL` at **line 144**, `NOTIFICATIONS_TASKS` at **line 155**). Confirm they match:

```java
TASKS_GET("tasks/get"),
TASKS_UPDATE("tasks/update"),
TASKS_CANCEL("tasks/cancel"),
NOTIFICATIONS_TASKS("notifications/tasks"),
```

And register the three requests as inbound. `INBOUND_METHODS` is at **line 94** of `IORouter.java`:

```java
private static final Set<UniqueKeys> INBOUND_METHODS = EnumSet.of(
        // … everything from earlier chapters …
        UniqueKeys.TASKS_GET,
        UniqueKeys.TASKS_UPDATE,
        UniqueKeys.TASKS_CANCEL);
```

Do **not** add `TASKS_RESULT` or `TASKS_LIST`. Deletions in this revision are physical: a method absent from the registry must be answered with `-32601`, even if you would happily service it.

Note the notification is the bare `notifications/tasks`, not `notifications/tasks/status`. The `notifications/tasks/` prefix is reserved for future use.

---

## Part 6: Wire `IORouter`

### a) Field and constructor

The `taskStore` field is at **line 111** of `IORouter.java`; the constructor that wires `onStatusChange` is at **line 113**:

```java
private final TaskStore taskStore = new TaskStore();

public IORouter(IOHandler io) {
    this.io = io;
    this.taskStore.onStatusChange(this::sendTaskStatusNotification);
}
```

### b) `TOOLS_LIST` is unchanged

There is nothing to advertise per tool. `execution.taskSupport` was removed along with the client's ability to choose, so `AppTool` has no `execution` field:

```java
public record AppTool(
    String name,
    String description,
    InputSchema inputSchema,
    AppMeta _meta
) {}
```

### c) Answer `tools/call` with a handle

In `executeKeyWordSearchCall` at **line 469** of `IORouter.java` — the method Chapter 5 left you with — branch on the predicate rather than on a request field:

```java
private void executeKeyWordSearchCall(RequestId requestId, ToolCallParams params, String keyword,
                                      Set<String> directories, RequestEnvelope envelope) {
    ToolCallParams resolved = resolvedCall(params, keyword);
    if (answersWithTask(envelope)) {
        Task task = taskStore.create(null);
        success(requestId, TaskResult.handle(task));
        runToolAsTask(task.taskId(), resolved, directories);
    } else {
        success(requestId, new KeyWordSearch().call(resolved, directories));
    }
}
```

`runToolAsTask` is at **line 591** of `IORouter.java`:

```java
private void runToolAsTask(String taskId, ToolCallParams params, Set<String> directories) {
    new Thread(() -> {
        try {
            Thread.sleep(4000L);                    // so the transition is observable
            ToolCallResult result = new KeyWordSearch().call(params, directories);
            taskStore.complete(taskId, result);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            taskStore.fail(taskId, "Interrupted: " + ie.getMessage());
        } catch (Exception e) {
            taskStore.fail(taskId, "Tool execution failed: " + e.getMessage());
        }
    }, "task-" + taskId).start();
}
```

### d) A handle or a question — never both

This is the subtlest thing in the chapter. `resultType` holds **one** value, so a `tools/call` response is *either* a task handle *or* an `input_required` — never one and then the other.

That forces an ordering on `handleKeywordSearch`: it has to commit to the answer shape **before** the tool works out what it is missing. Get it backwards and a task-declaring client with no directory receives an `input_required`, which the Inspector rejects outright:

> Unsupported result type `input_required` for `tools/call`: multi-round-trip auto-fulfilment is not enabled on this instance

**Action Required**: In `handleKeywordSearch` at **line 359** of `IORouter.java`, once no directory has been resolved, take the task branch *first*:

```java
if (answersWithTask(envelope)) {
    Task task = taskStore.create(null);
    success(requestId, TaskResult.handle(task));
    runSearchAsTaskThatAsks(task.taskId(), resolvedCall(params, keyword), envelope);
    return;
}

// … only now the Chapter 5 input_required branch …
```

Nothing is given up by committing early. The question simply moves off the call and onto the task, where it is asked as a **status**. Fill `runSearchAsTaskThatAsks` at **line 628** of `IORouter.java`:

```java
private void runSearchAsTaskThatAsks(String taskId, ToolCallParams params, RequestEnvelope envelope) {
    new Thread(() -> {
        Set<String> directories = new LinkedHashSet<>();

        // The same single question the inline path asks, carried by the task
        // instead of by the result.
        if (envelope.supportsElicitationForm()) {
            Map<String, Object> answers = askOnTask(
                    taskId, SearchContinuation.KEY_DIRECTORY,
                    InputRequest.elicitation(ElicitationBuilder.buildSearchDirectoryElicitation()));
            if (isTerminal(taskId)) {
                return;                             // cancelled while waiting
            }
            String directory = elicitedDirectory(answers.get(SearchContinuation.KEY_DIRECTORY));
            if (directory != null) {
                directories.add(directory);
            }
        }

        if (directories.isEmpty()) {
            directories.add(workingDirectory());    // answered nothing? still finish
        }

        taskStore.complete(taskId, new KeyWordSearch().call(params, directories));
    }, "task-" + taskId).start();
}

private Map<String, Object> askOnTask(String taskId, String key, InputRequest request) {
    taskStore.requireInput(taskId, Map.of(key, request));
    Map<String, Object> answers = taskStore.awaitInput(taskId, TASK_INPUT_TIMEOUT_MILLIS);
    return answers == null ? Map.of() : answers;
}
```

`askOnTask` returns an empty map rather than null when nothing arrives, because an unanswered question and an unasked one are the same thing to the caller: no directory, so fall back. Whether the silence was a cancellation is a separate question, and `isTerminal` is what asks it — a cancelled task must never go on to report a result.

`elicitedDirectory` is the method you already wrote in Chapter 5. Both paths ask the identical question and unpack the identical answer; only the carrier differs.

Bound the wait. A task that waited forever would sit there until its TTL expired, and a client has no obligation to answer at all:

```java
private static final long TASK_INPUT_TIMEOUT_MILLIS = 60_000L;
```

### e) Handle the three methods

Paste over the empty cases in `IORouter.java`:
- `TASKS_GET` — **line 219**
- `TASKS_UPDATE` — **line 222**
- `TASKS_CANCEL` — **line 225**

```java
case TASKS_GET -> {
    TasksGetParams params = deserializer.deserializeParams(message, TasksGetParams.class);
    TaskResult detail = params.taskId() == null ? null : taskStore.detail(params.taskId());
    if (detail == null) {
        error(message.id(), ErrorCodes.INVALID_PARAMS, "Failed to retrieve task: Task not found");
    } else {
        success(message.id(), detail);
    }
}
case TASKS_UPDATE -> {
    TasksUpdateParams params = deserializer.deserializeParams(message, TasksUpdateParams.class);
    if (params.taskId() == null || taskStore.get(params.taskId()) == null) {
        error(message.id(), ErrorCodes.INVALID_PARAMS, "Failed to update task: Task not found");
    } else if (taskStore.applyInput(params.taskId(), params.inputResponses()) == null) {
        error(message.id(), ErrorCodes.INVALID_PARAMS, "Cannot update task: it is not waiting for input");
    } else {
        success(message.id(), Map.of("resultType", ResultType.COMPLETE));
    }
}
case TASKS_CANCEL -> { /* cancel, or -32602 if unknown or already terminal */ }
```

An unknown task id is `-32602` **Invalid params**, not a "not found" result. And note that `tasks/get` answers `resultType: "complete"` even when the *task* has failed — the poll succeeded; the work did not.

Do not skip the `params.taskId() == null` guards. A request that omits `taskId` altogether is a different mistake from one that names a task nobody created, but both are the client's mistake and both deserve `-32602`. Reading a null id straight into the store instead throws a `NullPointerException` out of `route()`, and because that escapes rather than becoming a response, the client is left holding a request that will never be answered — a hang rather than an error, which is far harder to diagnose from the other end.

### f) Emit the status notification

`sendTaskStatusNotification` is at **line 665** of `IORouter.java`:

```java
private void sendTaskStatusNotification(TaskResult task) {
    io.emit(new JsonRpcNotification(
            JSON_RPC_VERSION,
            UniqueKeys.NOTIFICATIONS_TASKS.getValue(),
            task));
}
```

The params are the full task snapshot, flat — no `related-task` wrapper.

---

## Verifying

### Build

```bash
./gradlew clean build
```

### Inspector: the inline path still works

1. `./inspector/run.sh` and connect
2. Call `key_word_search` with a keyword **and** a `directory` — it returns `resultType: "complete"` synchronously, as in Chapter 5

### The task handle

3. Call the tool from a client that declares the extension. The first answer is a handle:
   ```json
   {"resultType":"task","taskId":"3dab39bc-…","status":"working",
    "ttlMs":300000,"pollIntervalMs":1000}
   ```
   Point out the renamed `ttlMs` / `pollIntervalMs`, and that there is no `task` object wrapping them.
4. Watch for `notifications/tasks` — the bare method name — carrying `status: "working"`
5. Send `tasks/get` with that `taskId`. Expect `working` for ~4s, then `completed` with the underlying `ToolCallResult` under `result`
6. Send `tasks/result` and `tasks/list`. Expect **`-32601`** from both. They are gone

### The task-level round trip

7. Call the tool with **no** `directory` from a task-declaring client that also declares form elicitation. Note the first answer is a **handle**, not an `input_required`
8. Poll `tasks/get` until `status` is `input_required`, and read `inputRequests` off the same response:
   ```json
   {"resultType":"complete","status":"input_required",
    "inputRequests":{"search_directory":{"method":"elicitation/create",
                                         "params":{"mode":"form","message":"…","requestedSchema":{ … }}}}}
   ```
9. Answer it:
   ```json
   {"jsonrpc":"2.0","id":2,"method":"tasks/update",
    "params":{"_meta":{ … },"taskId":"3dab39bc-…",
              "inputResponses":{"search_directory":{"action":"accept",
                                                    "content":{"directory":"/path/to/repo"}}}}}
   ```
   Expect `{"resultType":"complete"}`
10. Keep polling — the task resumes and completes, having searched the directory you handed it
11. Now repeat from step 7 but **decline** instead. The task still completes, rooted at the server's working directory. Declining is not a failure

### Cancellation

12. Create a fresh task, immediately send `tasks/cancel`. Expect `status: "cancelled"`
13. Send `tasks/cancel` on that same id again. Expect `-32602`
14. Cancel a task that is sitting in `input_required`. The waiting thread must notice and give up — a cancelled task must never go on to report a result

### Automated

```bash
./gradlew test
python3 lessons/presentation-3hr/verify/harness.py tasks tasks_input_required tasks_gated task_client_is_not_asked
```

---

## ✅ What You Completed

- Declared tasks as an **extension**, not a capability slot
- Built a framework-free `TaskStore` with status callbacks and an input-required lifecycle
- Wired three method handlers and one notification emitter into `IORouter`
- Made task creation a **server** decision, gated on what the client declared *on that request*
- Committed to the answer shape before asking anything, so a handle and a question never collide
- Implemented the task-level round trip, where the question is a **status** and `tasks/update` is the answer
- Left a fallback on every path, so no task ever strands waiting for input that never comes

You now understand the call-now / fetch-later pattern at the protocol level, and the more subtle point underneath it: once a request has been answered with a handle, every further conversation about that work has to happen through the task, because the request is over.
