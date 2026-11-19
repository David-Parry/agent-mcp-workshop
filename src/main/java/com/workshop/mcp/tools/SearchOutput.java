package com.workshop.mcp.tools;

import java.util.List;

/**
 * The structured content of a keyword search, matching
 * {@link KeyWordSearch#outputSchema()}.
 * <p>
 * It says the same thing as the search's text content, one line per file,
 * but as an object a program can read without parsing lines: the MCP App
 * renders from it, and a client may validate it against the schema.
 * </p>
 *
 * @param keyword      the keyword searched for
 * @param totalMatches the occurrences across every file
 * @param files        one entry per file that held the keyword
 */
public record SearchOutput(String keyword, int totalMatches, List<FileMatch> files) {

    /**
     * One file that held the keyword.
     *
     * @param path  the file's absolute path
     * @param count the occurrences in that file
     */
    public record FileMatch(String path, int count) {}

    /**
     * Sums the matches into an output.
     *
     * @param keyword the keyword searched for; null reads as an empty one,
     *                because the schema requires a string
     * @param files   the files that held it
     * @return the structured content of the search
     */
    public static SearchOutput of(String keyword, List<FileMatch> files) {
        return new SearchOutput(keyword == null ? "" : keyword,
                                files.stream().mapToInt(FileMatch::count).sum(),
                                List.copyOf(files));
    }
}
