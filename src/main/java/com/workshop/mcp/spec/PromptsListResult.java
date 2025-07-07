package com.workshop.mcp.spec;

import java.util.List;

/**
 * Represents the result of a prompts list request in the MCP (Model Context Protocol) system.
 * <p>
 * PromptsListResult contains a list of available prompts and supports pagination
 * through the nextCursor field. This allows servers to return large lists of
 * prompts in manageable chunks.
 * </p>
 * 
 * @param prompts the list of available prompts in this result set
 * @param nextCursor an optional cursor for pagination, null if no more results
 * 
 * @see Prompt
 * @see PromptsListParams
 * @since 1.0
 */
public record PromptsListResult(
    List<Prompt> prompts,
    String nextCursor
) {}
