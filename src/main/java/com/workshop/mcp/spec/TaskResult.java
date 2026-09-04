package com.workshop.mcp.spec;

import java.util.Map;

/**
 * The wire projection of a {@link Task}.
 * <p>
 * The same flat set of fields serves three different messages, distinguished
 * by {@code resultType} and by which of the trailing payload fields are
 * present:
 * </p>
 * <ul>
 *   <li>{@link #handle(Task)} answers {@code tools/call} with
 *       {@code resultType: "task"}, telling the client the work is running and
 *       to poll for it.</li>
 *   <li>{@link #detail(Task, Object, JsonRpcError, Map)} answers
 *       {@code tasks/get} with {@code resultType: "complete"}, adding the
 *       payload the status calls for — {@code result} when completed,
 *       {@code error} when failed, {@code inputRequests} when input is
 *       required.</li>
 *   <li>{@link #notification(Task, Object, JsonRpcError)} forms the params of
 *       {@code notifications/tasks}, which are task fields rather than a
 *       result and so carry no {@code resultType}.</li>
 * </ul>
 * <p>
 * Fields left null are omitted from the JSON, which is what keeps one record
 * usable for all three shapes.
 * </p>
 *
 * @param resultType     {@link ResultType#TASK}, {@link ResultType#COMPLETE}, or null for a notification
 * @param taskId         the task identifier
 * @param status         one of {@link TaskStatus}'s string values
 * @param statusMessage  optional human-readable description of the current state
 * @param createdAt      ISO 8601 timestamp of task creation
 * @param lastUpdatedAt  ISO 8601 timestamp of the most recent status change
 * @param ttlMs          milliseconds the task may be retained, null meaning unlimited
 * @param pollIntervalMs suggested polling interval in milliseconds
 * @param result         the underlying result, required once the status is completed
 * @param error          the failure, required once the status is failed
 * @param inputRequests  what the task needs, required while the status is input_required
 *
 * @see Task
 * @since 1.0
 */
public record TaskResult(
    String resultType,
    String taskId,
    String status,
    String statusMessage,
    String createdAt,
    String lastUpdatedAt,
    Long ttlMs,
    Long pollIntervalMs,
    Object result,
    JsonRpcError error,
    Map<String, InputRequest> inputRequests
) {

    /**
     * Projects a task as the handle returned from {@code tools/call}.
     *
     * @param task the task that was just created
     * @return the handle, carrying {@link ResultType#TASK}
     */
    public static TaskResult handle(Task task) {
        return new TaskResult(ResultType.TASK, task.taskId(), task.status(), task.statusMessage(),
                              task.createdAt(), task.lastUpdatedAt(), task.ttlMs(), task.pollIntervalMs(),
                              null, null, null);
    }

    /**
     * Projects a task as the result of {@code tasks/get}.
     *
     * @param task          the current task state
     * @param result        the underlying result when completed, otherwise null
     * @param error         the failure when failed, otherwise null
     * @param inputRequests what the task is waiting for, otherwise null
     * @return the detailed snapshot, carrying {@link ResultType#COMPLETE}
     */
    public static TaskResult detail(Task task, Object result, JsonRpcError error,
                                    Map<String, InputRequest> inputRequests) {
        return new TaskResult(ResultType.COMPLETE, task.taskId(), task.status(), task.statusMessage(),
                              task.createdAt(), task.lastUpdatedAt(), task.ttlMs(), task.pollIntervalMs(),
                              result, error, inputRequests);
    }

    /**
     * Projects a task as the params of {@code notifications/tasks}.
     *
     * @param task   the current task state
     * @param result the underlying result when completed, otherwise null
     * @param error  the failure when failed, otherwise null
     * @return the notification params, with no {@code resultType}
     */
    public static TaskResult notification(Task task, Object result, JsonRpcError error) {
        return new TaskResult(null, task.taskId(), task.status(), task.statusMessage(),
                              task.createdAt(), task.lastUpdatedAt(), task.ttlMs(), task.pollIntervalMs(),
                              result, error, null);
    }
}
