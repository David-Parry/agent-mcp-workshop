package com.workshop.mcp.spec;

import java.util.Map;

/**
 * Represents the capabilities provided by an MCP (Model Context Protocol) server.
 */
public record ServerCapabilities(
    Capability tools,
    Capability prompts,
    Capability resources,
    Capability completions,
    TasksCapability tasks,
    Map<String, Object> experimental
) {}
