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

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class KeyWordSearch implements Tool {
    private static final LogFile logger = LogFileWriter.getInstance();
    private static final String FILE_PREFIX = "file://";
    private final Set<String> roots;


    public KeyWordSearch(Set<String> roots) {
        this.roots = roots;
    }

    @Override
    public String name() {
        return "key_word_search";
    }

    @Override
    public String description() {
        return "Searches for a specified keyword across all files in a project. Returns the total count of matches " +
                "and the absolute file paths of the files containing the keyword.";
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
                .build();
    }

    public ToolCallResult call(ToolCallParams toolCallParams) {
        String keyword = toolCallParams.arguments().get("keyword");
        ToolCallResultBuilder builder = ToolCallResultBuilder.builder();
        if (roots.isEmpty()) {
            builder.addTextContent("No search directory available. Provide one via the MCP roots/list mechanism " +
                                   "or by accepting the directory elicitation form.");
            builder.asError();
        } else {
            List<ContentItem> contentItems = searchKeywordInDirectories(roots, keyword);
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
                Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
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

        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                // Count occurrences of keyword in the line (case-sensitive)
                int index = 0;
                while ((index = line.indexOf(keyword, index)) != -1) {
                    count++;
                    index += keyword.length();
                }
            }
        } catch (MalformedInputException e) {
            // Not a text file, skip it
            return 0;
        } catch (IOException e) {
            // Error reading file
            throw e;
        }

        return count;
    }

}
