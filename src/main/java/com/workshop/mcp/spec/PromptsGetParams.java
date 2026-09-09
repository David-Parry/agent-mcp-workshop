package com.workshop.mcp.spec;

import java.util.Map;

/**
 * Represents parameters for retrieving a specific prompt in the MCP (Model Context Protocol) system.
 * <p>
 * PromptsGetParams is used to request a prompt by name and provide any required
 * arguments for that prompt. The server will process the prompt template with the
 * provided arguments and return the resulting content.
 * </p>
 * 
 * @param _meta optional metadata information for the prompt request, including progress tracking
 * @param name the name of the prompt to retrieve
 * @param arguments a map of argument names to their values for the prompt template
 * 
 * @see PromptsGetResult
 * @see Prompt
 * @see MetaInfo
 * @since 1.0
 */
public record PromptsGetParams(
    MetaInfo _meta,
    String name,
    Map<String, String> arguments
) {}
