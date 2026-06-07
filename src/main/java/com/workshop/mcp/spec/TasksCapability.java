package com.workshop.mcp.spec;

/**
 * Server-side {@code tasks} capability declaration sent during initialization.
 * <p>
 * Per MCP 2025-11-25 spec § "Server Capabilities", this object advertises
 * three things: whether the server implements {@code tasks/list}, whether it
 * implements {@code tasks/cancel}, and which incoming server-side request
 * types support task augmentation.
 * </p>
 *
 * <pre>{@code
 * {
 *   "tasks": {
 *     "list": {},
 *     "cancel": {},
 *     "requests": { "tools": { "call": {} } }
 *   }
 * }
 * }</pre>
 *
 * @param list     {@link Capability} marker; non-null = {@code tasks/list}
 *                 supported
 * @param cancel   {@link Capability} marker; non-null = {@code tasks/cancel}
 *                 supported
 * @param requests nested toggle map of which inbound request types accept
 *                 task augmentation
 *
 * @since 1.0
 */
public record TasksCapability(
    Capability list,
    Capability cancel,
    ServerTaskRequests requests
) {}
