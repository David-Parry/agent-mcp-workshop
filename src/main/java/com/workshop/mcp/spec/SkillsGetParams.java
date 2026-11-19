package com.workshop.mcp.spec;

/**
 * Parameters for {@code skills/get} (Skills extension).
 *
 * @param uri the URI of a skill's {@code SKILL.md}; any other URI is invalid params
 *
 * @see SkillsGetResult
 * @since 1.0
 */
public record SkillsGetParams(String uri) {}
