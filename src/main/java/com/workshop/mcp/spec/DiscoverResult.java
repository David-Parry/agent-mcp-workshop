package com.workshop.mcp.spec;

import java.util.List;
import java.util.Map;

/**
 * The result of {@code server/discover}, the sessionless replacement for the
 * {@code initialize} handshake.
 * <p>
 * A server implementing revision {@code 2026-07-28} MUST answer this method.
 * It advertises which revisions the server speaks, what it can do, and who it
 * is, without creating any session state — a client may call it before any
 * other request, or not at all.
 * </p>
 * <p>
 * Two details differ from the old {@code InitializeResult}. There is no
 * single {@code protocolVersion}: the server lists everything it supports in
 * {@link #supportedVersions()} and the client picks. And {@code serverInfo} is
 * no longer a body field — it moved into {@code _meta} under
 * {@link MetaKeys#SERVER_INFO}.
 * </p>
 * <p>
 * On stdio this request is answered by a throwaway probe process that the
 * client discards immediately afterwards, so the answer must not depend on
 * anything the server has accumulated.
 * </p>
 *
 * @param resultType        always {@link ResultType#COMPLETE}
 * @param supportedVersions the protocol revisions this server speaks, required
 * @param capabilities      what this server offers, required
 * @param instructions      optional guidance for a model using this server
 * @param ttlMs             how long the client may cache this result
 * @param cacheScope        whether shared intermediaries may cache it
 * @param _meta             carries {@link MetaKeys#SERVER_INFO}
 *
 * @see ServerCapabilities
 * @see com.workshop.mcp.spec.builders.DiscoverResultBuilder
 * @since 1.0
 */
public record DiscoverResult(
    String resultType,
    List<String> supportedVersions,
    ServerCapabilities capabilities,
    String instructions,
    Long ttlMs,
    String cacheScope,
    Map<String, Object> _meta
) {
    /**
     * Creates a complete discovery result.
     *
     * @param supportedVersions the protocol revisions this server speaks
     * @param capabilities      what this server offers
     * @param instructions      optional guidance for a model
     * @param ttlMs             how long the client may cache this result
     * @param cacheScope        whether shared intermediaries may cache it
     * @param _meta             carries {@link MetaKeys#SERVER_INFO}
     */
    public DiscoverResult(List<String> supportedVersions,
                          ServerCapabilities capabilities,
                          String instructions,
                          Long ttlMs,
                          String cacheScope,
                          Map<String, Object> _meta) {
        this(ResultType.COMPLETE, supportedVersions, capabilities, instructions, ttlMs, cacheScope, _meta);
    }
}
