package com.workshop.mcp.spec;

/**
 * Represents parameters for listing available tools in the MCP (Model Context Protocol) system.
 * <p>
 * ToolsListParams is used when requesting a list of all tools that the server
 * provides. The parameters may include metadata for tracking the progress of
 * the listing operation.
 * </p>
 * 
 * @param _meta optional metadata information for the tools list request, including progress tracking
 * 
 * @see ToolsListResult
 * @see MetaInfo
 * @since 1.0
 */
public record ToolsListParams(
    MetaInfo _meta
) {}
