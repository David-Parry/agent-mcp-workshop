package com.workshop.mcp;

import com.workshop.mcp.io.IOHandler;
import com.workshop.mcp.io.LogFile;
import com.workshop.mcp.io.LogFileWriter;
import com.workshop.mcp.spec.*;
import com.workshop.mcp.spec.builders.InitializeResultBuilder;

import java.util.HashSet;
import java.util.Set;

public class IORouter implements Router {
    private static final LogFile logger = LogFileWriter.getInstance();
    private static final String JSON_RPC_VERSION = "2.0";
    private static final Long ROOTS_REQUEST_ID = -1000L;
    private static final JsonRpcRequest rootsRequest = new JsonRpcRequest(JSON_RPC_VERSION, ROOTS_REQUEST_ID,
                                                                          "roots" + "/list", null);
    private final IOHandler io;
    private final Set<String> roots = new HashSet<>();
    private final JsonRpcMessageDeserializer deserializer = new JsonRpcMessageDeserializer();
    private boolean hasRoots = false;

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
                InitializeResultBuilder builder = InitializeResultBuilder
                        .builder()
                        .withProtocolVersion(initializeParams.protocolVersion())
                        .withDefaultCapabilities()
                        .withDefaultServerInfo();
                success(message.id(), builder.build());
            }
            case PING -> {
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


}
