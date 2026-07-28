package com.workshop.mcp.spec.builders;

import com.workshop.mcp.spec.ReadResourceResult;
import com.workshop.mcp.spec.TextReadResource;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for creating {@link ReadResourceResult} objects with a fluent API.
 * <p>
 * This builder simplifies the construction of ReadResourceResult objects that represent
 * the contents of resources read in the MCP system. A read that fails is not built
 * here: it is answered with an error instead.
 * </p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * ReadResourceResult result = ReadResourceResultBuilder.builder()
 *     .addTextContent("file:///doc1.txt", "text/plain", "Document content")
 *     .addTextContent("file:///doc2.json", "application/json", "{\"key\": \"value\"}")
 *     .build();
 * }</pre>
 * 
 * @see ReadResourceResult
 * @see TextReadResource
 * @since 1.0
 */
public class ReadResourceResultBuilder {
    private List<TextReadResource> contents = new ArrayList<>();

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
     * Builds and returns the final {@link ReadResourceResult} object.
     * 
     * @return a new ReadResourceResult instance with the configured contents
     */
    public ReadResourceResult build() {
        return new ReadResourceResult(contents);
    }
}