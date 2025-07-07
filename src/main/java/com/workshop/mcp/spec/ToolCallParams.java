package com.workshop.mcp.spec;

import java.util.Map;

/**
 * Represents parameters for invoking a tool in the MCP (Model Context Protocol) system.
 * <p>
 * ToolCallParams contains all the information needed to execute a tool, including
 * the tool name and its arguments. The arguments are provided as a map of key-value
 * pairs that should match the tool's input schema.
 * </p>
 * 
 * @param _meta optional metadata information for the tool call, including progress tracking
 * @param name the name of the tool to invoke
 * @param arguments a map of argument names to their values, matching the tool's input schema
 * 
 * @see Tool
 * @see ToolCallResult
 * @see MetaInfo
 * @since 1.0
 */
public record ToolCallParams(
    MetaInfo _meta,
    String name,
    Map<String, String> arguments
) {}
