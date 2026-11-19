package com.workshop.mcp.spec;

/**
 * Parameters for {@code skills/list} (Skills extension).
 *
 * @param cursor the opaque {@code nextCursor} of the previous page, or null for the first
 *
 * @see SkillsListResult
 * @since 1.0
 */
public record SkillsListParams(String cursor) {}
