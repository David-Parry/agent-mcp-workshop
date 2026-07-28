package com.workshop.mcp.spec;

import java.util.Map;

/**
 * Parameters of {@code notifications/subscriptions/acknowledged}, which
 * reports back the subset of a {@code subscriptions/listen} filter the server
 * will actually honor.
 * <p>
 * The {@code _meta} must carry {@link MetaKeys#SUBSCRIPTION_ID}. A client that
 * does not find it there cannot match the acknowledgment to its pending listen
 * request, and waits indefinitely — with no error and no timeout, which makes
 * a missing id one of the harder failures to diagnose in this revision.
 * </p>
 *
 * @param _meta         must carry {@link MetaKeys#SUBSCRIPTION_ID}
 * @param notifications the honored subset of the requested filter
 *
 * @see SubscriptionFilter
 * @since 1.0
 */
public record SubscriptionsAcknowledgedParams(Map<String, Object> _meta, SubscriptionFilter notifications) {

    /**
     * Creates an acknowledgment tagged with its subscription id.
     *
     * @param subscriptionId the id of the listen request being acknowledged
     * @param honored        the subset of the filter the server will deliver
     * @return the notification params
     */
    public static SubscriptionsAcknowledgedParams of(RequestId subscriptionId, SubscriptionFilter honored) {
        return new SubscriptionsAcknowledgedParams(
                Map.of(MetaKeys.SUBSCRIPTION_ID, subscriptionId), honored);
    }
}
