package com.workshop.mcp.spec;

import java.util.List;

/**
 * The result of {@code prompts/list}.
 * <p>
 * A cacheable result: {@code resultType}, {@code ttlMs}, and
 * {@code cacheScope} are all required.
 * </p>
 *
 * @param resultType always {@link ResultType#COMPLETE}
 * @param prompts    the available prompt templates
 * @param nextCursor opaque cursor for the next page, or null when complete
 * @param ttlMs      how long the client may cache this list, non-negative
 * @param cacheScope one of {@link CacheScope#PUBLIC} or {@link CacheScope#PRIVATE}
 *
 * @see Prompt
 * @since 1.0
 */
public record PromptsListResult(
    String resultType,
    List<Prompt> prompts,
    String nextCursor,
    Long ttlMs,
    String cacheScope
) {
    /**
     * Creates a complete prompts list with explicit caching hints.
     *
     * @param prompts    the available prompt templates
     * @param nextCursor opaque cursor for the next page, or null
     * @param ttlMs      how long the client may cache this list
     * @param cacheScope whether shared intermediaries may cache it
     */
    public PromptsListResult(List<Prompt> prompts, String nextCursor, Long ttlMs, String cacheScope) {
        this(ResultType.COMPLETE, prompts, nextCursor, ttlMs, cacheScope);
    }

    /**
     * Creates a complete prompts list that clients should not cache or share.
     *
     * @param prompts    the available prompt templates
     * @param nextCursor opaque cursor for the next page, or null
     */
    public PromptsListResult(List<Prompt> prompts, String nextCursor) {
        this(prompts, nextCursor, CacheScope.DEFAULT_TTL_MS, CacheScope.PRIVATE);
    }
}
