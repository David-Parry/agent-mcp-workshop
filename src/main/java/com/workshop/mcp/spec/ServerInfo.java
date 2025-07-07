package com.workshop.mcp.spec;

/**
 * Represents information about an MCP (Model Context Protocol) server.
 * <p>
 * ServerInfo provides identification details about the server, including its
 * name and version. This information is returned during the initialization
 * process and helps clients understand the server's capabilities and ensure
 * compatibility.
 * </p>
 * 
 * @param name the name of the server application
 * @param version the version string of the server application
 * 
 * @see InitializeResult
 * @since 1.0
 */
public record ServerInfo(
    String name,
    String version
) {}
