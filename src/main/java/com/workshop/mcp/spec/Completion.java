package com.workshop.mcp.spec;

import java.util.List;

/**
 * Represents a completion result in the MCP (Model Context Protocol) system.
 * <p>
 * A completion contains a list of suggested values that can be used for
 * auto-completion or suggestion features. These values represent possible
 * completions for a partial input or context.
 * </p>
 * 
 * @param values the list of completion values/suggestions
 * 
 * @see CompletionCompleteResponse
 * @see CompletionArgument
 * @since 1.0
 */
public record Completion(List<String> values) {
}
