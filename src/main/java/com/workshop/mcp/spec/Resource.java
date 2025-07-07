package com.workshop.mcp.spec;

/**
 * Represents a resource that can be accessed through the MCP (Model Context Protocol) system.
 * <p>
 * Resources are content items that the server makes available to clients, such as files,
 * documents, or data streams. Each resource is identified by a URI and includes metadata
 * about its content type and purpose.
 * </p>
 * 
 * @param uri the unique identifier for the resource, typically a URI
 * @param name a human-readable name for the resource
 * @param description a detailed description of the resource's content and purpose
 * @param mimeType the MIME type of the resource content (e.g., "text/plain", "application/json")
 * @param annotations optional annotations providing additional metadata about the resource
 * 
 * @see Annotations
 * @since 1.0
 */
public record Resource(String uri, String name, String description, String mimeType, Annotations annotations) {
    /**
     * The default MIME type for resources when not explicitly specified.
     */
    public static final String DEFAULT_MIME_TYPE = "text/html";
    
    /**
     * MIME type constant for JSON content.
     */
    public static final String MIME_TYPE_JSON = "application/json";

    /**
     * Creates a resource with default MIME type and no annotations.
     * 
     * @param uri the unique identifier for the resource
     * @param name a human-readable name for the resource
     * @param description a detailed description of the resource
     */
    public Resource(String uri, String name, String description) {
        this(uri, name, description, DEFAULT_MIME_TYPE, null);
    }
}