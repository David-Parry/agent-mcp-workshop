package com.workshop.mcp.spec;

/**
 * Parameters for {@code tasks/cancel}. Receivers must reject cancel attempts
 * targeting a task already in a terminal state with JSON-RPC error
 * {@code -32602} (Invalid params).
 *
 * @param taskId the task to cancel
 *
 * @since 1.0
 */
public record TasksCancelParams(String taskId) {}
