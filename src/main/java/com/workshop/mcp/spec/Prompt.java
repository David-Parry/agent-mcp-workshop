package com.workshop.mcp.spec;

import java.util.List;

/**
 * Represents a prompt template in the MCP (Model Context Protocol) system.
 * <p>
 * Prompts are reusable templates that can be filled with arguments to generate
 * messages or instructions. They provide a way to standardize common interactions
 * and ensure consistency in how the server communicates or processes requests.
 * </p>
 * 
 * @param name the unique identifier for the prompt template
 * @param description a human-readable description of the prompt's purpose and usage
 * @param arguments the list of arguments that can be provided when using this prompt, may be empty
 * 
 * @see PromptArgument
 * @since 1.0
 */
public record Prompt(
    String name,
    String description,
    List<PromptArgument> arguments
) {}
