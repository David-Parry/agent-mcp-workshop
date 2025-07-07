package com.workshop.mcp.spec;

/**
 * Represents the capability configuration for roots in the MCP (Model Context Protocol) system.
 * <p>
 * RootsCapability indicates whether the server supports notifications when the list
 * of available roots changes. This allows clients to stay synchronized with dynamic
 * changes to the root structure without polling.
 * </p>
 * 
 * @param listChanged indicates whether the server will notify when the roots list changes
 * 
 * @see Root
 * @see ServerCapabilities
 * @since 1.0
 */
public record RootsCapability(
    boolean listChanged
) {}
