package com.workshop.mcp.spec;

/**
 * Parameters for {@code tasks/result}. The spec says receivers must block this
 * response until the task reaches a terminal status and then return the
 * underlying request's actual result (or its JSON-RPC error).
 *
 * @param taskId the identifier returned in a prior {@link CreateTaskResult}
 *
 * @since 1.0
 */
public record TasksResultParams(String taskId) {}
