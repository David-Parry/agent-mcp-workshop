package com.workshop.mcp.spec;

/**
 * Represents information about an MCP (Model Context Protocol) client.
 * <p>
 * ClientInfo provides identification details about the client connecting to
 * an MCP server, including its name and version. This information is typically
 * exchanged during the initialization handshake to ensure compatibility and
 * enable appropriate feature negotiation.
 * </p>
 * 
 * @param name the name of the client application
 * @param version the version string of the client application
 * 
 * @see InitializeParams
 * @since 1.0
 */
public record ClientInfo(
    String name,
    String version
) {}
