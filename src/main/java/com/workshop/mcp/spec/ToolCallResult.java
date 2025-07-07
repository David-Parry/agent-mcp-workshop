package com.workshop.mcp.spec;

import java.util.List;

/**
 * Represents the result of a tool invocation in the MCP (Model Context Protocol) system.
 * <p>
 * ToolCallResult encapsulates the output from a tool execution, including any
 * content produced and whether an error occurred. The content is provided as a
 * list of ContentItem objects, allowing tools to return multiple types of content
 * or structured results.
 * </p>
 * 
 * @param content a list of content items produced by the tool execution
 * @param isError indicates whether the tool execution resulted in an error
 * 
 * @see ToolCallParams
 * @see ContentItem
 * @since 1.0
 */
public record ToolCallResult(
    List<ContentItem> content,
    boolean isError
) {}
