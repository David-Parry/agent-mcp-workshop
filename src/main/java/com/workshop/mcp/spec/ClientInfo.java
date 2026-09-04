package com.workshop.mcp.spec;

/**
 * Represents information about an MCP (Model Context Protocol) client.
 * <p>
 * ClientInfo provides identification details about the client connecting to
 * an MCP server, including its name and version.
 * </p>
 * <p>
 * With the handshake removed in revision {@code 2026-07-28}, this arrives in
 * the {@code _meta} of every request rather than once at connection time. It
 * is the one recommended-but-optional member of that envelope — a server must
 * still work without it.
 * </p>
 *
 * @param name the name of the client application
 * @param version the version string of the client application
 *
 * @see RequestEnvelope
 * @see MetaKeys#CLIENT_INFO
 * @since 1.0
 */
public record ClientInfo(
    String name,
    String version
) {}
