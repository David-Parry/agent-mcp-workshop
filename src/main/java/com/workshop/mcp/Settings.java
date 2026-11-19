package com.workshop.mcp;

import java.util.Map;

/**
 * The runtime switches the stretch goals added, read once at startup.
 * <p>
 * They come from the environment rather than from constants for two reasons.
 * A constant {@code false} leaves the code behind it unreachable, which the
 * coverage gate would rightly refuse. And the environment is how an MCP host
 * configures a server it launches: the {@code env} block of
 * {@code lessons/mcp.json} or {@code inspector/config.json}.
 * </p>
 *
 * @param listChangedIntervalMillis how often a stream that asked for tool-list
 *                                  changes is told the list changed; zero or less
 *                                  turns the timer off, and with it the
 *                                  {@code tools.listChanged} advertisement
 * @param requireFormForListen      whether {@code subscriptions/listen} refuses a
 *                                  client that cannot show a form, with -32021
 */
public record Settings(long listChangedIntervalMillis, boolean requireFormForListen) {

    /** Milliseconds between {@code notifications/tools/list_changed} on each open stream. */
    public static final String LIST_CHANGED_MS = "WORKSHOP_LIST_CHANGED_MS";

    /** {@code true} to demonstrate -32021 on {@code subscriptions/listen}. */
    public static final String REQUIRE_FORM_FOR_LISTEN = "WORKSHOP_REQUIRE_FORM_FOR_LISTEN";

    public static final long DEFAULT_LIST_CHANGED_MILLIS = 30_000L;

    /**
     * The switches as they stand when nothing is configured.
     *
     * @return the defaults
     */
    public static Settings defaults() {
        return new Settings(DEFAULT_LIST_CHANGED_MILLIS, false);
    }

    /**
     * Reads the switches out of an environment, falling back to the default
     * for anything absent or unreadable.
     *
     * @param environment the process environment, or any map standing in for it
     * @return the settings it describes
     */
    public static Settings fromEnvironment(Map<String, String> environment) {
        return new Settings(millis(environment.get(LIST_CHANGED_MS), DEFAULT_LIST_CHANGED_MILLIS),
                            Boolean.parseBoolean(environment.get(REQUIRE_FORM_FOR_LISTEN)));
    }

    /**
     * Whether this server tells open streams that its tool list changed.
     *
     * @return true when the timer is on
     */
    public boolean announcesListChanges() {
        return listChangedIntervalMillis > 0;
    }

    private static long millis(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
