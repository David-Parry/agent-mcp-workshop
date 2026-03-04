package com.workshop.mcp.spec;

import java.util.List;

/**
 * Represents the result of a tools/list response when the tools carry MCP App UI metadata.
 * <p>
 * Parallel to {@link ToolsListResult} but typed to {@link AppTool} so that the
 * {@code _meta.ui.resourceUri} field is included in the serialised JSON.
 * </p>
 *
 * @param tools the list of app-enabled tools
 * @see AppTool
 * @since 1.0
 */
public record AppToolsListResult(List<AppTool> tools) {}
