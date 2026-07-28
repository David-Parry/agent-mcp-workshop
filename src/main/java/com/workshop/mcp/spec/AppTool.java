package com.workshop.mcp.spec;

/**
 * Represents a tool that declares MCP App UI capability via {@code _meta.ui.resourceUri}.
 * <p>
 * An {@code AppTool} is identical to a regular {@link Tool} in every way except that
 * it carries an additional {@code _meta} field. A host that supports MCP Apps calls
 * the tool normally and renders its result with the HTML resource at
 * {@code _meta.ui.resourceUri}, which it reads with {@code resources/read} and may
 * fetch ahead of the first call. Any other client ignores {@code _meta}.
 * </p>
 *
 * <p>Its JSON has this shape, shown here with a one-argument schema:</p>
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
 *     },
 *     "ui/resourceUri": "ui://keyword-search/mcp-app.html"
 *   }
 * }
 * }</pre>
 *
 * <p>
 * There is no {@code execution} field. Revision {@code 2026-07-28} removed
 * {@code Tool.execution} — clients strip it silently — because its
 * {@code taskSupport} hint forced a {@code tools/list} warmup before any
 * task-augmented call. Whether a call becomes a task is now decided by the
 * server per request.
 * </p>
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
        AppMeta _meta
) {}
