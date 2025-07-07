package com.workshop.mcp.spec.builders;

import com.workshop.mcp.spec.ReadResourceResult;
import com.workshop.mcp.spec.TextReadResource;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for creating {@link ReadResourceResult} objects with a fluent API.
 * <p>
 * This builder simplifies the construction of ReadResourceResult objects that represent
 * the outcome of reading resources in the MCP system. It provides convenient methods
 * for adding resource contents and handling error states.
 * </p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Success result with multiple resources
 * ReadResourceResult result = ReadResourceResultBuilder.builder()
 *     .addTextContent("file:///doc1.txt", "text/plain", "Document content")
 *     .addTextContent("file:///doc2.json", "application/json", "{\"key\": \"value\"}")
 *     .build();
 * 
 * // Error result
 * ReadResourceResult error = ReadResourceResultBuilder.builder()
 *     .asError()
 *     .build();
 * }</pre>
 * 
 * @see ReadResourceResult
 * @see TextReadResource
 * @since 1.0
 */
public class ReadResourceResultBuilder {
    private List<TextReadResource> contents = new ArrayList<>();
    private boolean isError = false;

    /**
     * Private constructor to enforce the use of the static builder method.
     */
    private ReadResourceResultBuilder() {
    }

    /**
     * Creates a new ReadResourceResultBuilder instance.
     * 
     * @return a new ReadResourceResultBuilder instance
     */
    public static ReadResourceResultBuilder builder() {
        return new ReadResourceResultBuilder();
    }

    /**
     * Sets the complete list of resource contents, replacing any existing contents.
     * 
     * @param contents the list of TextReadResource objects
     * @return this builder instance for method chaining
     */
    public ReadResourceResultBuilder withContents(List<TextReadResource> contents) {
        this.contents = contents;
        return this;
    }

    /**
     * Adds a single resource content to the result.
     * 
     * @param content the TextReadResource to add
     * @return this builder instance for method chaining
     */
    public ReadResourceResultBuilder addContent(TextReadResource content) {
        this.contents.add(content);
        return this;
    }

    /**
     * Adds a text resource with the specified properties.
     * <p>
     * This is a convenience method that creates a TextReadResource internally.
     * </p>
     * 
     * @param uri the URI of the resource
     * @param mimeType the MIME type of the resource content
     * @param text the actual text content of the resource
     * @return this builder instance for method chaining
     */
    public ReadResourceResultBuilder addTextContent(String uri, String mimeType, String text) {
        this.contents.add(new TextReadResource(uri, mimeType, text));
        return this;
    }

    /**
     * Sets whether this result represents an error.
     * 
     * @param isError true if this is an error result, false otherwise
     * @return this builder instance for method chaining
     */
    public ReadResourceResultBuilder withError(boolean isError) {
        this.isError = isError;
        return this;
    }

    /**
     * Marks this result as an error.
     * <p>
     * This is a convenience method equivalent to calling {@code withError(true)}.
     * When a result is marked as an error, the contents list typically remains
     * empty or contains error information.
     * </p>
     * 
     * @return this builder instance for method chaining
     */
    public ReadResourceResultBuilder asError() {
        this.isError = true;
        return this;
    }

    /**
     * Builds and returns the final {@link ReadResourceResult} object.
     * 
     * @return a new ReadResourceResult instance with the configured contents and error status
     */
    public ReadResourceResult build() {
        return new ReadResourceResult(contents, isError);
    }
}