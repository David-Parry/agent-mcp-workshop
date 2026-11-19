package com.workshop.mcp.legacy;

import com.workshop.mcp.spec.ServerCapabilities;
import com.workshop.mcp.spec.ServerInfo;

/**
 * The result of {@code initialize}: the revision the session will speak, and
 * what this server offers in it.
 *
 * @param protocolVersion the revision agreed; a client that cannot speak it must disconnect
 * @param capabilities    what this server offers a legacy client
 * @param serverInfo      this server's identity
 * @param instructions    guidance for the client's model
 *
 * @see LegacySession
 */
public record InitializeResult(
        String protocolVersion,
        ServerCapabilities capabilities,
        ServerInfo serverInfo,
        String instructions
) {}
