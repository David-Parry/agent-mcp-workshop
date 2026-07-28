package com.workshop.mcp.spec;

/**
 * Represents the schema for a single property in the MCP (Model Context Protocol) system.
 * <p>
 * PropertySchema defines the structure and constraints for individual properties
 * within an input schema. It follows JSON Schema conventions to specify the type,
 * description, and requirements for each property, enabling validation and
 * documentation of tool inputs.
 * </p>
 * <p>
 * Only {@code type} and {@code description} are written to the wire. The other
 * two components are inputs to {@link InputSchema}, which consumes them and
 * expresses the same facts in its own shape: {@code key} becomes the property's
 * position in {@link InputSchema#properties()}, and {@code isRequired} becomes
 * membership of {@link InputSchema#required()}. Serializing them on the property
 * as well would state each fact twice using keywords JSON Schema does not
 * define, so {@link PropertySchemaTypeAdapter} omits them.
 * </p>
 * 
 * @param key the name of the property; consumed by {@link InputSchema}, not serialized
 * @param type the JSON Schema type of the property (e.g., "string", "number", "boolean")
 * @param description a human-readable description of the property's purpose and constraints
 * @param isRequired whether this property is required in the input; consumed by
 *                   {@link InputSchema}, not serialized
 * 
 * @see InputSchema
 * @see PropertySchemaTypeAdapter
 * @since 1.0
 */
public record PropertySchema(
    String key,
    String type,
    String description,
    boolean isRequired
) {}
