# 3-Hour Workshop Walkthrough

This is the single script for the entire 3-hour live-coding session. The codebase
starts with compile-safe stubs; every paste site in the source is marked with a
comment like:

```java
// >>> STEP 6: paste the INITIALIZE handler here (lessons/presentation-3hr/walkthrough.md)
```

Find the same `STEP n` tag below, copy the code block, and paste it at (or over)
the marked location. Steps are numbered 1..33 in presentation order. Some later
steps **replace** a handler pasted earlier — those say so explicitly. Four steps
create a whole new class — for those, the finished file is prepared in
`lessons/presentation-3hr/files/` and the step gives the `cp` command to copy it
into the project.

Sections:

1. Transport (STDIO) — Steps 1–3
2. Handshake and Routing — Steps 4–9
3. Resources and Tools — Steps 10–15
4. Prompts and Completion — Steps 16–18
5. Elicitation — Steps 19–22
6. MCP Apps — Steps 23–26
7. Tasks — Steps 27–33

---

## Section 1 — Transport (STDIO)

File: `src/main/java/com/workshop/mcp/io/IOHandlerImpl.java`

Before Step 1, run
`./gradlew test --tests "com.workshop.mcp.io.IOHandlerImplTest"` — the transport
tests are red. After Step 3 they go green: that is the whole transport layer.

### STEP 1: the input reader loop

Say: the transport is dumb on purpose — a Scanner loop over `System.in` that
moves lines and knows nothing about MCP.

Replace the stub body of `startInputReader()` so the whole method reads:

```java
    @Override
    public void startInputReader() {
        if (running.get()) {
            return; // Already running
        }

        try (Scanner scanner = new Scanner(System.in)) {
            running.set(true);
            try {
                while (running.get()) {
                    if (!scanner.hasNextLine()) {
                        running.set(false);
                        break;
                    }
                    String line = scanner.nextLine();
                    logger.log("[API][RECEIVED]" + line);
                    publishLine(line);
                }
                logger.log("Input stream closed.");
            } catch (Exception e) {
                if (running.get()) { // Only log if we're still supposed to be running
                    logger.log("Error while reading next line from input reader: ", e);
                }
                throw e; // Re-throw to ensure outer catch handles it
            } finally {
                stopRunning();
            }
        } catch (Exception e) {
            logger.log("Fatal error in startInputReader: ", e);
            stopRunning(); // Ensure shutdown on any exception
        }
    }
```

### STEP 2: publishing lines to listeners

Say: the observer pattern in six lines — each listener call is isolated in its
own try-catch so one faulty listener never breaks the others.

Replace the stub body of `publishLine(String)` so the whole method reads:

```java
    private void publishLine(String line) {
        for (Consumer<String> listener : lineListeners) {
            try {
                listener.accept(line);
            } catch (Exception e) {
                logger.log("Error in line listener", e);
            }
        }
    }
```

### STEP 3: emitting JSON to stdout

Say: serialize, log `[API][SENT]`, println, explicit flush — MCP clients expect
real-time line-delimited responses. Stdout is protocol-only; all logging goes to
the PID-named log file.

Replace the stub body of `emit(Object)` so the whole method reads:

```java
    @Override
    public void emit(Object message) {
        String text = gson.toJson(message);
        logger.log("[API][SENT]: " + text);
        writer.println(text);
        writer.flush();
    }
```

Verify: `./gradlew test --tests "com.workshop.mcp.io.IOHandlerImplTest"` — green
now. Then `./gradlew clean build` to produce the JAR.

---

## Section 2 — Handshake and Routing

Files: `src/main/java/com/workshop/mcp/Runner.java`,
`src/main/java/com/workshop/mcp/spec/JsonRpcMessageDeserializer.java`,
`src/main/java/com/workshop/mcp/IORouter.java`,
`src/main/java/com/workshop/mcp/spec/NotificationCancelledParams.java` (new file)

### STEP 4: wire the server together

Say: transport + router + lifecycle, three lines. The router registers as a
line listener; the transport never knows what the lines mean.

In `Runner.java`, replace the stub body of `main` so the whole method reads:

```java
    public static void main(String[] args) {
        IOHandler io = new IOHandlerImpl();
        Server server = new Server(io,new IORouter(io), new CountDownLatch(1));
        server.start();
    }
```

### STEP 5: typed params out of raw JSON

Say: JSON-RPC params arrive as untyped maps; a Gson round-trip turns them into
the record types under `spec/`.

In `JsonRpcMessageDeserializer.java`, replace the three stub bodies so the
methods read:

```java
    public <T> T deserializeParams(JsonRpcRequest request, Class<T> paramsClass) {
        return gson.fromJson(gson.toJson(request.params()), paramsClass);
    }
```

```java
    public <T> T deserializeResult(JsonRpcResponse response, Class<T> resultClass) {
        return gson.fromJson(gson.toJson(response.result()), resultClass);
    }
```

```java
    public <T> T deserializeParams(JsonRpcNotification request, Class<T> paramsClass) {
        return gson.fromJson(gson.toJson(request.params()), paramsClass);
    }
```

### STEP 6: the INITIALIZE handshake

Say: the client declares its capabilities; we remember roots and sampling, then
answer with protocol version, our capabilities, and server info.
(This handler gets upgraded twice later — elicitation in Step 19, tasks in Step 29.)

In `IORouter.java`, paste the capability flags at the fields marker:

```java
    private boolean hasRoots = false;
    private boolean hasSampling = false;
```

Then paste the handler at the `STEP 6` marker inside the request switch:

```java
            case INITIALIZE -> {
                InitializeParams initializeParams = deserializer.deserializeParams(message, InitializeParams.class);
                ClientCapabilities clientCapabilities = initializeParams.capabilities();
                if (clientCapabilities.roots() != null) {
                    hasRoots = true;
                }
                if (clientCapabilities.sampling() != null) {
                    hasSampling = true;
                }
                InitializeResultBuilder builder = InitializeResultBuilder
                        .builder()
                        .withProtocolVersion(initializeParams.protocolVersion())
                        .withDefaultCapabilities()
                        .withDefaultServerInfo();
                success(message.id(), builder.build());
            }
```

Verify: `./gradlew build`, then connect the Inspector (`inspector/run.sh`) — the
handshake completes.

### STEP 7: PING and a taste of sampling

Say: ping is the simplest request — an empty object back. If the client declared
sampling we push a `sampling/createMessage` request at it, server-to-client.

Paste the constant at the `STEP 7` marker in the fields area:

```java
    private static final Long SAMPLE_REQUEST_ID = -2000L;
```

Paste the handler at the `STEP 7` marker inside the request switch:

```java
            case PING -> {
                success(message.id(), new Object());
                // If the client supports sampling, we can send a minimal sampling message
                // to demonstrate the sampling feature.
                // This is just a simulation for the sake of the example.
                if (hasSampling) {
                    sendSamplingMessage("Figure out what the single best word to search for in a Java project is.");
                }
            }
```

Paste the method at the `STEP 7` marker near the bottom of the class:

```java
    /**
     * Creates a minimal sampling message for testing purposes.
     * This method is used to demonstrate how to create a sampling message
     * with a single user message.
     */
    private void sendSamplingMessage(String message) {
        MessageContent messageContent = new MessageContent(message, "text");
        CreateSamplingMessage textMessage = new CreateSamplingMessageBuilder()
                .addMessage(new Message(Role.USER.getValue(), messageContent))
                .systemPrompt("You are a brilliant Java developer.")
                .build();
        JsonRpcRequest minimalMessageRequest = new JsonRpcRequest(JSON_RPC_VERSION, SAMPLE_REQUEST_ID,
                                                                  UniqueKeys.SAMPLING_CREATE_MESSAGE.getValue(),
                                                                  textMessage);
        io.emit(minimalMessageRequest);
    }
```

### STEP 8: roots — the server asks back

Say: requests flow both ways. Once the client says it has roots, we ask for the
list after `notifications/initialized` and whenever the list changes; the answer
comes back as a *response*, matched by our fixed request id.

Paste the constants at the `STEP 8` marker in the fields area:

```java
    private static final Long ROOTS_REQUEST_ID = -1000L;
    private static final JsonRpcRequest rootsRequest = new JsonRpcRequest(JSON_RPC_VERSION, ROOTS_REQUEST_ID,
                                                                          "roots" + "/list", null);
```

Paste the two cases at the `STEP 8` marker inside the notification switch:

```java
            case NOTIFICATIONS_INITIALIZED -> {
                // if server has roots, request the roots list.
                if (hasRoots) {
                    io.emit(rootsRequest);
                }
            }
            case NOTIFICATIONS_ROOTS_LIST_CHANGED -> {
                io.emit(rootsRequest);
            }
```

Paste the roots branch at the `STEP 8` marker inside `process(JsonRpcResponse)`:

```java
        if (ROOTS_REQUEST_ID.equals(message.id())) {
            RootsResponse rootsResponse = deserializer.deserializeResult(message, RootsResponse.class);
            roots.clear();
            for (Root root : rootsResponse.roots()) {
                roots.add(root.uri());
            }
            logger.log("[API][RECEIVED] roots/list response — populated " + roots.size() + " root(s)");
        }
```

### STEP 9: cancellation notifications

Say: notifications carry params too — model them as a record and deserialize the
same way.

Copy the prepared file `lessons/presentation-3hr/files/NotificationCancelledParams.java` into the
project as `src/main/java/com/workshop/mcp/spec/NotificationCancelledParams.java`:

```bash
cp lessons/presentation-3hr/files/NotificationCancelledParams.java src/main/java/com/workshop/mcp/spec/NotificationCancelledParams.java
```

Paste the case at the `STEP 9` marker inside the notification switch:

```java
            case NOTIFICATION_CANCELLED -> {
                NotificationCancelledParams params = deserializer.deserializeParams(message,
                                                                                    NotificationCancelledParams.class);
                logger.log("[API][RECEIVED] notifications/cancelled reason=" + params.reason());
            }
```

Verify: rebuild, reconnect the Inspector, watch the log file for the handshake,
ping, and roots traffic.

---

## Section 3 — Resources and Tools

Files: `src/main/java/com/workshop/mcp/resources/JavadocResources.java` (new file),
`src/main/java/com/workshop/mcp/tools/KeyWordSearch.java` (new file),
`src/main/java/com/workshop/mcp/IORouter.java`

### STEP 10: the resources backend

Say: resources are read-only context the server offers. Ours are the javadoc
HTML files bundled in the JAR, listed at build time in `html-files.txt`, with
descriptions scraped out of the HTML by JSoup.

Copy the prepared file `lessons/presentation-3hr/files/JavadocResources.java` into the
project as `src/main/java/com/workshop/mcp/resources/JavadocResources.java`:

```bash
mkdir -p src/main/java/com/workshop/mcp/resources
cp lessons/presentation-3hr/files/JavadocResources.java src/main/java/com/workshop/mcp/resources/JavadocResources.java
```

Then restore the import at the `STEP 10` marker at the top of `IORouter.java`:

```java
import com.workshop.mcp.resources.JavadocResources;
```

### STEP 11: resources/list

Say: pagination is part of the contract — `nextCursor` tells the client there is
more. (This handler gets upgraded in Step 25 when we add the MCP App resource.)

Paste at the `STEP 11` marker inside the request switch:

```java
            case RESOURCES_LIST -> {
                ResourcesListResultBuilder builder = ResourcesListResultBuilder
                        .builder()
                        .withResources(JavadocResources.loadAllHtmlResourcesFromFolder("javadoc/com/workshop/mcp/spec"))
                        .withNextCursor("pageNext");
                success(message.id(), builder.build());
            }
```

### STEP 12: resources/read

Say: validate the URI, read from the classpath, and on failure answer with an
error-shaped result instead of crashing the session.
(Upgraded in Step 26 for the `ui://` app resource.)

Paste at the `STEP 12` marker inside the request switch:

```java
            case RESOURCES_READ -> {
                ReadResourceParam param = deserializer.deserializeParams(message, ReadResourceParam.class);
                String resourceUri = param.uri();
                ReadResourceResultBuilder builder = ReadResourceResultBuilder.builder();
                if (resourceUri != null && !resourceUri.isEmpty()) {
                    try {
                        String content = JavadocResources.readResourceContent(resourceUri);
                        builder.addTextContent(resourceUri, DEFAULT_MIME_TYPE, content);
                    } catch (Exception e) {
                        logger.log("[API][SENT] resources/read — error reading resource: " + resourceUri);
                        builder.addTextContent(resourceUri, DEFAULT_MIME_TYPE, e.getMessage()).asError();
                    }
                } else {
                    builder
                            .addTextContent("", DEFAULT_MIME_TYPE, "Resource URI is null or empty, returning error.")
                            .asError();
                }
                success(message.id(), builder.build());
            }
```

Verify: rebuild, list and read a resource in the Inspector.

### STEP 13: the keyword search tool

Say: a tool is a name, a description, a JSON schema, and a `call` method. This
one walks the client's root directories counting keyword hits, skipping binary
files, and closing every reader so a big tree cannot exhaust file descriptors.

Copy the prepared file `lessons/presentation-3hr/files/KeyWordSearch.java` into the
project as `src/main/java/com/workshop/mcp/tools/KeyWordSearch.java`:

```bash
cp lessons/presentation-3hr/files/KeyWordSearch.java src/main/java/com/workshop/mcp/tools/KeyWordSearch.java
```

Then restore the import at the `STEP 13` marker at the top of `IORouter.java`:

```java
import com.workshop.mcp.tools.KeyWordSearch;
```

### STEP 14: tools/list

Say: advertise the tool — name, description, schema. The client decides when to
call it. (Upgraded in Step 24 for MCP Apps and Step 30 for tasks.)

Paste at the `STEP 14` marker inside the request switch:

```java
            case TOOLS_LIST -> {
                KeyWordSearch keyWordSearch = new KeyWordSearch(this.roots);
                ToolsListResultBuilder builder = ToolsListResultBuilder
                        .builder()
                        .addTool(keyWordSearch.name(), keyWordSearch.description(), keyWordSearch.schema());
                success(message.id(), builder.build());
            }
```

### STEP 15: tools/call

Say: look the tool up by name, run it, and report unknown tools as an
error-shaped result — never a protocol error. (Upgraded in Step 21 for
elicitation.)

Paste at the `STEP 15` marker inside the request switch:

```java
            case TOOLS_CALL -> {
                KeyWordSearch keyWordSearch = new KeyWordSearch(this.roots);
                ToolCallParams toolCallParams = deserializer.deserializeParams(message, ToolCallParams.class);
                if (keyWordSearch.name().equalsIgnoreCase(toolCallParams.name())) {
                    success(message.id(), keyWordSearch.call(toolCallParams));
                } else {
                    success(message.id(), ToolCallResultBuilder
                            .builder()
                            .addTextContent("Tool not found: " + toolCallParams.name())
                            .asError()
                            .build());
                }
            }
```

Verify: rebuild, call `key_word_search` from the Inspector with a keyword like
`java` (make sure a root directory is set).

---

## Section 4 — Prompts and Completion

File: `src/main/java/com/workshop/mcp/IORouter.java`

### STEP 16: prompts/list

Say: prompts are reusable, parameterized message templates the *user* picks —
unlike tools, which the model picks.

Paste at the `STEP 16` marker inside the request switch:

```java
            case PROMPTS_LIST -> {
                KeyWordSearch keyWordSearch = new KeyWordSearch(this.roots);
                PromptsListResultBuilder builder = PromptsListResultBuilder
                        .builder()
                        .withPrompt("search_keyword",
                                    "Creates a prompt, to search for a word using the " + keyWordSearch.name() +
                                            "tool.")
                        .withPromptArgument("keyword", "The word to search for", true)
                        .withNextCursor("nextPage");
                success(message.id(), builder.build());
            }
```

### STEP 17: prompts/get

Say: fold the caller's arguments into the message template and hand back a
ready-to-send conversation.

Paste at the `STEP 17` marker inside the request switch:

```java
            case PROMPTS_GET -> {
                // For the sake of the lesson we are dealing with a single prompt if we had more than one we would
                // need to look it up
                PromptsGetParams params = deserializer.deserializeParams(message, PromptsGetParams.class);
                // this would be the key to look up our prompt
                Object name = params.name();

                PromptsGetResultBuilder builder = PromptsGetResultBuilder
                        .builder()
                        .withDescription("keyword")
                        .addTextMessage("user", KEY_WORD_MESSAGE, params.arguments());
                success(message.id(), builder.build());
            }
```

### STEP 18: completion/complete

Say: argument autocompletion — the Inspector asks as the user types.

Paste at the `STEP 18` marker inside the request switch:

```java
            case COMPLETION_COMPLETE -> {
                CompletionCompleteParams params = deserializer.deserializeParams(message,
                                                                                 CompletionCompleteParams.class);
                if ("keyword" .equalsIgnoreCase(params.argument().name())) {
                    // Simulating a keyword search completion
                    CompletionCompleteBuilder response = CompletionCompleteBuilder.withValue("java");
                    response.value("the").value("and").total(3).hasMore(true);
                    success(message.id(), response.build());
                }
            }
```

Verify: rebuild, get the `search_keyword` prompt in the Inspector and watch the
completions appear on the keyword argument.

---

## Section 5 — Elicitation

File: `src/main/java/com/workshop/mcp/IORouter.java`

### STEP 19: declare the elicitation capability

Say: elicitation lets the server pause and ask the human a structured question.
It is an experimental extension, declared under `capabilities.experimental`.

Paste the flag at the `STEP 19` marker in the fields area:

```java
    private boolean hasElicitation = false;
```

Then **replace the INITIALIZE handler from Step 6** with this version (adds the
elicitation check and the experimental declaration):

```java
            case INITIALIZE -> {
                InitializeParams initializeParams = deserializer.deserializeParams(message, InitializeParams.class);
                ClientCapabilities clientCapabilities = initializeParams.capabilities();
                if (clientCapabilities.roots() != null) {
                    hasRoots = true;
                }
                if (clientCapabilities.sampling() != null) {
                    hasSampling = true;
                }
                if(clientCapabilities.elicitation() != null){
                    hasElicitation = true;
                }
                InitializeResultBuilder builder = InitializeResultBuilder
                        .builder()
                        .withProtocolVersion(initializeParams.protocolVersion())
                        .withExperimentalCapability("io.modelcontextprotocol/elicitation", new Object())
                        .withDefaultCapabilities()
                        .withDefaultServerInfo();
                success(message.id(), builder.build());
            }
```

### STEP 20: the elicitation request

Say: like roots, this is a server-to-client *request* with a reserved id. The
form itself is a small JSON schema built by `ElicitationBuilder`.

Paste at the `STEP 20` marker in the fields area:

```java
    private static final Long ELICITATION_REQUEST_ID = -4000L;
```

```java
    private Long pendingToolsCallRequestId = null;
    private ToolCallParams pendingToolCallParams = null;
```

Paste the method at the `STEP 20` marker near the bottom of the class:

```java
    private void sendElicitationMessage() {
        ElicitationCreateParams params = ElicitationBuilder.buildSearchDirectoryElicitation();
        JsonRpcRequest elicitationRequest = new JsonRpcRequest(JSON_RPC_VERSION, ELICITATION_REQUEST_ID,
                                                               UniqueKeys.ELICITATION_CREATE_MESSAGE.getValue(),
                                                               params);
        io.emit(elicitationRequest);
    }
```

### STEP 21: defer tools/call until the user answers

Say: no roots plus elicitation support means we do not fail the call — we park
it, ask the user for a directory, and resume when the answer arrives.

**Replace the TOOLS_CALL handler from Step 15** with:

```java
            case TOOLS_CALL -> {
                KeyWordSearch keyWordSearch = new KeyWordSearch(this.roots);
                ToolCallParams toolCallParams = deserializer.deserializeParams(message, ToolCallParams.class);
                if (!keyWordSearch.name().equalsIgnoreCase(toolCallParams.name())) {
                    success(message.id(), ToolCallResultBuilder
                            .builder()
                            .addTextContent("Tool not found: " + toolCallParams.name())
                            .asError()
                            .build());
                } else if (roots.isEmpty() && hasElicitation && pendingToolsCallRequestId == null) {
                    // No search directory available — defer the response, ask the user
                    // for one via elicitation, and resume the call once the directory
                    // arrives in process(JsonRpcResponse).
                    pendingToolsCallRequestId = message.id();
                    pendingToolCallParams = toolCallParams;
                    logger.log("[API][RECEIVED] tools/call deferred — roots empty, eliciting search directory"
                               + " (toolsCallId=" + message.id() + ")");
                    sendElicitationMessage();
                } else if (roots.isEmpty() && hasElicitation) {
                    // Another tools/call is already mid-elicitation — refuse this one
                    // rather than queueing, to keep the workshop flow easy to follow.
                    logger.log("[API][RECEIVED] tools/call rejected — another elicitation is already in flight"
                               + " (pendingToolsCallId=" + pendingToolsCallRequestId + ")");
                    success(message.id(), ToolCallResultBuilder
                            .builder()
                            .addTextContent("Another keyword search is currently waiting on the directory " +
                                            "elicitation form — finish that one first.")
                            .asError()
                            .build());
                } else {
                    executeKeyWordSearchCall(message.id(), toolCallParams);
                }
            }
```

Then paste the execution helper at the `STEP 21` marker near the bottom of the
class (it gets its task-aware upgrade in Step 31):

```java
    private void executeKeyWordSearchCall(Long requestId, ToolCallParams params) {
        KeyWordSearch keyWordSearch = new KeyWordSearch(this.roots);
        success(requestId, keyWordSearch.call(params));
    }
```

### STEP 22: handle the user's answer

Say: the answer comes back as a response with our reserved id. On accept we pull
the directory out of the form content, add it to roots, and resume the parked
call.

Paste this branch at the `STEP 22` marker inside `process(JsonRpcResponse)` —
it sits right after the closing brace of the roots `if` block from Step 8 and
chains as an `else if`:

```java
        else if (ELICITATION_REQUEST_ID.equals(message.id())) {
            ElicitationCreateResult result = deserializer.deserializeResult(message, ElicitationCreateResult.class);
            logger.log("[API][RECEIVED] elicitation/create response — action=" + result.action());
            if ("accept".equalsIgnoreCase(result.action()) && result.content() instanceof Map<?, ?> contentMap) {
                Object directory = contentMap.get("directory");
                if (directory instanceof String s && !s.isBlank()) {
                    roots.add(s);
                    logger.log("[API][RECEIVED] elicitation/create — added directory to roots: " + s);
                } else {
                    logger.log("[API][RECEIVED] elicitation/create — accepted but no usable directory field in content: " + contentMap);
                }
            }
            // Resume any tools/call that was waiting on the user's directory choice.
            if (pendingToolsCallRequestId != null) {
                Long resumeId = pendingToolsCallRequestId;
                ToolCallParams resumeParams = pendingToolCallParams;
                pendingToolsCallRequestId = null;
                pendingToolCallParams = null;
                logger.log("[API][RECEIVED] tools/call resumed after elicitation — toolsCallId=" + resumeId
                           + " rootsAvailable=" + !roots.isEmpty());
                executeKeyWordSearchCall(resumeId, resumeParams);
            }
        }
```

Also paste this case at the `STEP 22` marker inside the request switch (the
client-initiated direction):

```java
            case ELICITATION_CREATE_MESSAGE -> {
                // Handle elicitation method calls from client (client-initiated direction)
                logger.log("[API][RECEIVED] elicitation/create from client: " + message);
                // Parse the elicitation response and handle it appropriately
                // For now, just acknowledge the elicitation request
                success(message.id(), new Object());
            }
```

Verify: rebuild, clear roots in the Inspector, call the tool — the directory
form pops up, and accepting it resumes the search.

---

## Section 6 — MCP Apps

File: `src/main/java/com/workshop/mcp/IORouter.java`

### STEP 23: declare the apps capability

Say: MCP Apps let a tool ship an interactive HTML dashboard the host renders.
Another experimental extension.

In the INITIALIZE handler, add this line directly after the elicitation
`withExperimentalCapability` line:

```java
                        .withExperimentalCapability("io.modelcontextprotocol/apps", new Object())
```

### STEP 24: an app-aware tools/list

Say: the tool now carries `_meta.ui.resourceUri` pointing at a `ui://` resource.
We also clear roots on every list so the elicitation flow can be re-demoed.
(Gets its final task-aware form in Step 30.)

**Replace the TOOLS_LIST handler from Step 14** with:

```java
            case TOOLS_LIST -> {
                this.roots.clear();
                KeyWordSearch keyWordSearch = new KeyWordSearch(this.roots);
                AppTool appTool = AppToolBuilder.builder()
                        .withName(keyWordSearch.name())
                        .withDescription(keyWordSearch.description())
                        .withInputSchema(keyWordSearch.schema())
                        .withResourceUri("ui://keyword-search/mcp-app.html")
                        .build();
                success(message.id(), new AppToolsListResult(List.of(appTool)));
            }
```

### STEP 25: advertise the app resource

Say: the dashboard is just another resource, with the MCP-app MIME type.

**Replace the RESOURCES_LIST handler from Step 11** with:

```java
            case RESOURCES_LIST -> {
                ResourcesListResultBuilder builder = ResourcesListResultBuilder
                        .builder()
                        .withResources(JavadocResources.loadAllHtmlResourcesFromFolder("javadoc/com/workshop/mcp/spec"))
                        .addResource(ResourceBuilder.builder()
                                .withUri("ui://keyword-search/mcp-app.html")
                                .withName("Keyword Search App")
                                .withDescription("Interactive keyword search results dashboard")
                                .withMimeType(Resource.MIME_TYPE_UI_APP)
                                .build())
                        .withNextCursor("pageNext");
                success(message.id(), builder.build());
            }
```

### STEP 26: serve the app HTML

Say: reads over `ui://` get the bundled HTML; everything else stays classpath
javadoc.

**Replace the RESOURCES_READ handler from Step 12** with:

```java
            case RESOURCES_READ -> {
                ReadResourceParam param = deserializer.deserializeParams(message, ReadResourceParam.class);
                String resourceUri = param.uri();
                ReadResourceResultBuilder builder = ReadResourceResultBuilder.builder();
                if ("ui://keyword-search/mcp-app.html".equals(resourceUri)) {
                    try {
                        String html = JavadocResources.readResourceContent("lesson/mcp-app.html");
                        builder.addTextContent(resourceUri, Resource.MIME_TYPE_UI_APP, html);
                    } catch (Exception e) {
                        logger.log("[API][SENT] resources/read — error reading UI app resource: " + resourceUri);
                        builder.addTextContent(resourceUri, Resource.MIME_TYPE_UI_APP, e.getMessage()).asError();
                    }
                } else if (resourceUri != null && !resourceUri.isEmpty()) {
                    try {
                        String content = JavadocResources.readResourceContent(resourceUri);
                        builder.addTextContent(resourceUri, DEFAULT_MIME_TYPE, content);
                    } catch (Exception e) {
                        logger.log("[API][SENT] resources/read — error reading resource: " + resourceUri);
                        builder.addTextContent(resourceUri, DEFAULT_MIME_TYPE, e.getMessage()).asError();
                    }
                } else {
                    builder
                            .addTextContent("", DEFAULT_MIME_TYPE, "Resource URI is null or empty, returning error.")
                            .asError();
                }
                success(message.id(), builder.build());
            }
```

Verify: rebuild, call the tool in the Inspector — the results dashboard renders
in the app pane.

---

## Section 7 — Tasks

Files: `src/main/java/com/workshop/mcp/tasks/TaskStore.java` (new file),
`src/main/java/com/workshop/mcp/IORouter.java`

### STEP 27: the task store

Say: tasks make long-running calls pollable. This store is plain Java — a
ConcurrentHashMap of entries, per-entry monitors, status listeners, and one-shot
terminal callbacks so `tasks/result` can block without blocking the router.

Copy the prepared file `lessons/presentation-3hr/files/TaskStore.java` into the
project as `src/main/java/com/workshop/mcp/tasks/TaskStore.java`:

```bash
mkdir -p src/main/java/com/workshop/mcp/tasks
cp lessons/presentation-3hr/files/TaskStore.java src/main/java/com/workshop/mcp/tasks/TaskStore.java
```

Then restore the import at the `STEP 27` marker at the top of `IORouter.java`:

```java
import com.workshop.mcp.tasks.TaskStore;
```

### STEP 28: task plumbing in the router

Say: one store, one flag, one status listener that turns every transition into a
`notifications/tasks/status`, and the `_meta` bag that ties responses to their
parent task.

Paste the fields at the `STEP 28` marker in the fields area:

```java
    private final TaskStore taskStore = new TaskStore();
```

```java
    private boolean hasTasks = false;
```

In the constructor, paste at the `STEP 28` marker:

```java
        this.taskStore.onStatusChange(this::sendTaskStatusNotification);
```

Paste the two helpers at their `STEP 28` markers near the bottom of the class
(`relatedTaskMeta` sits above `runToolAsTask`; `sendTaskStatusNotification` is
the last method in the class):

```java
    /**
     * Builds the {@code _meta} bag that ties a response to its parent task per
     * spec § "Related Task Metadata".
     */
    private static Map<String, Object> relatedTaskMeta(String taskId) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("io.modelcontextprotocol/related-task", Map.of("taskId", taskId));
        return meta;
    }
```

```java
    /**
     * Emit a {@code notifications/tasks/status} for the given task snapshot.
     * Called for every status transition by the {@link TaskStore} listener
     * registered in the constructor.
     */
    private void sendTaskStatusNotification(Task task) {
        logger.log("[API][SENT] notifications/tasks/status — taskId=" + task.taskId() + " status=" + task.status());
        JsonRpcNotification notification = new JsonRpcNotification(
                JSON_RPC_VERSION,
                UniqueKeys.NOTIFICATIONS_TASKS_STATUS.getValue(),
                task);
        io.emit(notification);
    }
```

### STEP 29: declare the tasks capability

Say: the server advertises `tasks.requests.tools.call`, and we remember whether
the client can drive tasks. This is the final form of INITIALIZE.

**Replace the INITIALIZE handler (last touched in Steps 19 and 23)** with:

```java
            case INITIALIZE -> {
                InitializeParams initializeParams = deserializer.deserializeParams(message, InitializeParams.class);
                ClientCapabilities clientCapabilities = initializeParams.capabilities();
                if (clientCapabilities.roots() != null) {
                    hasRoots = true;
                }
                if (clientCapabilities.sampling() != null) {
                    hasSampling = true;
                }
                if(clientCapabilities.elicitation() != null){
                    hasElicitation = true;
                }
                if (clientCapabilities.tasks() != null) {
                    hasTasks = true;
                    logger.log("[API][RECEIVED] initialize — client declared tasks capability, task-augmented requests enabled");
                }
                InitializeResultBuilder builder = InitializeResultBuilder
                        .builder()
                        .withProtocolVersion(initializeParams.protocolVersion())
                        .withExperimentalCapability("io.modelcontextprotocol/elicitation", new Object())
                        .withExperimentalCapability("io.modelcontextprotocol/apps", new Object())
                        .withTasksCapability(InitializeResultBuilder.defaultTasksCapability())
                        .withDefaultCapabilities()
                        .withDefaultServerInfo();
                success(message.id(), builder.build());
            }
```

### STEP 30: the tool opts in to tasks

Say: `execution.taskSupport = "optional"` — the caller decides per call whether
to run synchronously or as a task. This is the final form of TOOLS_LIST.

**Replace the TOOLS_LIST handler from Step 24** with:

```java
            case TOOLS_LIST -> {
                this.roots.clear();
                KeyWordSearch keyWordSearch = new KeyWordSearch(this.roots);
                AppTool appTool = AppToolBuilder.builder()
                        .withName(keyWordSearch.name())
                        .withDescription(keyWordSearch.description())
                        .withInputSchema(keyWordSearch.schema())
                        .withResourceUri("ui://keyword-search/mcp-app.html")
                        .withExecution(ToolExecution.optional())
                        .build();
                success(message.id(), new AppToolsListResult(List.of(appTool)));
            }
```

### STEP 31: run the tool as a task

Say: when the call carries `params.task`, we answer immediately with a
`CreateTaskResult` and finish the real work on a background thread. The 4-second
sleep exists so the working-to-completed transition is observable.

**Replace the executeKeyWordSearchCall helper from Step 21** with:

```java
    /**
     * Runs the keyword search either synchronously or as a task-augmented call
     * depending on whether the requestor included {@code params.task}. Shared
     * by the immediate-dispatch path in {@code TOOLS_CALL} and the deferred
     * resumption path after an elicitation response arrives.
     */
    private void executeKeyWordSearchCall(Long requestId, ToolCallParams params) {
        KeyWordSearch keyWordSearch = new KeyWordSearch(this.roots);
        if (params.task() != null && hasTasks) {
            Task task = taskStore.create(params.task().ttl());
            logger.log("[API][SENT] tools/call augmented with task — created taskId=" + task.taskId()
                       + " status=" + task.status() + " ttl=" + task.ttl());
            success(requestId, new CreateTaskResult(task, relatedTaskMeta(task.taskId())));
            runToolAsTask(task.taskId(), params);
        } else {
            success(requestId, keyWordSearch.call(params));
        }
    }
```

Then paste the background runner at the `STEP 31` marker near the bottom of the
class:

```java
    /**
     * Spawn a background thread that runs the actual tool work and records
     * its outcome in the {@link TaskStore}. A small sleep is included so the
     * "working → completed" transition is observable in the inspector.
     */
    private void runToolAsTask(String taskId, ToolCallParams params) {
        new Thread(() -> {
            logger.log("[TASK " + taskId + "] background tool execution started for tool=" + params.name());
            try {
                Thread.sleep(4000L);
                KeyWordSearch tool = new KeyWordSearch(this.roots);
                ToolCallResult result = tool.call(params);
                taskStore.complete(taskId, result);
                logger.log("[TASK " + taskId + "] tool completed, transitioning to completed");
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.log("[TASK " + taskId + "] interrupted: " + ie.getMessage());
                taskStore.fail(taskId, "Interrupted: " + ie.getMessage());
            } catch (Exception e) {
                logger.log("[TASK " + taskId + "] tool execution failed: " + e.getMessage());
                taskStore.fail(taskId, "Tool execution failed: " + e.getMessage());
            }
        }, "task-" + taskId).start();
    }
```

### STEP 32: polling and delivery — tasks/get, tasks/result

Say: get is a non-blocking snapshot. `tasks/result` blocks until the task is
terminal — implemented without blocking the router thread via the store's
one-shot terminal callbacks; the payload is wrapped with the related-task
`_meta`.

Paste the two cases at the `STEP 32` marker inside the request switch:

```java
            case TASKS_GET -> {
                TasksGetParams params = deserializer.deserializeParams(message, TasksGetParams.class);
                Task task = taskStore.get(params.taskId());
                if (task == null) {
                    logger.log("[API][SENT] tasks/get — unknown taskId=" + params.taskId() + " (returning -32602)");
                    error(message.id(), ErrorCodes.INVALID_PARAMS, "Failed to retrieve task: Task not found");
                } else {
                    logger.log("[API][SENT] tasks/get taskId=" + task.taskId() + " status=" + task.status());
                    success(message.id(), task);
                }
            }
            case TASKS_RESULT -> {
                TasksResultParams params = deserializer.deserializeParams(message, TasksResultParams.class);
                Long requestId = message.id();
                boolean terminalNow = taskStore.isTerminal(params.taskId());
                logger.log("[API][RECEIVED] tasks/result taskId=" + params.taskId()
                           + (terminalNow ? " — terminal, replying immediately"
                                          : " — awaiting terminal status before reply"));
                taskStore.awaitTerminal(params.taskId(), terminal -> emitTaskResult(requestId, params.taskId(), terminal));
            }
```

Then paste the delivery method at the `STEP 32` marker near the bottom of the
class:

```java
    /**
     * Emit the {@code tasks/result} response once the task is terminal.
     * Returns the underlying request's payload wrapped with the required
     * {@code io.modelcontextprotocol/related-task} metadata.
     */
    private void emitTaskResult(Long requestId, String taskId, Task terminal) {
        if (terminal == null) {
            logger.log("[API][SENT] tasks/result — taskId=" + taskId + " not found at delivery time (returning -32602)");
            error(requestId, ErrorCodes.INVALID_PARAMS, "Failed to retrieve task: Task not found");
            return;
        }
        TaskStatus status = TaskStatus.fromValue(terminal.status());
        logger.log("[API][SENT] tasks/result — delivering for taskId=" + taskId + " terminal=" + terminal.status());
        if (status == TaskStatus.CANCELLED) {
            error(requestId, ErrorCodes.INVALID_PARAMS,
                  "Cannot retrieve result: task was cancelled");
            return;
        }
        if (status == TaskStatus.FAILED) {
            error(requestId, ErrorCodes.INTERNAL_ERROR,
                  terminal.statusMessage() != null
                          ? terminal.statusMessage()
                          : "Task failed");
            return;
        }
        Object stored = taskStore.resultFor(taskId);
        if (stored instanceof ToolCallResult tcr) {
            // Attach the related-task meta to the tool call result wrapper. We
            // construct a plain map so the workshop's records stay unchanged
            // and Gson still serializes the expected shape.
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("content", tcr.content());
            envelope.put("isError", tcr.isError());
            envelope.put("_meta", relatedTaskMeta(taskId));
            success(requestId, envelope);
        } else if (stored != null) {
            success(requestId, stored);
        } else {
            logger.log("[API][SENT] tasks/result — taskId=" + taskId + " terminal but no stored payload (returning -32603)");
            error(requestId, ErrorCodes.INTERNAL_ERROR, "No stored result for task");
        }
    }
```

### STEP 33: management — tasks/list, tasks/cancel

Say: list is everything in the store, and cancel is only legal before the task
reaches a terminal status — otherwise `-32602`.

Paste the two cases at the `STEP 33` marker inside the request switch:

```java
            case TASKS_LIST -> {
                List<Task> all = taskStore.list();
                logger.log("[API][SENT] tasks/list — returning " + all.size() + " task(s)");
                success(message.id(), new TasksListResult(all, null));
            }
            case TASKS_CANCEL -> {
                TasksCancelParams params = deserializer.deserializeParams(message, TasksCancelParams.class);
                Task before = taskStore.get(params.taskId());
                if (before == null) {
                    logger.log("[API][SENT] tasks/cancel — unknown taskId=" + params.taskId() + " (returning -32602)");
                    error(message.id(), ErrorCodes.INVALID_PARAMS, "Failed to cancel task: Task not found");
                } else {
                    Task cancelled = taskStore.cancel(params.taskId());
                    if (cancelled == null) {
                        logger.log("[API][SENT] tasks/cancel rejected — taskId=" + params.taskId()
                                   + " already terminal (" + before.status() + ", returning -32602)");
                        error(message.id(), ErrorCodes.INVALID_PARAMS,
                              "Cannot cancel task: already in terminal status '" + before.status() + "'");
                    } else {
                        logger.log("[API][SENT] tasks/cancel — taskId=" + cancelled.taskId() + " cancelled");
                        success(message.id(), cancelled);
                    }
                }
            }
```

Verify: rebuild, call the tool task-augmented in the Inspector, poll with
`tasks/get`, watch the status notifications, fetch the payload with
`tasks/result`, then `tasks/list` to enumerate, and cancel a fresh one with
`tasks/cancel`.

Done — the codebase now matches the finished server.
