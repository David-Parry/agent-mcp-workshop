package com.workshop.mcp.spec;

import java.util.List;

/**
 * Represents the result of a resources list request in the MCP (Model Context Protocol) system.
 * <p>
 * ResourcesListResult contains a list of available resources and supports pagination
 * through the nextCursor field. This allows servers to return large lists of
 * resources in manageable chunks, improving performance and scalability.
 * </p>
 * 
 * @param resources the list of available resources in this result set
 * @param nextCursor an optional cursor for pagination, null if no more results
 * 
 * @see Resource
 * @since 1.0
 */
public record ResourcesListResult(List<Resource> resources, String nextCursor) {
}
