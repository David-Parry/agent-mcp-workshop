package com.workshop.mcp.spec;

/**
 * Parameters of the {@code notifications/cancelled} notification.
 * <p>
 * On protocol revision {@code 2026-07-28} the {@code requestId} field is
 * required; earlier revisions allowed it to be omitted. It identifies the
 * in-flight request the sender is abandoning, and is also how a client tears
 * down a {@code subscriptions/listen} stream, since there is no stream to
 * close on stdio.
 * </p>
 *
 * @param requestId the id of the request being cancelled
 * @param reason    optional human-readable explanation
 *
 * @since 1.0
 */
public record NotificationCancelledParams(RequestId requestId, String reason) {
}
