package com.workshop.mcp.spec;

/**
 * Represents an argument for completion operations in the MCP (Model Context Protocol) system.
 * <p>
 * CompletionArgument is used to provide name-value pairs for completion requests,
 * allowing clients to pass contextual information or parameters that help generate
 * more accurate or relevant completions.
 * </p>
 * 
 * @param name the name of the completion argument
 * @param value the value of the completion argument
 * 
 * @see CompletionCompleteParams
 * @since 1.0
 */
public record CompletionArgument(
    String name,
    String value
) {}
