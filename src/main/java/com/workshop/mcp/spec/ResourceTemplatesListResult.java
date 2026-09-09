package com.workshop.mcp.spec;

import java.util.List;

/**
 * The result of {@code resources/templates/list}.
 * <p>
 * Clients fetch this immediately after {@code resources/list} whenever a
 * server declares any resource capability, so a server that advertises
 * resources has to answer it even when it exposes no templates. This workshop
 * server has none, and returns an empty list.
 * </p>
 * <p>
 * A cacheable result: {@code resultType}, {@code ttlMs}, and
 * {@code cacheScope} are all required.
 * </p>
 *
 * @param resultType        always {@link ResultType#COMPLETE}
 * @param resourceTemplates the available templates, possibly empty
 * @param nextCursor        opaque cursor for the next page, or null when complete
 * @param ttlMs             how long the client may cache this list, non-negative
 * @param cacheScope        one of {@link CacheScope#PUBLIC} or {@link CacheScope#PRIVATE}
 *
 * @since 1.0
 */
public record ResourceTemplatesListResult(
        String resultType,
        List<Object> resourceTemplates,
        String nextCursor,
        Long ttlMs,
        String cacheScope
) {
    /**
     * Creates an empty, publicly cacheable template list.
     *
     * @param ttlMs how long the client may cache this list
     * @return the empty result
     */
    public static ResourceTemplatesListResult empty(long ttlMs) {
        return new ResourceTemplatesListResult(ResultType.COMPLETE, List.of(), null, ttlMs, CacheScope.PUBLIC);
    }
}
