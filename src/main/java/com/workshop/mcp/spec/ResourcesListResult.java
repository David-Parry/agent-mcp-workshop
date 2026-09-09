package com.workshop.mcp.spec;

import java.util.List;

/**
 * The result of {@code resources/list}.
 * <p>
 * A cacheable result: {@code resultType}, {@code ttlMs}, and
 * {@code cacheScope} are all required.
 * </p>
 *
 * @param resultType always {@link ResultType#COMPLETE}
 * @param resources  the resources the client may read
 * @param nextCursor opaque cursor for the next page, or null when complete
 * @param ttlMs      how long the client may cache this list, non-negative
 * @param cacheScope one of {@link CacheScope#PUBLIC} or {@link CacheScope#PRIVATE}
 *
 * @see Resource
 * @since 1.0
 */
public record ResourcesListResult(
        String resultType,
        List<Resource> resources,
        String nextCursor,
        Long ttlMs,
        String cacheScope
) {
    /**
     * Creates a complete resources list with explicit caching hints.
     *
     * @param resources  the resources the client may read
     * @param nextCursor opaque cursor for the next page, or null
     * @param ttlMs      how long the client may cache this list
     * @param cacheScope whether shared intermediaries may cache it
     */
    public ResourcesListResult(List<Resource> resources, String nextCursor, Long ttlMs, String cacheScope) {
        this(ResultType.COMPLETE, resources, nextCursor, ttlMs, cacheScope);
    }

    /**
     * Creates a complete resources list that clients should not cache or share.
     *
     * @param resources  the resources the client may read
     * @param nextCursor opaque cursor for the next page, or null
     */
    public ResourcesListResult(List<Resource> resources, String nextCursor) {
        this(resources, nextCursor, CacheScope.DEFAULT_TTL_MS, CacheScope.PRIVATE);
    }
}
