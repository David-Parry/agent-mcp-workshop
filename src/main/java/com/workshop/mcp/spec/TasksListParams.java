package com.workshop.mcp.spec;

/**
 * Parameters for {@code tasks/list}. Pagination is cursor-based per spec.
 *
 * @param cursor opaque cursor from a prior {@link TasksListResult#nextCursor()};
 *               {@code null} or empty requests the first page
 *
 * @since 1.0
 */
public record TasksListParams(String cursor) {}
