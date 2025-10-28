package com.workshop.mcp.spec;

import java.util.Map;

/**
 * Represents parameters for creating an elicitation request in the MCP (Model Context Protocol) system.
 * <p>
 * ElicitationCreateParams is used to request information from the user by providing
 * a prompt message and a JSON schema that defines the structure of the expected response.
 * This allows the server to gather structured input from users in a standardized way.
 * </p>
 * 
 * @param prompt the message to display to the user when requesting information
 * @param requestedSchema a JSON schema (as a Map) defining the structure and validation rules for the requested data
 * 
 * @see PropertySchema
 * @since 1.0
 */
public record ElicitationCreateParams(
    String prompt,
    Map<String, Object> requestedSchema
) {}
