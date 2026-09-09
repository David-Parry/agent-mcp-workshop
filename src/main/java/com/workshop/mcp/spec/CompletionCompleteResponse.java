package com.workshop.mcp.spec;

/**
 * The result of {@code completion/complete}.
 * <p>
 * Not a cacheable method, so this result carries {@code resultType} but must
 * not carry {@code ttlMs} or {@code cacheScope}.
 * </p>
 *
 * @param resultType always {@link ResultType#COMPLETE}
 * @param completion the suggested completion values
 * @param total      the total number of matches available, or null if unknown
 * @param hasMore    true when more values exist beyond those returned
 *
 * @see Completion
 * @since 1.0
 */
public record CompletionCompleteResponse(String resultType, Completion completion, Integer total, Boolean hasMore) {
    /**
     * Creates a complete completion response.
     *
     * @param completion the suggested completion values
     * @param total      the total number of matches available, or null
     * @param hasMore    true when more values exist beyond those returned
     */
    public CompletionCompleteResponse(Completion completion, Integer total, Boolean hasMore) {
        this(ResultType.COMPLETE, completion, total, hasMore);
    }
}
