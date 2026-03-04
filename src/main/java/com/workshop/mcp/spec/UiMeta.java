package com.workshop.mcp.spec;

/**
 * Represents the UI metadata embedded in a tool's {@code _meta} field.
 * <p>
 * When a tool declares a {@code resourceUri}, MCP hosts that support MCP Apps
 * will preload the referenced {@code ui://} resource before the tool is called,
 * then render the HTML it returns in a sandboxed iframe inside the conversation.
 * </p>
 *
 * <p>Example JSON representation in a tool listing:</p>
 * <pre>{@code
 * {
 *   "name": "key_word_search",
 *   "description": "...",
 *   "inputSchema": { ... },
 *   "_meta": {
 *     "ui": {
 *       "resourceUri": "ui://keyword-search/mcp-app.html"
 *     }
 *   }
 * }
 * }</pre>
 *
 * @param resourceUri the {@code ui://} URI that points to the HTML resource
 *                    the host should fetch and render when the tool is called
 *
 * @see AppMeta
 * @see AppTool
 * @since 1.0
 */
public record UiMeta(String resourceUri) {}
