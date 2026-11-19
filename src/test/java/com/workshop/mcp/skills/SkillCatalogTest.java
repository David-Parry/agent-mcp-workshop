package com.workshop.mcp.skills;

import com.workshop.mcp.spec.Resource;
import com.workshop.mcp.spec.Skill;
import com.workshop.mcp.spec.SkillResource;
import com.workshop.mcp.spec.TextReadResource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * Covers the skills served over the Skills extension: the bundled
 * {@code keyword-search} skill and the manifest a host verifies it against,
 * the skills that cannot be loaded, and the frontmatter the parser reads and
 * the frontmatter it refuses rather than misreads.
 */
@Tag("agent")
@Tag("stretch")
class SkillCatalogTest {

    private static final String URI = "skill://keyword-search/SKILL.md";
    private static final String DESCRIPTION = "Search the codebase for a keyword using the workshop MCP server";
    private static final Path PLUGIN_COPY = Path.of("lessons/keyword-audit-plugin/skills/keyword-search/SKILL.md");

    private final SkillCatalog catalog = SkillCatalog.bundled();

    // --- the bundled skill ---------------------------------------------

    @Test
    void theBundledSkillIsKeywordSearchWithItsFrontmatterInTheOrderItIsWritten() {
        Skill skill = catalog.skill(URI);

        assertEquals(URI, skill.uri());
        assertEquals(List.of("name", "description"), List.copyOf(skill.frontmatter().keySet()));
        assertEquals(Map.of("name", "keyword-search", "description", DESCRIPTION), skill.frontmatter());
        assertEquals(List.of(skill), catalog.skills());
    }

    @Test
    void itsManifestIsTheDigestAndByteSizeOfThePluginsOwnFile() throws IOException {
        byte[] bytes = Files.readAllBytes(PLUGIN_COPY);

        List<SkillResource> files = catalog.skill(URI).resources();

        assertEquals(List.of(new SkillResource(URI, SkillCatalog.digest(bytes), bytes.length)), files);
        assertTrue(files.get(0).size() > new String(bytes, UTF_8).length(),
                   "the size counts bytes, and this file has multi-byte characters");
    }

    @Test
    void readingTheSkillReturnsExactlyThoseBytesAsMarkdown() throws IOException {
        TextReadResource file = catalog.read(URI);

        assertEquals(URI, file.uri());
        assertEquals("text/markdown", file.mimeType());
        assertArrayEquals(Files.readAllBytes(PLUGIN_COPY), file.text().getBytes(UTF_8));
    }

    @Test
    void theSkillFileIsListedAsAResourceNamedAndDescribedByItsFrontmatter() {
        assertEquals(List.of(new Resource(URI, "keyword-search", DESCRIPTION, "text/markdown", null)),
                     catalog.resources());
    }

    @Test
    void aUriThatServesNoSkillHasNoEntryAndNoContent() {
        for (String uri : Arrays.asList("skill://no-such-skill/SKILL.md", "ui://keyword-search/mcp-app.html", null)) {
            assertNull(catalog.skill(uri), uri);
            assertNull(catalog.read(uri), uri);
        }
    }

    // --- loading -------------------------------------------------------

    @Test
    void skillsAreServedInTheOrderTheyAreNamed() {
        ClassLoader classPath = classPathOf(Map.of(
                "skills/beta/SKILL.md", "---\nname: beta\ndescription: The second\n---\n",
                "skills/alpha/SKILL.md", "---\nname: alpha\ndescription: The first\n---\n"));

        SkillCatalog two = SkillCatalog.load(classPath, List.of("beta", "alpha"));

        assertEquals(List.of("skill://beta/SKILL.md", "skill://alpha/SKILL.md"),
                     two.skills().stream().map(Skill::uri).toList());
    }

    @Test
    void aSkillThatIsNotBundledCannotBeLoaded() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                                               () -> SkillCatalog.load(classPathOf(Map.of()), List.of("absent")));

        assertEquals("No skill is bundled at skills/absent/SKILL.md", e.getMessage());
    }

    @Test
    void aSkillThatCannotBeReadCannotBeLoaded() {
        ClassLoader unreadable = new ClassLoader(null) {
            @Override
            public InputStream getResourceAsStream(String name) {
                return new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new IOException("the disk went away");
                    }
                };
            }
        };

        assertThrows(UncheckedIOException.class, () -> SkillCatalog.load(unreadable, List.of("keyword-search")));
    }

    @Test
    void aSkillMustDeclareTheNameItIsServedUnder() {
        ClassLoader classPath = classPathOf(Map.of("skills/misnamed/SKILL.md", "---\nname: other\ndescription: d\n---\n"));

        assertThrows(IllegalStateException.class, () -> SkillCatalog.load(classPath, List.of("misnamed")));
    }

    @Test
    void aSkillMustDeclareADescription() {
        ClassLoader classPath = classPathOf(Map.of("skills/terse/SKILL.md", "---\nname: terse\n---\n"));

        assertThrows(IllegalStateException.class, () -> SkillCatalog.load(classPath, List.of("terse")));
    }

    // --- digests -------------------------------------------------------

    @Test
    void aDigestIsTheSha256AsLowercaseHex() {
        assertEquals("sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                     SkillCatalog.digest("abc".getBytes(UTF_8)));
    }

    @Test
    void aPlatformWithoutSha256CannotDigest() {
        try (MockedStatic<MessageDigest> digests = mockStatic(MessageDigest.class)) {
            digests.when(() -> MessageDigest.getInstance("SHA-256"))
                   .thenThrow(new NoSuchAlgorithmException("not on this platform"));

            assertThrows(IllegalStateException.class, () -> SkillCatalog.digest(new byte[0]));
        }
    }

    // --- frontmatter ---------------------------------------------------

    @Test
    void flatStringsAreReadInOrderAndTheirQuotesRemoved() {
        Map<String, Object> fields = SkillCatalog.frontmatter("""
                ---
                name: x
                # a comment

                description: "Use it when: the user asks"
                license: 'MIT'
                empty: ""
                plain: it's plain, and C#-flavoured
                ---
                body: not frontmatter
                """);

        assertEquals(List.of("name", "description", "license", "empty", "plain"), List.copyOf(fields.keySet()));
        assertEquals("x", fields.get("name"));
        assertEquals("Use it when: the user asks", fields.get("description"));
        assertEquals("MIT", fields.get("license"));
        assertEquals("", fields.get("empty"));
        assertEquals("it's plain, and C#-flavoured", fields.get("plain"));
    }

    @Test
    void valuesThatAreNotPlainStringsAreRefusedRatherThanMisread() {
        List<String> lines = List.of(
                "version: 1", "ratio: 0.5", "big: 1e3", "infinite: .inf", "flag: true", "answer: yes",
                "nothing: ~", "nothing: null", "octal: 0o17", "hex: 0xFF",
                "tools: [read, write]", "map: {a: b}", "block: |", "anchor: &a x", "alias: *a", "tag: !str x",
                "dash: -x", "nested: a: b", "commented: a #b",
                "escaped: \"a\\nb\"", "inner: \"a\"b\"", "unclosed: \"half", "lone: \"",
                "doubled: 'it''s'", "unclosedSingle: 'half");
        for (String line : lines) {
            assertThrows(IllegalArgumentException.class, () -> SkillCatalog.frontmatter("---\n" + line + "\n---\n"),
                         line);
        }
    }

    @Test
    void linesThatAreNotFlatKeysAreRefused() {
        List<String> lines = List.of("  nested: x", "\"quoted\": x", "key:", "key: ", "key:value", "just words",
                                     "name: a\nname: b");
        for (String line : lines) {
            assertThrows(IllegalArgumentException.class, () -> SkillCatalog.frontmatter("---\n" + line + "\n---\n"),
                         line);
        }
    }

    @Test
    void aFileWithoutClosedFrontmatterIsRefused() {
        for (String markdown : List.of("", "# keyword-search\n", "---\nname: x\n")) {
            assertThrows(IllegalArgumentException.class, () -> SkillCatalog.frontmatter(markdown), markdown);
        }
    }

    /** A class path holding only the given files, each as UTF-8 text. */
    private static ClassLoader classPathOf(Map<String, String> files) {
        return new ClassLoader(null) {
            @Override
            public InputStream getResourceAsStream(String name) {
                String text = files.get(name);
                return text == null ? null : new ByteArrayInputStream(text.getBytes(UTF_8));
            }
        };
    }
}
