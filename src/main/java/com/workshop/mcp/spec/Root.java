package com.workshop.mcp.spec;

/**
 * Represents a root directory or resource location in the MCP (Model Context Protocol) system.
 * <p>
 * A root provides a base URI and human-readable name for organizing and accessing resources
 * within the MCP server. Roots help structure the resource hierarchy and provide logical
 * groupings for related resources.
 * </p>
 * 
 * @param uri the URI identifying the root location, typically a file:// or custom protocol URI
 * @param name a human-readable name for the root, used for display purposes
 * 
 * @since 1.0
 */
public record Root(
    String uri,
    String name
) {}
