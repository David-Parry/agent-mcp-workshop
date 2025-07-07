package com.workshop.mcp.spec;

/**
 * Represents the schema for a single property in the MCP (Model Context Protocol) system.
 * <p>
 * PropertySchema defines the structure and constraints for individual properties
 * within an input schema. It follows JSON Schema conventions to specify the type,
 * description, and requirements for each property, enabling validation and
 * documentation of tool inputs.
 * </p>
 * 
 * @param key the name of the property
 * @param type the JSON Schema type of the property (e.g., "string", "number", "boolean")
 * @param description a human-readable description of the property's purpose and constraints
 * @param isRequired whether this property is required in the input
 * 
 * @see InputSchema
 * @since 1.0
 */
public record PropertySchema(
    String key,
    String type,
    String description,
    boolean isRequired
) {}
