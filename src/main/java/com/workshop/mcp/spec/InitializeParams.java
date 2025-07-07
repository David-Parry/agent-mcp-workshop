package com.workshop.mcp.spec;

/**
 * Represents parameters for initializing a connection in the MCP (Model Context Protocol) system.
 * <p>
 * InitializeParams contains all the information needed to establish a connection
 * between a client and server. This includes protocol version for compatibility
 * checking, client capabilities for feature negotiation, and client identification
 * information.
 * </p>
 * 
 * @param protocolVersion the version of the MCP protocol the client supports
 * @param capabilities the capabilities supported by the client
 * @param clientInfo information identifying the client application
 * 
 * @see InitializeResult
 * @see ClientCapabilities
 * @see ClientInfo
 * @since 1.0
 */
public record InitializeParams(
    String protocolVersion,
    ClientCapabilities capabilities,
    ClientInfo clientInfo
) {}
