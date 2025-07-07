package com.workshop.mcp.spec;

/**
 * Represents a response to a completion request in the MCP (Model Context Protocol) system.
 * <p>
 * This response contains the completion suggestions along with metadata about the
 * total number of available completions and whether additional completions exist
 * beyond those returned in the current response.
 * </p>
 * 
 * @param completion the completion object containing the list of suggested values
 * @param total the total number of completions available (may be greater than returned values)
 * @param hasMore indicates whether there are more completions available beyond those returned
 * 
 * @see Completion
 * @see CompletionCompleteParams
 * @since 1.0
 */
public record CompletionCompleteResponse(Completion completion, Integer total, Boolean hasMore) {

}
