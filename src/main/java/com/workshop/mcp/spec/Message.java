package com.workshop.mcp.spec;

/**
 * Represents a message in a conversation within the MCP (Model Context Protocol) system.
 * <p>
 * Messages are the fundamental units of communication between clients and servers,
 * containing both the role of the sender and the content of the message. The role
 * indicates whether the message is from a user, assistant, or system.
 * </p>
 * 
 * @param role the role of the message sender (e.g., "user", "assistant", "system")
 * @param content the content of the message, which may include text, images, or other data
 * 
 * @see MessageContent
 * @since 1.0
 */
public record Message(
    String role,
    MessageContent content
) {
    /**
     * A predefined message template for keyword search instructions.
     * This constant provides a standard prompt for initiating keyword searches
     * from the project root.
     */
    public static final String KEY_WORD_MESSAGE = "Start from the root of the project and use the key_word_search tool to search for this specific keyword:";
}
