package com.workshop.mcp.tools;

import com.workshop.mcp.spec.InputSchema;
import com.workshop.mcp.spec.ToolCallResult;
import com.workshop.mcp.spec.builders.InputSchemaBuilder;
import com.workshop.mcp.spec.builders.ToolCallResultBuilder;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * The searches this server has run, newest first, for a tool only the
 * keyword-search page calls.
 * <p>
 * The history lives in the router rather than in a tool, so the tools stay
 * stateless. It is bounded, and it lasts as long as the process, which on
 * stdio is one client's connection.
 * </p>
 */
public final class RecentSearches {

    /** The name the page calls the tool by. */
    public static final String TOOL_NAME = "recent_searches";

    /** What the tool does, for the host's tool list. */
    public static final String DESCRIPTION =
            "Lists the keyword searches this server has run, newest first. Only the keyword-search page calls it.";

    /** How many searches are kept. */
    public static final int CAPACITY = 10;

    private final Clock clock;
    private final Deque<Search> searches = new ArrayDeque<>();

    /**
     * One search that ran.
     *
     * @param keyword      what was searched for
     * @param directories  where
     * @param totalMatches how many occurrences were found
     * @param searchedAt   when, as an ISO-8601 instant
     */
    public record Search(String keyword, List<String> directories, int totalMatches, String searchedAt) {
    }

    /**
     * Creates an empty history.
     *
     * @param clock where each search's time is read from
     */
    public RecentSearches(Clock clock) {
        this.clock = clock;
    }

    /**
     * The tool takes no arguments.
     *
     * @return an object schema with no properties
     */
    public static InputSchema schema() {
        return InputSchemaBuilder.builder().withType("object").build();
    }

    /**
     * Records a search, if it produced a result to record. The oldest search
     * is dropped once {@link #CAPACITY} are kept.
     *
     * @param directories where the search looked
     * @param result      what the keyword search returned
     */
    public synchronized void record(Collection<String> directories, ToolCallResult result) {
        if (result.structuredContent() instanceof SearchOutput output) {
            searches.addFirst(new Search(output.keyword(), List.copyOf(directories), output.totalMatches(),
                                         clock.instant().toString()));
            if (searches.size() > CAPACITY) {
                searches.removeLast();
            }
        }
    }

    /**
     * The searches kept, newest first.
     *
     * @return a snapshot of the history
     */
    public synchronized List<Search> list() {
        return List.copyOf(searches);
    }

    /**
     * Answers a call to the tool: a line of text per search for any caller,
     * and the same list as structured content for the page.
     *
     * @return the tool result
     */
    public ToolCallResult result() {
        List<Search> snapshot = list();
        ToolCallResultBuilder builder = ToolCallResultBuilder.builder()
                .withStructuredContent(Map.of("searches", snapshot));
        if (snapshot.isEmpty()) {
            builder.addTextContent("No searches yet.");
        }
        for (Search search : snapshot) {
            builder.addTextContent(search.keyword() + " in " + String.join(", ", search.directories())
                                   + ", total_matches=" + search.totalMatches() + ", at " + search.searchedAt());
        }
        return builder.build();
    }
}
