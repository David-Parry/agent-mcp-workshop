package com.workshop.mcp.spec;

/**
 * Parameters for {@code tasks/get}. Requestors send this to poll a task's
 * current state without blocking until terminal.
 * <p>
 * Polling is the only way to collect a task's outcome on this revision: the
 * blocking {@code tasks/result} method was removed, and the terminal
 * {@code result} or {@code error} is delivered by whichever {@code tasks/get}
 * observes the terminal status.
 * </p>
 *
 * @param taskId the identifier from the {@link TaskResult} handle that created the task
 *
 * @see Task
 * @since 1.0
 */
public record TasksGetParams(String taskId) {}
