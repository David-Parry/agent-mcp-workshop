package com.workshop.mcp.spec;

import java.util.Map;

/**
 * Represents parameters for invoking a tool in the MCP (Model Context Protocol) system.
 * <p>
 * ToolCallParams contains all the information needed to execute a tool, including
 * the tool name and its arguments. The arguments are provided as a map of key-value
 * pairs that should match the tool's input schema.
 * </p>
 * <p>
 * The optional {@code task} field carries task-augmentation metadata introduced
 * in MCP 2025-11-25. When present (and the server has declared
 * {@code tasks.requests.tools.call}), the receiver responds with a
 * {@link CreateTaskResult} instead of a synchronous {@link ToolCallResult}.
 * </p>
 *
 * @param _meta optional metadata information for the tool call, including progress tracking
 * @param name the name of the tool to invoke
 * @param arguments a map of argument names to their values, matching the tool's input schema
 * @param task optional task-augmentation payload — when non-null the call is
 *             deferred and a {@link CreateTaskResult} is returned
 *
 * @see Tool
 * @see ToolCallResult
 * @see MetaInfo
 * @see TaskParams
 * @see CreateTaskResult
 * @since 1.0
 */
public record ToolCallParams(
    MetaInfo _meta,
    String name,
    Map<String, String> arguments,
    TaskParams task
) {}
