package com.workshop.mcp.spec;

/**
 * Represents the unique method keys used in the MCP (Model Context Protocol) for JSON-RPC communication.
 * <p>
 * This enum defines the method names and notification types of protocol
 * revision {@code 2026-07-28}. Deletions in that revision are physical: a
 * method absent from the registry must be answered with
 * {@link ErrorCodes#METHOD_NOT_FOUND}, even if the server would happily
 * service it. {@code initialize}, {@code notifications/initialized},
 * {@code ping}, {@code logging/setLevel},
 * {@code notifications/roots/list_changed}, {@code resources/subscribe},
 * {@code resources/unsubscribe}, {@code tasks/result}, and
 * {@code tasks/list} are all gone for that reason.
 * </p>
 *
 * @see JsonRpcRequest
 * @see JsonRpcNotification
 * @since 1.0
 */
public enum UniqueKeys {
    /**
     * Method for discovering a server's supported protocol revisions,
     * capabilities, and identity.
     * <p>
     * Introduced in {@code 2026-07-28} as the sessionless replacement for the
     * {@code initialize} handshake. Servers MUST implement it. On stdio a
     * client sends it first as a backward-compatibility probe: an answer means
     * the server speaks the stateless lifecycle, and silence or an error means
     * it should fall back to the legacy handshake.
     * </p>
     */
    SERVER_DISCOVER("server/discover"),

    /**
     * Method for listing all available prompts.
     * Returns a list of prompt templates that can be used.
     */
    PROMPTS_LIST("prompts/list"),

    /**
     * Method for retrieving a specific prompt by name.
     * Returns the expanded prompt with provided arguments.
     */
    PROMPTS_GET("prompts/get"),

    /**
     * Method for requesting completion suggestions.
     * Used for auto-completion functionality.
     */
    COMPLETION_COMPLETE("completion/complete"),

    /**
     * Notification sent when an operation is cancelled.
     * <p>
     * Its {@code requestId} is required on this revision. It is also how a
     * client tears down a {@code subscriptions/listen} stream, since stdio has
     * no stream to close.
     * </p>
     */
    NOTIFICATION_CANCELLED("notifications/cancelled"),

    /**
     * Method for listing all available tools.
     * Returns the tools that can be invoked by the client.
     */
    TOOLS_LIST("tools/list"),

    /**
     * Special key indicating that a method was not found.
     * Used internally for error handling.
     */
    NOT_FOUND("not_found"),

    /**
     * Method for invoking a specific tool.
     * Executes a tool with the provided arguments.
     */
    TOOLS_CALL("tools/call"),

    /**
     * Method for listing available resources.
     * Returns resources that can be accessed by the client.
     */
    RESOURCES_LIST("resources/list"),

    /**
     * Method for listing available resource templates.
     * <p>
     * Clients fetch this alongside {@code resources/list} whenever the server
     * declares any resource capability, so a server that advertises resources
     * must answer it even when it has no templates.
     * </p>
     */
    RESOURCES_TEMPLATES_LIST("resources/templates/list"),

    /**
     * Method for reading resource content.
     * Retrieves the actual content of one or more resources.
     */
    RESOURCES_READ("resources/read"),

    /**
     * Method for opening a long-lived notification stream.
     * <p>
     * Introduced in {@code 2026-07-28} to replace the HTTP GET endpoint and
     * the {@code resources/subscribe} family. On stdio the request is left
     * unanswered for the life of the subscription; notifications are
     * interleaved on stdout and demultiplexed by
     * {@link MetaKeys#SUBSCRIPTION_ID}.
     * </p>
     */
    SUBSCRIPTIONS_LISTEN("subscriptions/listen"),

    /**
     * Notification reporting which subset of a {@code subscriptions/listen}
     * filter the server honored.
     * <p>
     * It must carry {@link MetaKeys#SUBSCRIPTION_ID}; without it the client
     * waits forever with no error and no timeout.
     * </p>
     */
    NOTIFICATIONS_SUBSCRIPTIONS_ACKNOWLEDGED("notifications/subscriptions/acknowledged");

    private final String value;

    /**
     * Constructs a UniqueKeys enum constant with its string representation.
     *
     * @param value the string value used in JSON-RPC method names
     */
    UniqueKeys(String value) {
        this.value = value;
    }

    /**
     * Gets the string value of this method key.
     *
     * @return the string representation used in JSON-RPC
     */
    public String getValue() {
        return value;
    }

    /**
     * Returns the string value of this method key.
     *
     * @return the string representation used in JSON-RPC
     */
    @Override
    public String toString() {
        return value;
    }

    /**
     * Returns the enum constant for the given string value.
     * <p>
     * This method performs a case-insensitive lookup and returns
     * {@link #NOT_FOUND} if no matching constant is found.
     * </p>
     *
     * @param value the string value to look up
     * @return the corresponding UniqueKeys enum constant, or {@link #NOT_FOUND} if not found
     */
    public static UniqueKeys fromValue(String value) {
        for (UniqueKeys key : UniqueKeys.values()) {
            if (key.value.equalsIgnoreCase(value)) {
                return key;
            }
        }
        return NOT_FOUND;
    }
}
