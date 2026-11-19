package com.workshop.mcp.spec;

/**
 * The result of {@code skills/get} (Skills extension): one skill's entry, as
 * it stands now.
 * <p>
 * A cacheable result: {@code resultType}, {@code ttlMs}, and
 * {@code cacheScope} are all required. A host calls it again to refresh one
 * skill's digests without listing them all.
 * </p>
 *
 * @param resultType always {@link ResultType#COMPLETE}
 * @param skill      the skill's entry, the same as its {@code skills/list} entry
 * @param ttlMs      how long the client may treat the entry as current, non-negative
 * @param cacheScope one of {@link CacheScope#PUBLIC} or {@link CacheScope#PRIVATE}
 *
 * @see Skill
 * @since 1.0
 */
public record SkillsGetResult(String resultType, Skill skill, Long ttlMs, String cacheScope) {

    /**
     * Creates a complete skill result with explicit caching hints.
     *
     * @param skill      the skill's entry
     * @param ttlMs      how long the client may treat the entry as current
     * @param cacheScope whether shared intermediaries may cache it
     */
    public SkillsGetResult(Skill skill, Long ttlMs, String cacheScope) {
        this(ResultType.COMPLETE, skill, ttlMs, cacheScope);
    }
}
