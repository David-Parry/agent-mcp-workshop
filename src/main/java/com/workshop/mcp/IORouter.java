package com.workshop.mcp;

import com.workshop.mcp.io.IOHandler;
import com.workshop.mcp.io.LogFile;
import com.workshop.mcp.io.LogFileWriter;
import com.workshop.mcp.resources.JavadocResources;
import com.workshop.mcp.spec.*;
import com.workshop.mcp.spec.builders.*;
import com.workshop.mcp.tasks.TaskStore;

import com.workshop.mcp.tools.KeyWordSearch;
import com.workshop.mcp.tools.SearchContinuation;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.workshop.mcp.spec.Message.KEY_WORD_MESSAGE;
import static com.workshop.mcp.spec.Resource.DEFAULT_MIME_TYPE;

/**
 * Routes JSON-RPC messages for protocol revision {@code 2026-07-28}.
 * <p>
 * The shape of this class is dictated by what that revision removed. There is
 * no {@code initialize} handler, because there is no handshake: every request
 * re-states the protocol version and the client's capabilities in its
 * {@code _meta}, and {@link #envelopeFor} validates that before anything else
 * happens. There are no fields tracking what the client can do, because those
 * are per-request facts now. And there is no code that sends a request to the
 * client, because modern clients discard inbound requests — the three things
 * this server used to ask for are embedded in an {@link InputRequiredResult}
 * instead.
 * </p>
 *
 * @see RequestEnvelope
 * @see InputRequiredResult
 * @since 1.0
 */
public class IORouter implements Router {
    private static final LogFile logger = LogFileWriter.getInstance();
    private static final String JSON_RPC_VERSION = "2.0";
    private static final String KEYWORD_APP_URI = "ui://keyword-search/mcp-app.html";
    private static final long LIST_TTL_MILLIS = 60_000L;

    /**
     * Whether {@code tools/call} hands back a task handle instead of running
     * the search inline.
     * <p>
     * Task creation is the server's decision on this revision: the
     * {@code params.task} opt-in a client used to send was removed, and
     * servers MUST ignore it if one arrives. This switch is that decision,
     * kept as a constant so the workshop can show both shapes of the same
     * call.
     * </p>
     * <p>
     * A handle is only ever returned to a client that declared the tasks
     * extension on the request being answered, which is checked separately —
     * a client that cannot poll must not be handed something to poll.
     * </p>
     */
    private static final boolean TASK_HANDLES_ENABLED = true;

    /**
     * How long a task waiting in {@code input_required} gives the client to
     * answer with {@code tasks/update} before carrying on without the input.
     * <p>
     * Bounded on purpose: a task that waited forever would sit there until its
     * TTL expired, and the client has no obligation to answer at all.
     * </p>
     */
    private static final long TASK_INPUT_TIMEOUT_MILLIS = 60_000L;

    /**
     * The methods this server accepts as inbound requests.
     * <p>
     * The revision also defines three methods that only ever appear embedded
     * in an {@link InputRequiredResult} — {@code roots/list},
     * {@code sampling/createMessage}, and {@code elicitation/create}. Only the
     * last is something this server asks for; the other two are deprecated
     * under SEP-2577 and are not built here at all. None is ever answered, so
     * a client sending any of them gets {@link ErrorCodes#METHOD_NOT_FOUND}.
     * </p>
     * <p>
     * Every member of this set must have an arm in the switch in
     * {@link #process(JsonRpcRequest)}, or the request is accepted and then
     * silently dropped. That agreement is checked by a test rather than
     * guarded at runtime, because the only thing that can break it is an edit
     * to this file.
     * </p>
     */
    private static final Set<UniqueKeys> INBOUND_METHODS = EnumSet.of(
            UniqueKeys.SERVER_DISCOVER,
            UniqueKeys.PROMPTS_LIST,
            UniqueKeys.PROMPTS_GET,
            UniqueKeys.TOOLS_LIST,
            UniqueKeys.TOOLS_CALL,
            UniqueKeys.RESOURCES_LIST,
            UniqueKeys.RESOURCES_TEMPLATES_LIST,
            UniqueKeys.RESOURCES_READ,
            UniqueKeys.COMPLETION_COMPLETE,
            UniqueKeys.SUBSCRIPTIONS_LISTEN,
            UniqueKeys.TASKS_GET,
            UniqueKeys.TASKS_UPDATE,
            UniqueKeys.TASKS_CANCEL);

    private final IOHandler io;
    private final JsonRpcMessageDeserializer deserializer = new JsonRpcMessageDeserializer();
    private final TaskStore taskStore = new TaskStore();

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
            case JsonRpcErrorResponse errorResponse -> process(errorResponse);
            default -> logger.log("[API][RECEIVED] unknown message type: " + object);
        }
    }

    private void success(RequestId id, Object message) {
        JsonRpcResponse response = new JsonRpcResponse(JSON_RPC_VERSION, id, message);
        io.emit(response);
    }

    private void error(RequestId id, int code, String message) {
        error(id, code, message, null);
    }

    private void error(RequestId id, int code, String message, Object data) {
        JsonRpcErrorResponse response = new JsonRpcErrorResponse(
                JSON_RPC_VERSION, id, new JsonRpcError(code, message, data));
        io.emit(response);
    }

    private void process(JsonRpcRequest message) {
        UniqueKeys uniqueKey = UniqueKeys.fromValue(message.method());

        // server/discover is answered before any version check: a client uses
        // it precisely to find out which versions this server speaks, so
        // rejecting it for asking with the wrong one would be circular.
        if (uniqueKey == UniqueKeys.SERVER_DISCOVER) {
            success(message.id(), discoverResult());
            logger.log("[API][SENT] server/discover — advertised " + RequestEnvelope.SUPPORTED_VERSIONS);
            return;
        }

        // Method existence is settled before the envelope is looked at.
        // Deletions in this revision are physical: a method absent from the
        // registry is -32601 by absence, and answering a removed method like
        // initialize with a complaint about its _meta would tell a legacy
        // client the wrong thing about why it failed.
        if (uniqueKey == UniqueKeys.NOT_FOUND || !INBOUND_METHODS.contains(uniqueKey)) {
            logger.log("[API][SENT] unsupported method '" + message.method() + "' (returning -32601)");
            error(message.id(), ErrorCodes.METHOD_NOT_FOUND, "Method not found: " + message.method());
            return;
        }

        RequestEnvelope envelope = envelopeFor(message);
        if (envelope == null) {
            return;
        }

        switch (uniqueKey) {
            case PROMPTS_LIST -> {
                PromptsListResultBuilder builder = PromptsListResultBuilder
                        .builder()
                        .withPrompt("search_keyword",
                                    "Creates a prompt, to search for a word using the key_word_search tool.")
                        .withPromptArgument("keyword", "The word to search for", true)
                        .withNextCursor("nextPage");
                PromptsListResult result = builder.build();
                success(message.id(), new PromptsListResult(result.prompts(), result.nextCursor(),
                                                            LIST_TTL_MILLIS, CacheScope.PUBLIC));
            }
            case PROMPTS_GET -> {
                // For the sake of the lesson we are dealing with a single prompt if we had more than one we would
                // need to look it up
                PromptsGetParams params = deserializer.deserializeParams(message, PromptsGetParams.class);
                PromptsGetResultBuilder builder = PromptsGetResultBuilder
                        .builder()
                        .withDescription("keyword")
                        .addTextMessage("user", KEY_WORD_MESSAGE, params.arguments());
                success(message.id(), builder.build());
            }
            case TOOLS_LIST -> {
                KeyWordSearch keyWordSearch = new KeyWordSearch();
                AppTool appTool = AppToolBuilder.builder()
                        .withName(keyWordSearch.name())
                        .withDescription(keyWordSearch.description())
                        .withInputSchema(keyWordSearch.schema())
                        .withResourceUri(KEYWORD_APP_URI)
                        .build();
                success(message.id(), new AppToolsListResult(List.of(appTool), LIST_TTL_MILLIS, CacheScope.PUBLIC));
            }
            case TOOLS_CALL -> {
                ToolCallParams toolCallParams = deserializer.deserializeParams(message, ToolCallParams.class);
                KeyWordSearch keyWordSearch = new KeyWordSearch();
                if (!keyWordSearch.name().equalsIgnoreCase(toolCallParams.name())) {
                    success(message.id(), ToolCallResultBuilder
                            .builder()
                            .addTextContent("Tool not found: " + toolCallParams.name())
                            .asError()
                            .build());
                } else {
                    handleKeywordSearch(message.id(), toolCallParams, envelope);
                }
            }
            case TASKS_GET -> {
                TasksGetParams params = deserializer.deserializeParams(message, TasksGetParams.class);
                // A missing taskId is answered, not faulted on: reading a null
                // id straight into the store threw out of the router and left
                // the client waiting for a reply that never came.
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
                if (params.taskId() == null || taskStore.get(params.taskId()) == null) {
                    logger.log("[API][SENT] tasks/update — unknown taskId=" + params.taskId() + " (returning -32602)");
                    error(message.id(), ErrorCodes.INVALID_PARAMS, "Failed to update task: Task not found");
                } else if (taskStore.applyInput(params.taskId(), params.inputResponses()) == null) {
                    logger.log("[API][SENT] tasks/update rejected — taskId=" + params.taskId()
                               + " is not awaiting input (returning -32602)");
                    error(message.id(), ErrorCodes.INVALID_PARAMS,
                          "Cannot update task: it is not waiting for input");
                } else {
                    logger.log("[API][SENT] tasks/update — taskId=" + params.taskId() + " resumed");
                    success(message.id(), Map.of("resultType", ResultType.COMPLETE));
                }
            }
            case TASKS_CANCEL -> {
                TasksCancelParams params = deserializer.deserializeParams(message, TasksCancelParams.class);
                Task before = params.taskId() == null ? null : taskStore.get(params.taskId());
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
                        success(message.id(), TaskResult.detail(cancelled, null, null, null));
                    }
                }
            }
            case RESOURCES_LIST -> {
                ResourcesListResultBuilder builder = ResourcesListResultBuilder
                        .builder()
                        .withResources(JavadocResources.loadAllHtmlResourcesFromFolder("javadoc/com/workshop/mcp/spec"))
                        .addResource(ResourceBuilder.builder()
                                .withUri(KEYWORD_APP_URI)
                                .withName("Keyword Search App")
                                .withDescription("Interactive keyword search results dashboard")
                                .withMimeType(Resource.MIME_TYPE_UI_APP)
                                .build())
                        .withNextCursor("pageNext");
                ResourcesListResult result = builder.build();
                success(message.id(), new ResourcesListResult(result.resources(), result.nextCursor(),
                                                              LIST_TTL_MILLIS, CacheScope.PUBLIC));
            }
            case RESOURCES_TEMPLATES_LIST -> {
                // Clients fetch this whenever a server declares any resource
                // capability, so it has to be answered even though this server
                // exposes no templates.
                success(message.id(), ResourceTemplatesListResult.empty(LIST_TTL_MILLIS));
            }
            case RESOURCES_READ -> {
                ReadResourceParam param = deserializer.deserializeParams(message, ReadResourceParam.class);
                String resourceUri = param.uri();
                ReadResourceResultBuilder builder = ReadResourceResultBuilder.builder();
                if (resourceUri == null || resourceUri.isEmpty()) {
                    builder
                            .addTextContent("", DEFAULT_MIME_TYPE, "Resource URI is null or empty, returning error.")
                            .asError();
                } else {
                    // The app is the one resource whose uri is not also its
                    // classpath path, but it is read and reported like any
                    // other, so the two share a single failure path.
                    boolean isApp = KEYWORD_APP_URI.equals(resourceUri);
                    String mimeType = isApp ? Resource.MIME_TYPE_UI_APP : DEFAULT_MIME_TYPE;
                    try {
                        String content = JavadocResources.readResourceContent(
                                isApp ? "lesson/mcp-app.html" : resourceUri);
                        builder.addTextContent(resourceUri, mimeType, content);
                    } catch (Exception e) {
                        logger.log("[API][SENT] resources/read — error reading resource: " + resourceUri);
                        builder.addTextContent(resourceUri, mimeType, e.getMessage()).asError();
                    }
                }
                ReadResourceResult result = builder.build();
                success(message.id(), new ReadResourceResult(result.contents(), result.isError(),
                                                             LIST_TTL_MILLIS, CacheScope.PUBLIC));
            }
            case COMPLETION_COMPLETE -> {
                CompletionCompleteParams params = deserializer.deserializeParams(message,
                                                                                 CompletionCompleteParams.class);
                CompletionArgument argument = params.argument();
                if (argument == null) {
                    logger.log("[API][SENT] completion/complete — no argument to complete (returning -32602)");
                    error(message.id(), ErrorCodes.INVALID_PARAMS, "completion/complete requires an argument");
                } else if ("keyword".equalsIgnoreCase(argument.name())) {
                    // Simulating a keyword search completion
                    CompletionCompleteBuilder response = CompletionCompleteBuilder.withValue("java");
                    response.value("the").value("and").total(3).hasMore(true);
                    success(message.id(), response.build());
                } else {
                    success(message.id(), CompletionCompleteBuilder.withValue("java").total(1).hasMore(false).build());
                }
            }
            case SUBSCRIPTIONS_LISTEN -> acknowledgeSubscription(message);
        }
    }

    /**
     * Validates the stateless lifecycle envelope that replaced the handshake.
     * <p>
     * A request missing the protocol version or the client's capabilities
     * cannot be serviced, and one naming a revision this server does not speak
     * must be told which revisions it could use instead — that error is the
     * only way a client learns to renegotiate.
     * </p>
     *
     * @param message the inbound request
     * @return the validated envelope, or null when an error has already been
     *         emitted and the caller should stop
     */
    private RequestEnvelope envelopeFor(JsonRpcRequest message) {
        RequestEnvelope envelope = deserializer.deserializeEnvelope(message);
        if (!envelope.isComplete()) {
            logger.log("[API][SENT] " + message.method() + " — incomplete _meta envelope (returning -32602)");
            error(message.id(), ErrorCodes.INVALID_PARAMS,
                  "Requests must carry " + MetaKeys.PROTOCOL_VERSION + " and "
                  + MetaKeys.CLIENT_CAPABILITIES + " in params._meta");
            return null;
        }
        if (!envelope.isSupportedVersion()) {
            logger.log("[API][SENT] " + message.method() + " — unsupported protocol version '"
                       + envelope.protocolVersion() + "' (returning -32022)");
            error(message.id(), ErrorCodes.UNSUPPORTED_PROTOCOL_VERSION, "Unsupported protocol version",
                  Map.of("supported", RequestEnvelope.SUPPORTED_VERSIONS,
                         "requested", envelope.protocolVersion()));
            return null;
        }
        return envelope;
    }

    /**
     * Builds the {@code server/discover} answer.
     * <p>
     * On stdio this is answered by a throwaway probe process, so it is derived
     * entirely from static configuration.
     * </p>
     *
     * @return the discovery result
     */
    private DiscoverResult discoverResult() {
        DiscoverResultBuilder builder = DiscoverResultBuilder
                .builder()
                .withDefaultCapabilities()
                .withExtension(MetaKeys.UI_EXTENSION,
                               Map.of("mimeTypes", List.of(Resource.MIME_TYPE_UI_APP)))
                .withInstructions("Searches a project for a keyword. If no directory is supplied, the tool asks "
                                  + "for one over a Multi Round-Trip Request, and searches its own working "
                                  + "directory if the client offers nothing.")
                .withCacheHints(LIST_TTL_MILLIS, CacheScope.PUBLIC)
                .withDefaultServerInfo();
        if (TASK_HANDLES_ENABLED) {
            // Declaring the extension here is what permits a task handle at
            // all; a client that does not see it will not poll.
            builder.withTasksExtension();
        }
        return builder.build();
    }

    /**
     * Runs the keyword search, asking the client for a directory first if it
     * needs one.
     * <p>
     * This is the Multi Round-Trip Requests loop, and it exists in this shape
     * because there is no session to hold the answer in. Each stage returns an
     * {@link InputRequiredResult} carrying both the question and a
     * {@link SearchContinuation} that records the keyword and how far the
     * exchange has got; the client answers by re-sending the whole
     * {@code tools/call} with a new id.
     * </p>
     *
     * @param requestId the id to answer
     * @param params    the call parameters, including any prior answers
     * @param envelope  this request's declared client capabilities
     */
    private void handleKeywordSearch(RequestId requestId, ToolCallParams params, RequestEnvelope envelope) {
        SearchContinuation continuation = SearchContinuation.decode(params.requestState());
        String keyword = continuation != null ? continuation.keyword() : keywordArgument(params);
        String stage = continuation != null ? continuation.stage() : null;

        Set<String> directories = new LinkedHashSet<>();

        // A directory argument settles the question outright. Not every client
        // can answer an input_required — the Inspector's task-augmented
        // tools/call path cannot — so the tool has to stay callable in one hop.
        String argument = directoryArgument(params);
        if (argument != null) {
            directories.add(argument);
        }

        if (SearchContinuation.STAGE_DIRECTORY.equals(stage)) {
            String directory = elicitedDirectory(params.inputResponse(SearchContinuation.KEY_DIRECTORY));
            if (directory != null) {
                directories.add(directory);
            }
            logger.log("[API][RECEIVED] tools/call retry — elicited directory=" + directory);
        }

        if (!directories.isEmpty()) {
            executeKeyWordSearchCall(requestId, params, keyword, directories, envelope);
            return;
        }

        // Nothing to search yet, so the tool needs to ask. How it asks depends
        // on the answer shape this request is already committed to, because
        // resultType holds one value: a request answered with a task handle
        // cannot also be answered with input_required. Answering the call
        // itself is what produces "Unsupported result type 'input_required'
        // for tools/call" from the Inspector's task path.
        //
        // So a task-declaring client gets its handle now and the question
        // arrives later as a *status* on that task, resolved through
        // tasks/update rather than by re-sending this call.
        if (answersWithTask(envelope)) {
            Task task = taskStore.create(null);
            logger.log("[API][SENT] tools/call — created taskId=" + task.taskId()
                       + " with no directory; the task will ask for one");
            success(requestId, TaskResult.handle(task));
            runSearchAsTaskThatAsks(task.taskId(), resolvedCall(params, keyword), envelope);
            return;
        }

        // Asking the user for a directory is the only question this tool has
        // left. It used to embed a roots/list first, since a client answers
        // that from configuration without troubling anyone, but roots is
        // deprecated under SEP-2577 and the directory argument above is the
        // migration the spec names in its place.
        if (!SearchContinuation.STAGE_DIRECTORY.equals(stage) && envelope.supportsElicitationForm()) {
            logger.log("[API][SENT] tools/call — input_required, embedding elicitation/create");
            success(requestId, InputRequiredResult.of(
                    SearchContinuation.KEY_DIRECTORY,
                    InputRequest.elicitation(ElicitationBuilder.buildSearchDirectoryElicitation()),
                    SearchContinuation.awaitingDirectory(keyword).encode()));
            return;
        }

        // There is nothing left to ask, or nothing that may be asked. Rather
        // than fail, search from wherever the server was started — the same
        // place a shell command with no path argument would look.
        String workingDirectory = workingDirectory();
        logger.log("[API][SENT] tools/call — no directory to search, using the working directory "
                   + workingDirectory);
        executeKeyWordSearchCall(requestId, params, keyword, Set.of(workingDirectory), envelope);
    }

    /**
     * Reports whether this request will be answered with a task handle.
     * <p>
     * The decision has to be made before the tool considers asking for
     * anything, because a request answered with a handle cannot also be
     * answered with {@code input_required} — the two are alternative result
     * types for the same response. A task that needs input must instead reach
     * {@code input_required} as a <em>status</em>, resolved through
     * {@code tasks/update}, which is a round trip through a different method.
     * </p>
     *
     * @param envelope the envelope of the request being answered
     * @return true when a task handle is going back
     */
    private boolean answersWithTask(RequestEnvelope envelope) {
        return TASK_HANDLES_ENABLED && envelope.supportsTasks();
    }

    /**
     * Resolves {@code .} to an absolute path.
     * <p>
     * The search reports absolute paths, so the root it walks has to be
     * absolute too or the results come back relative to a directory the client
     * cannot see.
     * </p>
     *
     * @return the directory the server process was started in
     */
    private String workingDirectory() {
        return Path.of("").toAbsolutePath().normalize().toString();
    }

    /**
     * Runs the search either synchronously or as a task, depending on whether
     * a task handle can be handed back.
     * <p>
     * A server must not return a task to a client that did not declare the
     * tasks extension on the very request being answered.
     * </p>
     */
    private void executeKeyWordSearchCall(RequestId requestId, ToolCallParams params, String keyword,
                                          Set<String> directories, RequestEnvelope envelope) {
        ToolCallParams resolved = resolvedCall(params, keyword);
        if (answersWithTask(envelope)) {
            Task task = taskStore.create(null);
            logger.log("[API][SENT] tools/call — created taskId=" + task.taskId() + " status=" + task.status());
            success(requestId, TaskResult.handle(task));
            runToolAsTask(task.taskId(), resolved, directories);
        } else {
            success(requestId, new KeyWordSearch().call(resolved, directories));
        }
    }

    /**
     * Strips a call down to the arguments the tool itself needs, dropping the
     * round-trip bookkeeping. The keyword may have arrived either as an
     * argument or on a {@link SearchContinuation}, so it is passed explicitly.
     */
    private ToolCallParams resolvedCall(ToolCallParams params, String keyword) {
        return new ToolCallParams(params._meta(), params.name(),
                                  Map.of("keyword", keyword == null ? "" : keyword),
                                  null, null);
    }

    private String directoryArgument(ToolCallParams params) {
        String directory = params.arguments() == null ? null : params.arguments().get("directory");
        return directory == null || directory.isBlank() ? null : directory;
    }

    private String keywordArgument(ToolCallParams params) {
        return params.arguments() == null ? null : params.arguments().get("keyword");
    }

    /**
     * Reads the directory out of an embedded {@code elicitation/create} answer,
     * which the user may also have declined or cancelled.
     */
    private String elicitedDirectory(Object inputResponse) {
        ElicitationCreateResult result = deserializer.convert(inputResponse, ElicitationCreateResult.class);
        if (result == null || !"accept".equalsIgnoreCase(result.action())) {
            return null;
        }
        if (result.content() instanceof Map<?, ?> content && content.get("directory") instanceof String directory
            && !directory.isBlank()) {
            return directory;
        }
        return null;
    }

    private void process(JsonRpcNotification message) {
        UniqueKeys uniqueKey = UniqueKeys.fromValue(message.method());
        switch (uniqueKey) {
            case NOTIFICATION_CANCELLED -> {
                NotificationCancelledParams params = deserializer.deserializeParams(message,
                                                                                    NotificationCancelledParams.class);
                // There is never an in-flight request to abandon: this server
                // answers every request before returning, and its one
                // long-lived request, subscriptions/listen, is closed as it is
                // acknowledged. So the cancellation is recorded and nothing else.
                logger.log("[API][RECEIVED] notifications/cancelled requestId=" + params.requestId()
                           + " reason=" + params.reason());
            }
            default -> logger.log("[API][RECEIVED] unhandled notification method: " + message.method()
                                  + " for message: " + message);
        }
    }

    private void process(JsonRpcErrorResponse message) {
        logger.log("[API][RECEIVED] error from client: " + message);
    }

    /**
     * Opens a {@code subscriptions/listen} stream.
     * <p>
     * Note what is not sent here: a JSON-RPC result. On stdio the listen
     * request stays open and unanswered for the life of the subscription,
     * while notifications are interleaved on stdout and demultiplexed by
     * {@link MetaKeys#SUBSCRIPTION_ID}. The subscription is acknowledged with
     * a notification instead, and that acknowledgment must carry the
     * subscription id — a client that does not find it waits forever, with no
     * error and no timeout.
     * </p>
     * <p>
     * The acknowledgment reports the intersection of what the client asked for
     * and what this server advertised, so the client is never left waiting on
     * a notification that will not come. This server advertises no
     * {@code listChanged} support and no resource subscriptions — its tool,
     * prompt, and resource lists are fixed — so that intersection is always
     * empty and the stream is closed as soon as it is acknowledged rather than
     * held open to deliver nothing. A server with something to say would keep
     * the id and answer later instead.
     * </p>
     * <p>
     * The close is the one place in this revision where a {@code _meta} member
     * is genuinely required: it must carry the subscription id, because
     * closing without it reads to the client as a dropped connection.
     * </p>
     */
    private void acknowledgeSubscription(JsonRpcRequest message) {
        SubscriptionsListenParams params = deserializer.deserializeParams(message,
                                                                         SubscriptionsListenParams.class);
        SubscriptionFilter requested = params.notifications() == null
                ? new SubscriptionFilter(null, null, null, null)
                : params.notifications();
        SubscriptionFilter honored = requested.honoredUnder(discoverResult().capabilities());

        logger.log("[API][SENT] subscriptions/listen — acknowledging id=" + message.id()
                   + " honored=" + honored);
        io.emit(new JsonRpcNotification(
                JSON_RPC_VERSION,
                UniqueKeys.NOTIFICATIONS_SUBSCRIPTIONS_ACKNOWLEDGED.getValue(),
                SubscriptionsAcknowledgedParams.of(message.id(), honored)));

        logger.log("[API][SENT] subscriptions/listen — nothing honored, closing immediately");
        success(message.id(), SubscriptionsListenResult.closing(message.id()));
    }

    /**
     * Spawn a background thread that runs the actual tool work and records
     * its outcome in the {@link TaskStore}. A small sleep is included so the
     * "working to completed" transition is observable in the inspector.
     */
    private void runToolAsTask(String taskId, ToolCallParams params, Set<String> directories) {
        new Thread(() -> {
            logger.log("[TASK " + taskId + "] background tool execution started for tool=" + params.name());
            try {
                Thread.sleep(4000L);
                ToolCallResult result = new KeyWordSearch().call(params, directories);
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
     * Spawn a background thread for a task that still has to find out where to
     * search.
     * <p>
     * This is the task-level counterpart of the {@code tools/call} round trip,
     * and the difference is where the question lives. There, the question is
     * the <em>result</em> of the call and the client answers by re-sending the
     * whole call. Here the call has already been answered — with a handle —
     * so the question surfaces as the task's {@code input_required}
     * <em>status</em>, which a client sees when it polls {@code tasks/get} and
     * answers with {@code tasks/update}. The work waits in
     * {@link TaskStore#awaitInput} in between.
     * </p>
     * <p>
     * A client that answers nothing is not a failure: the search falls back to
     * the server's working directory, exactly as the inline path does.
     * </p>
     */
    private void runSearchAsTaskThatAsks(String taskId, ToolCallParams params, RequestEnvelope envelope) {
        new Thread(() -> {
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
        }, "task-" + taskId).start();
    }

    /**
     * Publishes one question on a task and blocks until the client answers it
     * with {@code tasks/update}.
     * <p>
     * An unanswered question is not distinguished from an unasked one: both
     * yield no answers, and the caller checks the task's status to tell a
     * cancellation from a client that simply stayed quiet. A quiet client is
     * not a failure — the search falls back to the working directory.
     * </p>
     *
     * @return the answers, empty when none arrived
     */
    private Map<String, Object> askOnTask(String taskId, String key, InputRequest request) {
        taskStore.requireInput(taskId, Map.of(key, request));
        logger.log("[TASK " + taskId + "] input_required — asking for " + request.method()
                   + " under key=" + key);
        Map<String, Object> answers = taskStore.awaitInput(taskId, TASK_INPUT_TIMEOUT_MILLIS);
        logger.log("[TASK " + taskId + "] "
                   + (answers == null ? "no answer arrived for " + key : "tasks/update answered " + key));
        return answers == null ? Map.of() : answers;
    }

    private boolean isTerminal(String taskId) {
        return TaskStatus.fromValue(taskStore.get(taskId).status()).isTerminal();
    }

    /**
     * Emit a {@code notifications/tasks} for the given task snapshot.
     * <p>
     * The method name is the bare {@code notifications/tasks}; the
     * {@code notifications/tasks/} prefix is reserved.
     * </p>
     */
    private void sendTaskStatusNotification(TaskResult task) {
        logger.log("[API][SENT] notifications/tasks — taskId=" + task.taskId() + " status=" + task.status());
        io.emit(new JsonRpcNotification(
                JSON_RPC_VERSION,
                UniqueKeys.NOTIFICATIONS_TASKS.getValue(),
                task));
    }
}
