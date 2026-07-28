package com.workshop.mcp.spec;

/**
 * Parameters of {@code subscriptions/listen}.
 * <p>
 * This method replaced {@code resources/subscribe} and
 * {@code resources/unsubscribe}, folding every server-to-client notification
 * into one long-lived request. On stdio it is not answered while the
 * subscription is live: notifications are interleaved on the same stdout
 * stream and demultiplexed by
 * {@code _meta["io.modelcontextprotocol/subscriptionId"]}, which holds the id
 * of this request. A result arrives only when the subscription ends
 * gracefully.
 * </p>
 *
 * @param notifications the notifications the client wants delivered
 *
 * @see SubscriptionFilter
 * @see SubscriptionsListenResult
 * @since 1.0
 */
public record SubscriptionsListenParams(SubscriptionFilter notifications) {}
