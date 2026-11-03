package com.workshop.mcp.spec;

/**
 * Represents a tool that can be invoked by the MCP (Model Context Protocol) client.
 * <p>
 * Tools are functions or operations that the server exposes to clients, allowing them
 * to perform specific actions or retrieve information. Each tool has a unique name,
 * a description of its functionality, and an optional schema defining its input parameters.
 * </p>
 * <p>
 * The specification also allows an optional {@code icons} array here — the
 * same general field {@link Prompt}, {@link Resource}, and the server's own
 * {@link ServerInfo} may carry, giving a host something to render beside the
 * name. This server advertises no icons, so the field is not modelled; an
 * inbound one would simply be ignored.
 * </p>
 * 
 * @param name the unique identifier for the tool, used when invoking it
 * @param description a human-readable description of what the tool does and how to use it
 * @param inputSchema the schema defining the tool's input parameters, may be null for tools with no parameters
 * 
 * @see InputSchema
 * @since 1.0
 */
public record Tool(
    String name,
    String description,
    InputSchema inputSchema
) {}
