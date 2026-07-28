package com.workshop.mcp.spec.builders;

import com.workshop.mcp.spec.ContentItem;
import com.workshop.mcp.spec.ToolCallResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for creating {@link ToolCallResult} objects with a fluent API.
 * <p>
 * This builder simplifies the construction of ToolCallResult objects that represent
 * the outcome of tool invocations in the MCP system. It provides convenient methods
 * for adding content items and marking results as errors.
 * </p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Success result
 * ToolCallResult result = ToolCallResultBuilder.builder()
 *     .addTextContent("Operation completed successfully")
 *     .addTextContent("Processed 100 items")
 *     .build();
 * 
 * // Error result
 * ToolCallResult error = ToolCallResultBuilder.builder()
 *     .addTextContent("Error: File not found")
 *     .asError()
 *     .build();
 * }</pre>
 * 
 * @see ToolCallResult
 * @see ContentItem
 * @since 1.0
 */
public class ToolCallResultBuilder {
    private List<ContentItem> content = new ArrayList<>();
    private boolean isError = false;

    /**
     * Private constructor to enforce the use of the static builder method.
     */
    private ToolCallResultBuilder() {
    }

    /**
     * Creates a new ToolCallResultBuilder instance.
     * 
     * @return a new ToolCallResultBuilder instance
     */
    public static ToolCallResultBuilder builder() {
        return new ToolCallResultBuilder();
    }

    /**
     * Sets the complete list of content items, replacing any existing content.
     * 
     * @param content the list of ContentItem objects
     * @return this builder instance for method chaining
     */
    public ToolCallResultBuilder withContent(List<ContentItem> content) {
        this.content = content;
        return this;
    }

    /**
     * Adds a single content item to the result.
     * 
     * @param contentItem the ContentItem to add
     * @return this builder instance for method chaining
     */
    public ToolCallResultBuilder addContent(ContentItem contentItem) {
        this.content.add(contentItem);
        return this;
    }

    /**
     * Adds a text content item to the result.
     * <p>
     * This is a convenience method that creates a ContentItem with type "text".
     * </p>
     * 
     * @param text the text content to add
     * @return this builder instance for method chaining
     */
    public ToolCallResultBuilder addTextContent(String text) {
        this.content.add(new ContentItem(text, "text"));
        return this;
    }

    /**
     * Sets whether this result represents an error.
     * 
     * @param isError true if this is an error result, false otherwise
     * @return this builder instance for method chaining
     */
    public ToolCallResultBuilder withError(boolean isError) {
        this.isError = isError;
        return this;
    }

    /**
     * Marks this result as an error.
     * <p>
     * This is a convenience method equivalent to calling {@code withError(true)}.
     * </p>
     * 
     * @return this builder instance for method chaining
     */
    public ToolCallResultBuilder asError() {
        this.isError = true;
        return this;
    }

    /**
     * Builds and returns the final {@link ToolCallResult} object.
     * 
     * @return a new ToolCallResult instance with the configured content and error status
     */
    public ToolCallResult build() {
        return new ToolCallResult(content, isError);
    }
}