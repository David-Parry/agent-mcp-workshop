package com.workshop.mcp.legacy;

import com.workshop.mcp.spec.ClientCapabilities;
import com.workshop.mcp.spec.ClientInfo;

/**
 * The params of {@code initialize}, the request a client on a handshake
 * revision opens with.
 * <p>
 * What a modern client re-states in every request's {@code _meta} is sent
 * here once, for the whole session.
 * </p>
 *
 * @param protocolVersion the newest revision the client speaks, required
 * @param capabilities    what the client can be asked to do, required
 * @param clientInfo      which client software is connecting
 *
 * @see LegacySession
 */
public record InitializeParams(String protocolVersion, ClientCapabilities capabilities, ClientInfo clientInfo) {}
