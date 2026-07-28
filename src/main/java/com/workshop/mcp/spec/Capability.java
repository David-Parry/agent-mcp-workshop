package com.workshop.mcp.spec;

/**
 * Represents a capability configuration in the MCP (Model Context Protocol) system.
 * <p>
 * Capabilities define what features or operations are supported by a server or client.
 * This includes whether list change notifications are supported and whether
 * subscription-based updates are available.
 * </p>
 * 
 * @param listChanged indicates whether the capability supports notifications when lists change
 * @param subscribe indicates whether the capability supports subscription-based updates
 * 
 * @since 1.0
 */
public record Capability(Boolean listChanged, Boolean subscribe) {
    /**
     * Creates a capability with only the listChanged flag specified.
     * The subscribe flag is set to null.
     * 
     * @param listChanged indicates whether list change notifications are supported
     */
    public Capability(Boolean listChanged) {
        this(listChanged, null);
    }

    /**
     * Creates a default capability with listChanged enabled and subscribe unspecified.
     * This constructor provides a sensible default for basic capability support.
     */
    public Capability() {
        this(true, null);
    }

}
