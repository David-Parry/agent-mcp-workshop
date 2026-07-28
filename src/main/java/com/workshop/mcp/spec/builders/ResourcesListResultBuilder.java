package com.workshop.mcp.spec.builders;

import com.workshop.mcp.spec.Resource;
import com.workshop.mcp.spec.ResourcesListResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for creating {@link ResourcesListResult} objects with a fluent API.
 * <p>
 * This builder simplifies the construction of ResourcesListResult objects that contain
 * lists of available resources. It provides multiple ways to add resources, including
 * direct addition, inline construction, and using the ResourceBuilder for complex resources.
 * The builder also supports pagination through the nextCursor field.
 * </p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * ResourcesListResult result = ResourcesListResultBuilder.builder()
 *     .addResource("file:///docs/readme.txt", "README", "Project documentation")
 *     .addResource(ResourceBuilder.builder()
 *         .withUri("file:///data/config.json")
 *         .withName("Configuration")
 *         .withDescription("Application configuration")
 *         .withJsonMimeType()
 *         .build())
 *     .withNextCursor("next-page-token")
 *     .build();
 * }</pre>
 * 
 * @see ResourcesListResult
 * @see Resource
 * @see ResourceBuilder
 * @since 1.0
 */
public class ResourcesListResultBuilder {
    private List<Resource> resources = new ArrayList<>();
    private String nextCursor;

    /**
     * Private constructor to enforce the use of the static builder method.
     */
    private ResourcesListResultBuilder() {
    }

    /**
     * Creates a new ResourcesListResultBuilder instance.
     * 
     * @return a new ResourcesListResultBuilder instance
     */
    public static ResourcesListResultBuilder builder() {
        return new ResourcesListResultBuilder();
    }

    /**
     * Sets the complete list of resources, replacing any existing resources.
     * 
     * @param resources the list of Resource objects
     * @return this builder instance for method chaining
     */
    public ResourcesListResultBuilder withResources(List<Resource> resources) {
        this.resources = resources;
        return this;
    }

    /**
     * Adds a single resource to the list.
     * 
     * @param resource the Resource to add
     * @return this builder instance for method chaining
     */
    public ResourcesListResultBuilder addResource(Resource resource) {
        this.resources.add(resource);
        return this;
    }

    /**
     * Adds a resource with the specified basic properties.
     * <p>
     * This convenience method creates a Resource with the default MIME type
     * and no annotations.
     * </p>
     * 
     * @param uri the URI of the resource
     * @param name the name of the resource
     * @param description the description of the resource
     * @return this builder instance for method chaining
     */
    public ResourcesListResultBuilder addResource(String uri, String name, String description) {
        this.resources.add(new Resource(uri, name, description));
        return this;
    }

    /**
     * Adds a resource using a ResourceBuilder.
     * <p>
     * This method allows for complex resource construction with all available
     * properties including MIME type and annotations.
     * </p>
     * 
     * @param resourceBuilder the ResourceBuilder to build and add
     * @return this builder instance for method chaining
     */
    public ResourcesListResultBuilder addResource(ResourceBuilder resourceBuilder) {
        this.resources.add(resourceBuilder.build());
        return this;
    }

    /**
     * Sets the cursor for pagination.
     * <p>
     * The nextCursor allows clients to retrieve additional pages of resources
     * when the full list is too large to return in a single response.
     * </p>
     * 
     * @param nextCursor the cursor string for retrieving the next page of results
     * @return this builder instance for method chaining
     */
    public ResourcesListResultBuilder withNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
        return this;
    }

    /**
     * Builds and returns the final {@link ResourcesListResult} object.
     * 
     * @return a new ResourcesListResult instance with the configured resources and cursor
     */
    public ResourcesListResult build() {
        return new ResourcesListResult(resources, nextCursor);
    }
}