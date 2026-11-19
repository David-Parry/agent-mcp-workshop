package com.workshop.mcp.spec;

import java.util.List;

/**
 * The result of {@code skills/list} (Skills extension).
 * <p>
 * A cacheable result: {@code resultType}, {@code ttlMs}, and
 * {@code cacheScope} are all required. An empty or partial listing is
 * allowed, so a host must not take it as proof that a skill is not served;
 * {@code skills/get} answers for every skill a server serves.
 * </p>
 *
 * @param resultType always {@link ResultType#COMPLETE}
 * @param skills     the entries on this page, never split across pages
 * @param nextCursor opaque cursor for the next page, or null when complete
 * @param ttlMs      how long the client may cache this list, non-negative
 * @param cacheScope one of {@link CacheScope#PUBLIC} or {@link CacheScope#PRIVATE}
 *
 * @see Skill
 * @since 1.0
 */
public record SkillsListResult(
        String resultType,
        List<Skill> skills,
        String nextCursor,
        Long ttlMs,
        String cacheScope
) {
    /**
     * Creates a complete skills list with explicit caching hints.
     *
     * @param skills     the entries on this page
     * @param nextCursor opaque cursor for the next page, or null
     * @param ttlMs      how long the client may cache this list
     * @param cacheScope whether shared intermediaries may cache it
     */
    public SkillsListResult(List<Skill> skills, String nextCursor, Long ttlMs, String cacheScope) {
        this(ResultType.COMPLETE, skills, nextCursor, ttlMs, cacheScope);
    }
}
