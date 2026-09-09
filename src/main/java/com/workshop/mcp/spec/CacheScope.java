package com.workshop.mcp.spec;

/**
 * Legal values of the {@code cacheScope} field on a cacheable result.
 * <p>
 * Paired with {@code ttlMs}, this tells the client whether a shared
 * intermediary may cache the response or whether it is specific to the calling
 * principal. Both fields are required on {@code tools/list},
 * {@code prompts/list}, {@code resources/list},
 * {@code resources/templates/list}, and {@code resources/read}.
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
