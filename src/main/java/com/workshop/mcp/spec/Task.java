package com.workshop.mcp.spec;

/**
 * The durable state of one unit of background work.
 * <p>
 * Tasks moved out of the core protocol in revision {@code 2026-07-28} and into
 * the {@code io.modelcontextprotocol/tasks} extension. Three things changed
 * with the move. The {@code ttl} and {@code pollInterval} fields were renamed
 * to {@code ttlMs} and {@code pollIntervalMs}. The blocking
 * {@code tasks/result} method was replaced by polling {@code tasks/get}. And
 * task creation became server-directed: there is no {@code params.task} opt-in
 * on a request any more, so the server decides per call whether to hand back a
 * handle.
 * </p>
 * <p>
 * This record is the store's view of a task. {@link TaskResult} is how it
 * appears on the wire, which differs depending on whether it is answering
 * {@code tools/call}, {@code tasks/get}, or {@code notifications/tasks}.
 * </p>
 *
 * @param taskId         receiver-generated unique identifier for the task
 * @param status         one of {@link TaskStatus}'s string values
 * @param statusMessage  optional human-readable description of the current state
 * @param createdAt      ISO 8601 timestamp of task creation
 * @param lastUpdatedAt  ISO 8601 timestamp of the most recent status change
 * @param ttlMs          milliseconds the task may be retained, null meaning unlimited
 * @param pollIntervalMs suggested polling interval in milliseconds, may be null
 *
 * @see TaskStatus
 * @see TaskResult
 * @since 1.0
 */
public record Task(
    String taskId,
    String status,
    String statusMessage,
    String createdAt,
    String lastUpdatedAt,
    Long ttlMs,
    Long pollIntervalMs
) {}
