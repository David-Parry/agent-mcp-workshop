package com.workshop.mcp.spec;

/**
 * Parameters for {@code tasks/get}. Requestors send this to poll a task's
 * current state without blocking until terminal.
 *
 * @param taskId the identifier returned in a prior {@link CreateTaskResult}
 *
 * @see Task
 * @since 1.0
 */
public record TasksGetParams(String taskId) {}
