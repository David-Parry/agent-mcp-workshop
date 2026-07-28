package com.workshop.mcp.spec;

import java.util.Map;
import java.util.List;

/**
 * Represents the input schema for tools in the MCP (Model Context Protocol) system.
 * <p>
 * InputSchema defines the structure and validation rules for input parameters
 * that a tool expects. It follows JSON Schema conventions to specify the type,
 * properties, and requirements for tool inputs, enabling proper validation and
 * type checking of tool invocations.
 * </p>
 * <p>
 * A schema with no {@code $schema} field defaults to JSON Schema 2020-12,
 * which is what this record emits. Revision {@code 2026-07-28} loosened the
 * field to accept any 2020-12 keyword rather than the small subset earlier
 * revisions allowed, so richer schemas are legal — at the cost of two rules
 * worth knowing. An implementation MUST NOT automatically dereference a
 * {@code $ref} that resolves to a network URI, and it SHOULD bound the
 * composition keywords ({@code anyOf}, {@code oneOf}, {@code allOf},
 * {@code if}/{@code then}/{@code else}, {@code $defs}) so a hostile schema
 * cannot exhaust the validator.
 * </p>
 * 
 * @param type the JSON Schema type (typically "object" for tool inputs)
 * @param properties a map of property names to their schemas, defining available input fields
 * @param required a list of property names that must be provided when invoking the tool
 * 
 * @see PropertySchema
 * @see Tool
 * @since 1.0
 */
public record InputSchema(
    String type,
    Map<String, PropertySchema> properties,
    List<String> required
) {}
