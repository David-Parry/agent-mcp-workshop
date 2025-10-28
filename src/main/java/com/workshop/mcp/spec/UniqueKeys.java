package com.workshop.mcp.spec;

/**
 * Represents the unique method keys used in the MCP (Model Context Protocol) for JSON-RPC communication.
 * <p>
 * This enum defines all the standard method names and notification types that can be
 * used in MCP requests and notifications. Each key corresponds to a specific operation
 * or event in the protocol.
 * </p>
 * 
 * @see JsonRpcRequest
 * @see JsonRpcNotification
 * @since 1.0
 */
public enum UniqueKeys {
    /**
     * Method for initializing a connection between client and server.
     * Used to establish protocol version and exchange capabilities.
     */
    INITIALIZE("initialize"),
    
    /**
     * Notification sent after successful initialization.
     * Indicates that the connection is ready for use.
     */
    NOTIFICATIONS_INITIALIZED("notifications/initialized"),
    
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
     * Indicates that a long-running operation has been terminated.
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
     * Method for health check or keep-alive.
     * Used to verify that the connection is still active.
     */
    PING("ping"),
    
    /**
     * Method for listing available roots.
     * Returns the root directories or locations available in the server.
     */
    ROOTS("roots"),
    
    /**
     * Notification sent when the roots list has changed.
     * Indicates that roots have been added, removed, or modified.
     */
    NOTIFICATIONS_ROOTS_LIST_CHANGED("notifications/roots/list_changed"),
    
    /**
     * Method for listing available resources.
     * Returns resources that can be accessed by the client.
     */
    RESOURCES_LIST("resources/list"),
    
    /**
     * Method for reading resource content.
     * Retrieves the actual content of one or more resources.
     */
    RESOURCES_READ("resources/read"),

    /**
     * Method for creating a new sampling message.
     * Used to initiate a sampling operation with specific parameters.
     */
    SAMPLING_CREATE_MESSAGE("sampling/createMessage"),

    /**
     * Method for creating a new elicitation message.
     * Used to initiate an elicitation operation with questions for the user.
     */
    ELICITATION_CREATE_MESSAGE("elicitation/create");

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