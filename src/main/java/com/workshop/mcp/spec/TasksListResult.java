package com.workshop.mcp.spec;

import java.util.List;

/**
 * Result shape for {@code tasks/list}.
 *
 * @param tasks      tasks visible to the requestor's authorization context
 * @param nextCursor opaque token for the next page, or {@code null} when this
 *                   is the final page
 *
 * @see Task
 * @since 1.0
 */
public record TasksListResult(List<Task> tasks, String nextCursor) {}
