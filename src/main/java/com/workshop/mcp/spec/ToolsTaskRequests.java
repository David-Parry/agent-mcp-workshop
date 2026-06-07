package com.workshop.mcp.spec;

/**
 * Server-side toggle indicating which {@code tools/*} request types may be
 * augmented with a task.
 * <p>
 * Wire shape (when {@link #call()} is non-null):
 * </p>
 * <pre>{@code
 * { "tools": { "call": {} } }
 * }</pre>
 *
 * @param call {@link Capability} marker; non-null means {@code tools/call}
 *             accepts task augmentation
 *
 * @since 1.0
 */
public record ToolsTaskRequests(Capability call) {}
