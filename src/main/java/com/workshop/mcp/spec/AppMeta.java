package com.workshop.mcp.spec;

/**
 * Represents the {@code _meta} field on a tool that declares MCP App UI capability.
 * <p>
 * When the host receives a tool listing that includes {@code _meta.ui.resourceUri},
 * it knows this tool has an interactive UI. The host fetches the referenced
 * {@code ui://} resource and renders the HTML inside a sandboxed iframe, directly
 * inside the conversation — no tab switching, no separate web app needed.
 * </p>
 *
 * <p>This object maps directly to the {@code _meta} key in the MCP protocol's tool
 * definition. The leading underscore is intentional and matches the protocol spec.</p>
 *
 * @param ui the UI metadata containing the resource URI to render
 *
 * @see UiMeta
 * @see AppTool
 * @since 1.0
 */
public record AppMeta(UiMeta ui) {}
