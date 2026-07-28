package com.workshop.mcp.spec;

import com.google.gson.annotations.SerializedName;

/**
 * The {@code _meta} block that links a tool to its MCP App UI.
 * <p>
 * The nested {@code ui.resourceUri} is the canonical form. The flat
 * {@code ui/resourceUri} is <strong>deprecated</strong> by the MCP Apps
 * extension and is scheduled for removal before GA, but hosts in the wild
 * still read one or the other depending on their vintage, so this record
 * emits both and keeps them in step. Write new hosts against the nested key.
 * </p>
 * <p>
 * The flat key contains a slash and so cannot be a Java identifier; it is
 * mapped with {@link SerializedName}.
 * </p>
 *
 * @param ui          the nested UI metadata, the canonical form
 * @param resourceUri the same URI under the deprecated flat {@code ui/resourceUri} key
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
        return new AppMeta(new UiMeta(resourceUri, null), resourceUri);
    }
}
