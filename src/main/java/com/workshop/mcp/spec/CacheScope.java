package com.workshop.mcp.spec;

/**
 * Legal values of the {@code cacheScope} field on a cacheable result.
 * <p>
 * Paired with {@code ttlMs}, this tells the client whether a shared
 * intermediary may cache the response or whether it is specific to the calling
 * principal. The specification calls a result carrying the pair a
 * {@code CacheableResult}, and requires both fields on exactly six
 * operations: {@code server/discover}, {@code tools/list},
 * {@code prompts/list}, {@code resources/list},
 * {@code resources/templates/list}, and {@code resources/read}.
 * </p>
 * <p>
 * Only results with {@code resultType} {@link ResultType#COMPLETE} are
 * cacheable. An interim {@link ResultType#INPUT_REQUIRED} result carries no
 * hints, and neither does the result of a retry that carried
 * {@code inputResponses} or {@code requestState} — those depend on inputs
 * that are not part of the cache key.
 * </p>
 *
 * @since 1.0
 */
public final class CacheScope {

    private CacheScope() {
        throw new AssertionError("CacheScope class should not be instantiated");
    }

    /** Shared intermediaries may cache the response. */
    public static final String PUBLIC = "public";

    /** The response is specific to this caller and must not be shared. */
    public static final String PRIVATE = "private";

    /** Conservative default applied when a result declares no freshness hint. */
    public static final long DEFAULT_TTL_MS = 0L;
}
