package com.workshop.mcp.spec;

import java.util.List;

/**
 * Which notifications a client wants delivered on a
 * {@code subscriptions/listen} stream.
 * <p>
 * A server answers with the subset it will actually honor, which is the
 * intersection of what the client asked for and what the server advertised in
 * {@link ServerCapabilities}. Silently accepting more than it can deliver
 * would leave the client waiting on notifications that never come.
 * </p>
 * <p>
 * There is no slot for task notifications: the reference client's filter has
 * exactly these four fields, so {@code notifications/tasks} is not reachable
 * through a listen stream and is delivered unsolicited instead.
 * </p>
 *
 * @param toolsListChanged      deliver {@code notifications/tools/list_changed}
 * @param promptsListChanged    deliver {@code notifications/prompts/list_changed}
 * @param resourcesListChanged  deliver {@code notifications/resources/list_changed}
 * @param resourceSubscriptions deliver updates for these specific resource URIs
 *
 * @see SubscriptionsListenParams
 * @since 1.0
 */
public record SubscriptionFilter(
        Boolean toolsListChanged,
        Boolean promptsListChanged,
        Boolean resourcesListChanged,
        List<String> resourceSubscriptions
) {

    /**
     * Narrows a requested filter to what the given capabilities can support.
     *
     * @param capabilities what this server advertised at discovery
     * @return the honored subset, with unsupported entries dropped rather than
     *         set to false, so the acknowledgment says nothing it cannot back
     */
    public SubscriptionFilter honoredUnder(ServerCapabilities capabilities) {
        return new SubscriptionFilter(
                honored(toolsListChanged, listChanged(capabilities.tools())),
                honored(promptsListChanged, listChanged(capabilities.prompts())),
                honored(resourcesListChanged, listChanged(capabilities.resources())),
                subscribable(capabilities) ? resourceSubscriptions : null);
    }

    /**
     * Reports whether this filter asks for anything at all, which decides
     * whether a subscription is worth holding open.
     *
     * @return true when at least one notification would be delivered
     */
    public boolean isEmpty() {
        return !Boolean.TRUE.equals(toolsListChanged)
               && !Boolean.TRUE.equals(promptsListChanged)
               && !Boolean.TRUE.equals(resourcesListChanged)
               && (resourceSubscriptions == null || resourceSubscriptions.isEmpty());
    }

    private static Boolean honored(Boolean requested, boolean supported) {
        return Boolean.TRUE.equals(requested) && supported ? Boolean.TRUE : null;
    }

    private static boolean listChanged(Capability capability) {
        return capability != null && Boolean.TRUE.equals(capability.listChanged());
    }

    private static boolean subscribable(ServerCapabilities capabilities) {
        Capability resources = capabilities.resources();
        return resources != null && Boolean.TRUE.equals(resources.subscribe());
    }
}
