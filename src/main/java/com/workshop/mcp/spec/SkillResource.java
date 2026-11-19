package com.workshop.mcp.spec;

/**
 * One file of a skill, as the Skills extension lists it in a {@link Skill}'s
 * manifest.
 * <p>
 * The digest and size cover the file's raw bytes, which are exactly what
 * {@code resources/read} returns for {@code uri}. A host verifies both before
 * it uses the content, and treats a mismatch as a skill that changed.
 * </p>
 *
 * @param uri    the file's resource URI
 * @param digest the SHA-256 of the file's bytes, as {@code sha256:} and 64 lowercase hex digits
 * @param size   the file's length in bytes
 *
 * @see Skill
 * @since 1.0
 */
public record SkillResource(String uri, String digest, long size) {}
