# Chapter 07: Tasks — Call Now, Fetch Later

## Overview

In this lesson a search can run as a **task**. The server answers `tools/call` at once with a handle instead of holding the request open, runs the search in the background, and the client polls for the outcome with `tasks/get`. A task that still needs to know where to search asks for it as its status, and the client answers with `tasks/update`.

Tasks are an official extension, `io.modelcontextprotocol/tasks`, and its `2026-07-28` version is **Stable**. They began as an experimental feature of the `2025-11-25` specification, and SEP-2663 moved them out of the core protocol and reshaped them on the way; `lessons/07-overview.md` lists what changed. The extension adds three methods: `tasks/get`, `tasks/update` and `tasks/cancel`.

The records, the extension's declaration, the decision to answer with a handle and the threads the work runs on are already on this branch. You write:

1. Seven methods of `TaskStore`, the class that holds each task's state: `create`, `complete`, `fail`, `requireInput`, `applyInput`, `awaitInput` and `cancel`
2. `runSearchAsTaskThatAsks` in `IORouter`, the search that asks for its directory as the task's status
3. The three cases that answer `tasks/get`, `tasks/update` and `tasks/cancel`

The line numbers below are where each method sits in the file as you checked it out. Every paste pushes what is below it further down, so after your first paste in a file, find each one by its name rather than its number.

> Official documentation: [https://modelcontextprotocol.io/extensions/tasks/overview](https://modelcontextprotocol.io/extensions/tasks/overview), and the specification itself at [https://github.com/modelcontextprotocol/ext-tasks/blob/main/specification/2026-07-28/tasks.md](https://github.com/modelcontextprotocol/ext-tasks/blob/main/specification/2026-07-28/tasks.md)

---

## Part 1: Read How a Call Becomes a Task

### Step 1: Read the records

**Action Required**: open `src/main/java/com/workshop/mcp/spec/Task.java`. It is the store's view of a task:

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

- **`taskId`** is a random UUID. There is no session to tie a task to, so the id is all that stops one client reading another's task. That is why it must be unguessable, and why the extension has no `tasks/list`.
- **`status`** is one of the five values in `TaskStatus`: `working`, `input_required`, `completed`, `failed` and `cancelled`. The last three are **terminal**: once a task reaches one, its status never changes again.
- **`ttlMs`** is how long the server keeps the task, and **`pollIntervalMs`** how often it would like to be asked. Both are the server's choice. The extension gives a client no way to ask for a different TTL.

`TaskResult` is how a task looks on the wire, and it has two shapes:

- **`TaskResult.handle(task)`** answers `tools/call` with `resultType: "task"`: the work has started, so poll for it.
- **`TaskResult.detail(...)`** answers `tasks/get` with `resultType: "complete"`, plus what the status calls for: `result` once the task has completed, `error` once it has failed, and `inputRequests` while it is waiting for input.

`tasks/get` answers `complete` even for a task that failed. The poll succeeded; the work did not.

The three params records name only what each method reads: `TasksGetParams` holds a `taskId`, `TasksCancelParams` the same, and `TasksUpdateParams` a `taskId` and the `inputResponses`.

### Step 2: Read the decision to answer with a task

`discoverResult` in `src/main/java/com/workshop/mcp/IORouter.java` declares the extension, which is what permits a handle at all:

```java
if (TASK_HANDLES_ENABLED) {
    // Declaring the extension here is what permits a task handle at
    // all; a client that does not see it will not poll.
    builder.withTasksExtension();
}
```

`withTasksExtension()` adds `"io.modelcontextprotocol/tasks": {}` to the `extensions` map. The extension has no settings, on either side.

The server decides call by call whether to answer with a handle. A client that declared the extension must accept either a handle or an ordinary result, and a client that did not must never get a handle, because it would not know to poll:

```java
private boolean answersWithTask(RequestEnvelope envelope) {
    return TASK_HANDLES_ENABLED && envelope.supportsTasks();
}
```

`envelope.supportsTasks()` reads the `clientCapabilities` of the request being answered. As in Chapter 5, there is no connection to remember a capability on, so the question is always about this request. `TASK_HANDLES_ENABLED` is a constant, so that you can turn handles off and watch the same call come back inline.

The router checks `answersWithTask` in two places. When the call names its directory, `executeKeyWordSearchCall` hands a task client its handle and starts `runToolAsTask`, which runs the search on a virtual thread. That thread waits 4 seconds before it searches, so that you can watch the task sit in `working`.

### Step 3: Read why a task asks as its status

When the call names no directory, the tool has to ask for one. Chapter 5 asked by answering the call with `input_required`, and a task client cannot be asked that way: `resultType` holds one value, so a response is a handle or a question, never both. The Inspector rejects the attempt:

> Unsupported result type 'input_required' for tools/call: multi-round-trip auto-fulfilment is not enabled on this instance

So `handleKeywordSearch` gives a task client its handle first:

```java
if (answersWithTask(envelope)) {
    Task task = taskStore.create();
    logger.log("[API][SENT] tools/call — created taskId=" + task.taskId()
               + " with no directory; the task will ask for one");
    success(requestId, TaskResult.handle(task));
    runSearchAsTaskThatAsks(task.taskId(), resolvedCall(params), envelope);
    return;
}
```

The question moves onto the task. `runSearchAsTaskThatAsks`, which you write in Part 3, puts the task in `input_required` with the same `elicitation/create` Chapter 5 embedded. The client sees it when it polls `tasks/get`, and answers with `tasks/update`. The task has an id of its own, so no `requestState` is needed to tie the answer to the question.

### Step 4: Read what a client without the extension is refused

In `process`, before the switch, a client that did not declare the extension is refused every `tasks/` method, and a listen stream for task status:

```java
// The tasks/ prefix belongs to the extension. Capabilities are declared
// per request, so even a client holding a task id has to declare the
// extension on every task method it calls.
if (uniqueKey.getValue().startsWith("tasks/") && !envelope.supportsTasks()) {
    logger.log("[API][SENT] " + message.method() + " — the tasks extension was not declared (returning -32021)");
    refuseWithoutTasksExtension(message.id());
    return;
}
// Task status on a listen stream belongs to it too: taskIds is the
// extension's filter, so only a client that declared it may ask.
if (uniqueKey == UniqueKeys.SUBSCRIPTIONS_LISTEN && !envelope.supportsTasks()
    && deserializer.deserializeParams(message, TaskStatusFilter.class).asksForTaskStatus()) {
    logger.log("[API][SENT] subscriptions/listen — taskIds without the tasks extension (returning -32021)");
    refuseWithoutTasksExtension(message.id());
    return;
}
```

The extension requires `-32021` for both, with what the client is missing in `data`:

```java
private void refuseWithoutTasksExtension(RequestId id) {
    error(id, ErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY, "Missing required client capability",
          Map.of("requiredCapabilities", Map.of("extensions", Map.of(MetaKeys.TASKS_EXTENSION, Map.of()))));
}
```

Capabilities travel with each request, so even a client holding a task id has to declare the extension on every `tasks/get`, `tasks/update` and `tasks/cancel` it sends. `taskIds` is the filter the extension adds to `subscriptions/listen`, and asking for it takes the same declaration. This server pushes no task status, so a client that did declare the extension is agreed no task ids, and its stream closes like any other that was agreed nothing.

---

## Part 2: Write the Task Store

`TaskStore` keeps every task in a `ConcurrentHashMap`, keyed by its id. Each `Entry` is a small mutable object guarded by its own lock: every method takes `synchronized (entry)` before it reads or changes one. When a task stops waiting for input, the method that moved it calls `entry.notifyAll()`, which wakes the background work waiting in `awaitInput`.

### Step 1: Fill `create`

**Action Required**: fill `create` at **line 60** of `src/main/java/com/workshop/mcp/tasks/TaskStore.java`:

```java
public Task create() {
    String id = UUID.randomUUID().toString();
    String now = Instant.now().toString();
    Entry entry = new Entry(
        id,
        TaskStatus.WORKING.getValue(),
        "The operation is now in progress.",
        now,
        now,
        TTL_MILLIS,
        DEFAULT_POLL_INTERVAL_MILLIS
    );
    entries.put(id, entry);
    return entry.snapshot();
}
```

The task is in the map before `create` returns, so a `tasks/get` for its id resolves as soon as the client has the handle. The extension requires that: a server must not hand out a task it cannot yet answer for.

### Step 2: Fill `complete` and `fail`

**Action Required**: fill `complete` at **line 103** of `TaskStore.java`:

```java
public void complete(String taskId, Object result) {
    Entry entry = entries.get(taskId);
    if (entry == null) {
        return;
    }
    synchronized (entry) {
        if (entry.isTerminal()) {
            return;
        }
        entry.status = TaskStatus.COMPLETED.getValue();
        entry.statusMessage = "The operation completed successfully.";
        entry.lastUpdatedAt = Instant.now().toString();
        entry.result = result;
        entry.inputRequests = null;
        entry.notifyAll();
    }
}
```

**Action Required**: fill `fail` at **line 114** of `TaskStore.java`:

```java
public void fail(String taskId, String reason) {
    Entry entry = entries.get(taskId);
    if (entry == null) {
        return;
    }
    synchronized (entry) {
        if (entry.isTerminal()) {
            return;
        }
        entry.status = TaskStatus.FAILED.getValue();
        entry.statusMessage = reason;
        entry.lastUpdatedAt = Instant.now().toString();
        entry.error = new JsonRpcError(ErrorCodes.INTERNAL_ERROR, reason, null);
        entry.inputRequests = null;
        entry.notifyAll();
    }
}
```

#### What to notice

1. **Neither touches a terminal task.** A task cancelled while its search was still running stays cancelled when the search finishes, because `complete` ignores it.
2. **`failed` means a JSON-RPC error.** The task's `error` is the `-32603` the same call would have got had it run inline and thrown. A search that finds nothing, or any tool result with `isError: true`, is a **completed** task with that result in `result`.

### Step 3: Fill `requireInput`, `applyInput` and `awaitInput`

These three carry the question a task asks.

**Action Required**: fill `requireInput` at **line 128** of `TaskStore.java`:

```java
public Task requireInput(String taskId, Map<String, InputRequest> inputRequests) {
    Entry entry = entries.get(taskId);
    if (entry == null) {
        return null;
    }
    synchronized (entry) {
        if (entry.isTerminal()) {
            return null;
        }
        entry.status = TaskStatus.INPUT_REQUIRED.getValue();
        entry.statusMessage = "The operation is waiting for input.";
        entry.lastUpdatedAt = Instant.now().toString();
        entry.inputRequests = inputRequests;
        return entry.snapshot();
    }
}
```

**Action Required**: fill `applyInput` at **line 151** of `TaskStore.java`:

```java
public Task applyInput(String taskId, Map<String, Object> inputResponses) {
    Entry entry = entries.get(taskId);
    if (entry == null) {
        return null;
    }
    synchronized (entry) {
        Map<String, Object> accepted = new HashMap<>();
        if (TaskStatus.INPUT_REQUIRED.getValue().equals(entry.status)
            && inputResponses != null && entry.inputRequests != null) {
            for (String key : entry.inputRequests.keySet()) {
                Object answer = inputResponses.get(key);
                if (answer != null) {
                    accepted.put(key, answer);
                }
            }
        }
        if (!accepted.isEmpty()) {
            entry.status = TaskStatus.WORKING.getValue();
            entry.statusMessage = "The operation resumed with the input provided.";
            entry.lastUpdatedAt = Instant.now().toString();
            entry.inputRequests = null;
            entry.inputResponses = accepted;
            entry.notifyAll();
        }
        return entry.snapshot();
    }
}
```

**Action Required**: fill `awaitInput` at **line 182** of `TaskStore.java`:

```java
public Map<String, Object> awaitInput(String taskId, long timeoutMillis) {
    Entry entry = entries.get(taskId);
    if (entry == null) {
        return null;
    }
    long deadline = System.currentTimeMillis() + timeoutMillis;
    synchronized (entry) {
        while (TaskStatus.INPUT_REQUIRED.getValue().equals(entry.status)) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return null;
            }
            try {
                entry.wait(remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        if (entry.isTerminal()) {
            return null;
        }
        return entry.inputResponses == null ? Map.of() : Map.copyOf(entry.inputResponses);
    }
}
```

#### What to notice

1. **`requireInput` returns at once.** It publishes the question and leaves. The work then waits in `awaitInput`, and the answer arrives on another thread, in `applyInput`, whose `notifyAll` wakes it.
2. **`applyInput` ignores what it cannot use, rather than refusing it.** The extension asks this of a server: an answer under a key the task never asked, or any answer when the task is no longer waiting, is dropped, because the task may have moved on by the time the update arrives. An update that answers nothing the task asked leaves it waiting.
3. **`awaitInput` returns `null` whenever no answer is coming.** The task went terminal while it waited, the wait ran out, or the waiting thread was interrupted. The caller tells a cancelled task from an unanswered one by reading the status again, which is what `runSearchAsTaskThatAsks` does.

### Step 4: Fill `cancel`

**Action Required**: fill `cancel` at **line 202** of `TaskStore.java`:

```java
public Task cancel(String taskId) {
    Entry entry = entries.get(taskId);
    if (entry == null) {
        return null;
    }
    synchronized (entry) {
        if (!entry.isTerminal()) {
            entry.status = TaskStatus.CANCELLED.getValue();
            entry.statusMessage = "The task was cancelled by request.";
            entry.lastUpdatedAt = Instant.now().toString();
            entry.inputRequests = null;
            entry.notifyAll();
        }
        return entry.snapshot();
    }
}
```

Cancelling only asks. A task that has already finished keeps its status, and `cancel` returns `null` only for an id it does not know. A task waiting for input stops: `notifyAll` wakes its thread, which sees the task is cancelled and never searches. A search already running is not stopped. It runs to its end, and `complete` ignores it. The first stretch below changes that.

---

## Part 3: Write the Task That Asks

**Action Required**: fill `runSearchAsTaskThatAsks` at **line 793** of `src/main/java/com/workshop/mcp/IORouter.java`:

```java
private void runSearchAsTaskThatAsks(String taskId, ToolCallParams params, RequestEnvelope envelope) {
    runOnVirtualThread(taskId, () -> {
        logger.log("[TASK " + taskId + "] background tool execution started for tool=" + params.name());
        try {
            Set<String> directories = new LinkedHashSet<>();

            if (envelope.supportsElicitationForm()) {
                Map<String, Object> answers = askOnTask(
                        taskId, SearchContinuation.KEY_DIRECTORY,
                        InputRequest.elicitation(ElicitationBuilder.buildSearchDirectoryElicitation()));
                if (isTerminal(taskId)) {
                    return;
                }
                String directory = elicitedDirectory(answers.get(SearchContinuation.KEY_DIRECTORY));
                if (directory != null) {
                    directories.add(directory);
                }
            }

            if (directories.isEmpty()) {
                String workingDirectory = workingDirectory();
                logger.log("[TASK " + taskId + "] nothing was offered, using the working directory "
                           + workingDirectory);
                directories.add(workingDirectory);
            }

            ToolCallResult result = new KeyWordSearch().call(params, directories);
            taskStore.complete(taskId, result);
            logger.log("[TASK " + taskId + "] tool completed, transitioning to completed");
        } catch (Exception e) {
            logger.log("[TASK " + taskId + "] tool execution failed: " + e.getMessage());
            taskStore.fail(taskId, "Tool execution failed: " + e.getMessage());
        }
    });
}
```

#### What to notice

1. **It asks only a client that can show a form.** Any other client could never answer, so the search starts at once from the server's working directory, as the inline path does.
2. **The wait is bounded.** `askOnTask`, which is already in the router, calls `requireInput` and then `awaitInput` with `TASK_INPUT_TIMEOUT_MILLIS`, a minute. A client does not have to answer at all, and without a limit the task would wait for good. After a minute with no answer, the search carries on from the working directory.
3. **A cancelled task stops there.** `isTerminal` is checked straight after the wait, so a task cancelled while it waited never searches or reports a result.
4. **The `catch` is what keeps a task from hanging.** Anything the search throws, such as a directory path with a NUL byte in it, fails the task. Without it the thread would die and leave the task in `working` for good.

---

## Part 4: Answer the Three Methods

**Action Required**: paste these cases over the empty ones: `TASKS_GET` at **line 314**, `TASKS_UPDATE` at **line 317** and `TASKS_CANCEL` at **line 320** of `IORouter.java`:

```java
case TASKS_GET -> {
    TasksGetParams params = deserializer.deserializeParams(message, TasksGetParams.class);
    // A missing taskId is answered like an unknown one. Read into
    // the store, a null id would throw out of the router and leave
    // the client waiting for a reply that never comes.
    TaskResult detail = params.taskId() == null ? null : taskStore.detail(params.taskId());
    if (detail == null) {
        logger.log("[API][SENT] tasks/get — unknown taskId=" + params.taskId() + " (returning -32602)");
        error(message.id(), ErrorCodes.INVALID_PARAMS, "Failed to retrieve task: Task not found");
    } else {
        logger.log("[API][SENT] tasks/get taskId=" + detail.taskId() + " status=" + detail.status());
        success(message.id(), detail);
    }
}
case TASKS_UPDATE -> {
    TasksUpdateParams params = deserializer.deserializeParams(message, TasksUpdateParams.class);
    // Answers the task is not waiting for are ignored, not refused:
    // the task may have moved on by the time they arrive.
    Task task = params.taskId() == null ? null
            : taskStore.applyInput(params.taskId(), params.inputResponses());
    if (task == null) {
        logger.log("[API][SENT] tasks/update — unknown taskId=" + params.taskId() + " (returning -32602)");
        error(message.id(), ErrorCodes.INVALID_PARAMS, "Failed to update task: Task not found");
    } else {
        logger.log("[API][SENT] tasks/update — taskId=" + task.taskId() + " acknowledged, now "
                   + task.status());
        success(message.id(), Map.of("resultType", ResultType.COMPLETE));
    }
}
case TASKS_CANCEL -> {
    TasksCancelParams params = deserializer.deserializeParams(message, TasksCancelParams.class);
    // A finished task keeps its status, and the cancel is still
    // acknowledged: cancelling only asks.
    Task task = params.taskId() == null ? null : taskStore.cancel(params.taskId());
    if (task == null) {
        logger.log("[API][SENT] tasks/cancel — unknown taskId=" + params.taskId() + " (returning -32602)");
        error(message.id(), ErrorCodes.INVALID_PARAMS, "Failed to cancel task: Task not found");
    } else {
        logger.log("[API][SENT] tasks/cancel — taskId=" + task.taskId() + " acknowledged, now "
                   + task.status());
        success(message.id(), Map.of("resultType", ResultType.COMPLETE));
    }
}
```

#### What to notice

1. **An unknown task id is `-32602` Invalid params**, and a missing one is answered the same way. Read into the store, a `null` id would throw out of the router, and the client would wait for a reply that never comes.
2. **`tasks/update` and `tasks/cancel` answer with an empty result**, `{"resultType":"complete"}` and the `_meta` every result carries. The extension calls this an acknowledgement: it says the request arrived, not what it did to the task. A client that wants to know polls `tasks/get`. After a cancel it need not, and the extension lets it forget the task at once.
3. **Neither refuses a task that has moved on.** An answer the task is not waiting for is ignored, and so is a cancel of a finished task, so a client never gets an error for a race it could not see.

---

## You are done when the tests pass

```bash
./gradlew chapterTest -Pchapter=07
```

The run reports 74 tests, and 58 of them fail before you start: 23 in `IORouterTest` and 35 in `TaskStoreTest`. The other 16 pass already. Seven are the given parts of the router: the extension is declared, the test hook that interrupts a task's thread behaves, and a client without the extension never gets a handle and is refused the task methods and a listen for task status. Any other listen is acknowledged, with no task ids agreed to. Four check what the store does with an id it does not know, which an empty method already gets right, and five check the task types in `spec`. Failures worth recognising:

- `createStartsATaskInWorkingStatus` or `createGeneratesAnUnguessableIdPerTask` — `create` still throws, or builds its task some other way. Almost every router test fails with it, because no task can be created.
- `completeIsIgnoredOnceTheTaskIsTerminal` or `failIsIgnoredOnceTheTaskIsTerminal` — the terminal check is missing, so a finished task changed its status.
- `anUpdateThatAnswersNothingTheTaskAskedLeavesItWaiting` or `anUpdateForATaskThatIsNotWaitingIsIgnored` — `applyInput` resumes the task, or changes it, without an answer to what it asked.
- `awaitInputReturnsNullWhenTheWaitExpires` or `awaitInputReturnsNullWhenTheTaskIsCancelledWhileWaiting` — the wait ignores its deadline, or returns an empty answer for a task cancelled while it waited.
- `cancellingAFinishedTaskLeavesItAsItWas` or `aSecondCancelChangesNothing` — `cancel` changes a task that has already finished.
- `aTaskAsksForItsDirectoryAsAStatusAndResumesOnTasksUpdate` — the task never reaches `input_required`, or never resumes after `tasks/update`.
- `cancellingATaskThatIsWaitingForInputStopsItInsteadOfCompletingIt` — the `isTerminal` check after the wait is missing, so a cancelled task searched anyway.
- `aTaskThatIsAnsweredWithAnImpossiblePathIsFailedRatherThanLeftWorking` — the `catch` is missing from `runSearchAsTaskThatAsks`, so the task stays in `working`.
- `everyMethodTheServerAcceptsIsActuallyAnswered` — one of the three cases is still empty.
- `theTaskMethodsAnswerRatherThanFaultOnAMissingTaskId` — a case reads a missing `taskId` into the store.
- `aCancelIsAcknowledgedWithAnEmptyResultAndASecondOneToo`, `cancellingAFinishedTaskIsAcknowledgedAndLeavesItFinished` or `answeringATaskThatIsNoLongerAskingIsAcknowledgedAndChangesNothing` — a case answers with the task, or with an error, where the extension wants an empty acknowledgement.

A router test that fails with `task never reached …` polled for 20 seconds for a status that never came, usually because one of the store's methods is still empty.

Then build everything and drive the built server the way a client would. The harness starts the JAR and checks everything up to this chapter over stdio:

```bash
./gradlew clean build
python3 lessons/presentation-3hr/verify/harness.py --chapter 07
```

Last, walk one task through by hand. `inspector/modern-tasks.sh` sends each request, reads the answer and uses the task id in it for the next. It calls the tool without a directory, polls until the task asks, answers with the tool's own source directory, polls until the task completes, and then cancels it. Finally come two requests that are refused: one from a client that left the extension off, and one for a task that was never created:

```bash
./inspector/modern-tasks.sh
```

```text
{"jsonrpc":"2.0","id":1,"result":{"resultType":"task","taskId":"<task-id>","status":"working",…,"ttlMs":300000,"pollIntervalMs":1000,…}}
{"jsonrpc":"2.0","id":"poll-…","result":{"resultType":"complete","taskId":"<task-id>","status":"input_required",…,"inputRequests":{"search_directory":{"method":"elicitation/create",…}}
{"jsonrpc":"2.0","id":2,"result":{"resultType":"complete","_meta":{…}}}
{"jsonrpc":"2.0","id":"poll-…","result":{"resultType":"complete","taskId":"<task-id>","status":"completed",…,"result":{"resultType":"complete","content":[{"text":"…KeyWordSearch.java, keyword_count…","type":"text"},…}}
{"jsonrpc":"2.0","id":3,"result":{"resultType":"complete","_meta":{…}}}
{"jsonrpc":"2.0","id":"poll-…","result":{"resultType":"complete","taskId":"<task-id>","status":"completed",…}}
{"jsonrpc":"2.0","id":4,"error":{"code":-32021,"message":"Missing required client capability","data":{"requiredCapabilities":{"extensions":{"io.modelcontextprotocol/tasks":{}}}}}}
{"jsonrpc":"2.0","id":5,"error":{"code":-32602,"message":"Failed to retrieve task: Task not found"}}
```

The poll after the cancel still says `completed`: the task had already finished. The server sent nothing it was not asked for. Nothing was subscribed to, so no `notifications/tasks` came, and every line is the answer to a request.

## Try It in the MCP Inspector

```bash
cd inspector
./run.sh
```

Turn on the switch on the `workshop` card to connect. The Inspector declares `io.modelcontextprotocol/tasks` on every request, so every call to `key_word_search` comes back as a task. Open **Tasks**, beside **Protocol** in the panel on the right.

If you open the Inspector before the tests pass, what goes wrong tells you what is still missing. A call that fails at once with `Internal error while handling tools/call` reached a `TaskStore` method that still throws, and the server's log in `inspector/logs/` names it. A call that fails after 30 seconds with `Raw request "tasks/get" timed out after 30000 ms` reached the `TASKS_GET` case while it was still empty, and the task stays under **Active** in **Tasks**. An empty case answers nothing, and a `tasks/update` or `tasks/cancel` sent to one times out the same way.

### A task that runs
- In the **Tools** tab, select `key_word_search`, give it a `keyword` and a `directory`, and click **Execute Tool**.
- The **Protocol** panel shows the `tools/call` answered at once with `resultType: "task"`. A `tasks/get` follows about once a second, the `pollIntervalMs` the handle asked for, and after 4 seconds one answers `completed`. The search result appears under **Results**.
- **Tasks** lists the task under **Completed**, with its id, its TTL of 300000ms and the whole task as `tasks/get` returned it.

### A task that asks
- Clear the `directory` and click **Execute Tool** again. The Inspector sends the empty field as `""`, which the tool treats as no directory at all.
- The handle comes back as before, and the first poll finds the task in `input_required`. The Inspector shows the form from its `inputRequests`, with a note that the answer goes back through `tasks/update`, not a retry.
- Enter a directory and click **Submit**. The **Protocol** panel shows the `tasks/update` and its empty acknowledgement, and the next poll finds the task `completed`.

### A task that is cancelled
- Run it once more without a directory, and click **Cancel** in the form this time. The Inspector sends `tasks/cancel`, and the server acknowledges it. The server's log in `inspector/logs/` shows the waiting thread waking with `no answer arrived for search_directory` and stopping without a search.
- The **Tools** tab reports `Pending request aborted`, because the Inspector stopped waiting for a result. **Tasks** moves the task under **Completed**, marked cancelled. The Inspector marks it from the acknowledgement alone and does not poll again, which the extension allows, so the panel keeps the status message of the last poll.

### The Run as task switch
- Turn on **Run as task** and run a search with a directory. The Inspector adds `"task":{"ttl":60000}` to the call, the opt-in of the `2025-11-25` specification that the extension removed. The server ignores it, as the extension requires: the call is answered as before, and the task's TTL is still 300000ms.

## Stretch: Stop the work when a task is cancelled

Cancelling marks the task, but the search runs on. The router already keeps every task's thread in `taskWorkers`, and `interruptTaskWorker` interrupts one. Call it from the `TASKS_CANCEL` case once the store has cancelled the task. A task cancelled during the 4-second wait in `runToolAsTask` is then interrupted out of its `sleep`, and the `fail` in its `catch` changes nothing, because the task is already cancelled. The search itself never checks for an interrupt. To stop a search halfway, have `KeyWordSearch` check `Thread.currentThread().isInterrupted()` between files.

## Stretch: Push status to a client that subscribes

This server never pushes task status, which the extension allows: polling is the default. A client asks for pushes with `subscriptions/listen`, naming the tasks in `notifications.taskIds`. Add `taskIds` to `SubscriptionFilter`, which `process` already refuses to a client that did not declare the extension. List the ids you agree to in the acknowledgement, and keep the stream open: `acknowledgeSubscription` answers the request at once, and that answer is what closes it. Then send a `notifications/tasks` on every change to a subscribed task, carrying what `tasks/get` would answer at that moment and the subscription's id in `_meta`. The extension forbids `notifications/progress` and `notifications/message` on a task's stream.

## Stretch: Report progress in the status message

The extension rules out `notifications/progress` for tasks. Progress goes in `statusMessage` instead, which a client reads on every poll. Give `TaskStore` a method that changes only the message, and have the search report as it goes, for example `"Searched 120 of 300 files"`.

## Stretch: Let a task expire

The store keeps every task until the server stops, and a task outlives its `ttlMs`. Once that has passed, the extension lets a server mark the task `failed` and later delete it. Purge expired tasks, and answer `tasks/get` for one with `-32602`, as for an id that was never issued. A server that must survive a restart would also keep its tasks somewhere durable, so that a client holding a task id could go on polling it.

## Where this goes next

That is the whole server. The `complete` branch has the same code, every chapter's lessons and two appendices, linked from its README: **Running against a live LLM** points a real assistant at your server, and **Building an agent plugin** wraps the tool in skills an agent can drive. Your code should now match `complete` file for file. Commit anything you want to keep, then switch to it with `./clean-checkout.sh complete`.
