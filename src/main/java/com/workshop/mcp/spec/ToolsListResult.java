package com.workshop.mcp.spec;

import java.util.List;

/**
 * Represents the result of a tools list request in the MCP (Model Context Protocol) system.
 * <p>
 * ToolsListResult contains the complete list of tools that the server exposes
 * to clients. Each tool represents a callable function or operation that clients
 * can invoke to perform specific actions or retrieve information.
 * </p>
 * 
 * @param tools the list of available tools provided by the server
 * 
 * @see Tool
 * @see ToolsListParams
 * @since 1.0
 */
public record ToolsListResult(
    List<Tool> tools
) {}
