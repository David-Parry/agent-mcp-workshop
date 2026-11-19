package com.workshop.mcp;

import java.util.Base64;
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
 * @param stateKey                  the Base64 HMAC key that signs {@code requestState},
 *                                  or null for a random key per process
 * @param elicitationPort           the loopback port of the URL-elicitation page,
 *                                  or 0 for any free port
 */
public record Settings(long listChangedIntervalMillis, boolean requireFormForListen, String stateKey,
                       int elicitationPort) {

    /** Milliseconds between {@code notifications/tools/list_changed} on each open stream. */
    public static final String LIST_CHANGED_MS = "WORKSHOP_LIST_CHANGED_MS";

    /** {@code true} to demonstrate -32021 on {@code subscriptions/listen}. */
    public static final String REQUIRE_FORM_FOR_LISTEN = "WORKSHOP_REQUIRE_FORM_FOR_LISTEN";

    /** A Base64 key of at least {@value #MIN_STATE_KEY_BYTES} bytes, so a sealed state survives a restart. */
    public static final String STATE_KEY = "WORKSHOP_STATE_KEY";

    /** A fixed port for the URL-elicitation page. */
    public static final String ELICITATION_PORT = "WORKSHOP_ELICITATION_PORT";

    public static final long DEFAULT_LIST_CHANGED_MILLIS = 30_000L;

    /** The shortest state key accepted: the output size of SHA-256. */
    public static final int MIN_STATE_KEY_BYTES = 32;

    /**
     * The switches as they stand when nothing is configured.
     *
     * @return the defaults
     */
    public static Settings defaults() {
        return new Settings(DEFAULT_LIST_CHANGED_MILLIS, false, null, 0);
    }

    /**
     * Reads the switches out of an environment, falling back to the default
     * for anything absent or unreadable.
     *
     * @param environment the process environment, or any map standing in for it
     * @return the settings it describes
     */
    public static Settings fromEnvironment(Map<String, String> environment) {
        long port = number(environment.get(ELICITATION_PORT), 0);
        return new Settings(number(environment.get(LIST_CHANGED_MS), DEFAULT_LIST_CHANGED_MILLIS),
                            Boolean.parseBoolean(environment.get(REQUIRE_FORM_FOR_LISTEN)),
                            environment.get(STATE_KEY),
                            port >= 0 && port <= 65_535 ? (int) port : 0);
    }

    /** @return these settings with a different list-changed interval */
    public Settings withListChangedIntervalMillis(long millis) {
        return new Settings(millis, requireFormForListen, stateKey, elicitationPort);
    }

    /** @return these settings with listen requiring a form, or not */
    public Settings withRequireFormForListen(boolean require) {
        return new Settings(listChangedIntervalMillis, require, stateKey, elicitationPort);
    }

    /** @return these settings with a different Base64 state key */
    public Settings withStateKey(String key) {
        return new Settings(listChangedIntervalMillis, requireFormForListen, key, elicitationPort);
    }

    /** @return these settings with a different elicitation-page port */
    public Settings withElicitationPort(int port) {
        return new Settings(listChangedIntervalMillis, requireFormForListen, stateKey, port);
    }

    /**
     * Whether this server tells open streams that its tool list changed.
     *
     * @return true when the timer is on
     */
    public boolean announcesListChanges() {
        return listChangedIntervalMillis > 0;
    }

    /**
     * The state key as bytes.
     *
     * @return the key, or null when none was given or it is unusable: not
     *         Base64, or shorter than {@value #MIN_STATE_KEY_BYTES} bytes
     */
    public byte[] stateKeyBytes() {
        if (stateKey == null) {
            return null;
        }
        try {
            byte[] key = Base64.getDecoder().decode(stateKey.trim());
            return key.length < MIN_STATE_KEY_BYTES ? null : key;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Describes the settings without the state key, which must never reach a log.
     */
    @Override
    public String toString() {
        return "Settings[listChangedIntervalMillis=" + listChangedIntervalMillis
               + ", requireFormForListen=" + requireFormForListen
               + ", stateKey=" + (stateKey == null ? "none" : "redacted")
               + ", elicitationPort=" + elicitationPort + "]";
    }

    private static long number(String value, long fallback) {
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
