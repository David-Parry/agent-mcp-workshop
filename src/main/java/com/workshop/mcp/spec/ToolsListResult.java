package com.workshop.mcp.spec;

import java.util.List;

/**
 * The result of {@code tools/list} for plain tools that declare no UI.
 * <p>
 * Like {@link AppToolsListResult}, this is a cacheable result and must carry
 * {@code resultType}, {@code ttlMs}, and {@code cacheScope}.
 * </p>
 *
 * @param resultType always {@link ResultType#COMPLETE}
 * @param tools      the advertised tools
 * @param ttlMs      how long the client may cache this list, non-negative
 * @param cacheScope one of {@link CacheScope#PUBLIC} or {@link CacheScope#PRIVATE}
 *
 * @see Tool
 * @since 1.0
 */
public record ToolsListResult(
    String resultType,
    List<Tool> tools,
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
    public ToolsListResult(List<Tool> tools, Long ttlMs, String cacheScope) {
        this(ResultType.COMPLETE, tools, ttlMs, cacheScope);
    }

    /**
     * Creates a complete tools list that clients should not cache or share.
     *
     * @param tools the advertised tools
     */
    public ToolsListResult(List<Tool> tools) {
        this(tools, CacheScope.DEFAULT_TTL_MS, CacheScope.PRIVATE);
    }
}
