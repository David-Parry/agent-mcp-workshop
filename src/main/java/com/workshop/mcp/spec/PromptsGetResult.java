package com.workshop.mcp.spec;

import java.util.List;

/**
 * Represents the result of retrieving a specific prompt in the MCP (Model Context Protocol) system.
 * <p>
 * PromptsGetResult contains the processed prompt content after applying any provided
 * arguments. The result includes a description and a list of messages that represent
 * the expanded prompt template.
 * </p>
 * 
 * @param description a description of the prompt result or its purpose
 * @param messages the list of messages generated from the prompt template
 * 
 * @see PromptsGetParams
 * @see Message
 * @since 1.0
 */
public record PromptsGetResult(
    String description,
    List<Message> messages
) {}
