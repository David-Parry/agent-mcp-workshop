package com.workshop.mcp.tools;

import com.workshop.mcp.spec.CompletionCompleteResponse;
import com.workshop.mcp.spec.builders.CompletionCompleteBuilder;

import java.util.List;
import java.util.Locale;

/**
 * Suggests keywords for the {@code search_keyword} prompt as the user types.
 * <p>
 * A completion offers what starts with the partial input, ignoring case, and
 * says how many values matched in {@code total}. One completion may carry at
 * most {@value #MAX_VALUES} values, so a longer match is cut there and
 * {@code hasMore} tells the client to ask again with more typed.
 * </p>
 */
public final class KeywordCompletions {

    /** The most values a single completion may carry. */
    public static final int MAX_VALUES = 100;

    /** Words worth searching this project for, most useful first. */
    static final List<String> VOCABULARY = List.of(
            "java", "the", "and", "javadoc", "json", "jsonrpc", "junit",
            "record", "request", "requestState", "resource", "response", "result", "resultType", "router",
            "server", "session", "schema", "search", "stream", "string", "subscription",
            "task", "test", "tool", "tools/call",
            "capability", "client", "completion", "cursor", "cache", "cacheScope",
            "elicitation", "envelope", "error", "extension",
            "keyword", "message", "method", "notification", "prompt", "protocol", "deprecated");

    private final List<String> words;

    /** Completes from {@link #VOCABULARY}. */
    public KeywordCompletions() {
        this(VOCABULARY);
    }

    KeywordCompletions(List<String> words) {
        this.words = List.copyOf(words);
    }

    /**
     * Completes what the user has typed so far.
     *
     * @param typed the partial input; null or empty offers every word
     * @return at most {@value #MAX_VALUES} matches, with how many there were in all
     */
    public CompletionCompleteResponse complete(String typed) {
        String prefix = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        List<String> matches = words.stream()
                .filter(word -> word.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
        List<String> values = matches.subList(0, Math.min(MAX_VALUES, matches.size()));
        return new CompletionCompleteBuilder()
                .values(values)
                .total(matches.size())
                .hasMore(values.size() < matches.size())
                .build();
    }
}
