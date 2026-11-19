package com.workshop.mcp.spec;

import java.util.List;
import java.util.Map;

/**
 * A skill's entry in the Skills extension, the same in {@code skills/list}
 * and {@code skills/get}.
 * <p>
 * An entry is a manifest, not a summary. The frontmatter is the
 * {@code SKILL.md}'s own, rendered as JSON, so a host can build its registry
 * without reading the file, and {@code resources} lists every file of the
 * skill, {@code SKILL.md} included, so a host can verify each one it reads.
 * </p>
 *
 * @param uri         the URI of the skill's {@code SKILL.md}, whose last path segment before the file is the skill's name
 * @param frontmatter the {@code SKILL.md} YAML frontmatter as a JSON object, always with {@code name} and {@code description}
 * @param resources   every file of the skill, each exactly once
 *
 * @see SkillResource
 * @see SkillsListResult
 * @see SkillsGetResult
 * @since 1.0
 */
public record Skill(String uri, Map<String, Object> frontmatter, List<SkillResource> resources) {}
