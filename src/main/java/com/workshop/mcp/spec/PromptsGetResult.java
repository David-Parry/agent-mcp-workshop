package com.workshop.mcp.spec;

import java.util.List;

/**
 * The result of {@code prompts/get}.
 * <p>
 * {@code prompts/get} is not cacheable, so this result carries
 * {@code resultType} but must not carry {@code ttlMs} or {@code cacheScope} —
 * a mistake worth noting, since the neighbouring {@code prompts/list} does
 * require them.
 * </p>
 *
 * @param resultType  always {@link ResultType#COMPLETE}
 * @param description a human-readable summary of the expanded prompt
 * @param messages    the expanded prompt messages
 *
 * @see Message
 * @since 1.0
 */
public record PromptsGetResult(
    String resultType,
    String description,
    List<Message> messages
) {
    /**
     * Creates a complete prompt result.
     *
     * @param description a human-readable summary of the expanded prompt
     * @param messages    the expanded prompt messages
     */
    public PromptsGetResult(String description, List<Message> messages) {
        this(ResultType.COMPLETE, description, messages);
    }
}
