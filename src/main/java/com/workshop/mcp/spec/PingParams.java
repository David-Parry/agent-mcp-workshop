package com.workshop.mcp.spec;

/**
 * Represents parameters for a ping request in the MCP (Model Context Protocol) system.
 * <p>
 * PingParams is used to check the health and responsiveness of an MCP server.
 * The ping operation is a simple heartbeat mechanism that helps clients verify
 * that the server is still active and responding to requests.
 * </p>
 * 
 * @param _meta optional metadata information for the ping request, including progress tracking
 * 
 * @see MetaInfo
 * @since 1.0
 */
public record PingParams(
    MetaInfo _meta
) {}
