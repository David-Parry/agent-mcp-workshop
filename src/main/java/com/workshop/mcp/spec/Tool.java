package com.workshop.mcp.spec;

import java.util.Map;

/**
 * Represents a tool that can be invoked by the MCP (Model Context Protocol) client.
 * <p>
 * Tools are functions or operations that the server exposes to clients, allowing them
 * to perform specific actions or retrieve information. Each tool has a unique name,
 * a description of its functionality, and a schema defining its input parameters.
 * </p>
 * <p>
 * The schema is required even for a tool that takes no arguments: the
 * specification says it MUST be a JSON Schema object and not {@code null}.
 * Such a tool sends an object schema with no properties, which is what an
 * {@code InputSchemaBuilder} given nothing builds.
 * </p>
 * <p>
 * The specification also allows an optional {@code icons} array here — the
 * same general field {@link Prompt}, {@link Resource}, and the server's own
 * {@link ServerInfo} may carry, giving a host something to render beside the
 * name. This server advertises no icons, so the field is not modelled; an
 * inbound one would simply be ignored.
 * </p>
 * <p>
 * The {@code outputSchema} is optional. A tool that declares one MUST return
 * {@code structuredContent} matching it on every successful result, and a
 * client may validate that it does. It is held as a plain JSON Schema map
 * because, unlike an input schema, it nests: an array of objects is the usual
 * shape of a result.
 * </p>
 * 
 * @param name the unique identifier for the tool, used when invoking it
 * @param description a human-readable description of what the tool does and how to use it
 * @param inputSchema the schema defining the tool's input parameters
 * @param outputSchema the JSON Schema of the tool's {@code structuredContent}, or null when it returns none
 * 
 * @see InputSchema
 * @since 1.0
 */
public record Tool(
    String name,
    String description,
    InputSchema inputSchema,
    Map<String, Object> outputSchema
) {
    /**
     * Creates a tool whose results carry no structured content.
     *
     * @param name        the unique identifier for the tool
     * @param description what the tool does
     * @param inputSchema the schema defining the tool's input parameters
     */
    public Tool(String name, String description, InputSchema inputSchema) {
        this(name, description, inputSchema, null);
    }
}
