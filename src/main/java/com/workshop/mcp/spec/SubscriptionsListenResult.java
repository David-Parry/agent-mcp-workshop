package com.workshop.mcp.spec;

import java.util.Map;

/**
 * The result that ends a {@code subscriptions/listen} stream gracefully.
 * <p>
 * It carries no payload; its arrival is the message. A stream that closes
 * without one is read by the client as a disconnect rather than a clean end.
 * </p>
 * <p>
 * The {@code _meta} here is worth noting: across the whole
 * {@code 2026-07-28} schema set this is the one place a {@code _meta} member
 * is genuinely required rather than optional, because the subscription id is
 * the only thing tying this result to the request it closes.
 * </p>
 *
 * @param resultType always {@link ResultType#COMPLETE}
 * @param _meta      must carry {@link MetaKeys#SUBSCRIPTION_ID}
 *
 * @see SubscriptionsListenParams
 * @since 1.0
 */
public record SubscriptionsListenResult(String resultType, Map<String, Object> _meta) {

    /**
     * Creates the graceful-close result for a subscription.
     *
     * @param subscriptionId the id of the listen request being closed
     * @return the close result
     */
    public static SubscriptionsListenResult closing(RequestId subscriptionId) {
        return new SubscriptionsListenResult(ResultType.COMPLETE,
                                             Map.of(MetaKeys.SUBSCRIPTION_ID, subscriptionId));
    }
}
