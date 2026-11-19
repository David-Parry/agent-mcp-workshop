package com.workshop.mcp.spec;

import java.util.Map;

/**
 * Parameters of {@code notifications/tasks}, the tasks extension's push of a
 * task's state to a client that subscribed to it.
 * <p>
 * They carry what {@code tasks/get} would answer at that moment, the task and
 * whichever of {@code result}, {@code error} or {@code inputRequests} its
 * status calls for, and the subscription id that every notification on a
 * listen stream carries in its {@code _meta}. A notification is not a result,
 * so there is no {@code resultType}.
 * </p>
 *
 * @param _meta          must carry {@link MetaKeys#SUBSCRIPTION_ID}
 * @param taskId         the task identifier
 * @param status         one of {@link TaskStatus}'s string values
 * @param statusMessage  optional human-readable description of the current state
 * @param createdAt      ISO 8601 timestamp of task creation
 * @param lastUpdatedAt  ISO 8601 timestamp of the most recent status change
 * @param ttlMs          milliseconds the task may be retained
 * @param pollIntervalMs suggested polling interval in milliseconds
 * @param result         the underlying result, once the status is completed
 * @param error          the failure, once the status is failed
 * @param inputRequests  what the task needs, while the status is input_required
 *
 * @see TaskResult
 * @see SubscriptionFilter#taskIds()
 * @since 1.0
 */
public record TasksNotificationParams(
    Map<String, Object> _meta,
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
     * Tags a task's detail with the stream it is pushed on.
     *
     * @param subscriptionId the id of the listen request that subscribed to the task
     * @param detail         what {@code tasks/get} would answer now
     * @return the notification params
     */
    public static TasksNotificationParams of(RequestId subscriptionId, TaskResult detail) {
        return new TasksNotificationParams(Map.of(MetaKeys.SUBSCRIPTION_ID, subscriptionId), detail.taskId(),
                                           detail.status(), detail.statusMessage(), detail.createdAt(),
                                           detail.lastUpdatedAt(), detail.ttlMs(), detail.pollIntervalMs(),
                                           detail.result(), detail.error(), detail.inputRequests());
    }
}
