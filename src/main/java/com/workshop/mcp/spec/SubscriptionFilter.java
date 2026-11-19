package com.workshop.mcp.spec;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

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
 * The first four are the core protocol's filters. The tasks extension adds a
 * fifth, {@code taskIds}, for its {@code notifications/tasks}, and only a
 * client that declared the extension may ask for it. A server agrees only to
 * the tasks it holds: an id it never issued, or one it has already purged,
 * has no status to push.
 * </p>
 *
 * @param toolsListChanged      deliver {@code notifications/tools/list_changed}
 * @param promptsListChanged    deliver {@code notifications/prompts/list_changed}
 * @param resourcesListChanged  deliver {@code notifications/resources/list_changed}
 * @param resourceSubscriptions deliver updates for these specific resource URIs
 * @param taskIds               deliver {@code notifications/tasks} for these tasks
 *
 * @see SubscriptionsListenParams
 * @since 1.0
 */
public record SubscriptionFilter(
        Boolean toolsListChanged,
        Boolean promptsListChanged,
        Boolean resourcesListChanged,
        List<String> resourceSubscriptions,
        List<String> taskIds
) {

    /**
     * A filter of the core protocol's four, asking for no task status.
     *
     * @param toolsListChanged      deliver {@code notifications/tools/list_changed}
     * @param promptsListChanged    deliver {@code notifications/prompts/list_changed}
     * @param resourcesListChanged  deliver {@code notifications/resources/list_changed}
     * @param resourceSubscriptions deliver updates for these specific resource URIs
     */
    public SubscriptionFilter(Boolean toolsListChanged, Boolean promptsListChanged, Boolean resourcesListChanged,
                              List<String> resourceSubscriptions) {
        this(toolsListChanged, promptsListChanged, resourcesListChanged, resourceSubscriptions, null);
    }

    /**
     * Narrows a requested filter to what the given capabilities can support.
     *
     * @param capabilities what this server advertised at discovery
     * @param isKnownTask  whether this server holds a task, and so has a status to push for it
     * @return the honored subset, with unsupported entries dropped rather than
     *         set to false, so the acknowledgment says nothing it cannot back
     */
    public SubscriptionFilter honoredUnder(ServerCapabilities capabilities, Predicate<String> isKnownTask) {
        return new SubscriptionFilter(
                honored(toolsListChanged, listChanged(capabilities.tools())),
                honored(promptsListChanged, listChanged(capabilities.prompts())),
                honored(resourcesListChanged, listChanged(capabilities.resources())),
                subscribable(capabilities) ? resourceSubscriptions : null,
                pushesTaskStatus(capabilities) ? knownTasks(isKnownTask) : null);
    }

    /**
     * Reports whether this filter asks for task status, which belongs to the
     * tasks extension.
     *
     * @return true when it names at least one task
     */
    public boolean asksForTaskStatus() {
        return taskIds != null && !taskIds.isEmpty();
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
               && (resourceSubscriptions == null || resourceSubscriptions.isEmpty())
               && !asksForTaskStatus();
    }

    /**
     * The same filter without its tasks, for a stream whose tasks have all
     * finished.
     *
     * @return this filter with {@code taskIds} dropped
     */
    public SubscriptionFilter withoutTasks() {
        return new SubscriptionFilter(toolsListChanged, promptsListChanged, resourcesListChanged,
                                      resourceSubscriptions);
    }

    private List<String> knownTasks(Predicate<String> isKnownTask) {
        List<String> known = taskIds == null ? List.of()
                : taskIds.stream().filter(Objects::nonNull).distinct().filter(isKnownTask).toList();
        return known.isEmpty() ? null : known;
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

    private static boolean pushesTaskStatus(ServerCapabilities capabilities) {
        return capabilities.extensions() != null && capabilities.extensions().containsKey(MetaKeys.TASKS_EXTENSION);
    }
}
