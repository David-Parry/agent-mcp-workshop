package com.workshop.mcp.spec;

/**
 * Represents the content of a message in the MCP (Model Context Protocol) system.
 * <p>
 * MessageContent encapsulates the actual content of a message along with its type.
 * This allows messages to contain different types of content such as plain text,
 * formatted text, or other structured data, with the type field indicating how
 * the content should be interpreted.
 * </p>
 * 
 * @param text the actual content text of the message
 * @param type the type of content (e.g., "text", "markdown", "html")
 * 
 * @see Message
 * @see ContentItem
 * @since 1.0
 */
public record MessageContent(
    String text,
    String type
) {}
