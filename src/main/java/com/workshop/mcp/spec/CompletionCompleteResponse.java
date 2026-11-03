package com.workshop.mcp.spec;

/**
 * The result of {@code completion/complete}.
 * <p>
 * Not a cacheable method, so this result carries {@code resultType} but must
 * not carry {@code ttlMs} or {@code cacheScope}.
 * </p>
 *
 * @param resultType always {@link ResultType#COMPLETE}
 * @param completion the suggested values, with their total and whether there are more
 *
 * @see Completion
 * @since 1.0
 */
public record CompletionCompleteResponse(String resultType, Completion completion) {
    /**
     * Creates a complete completion response.
     *
     * @param completion the suggested values, with their total and whether there are more
     */
    public CompletionCompleteResponse(Completion completion) {
        this(ResultType.COMPLETE, completion);
    }
}
