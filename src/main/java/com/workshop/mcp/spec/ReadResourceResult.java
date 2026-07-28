package com.workshop.mcp.spec;

import java.util.List;

/**
 * The result of {@code resources/read}.
 * <p>
 * A cacheable result: {@code resultType}, {@code ttlMs}, and
 * {@code cacheScope} are all required. It is the only cacheable method that is
 * not a list.
 * </p>
 * <p>
 * There is no failed read result. A uri the server cannot read is answered
 * with {@link ErrorCodes#INVALID_PARAMS} instead, never with empty
 * {@code contents}.
 * </p>
 *
 * @param resultType always {@link ResultType#COMPLETE}
 * @param contents   the resource contents that were read
 * @param ttlMs      how long the client may cache this content, non-negative
 * @param cacheScope one of {@link CacheScope#PUBLIC} or {@link CacheScope#PRIVATE}
 *
 * @see TextReadResource
 * @since 1.0
 */
public record ReadResourceResult(
        String resultType,
        List<TextReadResource> contents,
        Long ttlMs,
        String cacheScope
) {
    /**
     * Creates a complete read result with explicit caching hints.
     *
     * @param contents   the resource contents that were read
     * @param ttlMs      how long the client may cache this content
     * @param cacheScope whether shared intermediaries may cache it
     */
    public ReadResourceResult(List<TextReadResource> contents, Long ttlMs, String cacheScope) {
        this(ResultType.COMPLETE, contents, ttlMs, cacheScope);
    }

    /**
     * Creates a complete read result that clients should not cache or share.
     *
     * @param contents the resource contents that were read
     */
    public ReadResourceResult(List<TextReadResource> contents) {
        this(contents, CacheScope.DEFAULT_TTL_MS, CacheScope.PRIVATE);
    }
}
