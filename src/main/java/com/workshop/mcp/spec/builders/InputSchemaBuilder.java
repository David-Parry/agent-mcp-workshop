package com.workshop.mcp.spec.builders;

import com.workshop.mcp.spec.InputSchema;
import com.workshop.mcp.spec.PropertySchema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for creating {@link InputSchema} objects with a fluent API.
 * <p>
 * This builder simplifies the construction of InputSchema objects used to define
 * the structure and validation rules for tool inputs in the MCP system.
 * It provides multiple ways to add properties and manage required fields,
 * automatically tracking requirements based on property configurations.
 * </p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * InputSchema schema = InputSchemaBuilder.builder()
 *     .addProperty("query", "string", "Search query", true)
 *     .addProperty("limit", "number", "Maximum results")
 *     .addProperty(PropertySchemaBuilder.builder()
 *         .withKey("filter")
 *         .withType("object")
 *         .withDescription("Filter options")
 *         .build())
 *     .build();
 * }</pre>
 * 
 * @see InputSchema
 * @see PropertySchema
 * @see PropertySchemaBuilder
 * @since 1.0
 */
public class InputSchemaBuilder {
    private String type = "object";
    private Map<String, PropertySchema> properties = new HashMap<>();
    private List<String> required = new ArrayList<>();

    /**
     * Private constructor to enforce the use of the static builder method.
     */
    private InputSchemaBuilder() {
    }

    /**
     * Creates a new InputSchemaBuilder instance.
     * <p>
     * The builder is initialized with a default type of "object".
     * </p>
     * 
     * @return a new InputSchemaBuilder instance
     */
    public static InputSchemaBuilder builder() {
        return new InputSchemaBuilder();
    }

    /**
     * Sets the JSON Schema type for the input schema.
     * 
     * @param type the JSON Schema type (e.g., "object", "array")
     * @return this builder instance for method chaining
     */
    public InputSchemaBuilder withType(String type) {
        this.type = type;
        return this;
    }

    /**
     * Sets all properties at once, replacing any existing properties.
     * <p>
     * Note: This method does not automatically update the required list.
     * Use {@link #withRequired(List)} to set required fields when using this method.
     * </p>
     * 
     * @param properties a map of property names to PropertySchema objects
     * @return this builder instance for method chaining
     */
    public InputSchemaBuilder withProperties(Map<String, PropertySchema> properties) {
        this.properties = properties;
        return this;
    }

    /**
     * Adds a single property to the schema.
     * <p>
     * If the property is marked as required, it will automatically be added
     * to the required list.
     * </p>
     * 
     * @param property the PropertySchema to add
     * @return this builder instance for method chaining
     */
    public InputSchemaBuilder addProperty(PropertySchema property) {
        this.properties.put(property.key(), property);
        if (property.isRequired()) {
            this.required.add(property.key());
        }
        return this;
    }

    /**
     * Adds a property using a PropertySchemaBuilder.
     * <p>
     * This is a convenience method that builds the property and adds it.
     * </p>
     * 
     * @param propertyBuilder the PropertySchemaBuilder to build and add
     * @return this builder instance for method chaining
     */
    public InputSchemaBuilder addProperty(PropertySchemaBuilder propertyBuilder) {
        return addProperty(propertyBuilder.build());
    }

    /**
     * Adds a property with all parameters specified.
     * 
     * @param key the property name
     * @param type the JSON Schema type of the property
     * @param description the description of the property
     * @param isRequired whether the property is required
     * @return this builder instance for method chaining
     */
    public InputSchemaBuilder addProperty(String key, String type, String description, boolean isRequired) {
        PropertySchema property = new PropertySchema(key, type, description, isRequired);
        return addProperty(property);
    }

    /**
     * Adds an optional property (not required).
     * <p>
     * This is a convenience method for adding properties that are not required.
     * </p>
     * 
     * @param key the property name
     * @param type the JSON Schema type of the property
     * @param description the description of the property
     * @return this builder instance for method chaining
     */
    public InputSchemaBuilder addProperty(String key, String type, String description) {
        return addProperty(key, type, description, false);
    }

    /**
     * Sets the complete list of required properties, replacing any existing list.
     * <p>
     * Use this method when you want to manually control the required list
     * rather than having it automatically managed based on property settings.
     * </p>
     * 
     * @param required the list of property names that are required
     * @return this builder instance for method chaining
     */
    public InputSchemaBuilder withRequired(List<String> required) {
        this.required = required;
        return this;
    }

    /**
     * Adds a property name to the required list.
     * <p>
     * This method prevents duplicates in the required list.
     * </p>
     * 
     * @param propertyKey the name of the property to mark as required
     * @return this builder instance for method chaining
     */
    public InputSchemaBuilder addRequired(String propertyKey) {
        if (!this.required.contains(propertyKey)) {
            this.required.add(propertyKey);
        }
        return this;
    }

    /**
     * Builds and returns the final {@link InputSchema} object.
     * 
     * @return a new InputSchema instance with the configured properties
     */
    public InputSchema build() {
        return new InputSchema(type, properties, required);
    }
}