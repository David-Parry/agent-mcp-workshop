package com.workshop.mcp.spec;

import java.util.List;

/**
 * The result of a completed {@code tools/call}.
 * <p>
 * {@code tools/call} is not a cacheable method, so this result carries
 * {@code resultType} but must not carry {@code ttlMs} or {@code cacheScope}.
 * </p>
 * <p>
 * A tool that cannot finish without more information returns an
 * {@link InputRequiredResult} instead of this record.
 * </p>
 *
 * @param resultType always {@link ResultType#COMPLETE}
 * @param content    the content items the tool produced
 * @param isError    true when the tool itself failed, as opposed to a protocol error
 *
 * @see InputRequiredResult
 * @since 1.0
 */
public record ToolCallResult(
    String resultType,
    List<ContentItem> content,
    boolean isError
) {
    /**
     * Creates a complete tool call result.
     *
     * @param content the content items the tool produced
     * @param isError true when the tool itself failed
     */
    public ToolCallResult(List<ContentItem> content, boolean isError) {
        this(ResultType.COMPLETE, content, isError);
    }
}
