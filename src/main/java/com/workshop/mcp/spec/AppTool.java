package com.workshop.mcp.spec;

/**
 * Represents a tool that declares MCP App UI capability via {@code _meta.ui.resourceUri}.
 * <p>
 * An {@code AppTool} is identical to a regular {@link Tool} in every way except that
 * it carries an additional {@code _meta} field. When an MCP host receives an
 * {@code AppTool} in the tools list, it treats the tool normally AND knows to preload
 * the HTML resource at {@code _meta.ui.resourceUri} to render an interactive UI.
 * </p>
 *
 * <p>The full JSON representation sent to the host looks like:</p>
 * <pre>{@code
 * {
 *   "name": "key_word_search",
 *   "description": "Searches for a keyword across all project files.",
 *   "inputSchema": {
 *     "type": "object",
 *     "properties": {
 *       "keyword": { "type": "string", "description": "The keyword to search for" }
 *     },
 *     "required": ["keyword"]
 *   },
 *   "_meta": {
 *     "ui": {
 *       "resourceUri": "ui://keyword-search/mcp-app.html"
 *     }
 *   }
 * }
 * }</pre>
 *
 * @param name        the unique identifier for the tool
 * @param description a human-readable description of what the tool does
 * @param inputSchema the schema defining the tool's input parameters
 * @param _meta       UI metadata that declares the resource URI for the interactive app
 *
 * @see Tool
 * @see AppMeta
 * @see UiMeta
 * @since 1.0
 */
public record AppTool(
        String name,
        String description,
        InputSchema inputSchema,
        AppMeta _meta,
        ToolExecution execution
) {}
