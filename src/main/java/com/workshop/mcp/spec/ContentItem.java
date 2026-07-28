package com.workshop.mcp.spec;

/**
 * Represents a single content item within a message in the MCP (Model Context Protocol) system.
 * <p>
 * ContentItem encapsulates a piece of content along with its type, allowing messages
 * to contain different kinds of content such as plain text, markdown, code, or other
 * structured data. The type field helps clients properly render or process the content.
 * </p>
 * 
 * @param text the actual content text
 * @param type the type of content (e.g., "text", "markdown", "code")
 * 
 * @see MessageContent
 * @since 1.0
 */
public record ContentItem(
    String text,
    String type
) {}
