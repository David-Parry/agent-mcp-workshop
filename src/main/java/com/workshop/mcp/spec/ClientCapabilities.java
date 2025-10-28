package com.workshop.mcp.spec;

/**
 * Represents the capabilities supported by an MCP (Model Context Protocol) client.
 * <p>
 * ClientCapabilities defines what features and operations the client can handle.
 * This information is sent during initialization to allow the server to tailor
 * its behavior and responses based on what the client supports.
 * </p>
 * 
 * @param sampling the sampling-related capabilities supported by the client
 * @param roots the roots-related capabilities supported by the client
 * 
 * @see SamplingCapability
 * @see RootsCapability
 * @see InitializeParams
 * @since 1.0
 */
public record ClientCapabilities(
    SamplingCapability sampling,
    RootsCapability roots,
    Elicitation elicitation
) {}
