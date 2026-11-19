package com.workshop.mcp.spec;

import java.util.List;

/**
 * The {@code _meta} block that links a tool to its MCP App UI.
 * <p>
 * The page's address sits under {@code ui.resourceUri}. The MCP Apps
 * extension also defines a flat {@code ui/resourceUri} key and marks it
 * <strong>deprecated</strong> in favour of the nested one, so this record
 * does not emit it.
 * </p>
 *
 * @param ui the nested UI metadata
 *
 * @see UiMeta
 * @see MetaKeys#UI_EXTENSION
 * @since 1.0
 */
public record AppMeta(UiMeta ui) {
    /**
     * Creates the metadata block for an app resource.
     *
     * @param resourceUri the {@code ui://} URI of the app's HTML resource
     * @return the metadata block
     */
    public static AppMeta of(String resourceUri) {
        return new AppMeta(new UiMeta(resourceUri, null));
    }

    /**
     * Creates the metadata block for an app resource whose tool only some
     * callers may invoke.
     * <p>
     * {@code List.of("app")} keeps the tool out of the agent's tool list:
     * the host still receives it in {@code tools/list}, but only the rendered
     * page may call it.
     * </p>
     *
     * @param resourceUri the {@code ui://} URI of the app's HTML resource
     * @param visibility  who may invoke the tool: {@code "model"}, {@code "app"}, or both
     * @return the metadata block
     */
    public static AppMeta of(String resourceUri, List<String> visibility) {
        return new AppMeta(new UiMeta(resourceUri, visibility));
    }
}
