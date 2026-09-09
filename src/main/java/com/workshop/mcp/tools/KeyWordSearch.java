package com.workshop.mcp.tools;

import com.workshop.mcp.io.LogFile;
import com.workshop.mcp.io.LogFileWriter;
import com.workshop.mcp.spec.ContentItem;
import com.workshop.mcp.spec.InputSchema;
import com.workshop.mcp.spec.ToolCallParams;
import com.workshop.mcp.spec.ToolCallResult;
import com.workshop.mcp.spec.builders.InputSchemaBuilder;
import com.workshop.mcp.spec.builders.PropertySchemaBuilder;
import com.workshop.mcp.spec.builders.ToolCallResultBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Searches a set of directories for a keyword.
 * <p>
 * This tool holds no state, and in particular it does not hold the directories
 * it searches. Revision {@code 2026-07-28} removed protocol sessions, so there
 * is no long-lived set of directories to consult: the router resolves them
 * afresh for every {@code tools/call} — from the call's own {@code directory}
 * argument, from an elicited path, or from
 * the working-directory fallback — and passes them to
 * {@link #call(ToolCallParams, Set)}.
 * </p>
 * <p>
 * An earlier version took the directories in its constructor and kept them in a
 * field. That was harmless while a fresh instance was built per call, but it
 * left the type looking like it remembered something across calls, and it meant
 * reading the tool's own metadata required inventing an empty set to construct
 * it with. Taking them as a call parameter makes the per-call lifetime
 * structural instead of a convention someone has to maintain.
 * </p>
 *
 * @see SearchContinuation
 * @since 1.0
 */
public class KeyWordSearch implements Tool {
    private static final LogFile logger = LogFileWriter.getInstance();
    private static final String FILE_PREFIX = "file://";

    @Override
    public String name() {
        return "key_word_search";
    }

    @Override
    public String description() {
        return "Searches for a specified keyword across all files in a project. Returns the total count of matches " +
                "and the absolute file paths of the files containing the keyword. Pass a directory to search it " +
                "directly; omit it and the server asks for one over a Multi Round-Trip Request, falling back to " +
                "its own working directory if nothing is offered.";
    }

    @Override
    public InputSchema schema() {
        return InputSchemaBuilder
                .builder()
                .withType("object")
                .addProperty(PropertySchemaBuilder
                                     .builder()
                                     .withKey("keyword")
                                     .withType("string")
                                     .withDescription("the keyword to search for in a file").required())
                .addProperty(PropertySchemaBuilder
                                     .builder()
                                     .withKey("directory")
                                     .withType("string")
                                     .withDescription("the absolute directory to search; omit it to have the "
                                                      + "server ask for one over a Multi Round-Trip Request and "
                                                      + "fall back to its working directory"))
                .build();
    }

    /**
     * Runs the search.
     * <p>
     * The router only calls this once it has at least one directory, so an
     * empty set means the resolution logic upstream let something through.
     * </p>
     *
     * @param toolCallParams the call parameters, whose {@code keyword} argument drives the search
     * @param directories    the directories to search, resolved for this call alone
     * @return the matching files and their match counts
     */
    @Override
    public ToolCallResult call(ToolCallParams toolCallParams, Set<String> directories) {
        String keyword = toolCallParams.arguments().get("keyword");
        ToolCallResultBuilder builder = ToolCallResultBuilder.builder();
        if (directories == null || directories.isEmpty()) {
            builder.addTextContent("No search directory was resolved for this call.");
            builder.asError();
        } else {
            List<ContentItem> contentItems = searchKeywordInDirectories(directories, keyword);
            builder.withContent(contentItems);
        }
        return builder.build();
    }

    /**
     * Searches for a keyword across multiple root directories and returns a list of ContentItem objects
     * containing files where the keyword was found along with the count of occurrences.
     *
     * @param rootDirectories List of root directory paths to search
     * @param keyword         The keyword to search for in files
     * @return List of ContentItem objects containing file paths and keyword counts
     */
    public List<ContentItem> searchKeywordInDirectories(Set<String> rootDirectories, String keyword) {
        List<ContentItem> results = new ArrayList<>();

        if (keyword == null || keyword.trim().isEmpty()) {
            return results;
        }

        String searchKeyword = keyword.trim();

        for (String rootDir : rootDirectories) {
            String cleanRootDir = rootDir;
            if (rootDir != null && rootDir.startsWith(FILE_PREFIX)) {
                cleanRootDir = rootDir.substring(FILE_PREFIX.length());
            }
            // Skip if cleanRootDir is null or empty
            if (cleanRootDir == null || cleanRootDir.trim().isEmpty()) {
                continue;
            }
            Path rootPath = Paths.get(cleanRootDir);

            if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
                continue;
            }
            try {
                // walkFileTree does not follow symlinks, so a symlinked root (on macOS
                // /tmp is a link to private/tmp) is visited as a single file and the tree
                // is never descended into — yielding zero matches. Resolve the link first.
                Path realRoot = rootPath.toRealPath();
                Files.walkFileTree(realRoot, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        // Skip binary files, hidden files, and archive files
                        String fileName = file.getFileName().toString();
                        if (Files.isRegularFile(file) &&
                                !fileName.startsWith(".") &&
                                !fileName.endsWith(".zip") &&
                                !fileName.endsWith(".jar") &&
                                isTextFile(file)) {
                            try {
                                int count = searchInFile(file, searchKeyword);
                                if (count > 0) {
                                    results.add(new ContentItem(file.toAbsolutePath() + ", keyword_count=" + count,
                                                                "text"));
                                }
                            } catch (IOException e) {
                                logger.log("Error searching in file: " + file, e);
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                        // Skip files/directories that can't be accessed
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                // Log error or handle as needed
                logger.log("Error walking directory tree: " + rootDir, e);
            }
        }

        return results;
    }

    /**
     * Checks if a file is likely a text file by attempting to read it as text.
     * <p>
     * Uses try-with-resources so the underlying {@code BufferedReader} is
     * always closed, even when the iteration short-circuits at the 10-line
     * limit or the file is not valid UTF-8. Without this, large recursive
     * walks (e.g. across a {@code node_modules} tree) leak a file descriptor
     * per file and quickly exhaust the per-process FD limit — at which point
     * every subsequent open fails and the tool starts returning empty results.
     * </p>
     *
     * @param file The file to check
     * @return true if the file appears to be a text file, false otherwise
     */
    private boolean isTextFile(Path file) {
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            lines.limit(10).forEach(line -> { /* just force iteration */ });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Searches for a keyword in a single file and returns the count of occurrences.
     *
     * @param file    The file to search in
     * @param keyword The keyword to search for
     * @return The count of keyword occurrences in the file
     * @throws IOException if the file cannot be read
     */
    private int searchInFile(Path file, String keyword) throws IOException {
        int count = 0;

        // Undecodable bytes are replaced rather than thrown on. isTextFile
        // only probes the first ten lines, so a file that turns out to hold
        // one bad byte further down had already been accepted as text — and
        // failing the decode here discarded every match in the readable part
        // of it. Whether that happened depended on where the byte fell
        // relative to the decoder's buffer, so the same content reported
        // different results at different sizes.
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Files.newInputStream(file),
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Count occurrences of keyword in the line (case-sensitive)
                int index = 0;
                while ((index = line.indexOf(keyword, index)) != -1) {
                    count++;
                    index += keyword.length();
                }
            }
        }

        return count;
    }

}
