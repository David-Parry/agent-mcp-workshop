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
