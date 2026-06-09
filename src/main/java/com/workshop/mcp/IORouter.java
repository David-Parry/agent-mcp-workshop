package com.workshop.mcp;

import com.workshop.mcp.io.IOHandler;
import com.workshop.mcp.io.LogFile;
import com.workshop.mcp.io.LogFileWriter;
import com.workshop.mcp.resources.JavadocResources;
import com.workshop.mcp.spec.*;
import com.workshop.mcp.spec.builders.*;
import com.workshop.mcp.tasks.TaskStore;

import com.workshop.mcp.tools.KeyWordSearch;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.workshop.mcp.spec.Message.KEY_WORD_MESSAGE;
import static com.workshop.mcp.spec.Resource.DEFAULT_MIME_TYPE;
import static com.workshop.mcp.spec.builders.ElicitationBuilder.buildSearchDirectoryElicitation;

public class IORouter implements Router {
    private static final LogFile logger = LogFileWriter.getInstance();
    private static final String JSON_RPC_VERSION = "2.0";
    private static final Long ROOTS_REQUEST_ID = -1000L;
    private static final Long SAMPLE_REQUEST_ID = -2000L;
    private static final Long ELICITATION_REQUEST_ID = -4000L;
    private static final JsonRpcRequest rootsRequest = new JsonRpcRequest(JSON_RPC_VERSION, ROOTS_REQUEST_ID,
                                                                          "roots" + "/list", null);
    private final IOHandler io;
    private final Set<String> roots = new HashSet<>();
    private final JsonRpcMessageDeserializer deserializer = new JsonRpcMessageDeserializer();
    private final TaskStore taskStore = new TaskStore();
    private boolean hasRoots = false;
    private boolean hasSampling = false;
    private boolean hasElicitation = false;
    private boolean hasTasks = false;
    private Long pendingToolsCallRequestId = null;
    private ToolCallParams pendingToolCallParams = null;

    public IORouter(IOHandler io) {
        this.io = io;
        this.taskStore.onStatusChange(this::sendTaskStatusNotification);
    }

    public void route(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        Object object = deserializer.deserialize(message);
        switch (object) {
            case JsonRpcRequest request -> process(request);
            case JsonRpcNotification notification -> process(notification);
            case JsonRpcResponse successResponse -> process(successResponse);
            case JsonRpcErrorResponse errorResponse -> process(errorResponse);
            default -> logger.log("[API][RECEIVED] unknown message type: " + object);
        }
    }

    private void success(Long id, Object message) {
        JsonRpcResponse response = new JsonRpcResponse(JSON_RPC_VERSION, id, message);
        io.emit(response);
    }

    private void error(Long id, int code, String message) {
        JsonRpcErrorResponse response = new JsonRpcErrorResponse(
                JSON_RPC_VERSION, id, new JsonRpcError(code, message, null));
        io.emit(response);
    }

    private void process(JsonRpcRequest message) {
        UniqueKeys uniqueKey = UniqueKeys.fromValue(message.method());
        switch (uniqueKey) {
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
            case PING -> {
                success(message.id(), new Object());
                // If the client supports sampling, we can send a minimal sampling message
                // to demonstrate the sampling feature.
                // This is just a simulation for the sake of the example.
                if (hasSampling) {
                    sendSamplingMessage("Figure out what the single best word to search for in a Java project is.");
                }
            }
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
            case ELICITATION_CREATE_MESSAGE -> {
                // Handle elicitation method calls from client (client-initiated direction)
                logger.log("[API][RECEIVED] elicitation/create from client: " + message);
                // Parse the elicitation response and handle it appropriately
                // For now, just acknowledge the elicitation request
                success(message.id(), new Object());
            }
            default -> logger.log("[API][RECEIVED] unhandled RpcRequest method: " + uniqueKey + " for message: " + message);
        }
    }

    private void process(JsonRpcNotification message) {
        UniqueKeys uniqueKey = UniqueKeys.fromValue(message.method());
        switch (uniqueKey) {
            case NOTIFICATIONS_INITIALIZED -> {
                // if server has roots, request the roots list.
                if (hasRoots) {
                    io.emit(rootsRequest);
                }
            }
            case NOTIFICATIONS_ROOTS_LIST_CHANGED -> {
                io.emit(rootsRequest);
            }
            case NOTIFICATION_CANCELLED -> {
                NotificationCancelledParams params = deserializer.deserializeParams(message,
                                                                                    NotificationCancelledParams.class);
                logger.log("[API][RECEIVED] notifications/cancelled reason=" + params.reason());
            }
            default -> logger.log("[API][RECEIVED] unhandled notification method: " + uniqueKey + " for message: " + message);
        }
    }

    private void process(JsonRpcResponse message) {
        if (ROOTS_REQUEST_ID.equals(message.id())) {
            RootsResponse rootsResponse = deserializer.deserializeResult(message, RootsResponse.class);
            roots.clear();
            for (Root root : rootsResponse.roots()) {
                roots.add(root.uri());
            }
            logger.log("[API][RECEIVED] roots/list response — populated " + roots.size() + " root(s)");
        } else if (ELICITATION_REQUEST_ID.equals(message.id())) {
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
    }

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

    private void process(JsonRpcErrorResponse message) {
        logger.log("[API][RECEIVED] error from client: " + message);
    }

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

    private void sendElicitationMessage() {
        ElicitationCreateParams params = ElicitationBuilder.buildSearchDirectoryElicitation();
        JsonRpcRequest elicitationRequest = new JsonRpcRequest(JSON_RPC_VERSION, ELICITATION_REQUEST_ID,
                                                               UniqueKeys.ELICITATION_CREATE_MESSAGE.getValue(),
                                                               params);
        io.emit(elicitationRequest);
    }

    /**
     * Builds the {@code _meta} bag that ties a response to its parent task per
     * spec § "Related Task Metadata".
     */
    private static Map<String, Object> relatedTaskMeta(String taskId) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("io.modelcontextprotocol/related-task", Map.of("taskId", taskId));
        return meta;
    }

    /**
     * Spawn a background thread that runs the actual tool work and records
     * its outcome in the {@link TaskStore}. A small sleep is included so the
     * "working → completed" transition is observable in the inspector.
     */
    private void runToolAsTask(String taskId, ToolCallParams params) {
        new Thread(() -> {
            logger.log("[TASK " + taskId + "] background tool execution started for tool=" + params.name());
            try {
                Thread.sleep(2000L);
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

}
