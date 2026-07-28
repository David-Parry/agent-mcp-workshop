package com.workshop.mcp.spec;

import java.util.List;

/**
 * Represents a completion result in the MCP (Model Context Protocol) system.
 * <p>
 * A completion contains a list of suggested values that can be used for
 * auto-completion or suggestion features. These values represent possible
 * completions for a partial input or context.
 * </p>
 * <p>
 * {@code total} and {@code hasMore} belong here, beside the values they
 * describe, and not on the result that carries the completion.
 * </p>
 * 
 * @param values  the list of completion values/suggestions, at most 100
 * @param total   the total number of matches available, or null if unknown.
 *                It may exceed the number of values sent
 * @param hasMore true when more values exist beyond those returned
 * 
 * @see CompletionCompleteResponse
 * @see CompletionArgument
 * @since 1.0
 */
public record Completion(List<String> values, Integer total, Boolean hasMore) {
}
