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
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
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
    /**
     * Directories the search never descends into below its root: build output,
     * version-control metadata, and the server's own logs, which record every
     * keyword it is asked for and would otherwise match each search.
     */
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of("logs", "build", ".git", ".gradle");

    /**
     * Told how far a search has got, after each file it reads.
     */
    @FunctionalInterface
    public interface Progress {

        /** Hears nothing, for a search nobody is waiting on. */
        Progress NONE = (searched, total) -> { };

        /**
         * @param searched how many files have been read so far
         * @param total    how many files the search will read in all
         */
        void searched(int searched, int total);

        /**
         * Passes on the first report, the last, and at most one per interval
         * in between, so a fast search does not flood whoever is listening.
         *
         * @param clock          where the time between reports is read from
         * @param intervalMillis the least time between two reports in the middle
         * @param target         where the reports that get through go
         * @return a progress that reports to {@code target} less often
         */
        static Progress throttled(Clock clock, long intervalMillis, Progress target) {
            return new Throttled(clock, intervalMillis, target);
        }
    }

    /** One search's reports, thinned out. Used by the one thread running that search. */
    private static final class Throttled implements Progress {
        private final Clock clock;
        private final long intervalMillis;
        private final Progress target;
        private boolean reported;
        private long lastReportedAt;

        Throttled(Clock clock, long intervalMillis, Progress target) {
            this.clock = clock;
            this.intervalMillis = intervalMillis;
            this.target = target;
        }

        @Override
        public void searched(int searched, int total) {
            long now = clock.millis();
            if (reported && searched < total && now - lastReportedAt < intervalMillis) {
                return;
            }
            reported = true;
            lastReportedAt = now;
            target.searched(searched, total);
        }
    }

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
     * The JSON Schema of the search's {@code structuredContent}: the keyword,
     * the total count, and one entry per file.
     *
     * @return a JSON Schema object describing {@link SearchOutput}
     */
    public Map<String, Object> outputSchema() {
        Map<String, Object> file = ordered(
                "type", "object",
                "properties", ordered(
                        "path", ordered("type", "string", "description", "the absolute path of the file"),
                        "count", ordered("type", "integer", "description", "occurrences in this file")),
                "required", List.of("path", "count"));
        return ordered(
                "type", "object",
                "properties", ordered(
                        "keyword", ordered("type", "string", "description", "the keyword searched for"),
                        "totalMatches", ordered("type", "integer", "description", "occurrences across every file"),
                        "files", ordered("type", "array", "items", file)),
                "required", List.of("keyword", "totalMatches", "files"));
    }

    /** A map that keeps its insertion order, so the schema goes on the wire the same way every run. */
    private static Map<String, Object> ordered(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    /**
     * Runs the search.
     * <p>
     * The router only calls this once it has at least one directory, so an
     * empty set means the resolution logic upstream let something through.
     * </p>
     * <p>
     * A successful result carries the matches twice: as text, one line per
     * file, which is what a model reads, and as {@code structuredContent}
     * matching {@link #outputSchema()}. An error result carries text only.
     * </p>
     *
     * @param toolCallParams the call parameters, whose {@code keyword} argument drives the search
     * @param directories    the directories to search, resolved for this call alone
     * @return the matching files and their match counts
     */
    @Override
    public ToolCallResult call(ToolCallParams toolCallParams, Set<String> directories) {
        return call(toolCallParams, directories, Progress.NONE);
    }

    /**
     * Runs the search, reporting how far it has got as it goes.
     * <p>
     * An interrupt stops the search between files. The result then says the
     * search was stopped, rather than passing the matches found so far off as
     * all of them.
     * </p>
     *
     * @param toolCallParams the call parameters, whose {@code keyword} argument drives the search
     * @param directories    the directories to search, resolved for this call alone
     * @param progress       told after each file how many have been read, out of how many
     * @return the matching files and their match counts
     */
    public ToolCallResult call(ToolCallParams toolCallParams, Set<String> directories, Progress progress) {
        String keyword = toolCallParams.arguments().get("keyword");
        ToolCallResultBuilder builder = ToolCallResultBuilder.builder();
        if (directories == null || directories.isEmpty()) {
            builder.addTextContent("No search directory was resolved for this call.");
            builder.asError();
        } else {
            List<SearchOutput.FileMatch> matches = findMatches(directories, keyword, progress);
            if (Thread.currentThread().isInterrupted()) {
                builder.addTextContent("The search was stopped before it finished.");
                builder.asError();
            } else {
                builder.withContent(asContent(matches));
                builder.withStructuredContent(SearchOutput.of(keyword, matches));
            }
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
        return asContent(findMatches(rootDirectories, keyword));
    }

    private static List<ContentItem> asContent(List<SearchOutput.FileMatch> matches) {
        List<ContentItem> content = new ArrayList<>();
        for (SearchOutput.FileMatch match : matches) {
            content.add(new ContentItem(match.path() + ", keyword_count=" + match.count(), "text"));
        }
        return content;
    }

    /**
     * Searches for a keyword across multiple root directories.
     *
     * @param rootDirectories the root directory paths to search
     * @param keyword         the keyword to search for in files
     * @return one entry per file that held the keyword, with how often it did
     */
    public List<SearchOutput.FileMatch> findMatches(Set<String> rootDirectories, String keyword) {
        return findMatches(rootDirectories, keyword, Progress.NONE);
    }

    /**
     * Searches for a keyword across multiple root directories, reporting how
     * far it has got after each file, and stopping between files once the
     * thread is interrupted.
     * <p>
     * The files are counted first, in a walk that reads none of them, so each
     * report can say how many there are in all.
     * </p>
     *
     * @param rootDirectories the root directory paths to search
     * @param keyword         the keyword to search for in files
     * @param progress        told after each file how many have been read, out of how many
     * @return one entry per file that held the keyword, with how often it did
     */
    public List<SearchOutput.FileMatch> findMatches(Set<String> rootDirectories, String keyword,
                                                    Progress progress) {
        List<SearchOutput.FileMatch> results = new ArrayList<>();

        if (keyword == null || keyword.trim().isEmpty()) {
            return results;
        }

        String searchKeyword = keyword.trim();
        int[] total = {0};
        walk(rootDirectories, file -> total[0]++);
        int[] searched = {0};
        walk(rootDirectories, file -> {
            if (isTextFile(file)) {
                try {
                    int count = searchInFile(file, searchKeyword);
                    if (count > 0) {
                        results.add(new SearchOutput.FileMatch(file.toAbsolutePath().toString(), count));
                    }
                } catch (IOException e) {
                    logger.log("Error searching in file: " + file, e);
                }
            }
            progress.searched(++searched[0], total[0]);
        });
        return results;
    }

    /**
     * Walks every root, handing each file worth reading to {@code visit}:
     * regular files that are not hidden and not archives, outside the
     * directories the search skips.
     */
    private void walk(Set<String> rootDirectories, Consumer<Path> visit) {
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
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        boolean skipped = !dir.equals(realRoot)
                                          && SKIPPED_DIRECTORIES.contains(dir.getFileName().toString());
                        return skipped ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (Thread.currentThread().isInterrupted()) {
                            return FileVisitResult.TERMINATE;
                        }
                        // Skip hidden files and archive files
                        String fileName = file.getFileName().toString();
                        if (Files.isRegularFile(file) &&
                                !fileName.startsWith(".") &&
                                !fileName.endsWith(".zip") &&
                                !fileName.endsWith(".jar")) {
                            visit.accept(file);
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
