package com.workshop.mcp.spec;

/**
 * Represents the capabilities provided by an MCP (Model Context Protocol) server.
 * <p>
 * ServerCapabilities defines what features and operations the server supports,
 * including tools, prompts, and resources. Each capability indicates whether
 * the server supports list change notifications and subscriptions for that
 * particular feature.
 * </p>
 * 
 * @param tools capability configuration for tool-related features
 * @param prompts capability configuration for prompt-related features
 * @param resources capability configuration for resource-related features
 * @param completions capability configuration for resource-related features
 * 
 * @see Capability
 * @see InitializeResult
 * @since 1.0
 */
public record ServerCapabilities(
    Capability tools,
    Capability prompts,
    Capability resources,
    Capability completions
) {}
