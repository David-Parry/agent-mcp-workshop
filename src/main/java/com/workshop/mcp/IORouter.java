package com.workshop.mcp;

import com.workshop.mcp.io.IOHandler;
import com.workshop.mcp.io.LogFile;
import com.workshop.mcp.io.LogFileWriter;
import com.workshop.mcp.resources.JavadocResources;
import com.workshop.mcp.spec.*;
import com.workshop.mcp.spec.builders.*;
import com.workshop.mcp.tools.KeyWordSearch;

import java.util.HashSet;
import java.util.Set;

import static com.workshop.mcp.spec.Message.KEY_WORD_MESSAGE;
import static com.workshop.mcp.spec.Resource.DEFAULT_MIME_TYPE;
import static com.workshop.mcp.spec.builders.ElicitationBuilder.buildJiraProjectElicitation;

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
    private boolean hasRoots = false;
    private boolean hasSampling = false;
    private boolean hasElicitation = false;

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
            case JsonRpcResponse successResponse -> process(successResponse);
            case JsonRpcErrorResponse errorResponse -> process(errorResponse);
            default -> logger.log("Unknown message type: " + object);
        }
    }

    private void success(Long id, Object message) {
        JsonRpcResponse response = new JsonRpcResponse(JSON_RPC_VERSION, id, message);
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
                InitializeResultBuilder builder = InitializeResultBuilder
                        .builder()
                        .withProtocolVersion(initializeParams.protocolVersion())
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
                KeyWordSearch keyWordSearch = new KeyWordSearch(this.roots);
                ToolsListResultBuilder builder = ToolsListResultBuilder
                        .builder()
                        .addTool(keyWordSearch.name(), keyWordSearch.description(), keyWordSearch.schema());
                success(message.id(), builder.build());
            }
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
            case RESOURCES_LIST -> {
                ResourcesListResultBuilder builder = ResourcesListResultBuilder
                        .builder()
                        .withResources(JavadocResources.loadAllHtmlResourcesFromFolder("javadoc/com/workshop/mcp/spec"))
                        .withNextCursor("pageNext");
                success(message.id(), builder.build());
            }
            case RESOURCES_READ -> {
                ReadResourceParam param = deserializer.deserializeParams(message, ReadResourceParam.class);
                String resourceUri = param.uri();
                ReadResourceResultBuilder builder = ReadResourceResultBuilder.builder();
                if (resourceUri != null && !resourceUri.isEmpty()) {
                    try {
                        String content = JavadocResources.readResourceContent(resourceUri);
                        builder.addTextContent(resourceUri, DEFAULT_MIME_TYPE, content);
                    } catch (Exception e) {
                        logger.log("Error reading resource: " + resourceUri);
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
                if(hasElicitation) {
                    logger.log("The client has elicitation");
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
                // Handle elicitation method calls from client
                logger.log("Received elicitation/create method call from client: " + message);
                // Parse the elicitation response and handle it appropriately
                // For now, just acknowledge the elicitation request
                success(message.id(), new Object());
            }
            default -> logger.log("Unhandled RpcRequest method: " + uniqueKey + " for message: " + message);
        }
    }

    private void process(JsonRpcNotification message) {
        UniqueKeys uniqueKey = UniqueKeys.fromValue(message.method());
        switch (uniqueKey) {
            case NOTIFICATIONS_INITIALIZED -> {
                // if server has roots, request the roots list
                if (hasRoots) {
                    io.emit(rootsRequest);
                }
                if(hasElicitation) {
                    sendElicitationMessage();
                }
            }
            case NOTIFICATIONS_ROOTS_LIST_CHANGED -> {
                io.emit(rootsRequest);
            }
            case NOTIFICATION_CANCELLED -> {
                NotificationCancelledParams params = deserializer.deserializeParams(message,
                                                                                    NotificationCancelledParams.class);
                logger.log("Notification cancelled reason " + params.reason());
            }
            default -> logger.log("Unhandled notification method: " + uniqueKey + " for message: " + message);
        }
    }

    private void process(JsonRpcResponse message) {
        if (ROOTS_REQUEST_ID.equals(message.id())) {
            RootsResponse rootsResponse = deserializer.deserializeResult(message, RootsResponse.class);
            roots.clear();
            for (Root root : rootsResponse.roots()) {
                roots.add(root.uri());
            }
        }
    }

    private void process(JsonRpcErrorResponse message) {
        logger.log("Error from Client " + message);
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
        ElicitationCreateParams params = ElicitationBuilder.buildJiraProjectElicitation();
        JsonRpcRequest elicitationRequest = new JsonRpcRequest(JSON_RPC_VERSION, ELICITATION_REQUEST_ID,
                                                               UniqueKeys.ELICITATION_CREATE_MESSAGE.getValue(),
                                                               params);
        io.emit(elicitationRequest);
    }

}
