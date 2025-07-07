package com.workshop.mcp.spec;

/**
 * Represents the result of an initialization request in the MCP (Model Context Protocol) system.
 * <p>
 * InitializeResult is returned by the server in response to an initialization request,
 * providing information about the server's protocol version, capabilities, and identity.
 * This allows the client to understand what features are available and ensure
 * compatibility.
 * </p>
 * 
 * @param protocolVersion the version of the MCP protocol the server supports
 * @param capabilities the capabilities provided by the server
 * @param serverInfo information identifying the server application
 * 
 * @see InitializeParams
 * @see ServerCapabilities
 * @see ServerInfo
 * @since 1.0
 */
public record InitializeResult(
    String protocolVersion,
    ServerCapabilities capabilities,
    ServerInfo serverInfo
) {}
