package com.workshop.mcp.spec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Single source of the Gson configuration used for MCP wire traffic.
 * <p>
 * Inbound parsing and outbound serialization happen in different classes
 * ({@link JsonRpcMessageDeserializer} and
 * {@code com.workshop.mcp.io.IOHandlerImpl}). Both must agree on how a
 * {@link RequestId} is represented, so both take their instance from here.
 * </p>
 *
 * @since 1.0
 */
public final class McpGson {

    private McpGson() {
    }

    /**
     * Creates a Gson instance configured for MCP messages.
     *
     * @return a Gson instance that round-trips {@link RequestId} faithfully and
     *         writes a {@link PropertySchema} as valid JSON Schema
     */
    public static Gson create() {
        return new GsonBuilder()
                .registerTypeAdapter(RequestId.class, new RequestIdTypeAdapter())
                .create();
    }
}
