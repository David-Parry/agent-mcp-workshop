package com.workshop.mcp.skills;

import com.workshop.mcp.spec.Resource;
import com.workshop.mcp.spec.Skill;
import com.workshop.mcp.spec.SkillResource;
import com.workshop.mcp.spec.TextReadResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The skills this server serves over the Skills extension
 * ({@code io.modelcontextprotocol/skills}), read once from the classpath.
 * <p>
 * A skill is a {@code SKILL.md} bundled at {@code skills/<name>/SKILL.md} and
 * served at {@code skill://<name>/SKILL.md}. Gradle copies it in from the
 * lessons plugin at build time, so what a host reads over MCP is the file the
 * plugin installs from disk.
 * </p>
 * <p>
 * Each entry is the manifest a host verifies against: the frontmatter as JSON,
 * and the SHA-256 digest and byte size of exactly the text that
 * {@code resources/read} returns. These skills have no supporting files, so
 * each manifest lists its {@code SKILL.md} alone.
 * </p>
 */
public final class SkillCatalog {

    /** The scheme the extension recommends for skill files. */
    public static final String SCHEME = "skill://";

    /** The file every skill has at its root. */
    public static final String SKILL_FILE = "SKILL.md";

    /** The type a {@code SKILL.md} is served as. */
    public static final String MIME_TYPE = "text/markdown";

    /** The skills this server bundles, by name. */
    private static final List<String> BUNDLED = List.of("keyword-search");

    /** A frontmatter key this parser reads: a plain word, as every Agent Skills field is. */
    private static final Pattern KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");

    /** Characters that make a value something other than a plain string when they begin it. */
    private static final String INDICATORS = "-?:,[]{}#&*!|>'\"%@`";

    /** What ends a plain value early in YAML: a further mapping, or a comment. */
    private static final Pattern PLAIN_STOP = Pattern.compile(":(\\s|$)|\\s#");

    /** Plain values YAML reads as null, a boolean, or a number: the 1.2 core schema, and the 1.1 booleans. */
    private static final Pattern NOT_A_STRING = Pattern.compile(
            "~|null|Null|NULL|true|True|TRUE|false|False|FALSE"
            + "|y|Y|yes|Yes|YES|n|N|no|No|NO|on|On|ON|off|Off|OFF"
            + "|[-+]?[0-9]+|0o[0-7]+|0x[0-9a-fA-F]+"
            + "|[-+]?(\\.[0-9]+|[0-9]+(\\.[0-9]*)?)([eE][-+]?[0-9]+)?"
            + "|[-+]?\\.(inf|Inf|INF)|\\.(nan|NaN|NAN)");

    private final Map<String, Served> byUri;

    private record Served(Skill skill, Resource resource, String text) {}

    private SkillCatalog(Map<String, Served> byUri) {
        this.byUri = Collections.unmodifiableMap(byUri);
    }

    /**
     * Loads the skills bundled with this server.
     *
     * @return the catalog
     */
    public static SkillCatalog bundled() {
        return load(SkillCatalog.class.getClassLoader(), BUNDLED);
    }

    /**
     * Loads the named skills from {@code skills/<name>/SKILL.md} on a class path.
     *
     * @param loader where to find the files
     * @param names  the skills to load, each the name its frontmatter declares
     * @return the catalog
     * @throws IllegalStateException    when a skill is missing, or does not declare its name and a description
     * @throws IllegalArgumentException when a skill's frontmatter cannot be read
     * @throws UncheckedIOException     when a skill's file cannot be read
     */
    static SkillCatalog load(ClassLoader loader, List<String> names) {
        Map<String, Served> byUri = new LinkedHashMap<>();
        for (String name : names) {
            byte[] bytes = read(loader, "skills/" + name + "/" + SKILL_FILE);
            String text = new String(bytes, StandardCharsets.UTF_8);
            Map<String, Object> frontmatter = frontmatter(text);
            if (!name.equals(frontmatter.get("name")) || !frontmatter.containsKey("description")) {
                throw new IllegalStateException("The skill bundled as " + name
                                                + " must declare that name and a description: " + frontmatter);
            }
            String uri = SCHEME + name + "/" + SKILL_FILE;
            Skill skill = new Skill(uri, frontmatter, List.of(new SkillResource(uri, digest(bytes), bytes.length)));
            Resource resource = new Resource(uri, name, (String) frontmatter.get("description"), MIME_TYPE, null);
            byUri.put(uri, new Served(skill, resource, text));
        }
        return new SkillCatalog(byUri);
    }

    /**
     * @return every skill's entry, for {@code skills/list}
     */
    public List<Skill> skills() {
        return byUri.values().stream().map(Served::skill).toList();
    }

    /**
     * @param uri the URI of a skill's {@code SKILL.md}
     * @return the skill's entry, or null when no skill is served there
     */
    public Skill skill(String uri) {
        Served served = byUri.get(uri);
        return served == null ? null : served.skill();
    }

    /**
     * @return every skill file, for {@code resources/list}
     */
    public List<Resource> resources() {
        return byUri.values().stream().map(Served::resource).toList();
    }

    /**
     * @param uri a resource URI
     * @return the file's contents, or null when it is not a skill file this server serves
     */
    public TextReadResource read(String uri) {
        Served served = byUri.get(uri);
        return served == null ? null : new TextReadResource(uri, MIME_TYPE, served.text());
    }

    /**
     * Reads the YAML frontmatter of a {@code SKILL.md}, as the JSON object a
     * skill entry carries.
     * <p>
     * There is no YAML library on this server's class path, so this reads only
     * flat {@code key: value} lines whose value is a string: plain, or in
     * quotes that hold no escapes. Anything else is refused rather than
     * misread, since the entry must say exactly what the file says; a value
     * YAML reads as a number, a boolean, or null is refused for that reason.
     * </p>
     *
     * @param markdown the whole {@code SKILL.md}
     * @return the fields in the order they were written
     * @throws IllegalArgumentException when there is no frontmatter, or it holds more than flat strings
     */
    static Map<String, Object> frontmatter(String markdown) {
        List<String> lines = markdown.lines().toList();
        if (lines.isEmpty() || !lines.get(0).equals("---")) {
            throw new IllegalArgumentException("A SKILL.md must begin with YAML frontmatter");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.equals("---")) {
                return fields;
            }
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(": ");
            String key = colon < 0 ? "" : line.substring(0, colon);
            String value = colon < 0 ? null : string(line.substring(colon + 2).strip());
            if (!KEY.matcher(key).matches() || value == null || fields.containsKey(key)) {
                throw new IllegalArgumentException("Only flat key: string frontmatter is supported, not: " + line);
            }
            fields.put(key, value);
        }
        throw new IllegalArgumentException("The frontmatter is never closed with ---");
    }

    /**
     * @param bytes a file's raw content
     * @return its SHA-256, formatted as the extension requires
     */
    static String digest(byte[] bytes) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform is required to provide SHA-256", e);
        }
    }

    /**
     * @param value a YAML scalar, as written after its key
     * @return the string it spells, or null when YAML reads it as something
     *         else, or it needs more of YAML than this parser reads
     */
    private static String string(String value) {
        if (value.isEmpty()) {
            return null;
        }
        char first = value.charAt(0);
        boolean closed = value.length() > 1 && value.charAt(value.length() - 1) == first;
        String inner = closed ? value.substring(1, value.length() - 1) : "";
        if (first == '"') {
            return closed && inner.indexOf('"') < 0 && inner.indexOf('\\') < 0 ? inner : null;
        }
        if (first == '\'') {
            return closed && inner.indexOf('\'') < 0 ? inner : null;
        }
        boolean plain = INDICATORS.indexOf(first) < 0 && !PLAIN_STOP.matcher(value).find()
                        && !NOT_A_STRING.matcher(value).matches();
        return plain ? value : null;
    }

    private static byte[] read(ClassLoader loader, String path) {
        try (InputStream in = loader.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("No skill is bundled at " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("The skill bundled at " + path + " cannot be read", e);
        }
    }
}
