package com.workshop.mcp.spec;

/**
 * Result of an elicitation request in the MCP (Model Context Protocol).
 * 
 * <p>This record represents the response from a client when handling an "elicitation/create" request.
 * The client can respond with one of three actions: accept, decline, or cancel.</p>
 * 
 * <p>Response actions:</p>
 * <ul>
 *   <li><strong>accept</strong> - User explicitly approved and submitted data</li>
 *   <li><strong>decline</strong> - User explicitly declined the request</li>
 *   <li><strong>cancel</strong> - User dismissed without making an explicit choice</li>
 * </ul>
 * 
 * @param action The user's response action ("accept", "decline", or "cancel")
 * @param content The submitted data (only present when action is "accept")
 * 
 * @since 1.0
 * @see ElicitationCreateParams
 */
public record ElicitationCreateResult(
        String action,
        Object content
) {}