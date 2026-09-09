package com.workshop.mcp.tools;

import com.workshop.mcp.spec.ContentItem;
import com.workshop.mcp.spec.ToolCallParams;
import com.workshop.mcp.spec.ToolCallResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/**
 * Covers the keyword search tool and the continuation state it carries across a
 * Multi Round-Trip Requests exchange: the directories it accepts and rejects,
 * the files it refuses to read, and the failures it swallows so one unreadable
 * corner of a tree cannot sink the whole search.
 */
class KeyWordSearchTest {

    private static final String KEYWORD = "needle";

    @TempDir
    Path root;

    private KeyWordSearch tool;

    @BeforeEach
    void setUp() {
        tool = new KeyWordSearch();
    }

    // --- call ------------------------------------------------------------

    @Test
    void callWithNoResolvedDirectoryIsAToolError() {
        ToolCallResult result = tool.call(params(KEYWORD), null);

        assertTrue(result.isError());
        assertEquals("No search directory was resolved for this call.", result.content().get(0).text());
    }

    @Test
    void callWithAnEmptyDirectorySetIsAToolError() {
        ToolCallResult result = tool.call(params(KEYWORD), Set.of());

        assertTrue(result.isError());
    }

    @Test
    void callReportsTheMatchesFoundInTheResolvedDirectories() throws IOException {
        write("hit.txt", "a " + KEYWORD + " here");

        ToolCallResult result = tool.call(params(KEYWORD), Set.of(root.toString()));

        assertFalse(result.isError());
        assertEquals(1, result.content().size());
        assertTrue(result.content().get(0).text().endsWith("keyword_count=1"));
    }

    // --- the keyword ------------------------------------------------------

    @Test
    void aNullKeywordMatchesNothing() throws IOException {
        write("hit.txt", KEYWORD);

        assertEquals(List.of(), tool.searchKeywordInDirectories(Set.of(root.toString()), null));
    }

    @Test
    void aBlankKeywordMatchesNothing() throws IOException {
        write("hit.txt", KEYWORD);

        assertEquals(List.of(), tool.searchKeywordInDirectories(Set.of(root.toString()), "   "));
    }

    @Test
    void everyOccurrenceOnEveryLineIsCounted() throws IOException {
        write("hit.txt", KEYWORD + " " + KEYWORD + "\nand another " + KEYWORD + "\n");

        List<ContentItem> results = tool.searchKeywordInDirectories(Set.of(root.toString()), KEYWORD);

        assertEquals(1, results.size());
        assertTrue(results.get(0).text().endsWith("keyword_count=3"), results.get(0).text());
        assertEquals("text", results.get(0).type());
    }

    @Test
    void aFileWithoutTheKeywordIsNotReported() throws IOException {
        write("miss.txt", "nothing of interest");

        assertEquals(List.of(), tool.searchKeywordInDirectories(Set.of(root.toString()), KEYWORD));
    }

    @Test
    void nestedDirectoriesAreSearchedToo() throws IOException {
        Files.createDirectories(root.resolve("a/b"));
        write("a/b/deep.txt", KEYWORD);

        assertEquals(1, tool.searchKeywordInDirectories(Set.of(root.toString()), KEYWORD).size());
    }

    // --- the directory argument -------------------------------------------

    @Test
    void aNullDirectoryEntryIsSkipped() {
        assertEquals(List.of(), tool.searchKeywordInDirectories(Collections.singleton(null), KEYWORD));
    }

    @Test
    void aBlankDirectoryEntryIsSkipped() {
        assertEquals(List.of(), tool.searchKeywordInDirectories(Set.of("   "), KEYWORD));
    }

    @Test
    void aFileUriPrefixIsStrippedBeforeTheDirectoryIsOpened() throws IOException {
        write("hit.txt", KEYWORD);

        assertEquals(1, tool.searchKeywordInDirectories(Set.of("file://" + root), KEYWORD).size());
    }

    @Test
    void aFileUriWithNoPathLeftAfterThePrefixIsSkipped() {
        assertEquals(List.of(), tool.searchKeywordInDirectories(Set.of("file://"), KEYWORD));
    }

    @Test
    void aDirectoryThatDoesNotExistIsSkipped() {
        String missing = root.resolve("nowhere").toString();

        assertEquals(List.of(), tool.searchKeywordInDirectories(Set.of(missing), KEYWORD));
    }

    @Test
    void aPathThatIsAFileRatherThanADirectoryIsSkipped() throws IOException {
        Path file = write("hit.txt", KEYWORD);

        assertEquals(List.of(), tool.searchKeywordInDirectories(Set.of(file.toString()), KEYWORD));
    }

    @Test
    void aSymlinkedRootIsResolvedSoTheTreeIsDescendedInto() throws IOException {
        Files.createDirectory(root.resolve("target"));
        write("target/hit.txt", KEYWORD);
        Path link = Files.createSymbolicLink(root.resolve("link"), root.resolve("target"));

        // walkFileTree does not follow links, so without toRealPath the link is
        // visited as a single file — this is the macOS /tmp -> /private/tmp case.
        assertEquals(1, tool.searchKeywordInDirectories(Set.of(link.toString()), KEYWORD).size());
    }

    // --- files the walk refuses to read ------------------------------------

    @Test
    void hiddenFilesAreSkipped() throws IOException {
        write(".hidden.txt", KEYWORD);

        assertEquals(List.of(), tool.searchKeywordInDirectories(Set.of(root.toString()), KEYWORD));
    }

    @Test
    void archiveFilesAreSkipped() throws IOException {
        write("bundle.zip", KEYWORD);
        write("bundle.jar", KEYWORD);

        assertEquals(List.of(), tool.searchKeywordInDirectories(Set.of(root.toString()), KEYWORD));
    }

    @Test
    void aBinaryFileIsSkipped() throws IOException {
        Files.write(root.resolve("image.bin"),
                    new byte[]{(byte) 0xFF, (byte) 0xFE, 'n', 'e', 'e', 'd', 'l', 'e', (byte) 0xC3, (byte) 0x28});

        assertEquals(List.of(), tool.searchKeywordInDirectories(Set.of(root.toString()), KEYWORD));
    }

    @Test
    void aDanglingSymlinkIsNotARegularFileAndIsSkipped() throws IOException {
        Files.createSymbolicLink(root.resolve("dangling.txt"), root.resolve("gone.txt"));

        assertEquals(List.of(), tool.searchKeywordInDirectories(Set.of(root.toString()), KEYWORD));
    }

    @Test
    void oneBadByteDoesNotDiscardTheMatchesInTheRestOfTheFile() throws IOException {
        writeTextWithAMalformedTail("late-binary.txt");

        // The first ten lines decode cleanly, so the file gets past isTextFile,
        // and the bad byte only shows up thousands of lines later. Decoding
        // strictly used to throw at that point and throw away all ten matches
        // with it, so the same content reported differently depending on
        // whether the bad byte landed inside the decoder's first buffer.
        List<ContentItem> found = tool.searchKeywordInDirectories(Set.of(root.toString()), KEYWORD);

        assertEquals(1, found.size(), "" + found);
        assertTrue(found.get(0).text().endsWith("keyword_count=10"), "" + found);
    }

    @Test
    void anUnreadableSubdirectoryIsSteppedOverRatherThanFailingTheWalk() throws IOException {
        write("hit.txt", KEYWORD);
        Path locked = Files.createDirectory(root.resolve("locked"));
        write("locked/buried.txt", KEYWORD);

        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
        try {
            assertEquals(1, tool.searchKeywordInDirectories(Set.of(root.toString()), KEYWORD).size());
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    void aFileThatFailsMidReadIsLoggedAndTheRestOfTheTreeIsStillSearched() throws IOException {
        write("hit.txt", KEYWORD);

        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.newInputStream(any(Path.class)))
                 .thenThrow(new IOException("the volume went away"));

            assertEquals(List.of(), tool.searchKeywordInDirectories(Set.of(root.toString()), KEYWORD));
        }
    }

    @Test
    void aDirectoryWalkThatFailsIsLoggedRatherThanPropagated() throws IOException {
        write("hit.txt", KEYWORD);

        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.walkFileTree(any(Path.class), ArgumentMatchers.<FileVisitor<Path>>any()))
                 .thenThrow(new IOException("the volume went away"));

            assertEquals(List.of(), tool.searchKeywordInDirectories(Set.of(root.toString()), KEYWORD));
        }
    }

    // --- SearchContinuation ------------------------------------------------

    @Test
    void awaitingDirectoryCarriesTheKeywordAndTheDirectoryStage() {
        SearchContinuation state = SearchContinuation.awaitingDirectory(KEYWORD);

        assertEquals(KEYWORD, state.keyword());
        assertEquals(SearchContinuation.STAGE_DIRECTORY, state.stage());
    }

    @Test
    void anEncodedContinuationDecodesBackToTheSameState() {
        SearchContinuation state = SearchContinuation.awaitingDirectory(KEYWORD);

        SearchContinuation decoded = SearchContinuation.decode(state.encode());

        assertEquals(state, decoded);
    }

    @Test
    void anEncodedContinuationIsUnpaddedUrlSafeBase64() {
        String encoded = SearchContinuation.awaitingDirectory(KEYWORD).encode();

        assertFalse(encoded.contains("="), "requestState travels in JSON, so padding buys nothing");
        assertFalse(encoded.contains("+") || encoded.contains("/"));
        assertNotNull(SearchContinuation.decode(encoded));
    }

    @Test
    void decodeReturnsNullForAnAbsentRequestState() {
        assertNull(SearchContinuation.decode(null));
    }

    @Test
    void decodeReturnsNullForABlankRequestState() {
        assertNull(SearchContinuation.decode("   "));
    }

    @Test
    void decodeReturnsNullForAValueThatIsNotBase64() {
        assertNull(SearchContinuation.decode("!!! not base64 !!!"));
    }

    @Test
    void decodeReturnsNullForBase64ThatIsNotJson() {
        assertNull(SearchContinuation.decode(base64("{{{ not json")));
    }

    @Test
    void decodeReturnsNullForBase64ThatDecodesToJsonNull() {
        assertNull(SearchContinuation.decode(base64("null")));
    }

    @Test
    void decodeReturnsNullWhenTheStateNamesNoStage() {
        assertNull(SearchContinuation.decode(base64("{\"keyword\":\"" + KEYWORD + "\"}")),
                   "a state that does not say which question was asked is not usable");
    }

    // --- helpers -----------------------------------------------------------

    private static ToolCallParams params(String keyword) {
        Map<String, String> arguments = new HashMap<>();
        arguments.put("keyword", keyword);
        return new ToolCallParams(null, "key_word_search", arguments, null, null);
    }

    private static String base64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private Path write(String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.writeString(file, content);
        return file;
    }

    /**
     * Writes a file whose first ten lines are clean UTF-8 but whose tail is not,
     * with enough filler between them that the ten-line text probe never reaches
     * the bad bytes.
     */
    private void writeTextWithAMalformedTail(String name) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < 10; i++) {
            bytes.write((KEYWORD + " line " + i + "\n").getBytes(StandardCharsets.UTF_8));
        }
        for (int i = 0; i < 2_000; i++) {
            bytes.write("filler filler filler filler filler filler\n".getBytes(StandardCharsets.UTF_8));
        }
        bytes.write(new byte[]{(byte) 0xC3, (byte) 0x28, '\n'});
        Files.write(root.resolve(name), bytes.toByteArray());
    }
}
