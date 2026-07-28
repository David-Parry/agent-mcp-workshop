package com.workshop.mcp.spec.builders;

import com.workshop.mcp.spec.Annotations;
import com.workshop.mcp.spec.Resource;
import com.workshop.mcp.spec.Role;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for creating {@link Resource} objects with a fluent API.
 * <p>
 * This builder provides a flexible way to construct Resource objects with
 * various configurations. It supports setting all resource properties including
 * URI, name, description, MIME type, and annotations. The builder can also
 * construct Annotations inline by setting audience and priority values.
 * </p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * Resource resource = ResourceBuilder.builder()
 *     .withUri("file:///path/to/file.txt")
 *     .withName("Sample File")
 *     .withDescription("A sample text file")
 *     .withMimeType("text/plain")
 *     .addAudience(Role.USER)
 *     .withPriority(1.0)
 *     .build();
 * }</pre>
 * 
 * @see Resource
 * @see Annotations
 * @see Role
 * @since 1.0
 */
public class ResourceBuilder {
    private String uri;
    private String name;
    private String description;
    private String mimeType = Resource.DEFAULT_MIME_TYPE;
    private Annotations annotations;
    
    // Fields for building Annotations
    private List<Role> audience;
    private Double priority;

    /**
     * Private constructor to enforce the use of the static builder method.
     */
    private ResourceBuilder() {
    }

    /**
     * Creates a new ResourceBuilder instance.
     * 
     * @return a new ResourceBuilder instance
     */
    public static ResourceBuilder builder() {
        return new ResourceBuilder();
    }

    /**
     * Sets the URI for the resource being built.
     * 
     * @param uri the URI identifying the resource
     * @return this builder instance for method chaining
     */
    public ResourceBuilder withUri(String uri) {
        this.uri = uri;
        return this;
    }

    /**
     * Sets the name for the resource being built.
     * 
     * @param name the human-readable name of the resource
     * @return this builder instance for method chaining
     */
    public ResourceBuilder withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Sets the description for the resource being built.
     * 
     * @param description a detailed description of the resource
     * @return this builder instance for method chaining
     */
    public ResourceBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Sets the MIME type for the resource being built.
     * 
     * @param mimeType the MIME type of the resource content
     * @return this builder instance for method chaining
     */
    public ResourceBuilder withMimeType(String mimeType) {
        this.mimeType = mimeType;
        return this;
    }

    /**
     * Sets the MIME type to JSON for the resource being built.
     * <p>
     * This is a convenience method that sets the MIME type to "application/json".
     * </p>
     * 
     * @return this builder instance for method chaining
     */
    public ResourceBuilder withJsonMimeType() {
        this.mimeType = Resource.MIME_TYPE_JSON;
        return this;
    }

    /**
     * Sets pre-built annotations for the resource being built.
     * <p>
     * Note: If this method is used, any audience or priority values set
     * separately will be ignored.
     * </p>
     * 
     * @param annotations the annotations to attach to the resource
     * @return this builder instance for method chaining
     */
    public ResourceBuilder withAnnotations(Annotations annotations) {
        this.annotations = annotations;
        return this;
    }

    /**
     * Sets the audience list for the resource's annotations.
     * <p>
     * This will be used to create an Annotations object if one is not
     * provided directly via {@link #withAnnotations(Annotations)}.
     * </p>
     * 
     * @param audience a list of roles representing the intended audience
     * @return this builder instance for method chaining
     */
    public ResourceBuilder withAudience(List<Role> audience) {
        this.audience = audience;
        return this;
    }

    /**
     * Adds a single role to the audience list for the resource's annotations.
     * <p>
     * This method can be called multiple times to build up the audience list.
     * </p>
     * 
     * @param role the role to add to the audience
     * @return this builder instance for method chaining
     */
    public ResourceBuilder addAudience(Role role) {
        if (this.audience == null) {
            this.audience = new ArrayList<>();
        }
        this.audience.add(role);
        return this;
    }

    /**
     * Sets the priority for the resource's annotations.
     * <p>
     * This will be used to create an Annotations object if one is not
     * provided directly via {@link #withAnnotations(Annotations)}.
     * </p>
     * 
     * @param priority the priority value for the resource
     * @return this builder instance for method chaining
     */
    public ResourceBuilder withPriority(Double priority) {
        this.priority = priority;
        return this;
    }

    /**
     * Builds and returns the final {@link Resource} object.
     * <p>
     * This method validates that required fields (URI and name) are set,
     * creates annotations if audience or priority were specified, and
     * constructs the final Resource object.
     * </p>
     * 
     * @return a new Resource instance with the configured properties
     * @throws IllegalStateException if URI or name is not set
     */
    public Resource build() {
        // Build annotations if audience or priority were set
        if (annotations == null && (audience != null || priority != null)) {
            annotations = new Annotations(audience, priority);
        }
        
        // Validate required fields
        if (uri == null) {
            throw new IllegalStateException("URI is required for Resource");
        }
        if (name == null) {
            throw new IllegalStateException("Name is required for Resource");
        }
        
        return new Resource(uri, name, description, mimeType, annotations);
    }
}