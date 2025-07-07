package com.workshop.mcp.spec.builders;

import com.workshop.mcp.spec.PropertySchema;

/**
 * Builder for creating {@link PropertySchema} objects with a fluent API.
 * <p>
 * This builder simplifies the construction of PropertySchema objects used to define
 * individual properties within an InputSchema. It provides convenient methods for
 * setting all property attributes and includes shortcuts for common patterns like
 * marking properties as required or optional.
 * </p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * PropertySchema property = PropertySchemaBuilder.builder()
 *     .withKey("username")
 *     .withType("string")
 *     .withDescription("The user's username")
 *     .required()
 *     .build();
 * }</pre>
 * 
 * @see PropertySchema
 * @see InputSchemaBuilder
 * @since 1.0
 */
public class PropertySchemaBuilder {
    private String key;
    private String type;
    private String description;
    private boolean isRequired = false;

    /**
     * Private constructor to enforce the use of the static builder method.
     */
    private PropertySchemaBuilder() {
    }

    /**
     * Creates a new PropertySchemaBuilder instance.
     * 
     * @return a new PropertySchemaBuilder instance
     */
    public static PropertySchemaBuilder builder() {
        return new PropertySchemaBuilder();
    }

    /**
     * Sets the key (name) for the property being built.
     * 
     * @param key the property name
     * @return this builder instance for method chaining
     */
    public PropertySchemaBuilder withKey(String key) {
        this.key = key;
        return this;
    }

    /**
     * Sets the JSON Schema type for the property being built.
     * 
     * @param type the JSON Schema type (e.g., "string", "number", "boolean", "object", "array")
     * @return this builder instance for method chaining
     */
    public PropertySchemaBuilder withType(String type) {
        this.type = type;
        return this;
    }

    /**
     * Sets the description for the property being built.
     * 
     * @param description a human-readable description of the property's purpose and constraints
     * @return this builder instance for method chaining
     */
    public PropertySchemaBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Sets whether the property is required.
     * 
     * @param isRequired true if the property is required, false otherwise
     * @return this builder instance for method chaining
     */
    public PropertySchemaBuilder isRequired(boolean isRequired) {
        this.isRequired = isRequired;
        return this;
    }

    /**
     * Marks the property as required.
     * <p>
     * This is a convenience method equivalent to calling {@code isRequired(true)}.
     * </p>
     * 
     * @return this builder instance for method chaining
     */
    public PropertySchemaBuilder required() {
        this.isRequired = true;
        return this;
    }

    /**
     * Marks the property as optional (not required).
     * <p>
     * This is a convenience method equivalent to calling {@code isRequired(false)}.
     * Properties are optional by default, so this method is mainly useful for
     * clarity or when changing a property from required to optional.
     * </p>
     * 
     * @return this builder instance for method chaining
     */
    public PropertySchemaBuilder optional() {
        this.isRequired = false;
        return this;
    }

    /**
     * Builds and returns the final {@link PropertySchema} object.
     * <p>
     * This method validates that all required fields (key, type, and description)
     * are set before creating the PropertySchema.
     * </p>
     * 
     * @return a new PropertySchema instance with the configured properties
     * @throws IllegalStateException if key, type, or description is null or empty
     */
    public PropertySchema build() {
        if (key == null || key.isEmpty()) {
            throw new IllegalStateException("Property key is required");
        }
        if (type == null || type.isEmpty()) {
            throw new IllegalStateException("Property type is required");
        }
        if (description == null || description.isEmpty()) {
            throw new IllegalStateException("Property description is required");
        }
        return new PropertySchema(key, type, description, isRequired);
    }
}