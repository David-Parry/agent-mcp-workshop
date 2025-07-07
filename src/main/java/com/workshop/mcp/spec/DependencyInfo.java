package com.workshop.mcp.spec;

/**
 * Represents dependency information for components in the MCP (Model Context Protocol) system.
 * <p>
 * DependencyInfo captures the essential metadata about a dependency, following common
 * dependency management conventions. This information can be used to track and manage
 * dependencies between different components or libraries used by the MCP server or client.
 * </p>
 * 
 * @param group the group or organization identifier for the dependency (e.g., "com.example")
 * @param name the artifact or module name of the dependency
 * @param version the version string of the dependency
 * 
 * @since 1.0
 */
public record DependencyInfo(
    String group,
    String name,
    String version
) {}
