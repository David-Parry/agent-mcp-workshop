package com.workshop.mcp;

import com.workshop.mcp.io.IOHandler;
import com.workshop.mcp.io.LogFile;
import com.workshop.mcp.io.LogFileWriter;
import com.workshop.mcp.resources.JavadocResources;
import com.workshop.mcp.spec.*;
import com.workshop.mcp.spec.builders.*;
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
            UniqueKeys.SUBSCRIPTIONS_LISTEN);

    private final IOHandler io;
    private final JsonRpcMessageDeserializer deserializer = new JsonRpcMessageDeserializer();
    public IORouter(IOHandler io) {
        this.io = io;
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

    private DiscoverResult discoverResult() {
        return DiscoverResultBuilder
                .builder()
                .withDefaultCapabilities()
                .withExtension(MetaKeys.UI_EXTENSION,
                               Map.of("mimeTypes", List.of(Resource.MIME_TYPE_UI_APP)))
                .withInstructions("Searches a project for a keyword. If no directory is supplied, the tool asks for one over a Multi Round-Trip Request, and searches its own working directory if the client offers nothing.")
                .withCacheHints(LIST_TTL_MILLIS, CacheScope.PUBLIC)
                .withDefaultServerInfo()
                .build();
    }


    private void handleKeywordSearch(RequestId requestId, ToolCallParams params, RequestEnvelope envelope) {
        SearchContinuation continuation = SearchContinuation.decode(params.requestState());
        String keyword = continuation != null ? continuation.keyword() : keywordArgument(params);
        String stage = continuation != null ? continuation.stage() : null;

        Set<String> directories = new LinkedHashSet<>();

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

        if (!SearchContinuation.STAGE_DIRECTORY.equals(stage) && envelope.supportsElicitationForm()) {
            logger.log("[API][SENT] tools/call — input_required, embedding elicitation/create");
            success(requestId, InputRequiredResult.of(
                    SearchContinuation.KEY_DIRECTORY,
                    InputRequest.elicitation(ElicitationBuilder.buildSearchDirectoryElicitation()),
                    SearchContinuation.awaitingDirectory(keyword).encode()));
            return;
        }

        String workingDirectory = workingDirectory();
        logger.log("[API][SENT] tools/call — no directory to search, using the working directory "
                   + workingDirectory);
        executeKeyWordSearchCall(requestId, params, keyword, Set.of(workingDirectory), envelope);
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

    private void executeKeyWordSearchCall(RequestId requestId, ToolCallParams params, String keyword,
                                          Set<String> directories, RequestEnvelope envelope) {
        success(requestId, new KeyWordSearch().call(resolvedCall(params, keyword), directories));
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





}
