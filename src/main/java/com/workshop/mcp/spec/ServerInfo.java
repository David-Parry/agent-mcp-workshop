package com.workshop.mcp.spec;

/**
 * Represents information about an MCP (Model Context Protocol) server.
 * <p>
 * ServerInfo provides identification details about the server, including its
 * name and version.
 * </p>
 * <p>
 * On revision {@code 2026-07-28} this is no longer a body field of a
 * handshake result. It travels in {@code _meta} under
 * {@link MetaKeys#SERVER_INFO}, which lets a client identify the server from
 * any result rather than only from the one that opened the connection.
 * </p>
 *
 * @param name the name of the server application
 * @param version the version string of the server application
 *
 * @see DiscoverResult
 * @see MetaKeys#SERVER_INFO
 * @since 1.0
 */
public record ServerInfo(
    String name,
    String version
) {}
