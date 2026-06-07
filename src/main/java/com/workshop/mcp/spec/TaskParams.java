package com.workshop.mcp.spec;

/**
 * Augmentation payload that requestors attach to a task-eligible request to
 * opt into task-based (call-now / fetch-later) execution.
 * <p>
 * Per MCP 2025-11-25 spec § "Task Parameters", this object lives under the
 * {@code task} field of the request's {@code params}:
 * </p>
 * <pre>{@code
 * { "method": "tools/call",
 *   "params": { "name": "...", "arguments": {...}, "task": { "ttl": 60000 } } }
 * }</pre>
 * <p>
 * When this field is present in {@link ToolCallParams#task()} the receiver
 * responds with a {@link CreateTaskResult} instead of the underlying result.
 * </p>
 *
 * @param ttl requested retention duration in milliseconds; may be {@code null}
 *            to let the receiver pick a default
 *
 * @see CreateTaskResult
 * @see ToolCallParams
 * @since 1.0
 */
public record TaskParams(Long ttl) {}
