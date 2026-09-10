package com.workshop.mcp.spec;

import com.google.gson.annotations.SerializedName;

/**
 * The {@code _meta} block that links a tool to its MCP App UI.
 * <p>
 * The reference MCP Apps extension writes the resource URI under two keys and
 * mirrors whichever one the author omitted: the nested {@code ui.resourceUri}
 * and the flat {@code ui/resourceUri}. Hosts read one or the other depending
 * on their vintage, so this record carries both.
 * </p>
 * <p>
 * The flat key contains a slash and so cannot be a Java identifier; it is
 * mapped with {@link SerializedName}.
 * </p>
 *
 * @param ui          the nested UI metadata
 * @param resourceUri the same URI under the flat {@code ui/resourceUri} key
 *
 * @see UiMeta
 * @see MetaKeys#UI_EXTENSION
 * @since 1.0
 */
public record AppMeta(
        UiMeta ui,
        @SerializedName("ui/resourceUri") String resourceUri
) {
    /**
     * Creates the metadata block for an app resource, filling both keys.
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
