package com.workshop.mcp.spec;

/**
 * Represents an argument definition for a prompt in the MCP (Model Context Protocol) system.
 * <p>
 * PromptArgument defines the parameters that can be passed to a prompt template.
 * Each argument has a name, description, and requirement status, allowing prompts
 * to be parameterized and reused with different values.
 * </p>
 * 
 * @param name the unique name of the argument within the prompt
 * @param description a human-readable description of the argument's purpose and expected values
 * @param required whether this argument must be provided when using the prompt
 * 
 * @see Prompt
 * @since 1.0
 */
public record PromptArgument(
    String name,
    String description,
    boolean required
) {}
