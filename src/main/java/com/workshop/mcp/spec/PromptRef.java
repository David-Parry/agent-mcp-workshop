package com.workshop.mcp.spec;

/**
 * Represents a reference to a prompt in the MCP (Model Context Protocol) system.
 * <p>
 * PromptRef is used to reference existing prompts by their type and name,
 * allowing for reuse and composition of prompts in different contexts.
 * This enables modular prompt design and management.
 * </p>
 * 
 * @param type the type or category of the prompt reference
 * @param name the unique name identifying the specific prompt
 * 
 * @see Prompt
 * @since 1.0
 */
public record PromptRef(
    String type,
    String name
) {}
