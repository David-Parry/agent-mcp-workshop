package com.workshop.mcp.spec;

/**
 * Represents the durable state of a task-augmented request in the MCP
 * (Model Context Protocol) system.
 * <p>
 * Tasks were introduced in version 2025-11-25 of the MCP specification as an
 * experimental utility for deferred execution and polling. A task is a
 * receiver-managed state machine that carries the execution state of a
 * task-augmented request and is uniquely identified by a receiver-generated
 * {@code taskId}.
 * </p>
 * <p>
 * The same {@code Task} shape is reused as the body of:
 * <ul>
 *   <li>The {@code task} field of a {@link CreateTaskResult}</li>
 *   <li>The result of {@code tasks/get}</li>
 *   <li>The result of {@code tasks/cancel}</li>
 *   <li>The {@code params} of {@code notifications/tasks/status}</li>
 *   <li>Each entry of the {@code tasks} array in {@link TasksListResult}</li>
 * </ul>
 *
 * @param taskId         receiver-generated unique identifier for the task
 * @param status         one of {@link TaskStatus}'s string values
 * @param statusMessage  optional human-readable description of the current state
 * @param createdAt      ISO 8601 timestamp of task creation
 * @param lastUpdatedAt  ISO 8601 timestamp of the most recent status change
 * @param ttl            milliseconds the task may be retained from creation
 * @param pollInterval   suggested polling interval in milliseconds, may be null
 *
 * @see TaskStatus
 * @see CreateTaskResult
 * @see TasksListResult
 * @since 1.0
 */
public record Task(
    String taskId,
    String status,
    String statusMessage,
    String createdAt,
    String lastUpdatedAt,
    Long ttl,
    Long pollInterval
) {}
