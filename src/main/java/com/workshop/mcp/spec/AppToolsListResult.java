package com.workshop.mcp.spec;

import java.util.List;

/**
 * The result of {@code tools/list} when the tools declare MCP App UIs.
 * <p>
 * {@code tools/list} is a cacheable method, so {@code ttlMs} and
 * {@code cacheScope} are required alongside {@code resultType}. A client
 * validating against the reference schemas rejects the result outright if
 * either is missing.
 * </p>
 * <p>
 * Servers should return tools in a deterministic order so clients can cache
 * the response and keep model prompt caches warm.
 * </p>
 *
 * @param resultType always {@link ResultType#COMPLETE}
 * @param tools      the advertised tools
 * @param ttlMs      how long the client may cache this list, non-negative
 * @param cacheScope one of {@link CacheScope#PUBLIC} or {@link CacheScope#PRIVATE}
 *
 * @see AppTool
 * @since 1.0
 */
public record AppToolsListResult(
        String resultType,
        List<AppTool> tools,
        Long ttlMs,
        String cacheScope
) {
    /**
     * Creates a complete tools list with explicit caching hints.
     *
     * @param tools      the advertised tools
     * @param ttlMs      how long the client may cache this list
     * @param cacheScope whether shared intermediaries may cache it
     */
    public AppToolsListResult(List<AppTool> tools, Long ttlMs, String cacheScope) {
        this(ResultType.COMPLETE, tools, ttlMs, cacheScope);
    }

    /**
     * Creates a complete tools list that clients should not cache or share.
     *
     * @param tools the advertised tools
     */
    public AppToolsListResult(List<AppTool> tools) {
        this(tools, CacheScope.DEFAULT_TTL_MS, CacheScope.PRIVATE);
    }
}
