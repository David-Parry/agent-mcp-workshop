package com.workshop.mcp.spec;

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
        // Chapter 06: implement of(...).
        throw new UnsupportedOperationException(
                "Chapter 06: of(...) is not implemented yet");
    }
}
