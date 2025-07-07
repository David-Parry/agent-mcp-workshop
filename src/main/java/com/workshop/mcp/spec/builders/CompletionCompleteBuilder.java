package com.workshop.mcp.spec.builders;

import com.workshop.mcp.spec.Completion;
import com.workshop.mcp.spec.CompletionCompleteResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder class for constructing {@link CompletionCompleteResponse} objects.
 * <p>
 * This builder provides a fluent API for creating completion responses in the MCP
 * (Model Context Protocol) system. It allows setting completion values, the total
 * number of available completions, and whether more completions are available.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * CompletionCompleteResponse response = CompletionCompleteBuilder
 *     .withValue("completion1")
 *     .value("completion2")
 *     .total(10)
 *     .hasMore(true)
 *     .build();
 * }</pre>
 * 
 * @see CompletionCompleteResponse
 * @see Completion
 * @since 1.0
 */
public class CompletionCompleteBuilder {
    private final List<String> values = new ArrayList<>();
    private Integer total = 1;
    private Boolean hasMore = false;

    /**
     * Creates a new CompletionCompleteBuilder instance.
     */
    public CompletionCompleteBuilder() {
    }

    /**
     * Convenience method to create a builder with a single value.
     * 
     * @param value the initial completion value to add
     * @return a new builder instance with the specified value
     */
    public static CompletionCompleteBuilder withValue(String value) {
        return new CompletionCompleteBuilder().value(value);
    }

    /**
     * Add a single value to the completion (with null description).
     * 
     * @param value the completion value to add
     * @return this builder instance for method chaining
     */
    public CompletionCompleteBuilder value(String value) {
        this.values.add(value);
        return this;
    }

    /**
     * Add multiple string values to the completion (with null descriptions).
     * 
     * @param values the list of completion values to add
     * @return this builder instance for method chaining
     */
    public CompletionCompleteBuilder values(List<String> values) {
        this.values.addAll(values);
        return this;
    }

    /**
     * Set the total number of completions available.
     * Default is 1.
     * 
     * @param total the total number of completions available
     * @return this builder instance for method chaining
     */
    public CompletionCompleteBuilder total(Integer total) {
        this.total = total;
        return this;
    }

    /**
     * Set whether there are more completions available.
     * Default is false.
     * 
     * @param hasMore true if more completions are available, false otherwise
     * @return this builder instance for method chaining
     */
    public CompletionCompleteBuilder hasMore(Boolean hasMore) {
        this.hasMore = hasMore;
        return this;
    }

    /**
     * Build the CompletionCompleteResponse.
     * 
     * @return a new CompletionCompleteResponse with the configured values
     */
    public CompletionCompleteResponse build() {
        Completion completion = new Completion(values);
        return new CompletionCompleteResponse(completion, total, hasMore);
    }
}
