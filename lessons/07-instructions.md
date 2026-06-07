# Chapter 07: Tasks — Step-By-Step

## What You Are Building

You will extend the workshop server so that **task-augmented `tools/call`** requests are accepted: the server hands back a `CreateTaskResult` immediately, runs the work in the background, and then services `tasks/get`, `tasks/result`, `tasks/list`, `tasks/cancel`, plus the optional `notifications/tasks/status` push.

Everything you add is additive — the existing synchronous `tools/call` path keeps working unchanged.

---

## Part 1: Add the New Spec Records

Create the following files under `src/main/java/com/workshop/mcp/spec/`. They are all immutable records, matching the workshop style.

### `Task.java`

```java
public record Task(
    String taskId,
    String status,
    String statusMessage,
    String createdAt,
    String lastUpdatedAt,
    Long ttl,
    Long pollInterval
) {}
```

Same shape is reused for `tasks/get` results, `tasks/cancel` results, list entries, and the body of `notifications/tasks/status`.

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

### `TaskParams.java`

```java
public record TaskParams(Long ttl) {}
```

This is the payload that requestors attach to a request's params under the `task` key.

### `CreateTaskResult.java`

```java
public record CreateTaskResult(Task task, Map<String, Object> _meta) {}
```

The `_meta` map carries the `io.modelcontextprotocol/related-task` key. We model it as `Map<String, Object>` because record component names cannot contain dots/slashes.

### Per-method params and results

```java
public record TasksGetParams(String taskId) {}
public record TasksResultParams(String taskId) {}
public record TasksListParams(String cursor) {}
public record TasksListResult(List<Task> tasks, String nextCursor) {}
public record TasksCancelParams(String taskId) {}
```

### The capability declarations

```java
public record ToolsTaskRequests(Capability call) {}
public record ServerTaskRequests(ToolsTaskRequests tools) {}
public record TasksCapability(Capability list, Capability cancel, ServerTaskRequests requests) {}

public record SamplingTaskRequests(Capability createMessage) {}
public record ElicitationTaskRequests(Capability create) {}
public record ClientTaskRequests(SamplingTaskRequests sampling, ElicitationTaskRequests elicitation) {}
public record ClientTasksCapability(Capability list, Capability cancel, ClientTaskRequests requests) {}
```

The empty-marker `Capability` instances (`new Capability(null, null)`) serialize to `{}` thanks to Gson's default null-skipping behavior, which exactly matches the spec's "presence-as-toggle" pattern.

### `ToolExecution.java` — tool-level taskSupport

```java
public record ToolExecution(String taskSupport) {
    public static ToolExecution optional()  { return new ToolExecution("optional"); }
    public static ToolExecution required()  { return new ToolExecution("required"); }
    public static ToolExecution forbidden() { return new ToolExecution("forbidden"); }
}
```

---

## Part 2: Extend `ClientCapabilities` and `ServerCapabilities`

**Find this block** in `ClientCapabilities.java`:
```java
public record ClientCapabilities(
    SamplingCapability sampling,
    RootsCapability roots,
    Elicitation elicitation
) {}
```

**Replace with:**
```java
public record ClientCapabilities(
    SamplingCapability sampling,
    RootsCapability roots,
    Elicitation elicitation,
    ClientTasksCapability tasks
) {}
```

**Find this block** in `ServerCapabilities.java`:
```java
public record ServerCapabilities(
    Capability tools,
    Capability prompts,
    Capability resources,
    Capability completions,
    Map<String, Object> experimental
) {}
```

**Replace with:**
```java
public record ServerCapabilities(
    Capability tools,
    Capability prompts,
    Capability resources,
    Capability completions,
    TasksCapability tasks,
    Map<String, Object> experimental
) {}
```

---

## Part 3: Extend `ToolCallParams` and `AppTool`

The `task` augmentation lives inside the tool call's params. Add the new field:

**Find** in `ToolCallParams.java`:
```java
public record ToolCallParams(
    MetaInfo _meta,
    String name,
    Map<String, String> arguments
) {}
```

**Replace with:**
```java
public record ToolCallParams(
    MetaInfo _meta,
    String name,
    Map<String, String> arguments,
    TaskParams task
) {}
```

For `AppTool`, add a per-tool execution hint:

**Find** in `AppTool.java`:
```java
public record AppTool(
    String name,
    String description,
    InputSchema inputSchema,
    AppMeta _meta
) {}
```

**Replace with:**
```java
public record AppTool(
    String name,
    String description,
    InputSchema inputSchema,
    AppMeta _meta,
    ToolExecution execution
) {}
```

Update `AppToolBuilder` to expose `withExecution(...)` and pass it into the `new AppTool(...)` call.

---

## Part 4: Update `InitializeResultBuilder`

Add a `withTasksCapability(...)` setter and a `defaultTasksCapability()` static helper. Have `withDefaultCapabilities()` thread the captured tasks capability into the new `ServerCapabilities` constructor.

```java
public InitializeResultBuilder withTasksCapability(TasksCapability tasks) {
    this.tasks = tasks;
    return this;
}

public static TasksCapability defaultTasksCapability() {
    Capability marker = new Capability(null, null);
    return new TasksCapability(
        marker,
        marker,
        new ServerTaskRequests(new ToolsTaskRequests(marker))
    );
}
```

---

## Part 5: Add the New Method Constants

Append to `UniqueKeys.java`:
```java
TASKS_GET("tasks/get"),
TASKS_RESULT("tasks/result"),
TASKS_LIST("tasks/list"),
TASKS_CANCEL("tasks/cancel"),
NOTIFICATIONS_TASKS_STATUS("notifications/tasks/status");
```

Remember to flip the previous trailing semicolon on `ELICITATION_CREATE_MESSAGE` to a comma.

---

## Part 6: Create the `TaskStore`

Under a new package `src/main/java/com/workshop/mcp/tasks/`, add `TaskStore.java`. Key responsibilities:

1. **Generate task IDs** (UUIDs — the spec requires unique, ideally unguessable strings).
2. **Track per-task state** in a `ConcurrentHashMap`. Each entry holds status, timestamps, TTL, poll interval, status message, and the eventual underlying result.
3. **Expose `create / get / list / cancel / complete / fail / resultFor / isTerminal`.**
4. **Fire status listeners** every time a transition happens, so the router can emit `notifications/tasks/status`.
5. **Hold pending `tasks/result` callbacks** until the task reaches a terminal state — this lets the router non-blocking-style fulfill the spec's "blocking" semantic for `tasks/result`.

See the full file in `src/main/java/com/workshop/mcp/tasks/TaskStore.java` for the canonical implementation.

---

## Part 7: Wire `IORouter`

### a) Fields and constructor

Add near the existing capability flags:
```java
private final TaskStore taskStore = new TaskStore();
private boolean hasTasks = false;
```

Register the status-change listener in the constructor:
```java
public IORouter(IOHandler io) {
    this.io = io;
    this.taskStore.onStatusChange(this::sendTaskStatusNotification);
}
```

Add a small error helper alongside `success(...)`:
```java
private void error(Long id, int code, String message) {
    JsonRpcErrorResponse response = new JsonRpcErrorResponse(
            JSON_RPC_VERSION, id, new JsonRpcError(code, message, null));
    io.emit(response);
}
```

### b) Detect client tasks support in `INITIALIZE`

After the existing `hasElicitation` check, add:
```java
if (clientCapabilities.tasks() != null) {
    hasTasks = true;
}
```

Thread the server's tasks capability through the builder:
```java
InitializeResultBuilder builder = InitializeResultBuilder
        .builder()
        .withProtocolVersion(initializeParams.protocolVersion())
        .withExperimentalCapability("io.modelcontextprotocol/elicitation", new Object())
        .withExperimentalCapability("io.modelcontextprotocol/apps", new Object())
        .withTasksCapability(InitializeResultBuilder.defaultTasksCapability())
        .withDefaultCapabilities()
        .withDefaultServerInfo();
```

### c) Advertise `execution.taskSupport` in `TOOLS_LIST`

```java
AppTool appTool = AppToolBuilder.builder()
        .withName(keyWordSearch.name())
        .withDescription(keyWordSearch.description())
        .withInputSchema(keyWordSearch.schema())
        .withResourceUri("ui://keyword-search/mcp-app.html")
        .withExecution(ToolExecution.optional())
        .build();
```

### d) Branch `TOOLS_CALL` on the `task` field

```java
if (!keyWordSearch.name().equalsIgnoreCase(toolCallParams.name())) {
    success(message.id(), ToolCallResultBuilder
            .builder()
            .addTextContent("Tool not found: " + toolCallParams.name())
            .asError()
            .build());
} else if (toolCallParams.task() != null && hasTasks) {
    Task task = taskStore.create(toolCallParams.task().ttl());
    success(message.id(), new CreateTaskResult(task, relatedTaskMeta(task.taskId())));
    runToolAsTask(task.taskId(), toolCallParams);
} else {
    success(message.id(), keyWordSearch.call(toolCallParams));
}
```

### e) Handle the four new methods

```java
case TASKS_GET -> { /* return Task or -32602 */ }
case TASKS_RESULT -> {
    TasksResultParams params = deserializer.deserializeParams(message, TasksResultParams.class);
    Long requestId = message.id();
    taskStore.awaitTerminal(params.taskId(),
        terminal -> emitTaskResult(requestId, params.taskId(), terminal));
}
case TASKS_LIST   -> success(message.id(), new TasksListResult(taskStore.list(), null));
case TASKS_CANCEL -> { /* terminate or -32602 */ }
```

The full implementations are in `src/main/java/com/workshop/mcp/IORouter.java`. The two interesting helpers are:

```java
private static Map<String, Object> relatedTaskMeta(String taskId) {
    Map<String, Object> meta = new HashMap<>();
    meta.put("io.modelcontextprotocol/related-task", Map.of("taskId", taskId));
    return meta;
}

private void runToolAsTask(String taskId, ToolCallParams params) {
    new Thread(() -> {
        try {
            Thread.sleep(2000L);                    // simulate latency
            KeyWordSearch tool = new KeyWordSearch(this.roots);
            ToolCallResult result = tool.call(params);
            taskStore.complete(taskId, result);
        } catch (Exception e) {
            taskStore.fail(taskId, "Tool execution failed: " + e.getMessage());
        }
    }, "task-" + taskId).start();
}
```

### f) Emit the status notification

```java
private void sendTaskStatusNotification(Task task) {
    JsonRpcNotification notification = new JsonRpcNotification(
            JSON_RPC_VERSION,
            UniqueKeys.NOTIFICATIONS_TASKS_STATUS.getValue(),
            task);
    io.emit(notification);
}
```

---

## Verifying

### Build
```bash
./gradlew clean build
```

### Inspector smoke test (existing path unchanged)
1. `./inspector/run.sh` — opens the MCP Inspector connected to the workshop server.
2. Click **List Tools** — `key_word_search` now has `"execution": { "taskSupport": "optional" }`.
3. Call the tool **without** `task` — it returns synchronously as before.

### Tasks happy path
4. In the inspector's raw JSON-RPC pane, send:
   ```json
   { "jsonrpc": "2.0", "id": 11, "method": "tools/call",
     "params": { "name": "key_word_search",
                 "arguments": { "keyword": "MCP" },
                 "task": { "ttl": 60000 } } }
   ```
   Expect immediate `CreateTaskResult` with `status: "working"`.
5. Send `tasks/get` with that `taskId`. Expect `working` for ~2s, then `completed`.
6. Send `tasks/result` with that `taskId`. Expect the actual `ToolCallResult` content, with `_meta.io.modelcontextprotocol/related-task.taskId` matching.
7. Send `tasks/list`. Expect the task appearing.

### Cancellation
8. Create a fresh task, immediately send `tasks/cancel`. Expect `status: "cancelled"`.
9. Send `tasks/cancel` on that same taskId again. Expect `-32602`.

### Notifications
10. Watch the inspector message log during step 5 — you should see a `notifications/tasks/status` push when the task completes.

---

## ✅ What You Completed

- Added 16+ new spec records covering the entire tasks utility.
- Built a framework-free `TaskStore` with status callbacks and pending-result delivery.
- Wired four new method handlers + one notification emitter into `IORouter`.
- Branched `tools/call` cleanly between synchronous and task-augmented execution.
- Advertised `execution.taskSupport` on the existing tool.

You now understand the call-now / fetch-later pattern at the protocol level — the same shape that powers production batch APIs, long-running ML jobs, and asynchronous integrations across the MCP ecosystem.
