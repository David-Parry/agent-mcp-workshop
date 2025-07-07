package com.workshop.mcp.spec;

/**
 * Represents an unknown or unrecognized JSON structure in the MCP (Model Context Protocol) system.
 * <p>
 * This record is used as a fallback when deserializing JSON data that doesn't match
 * any known message types or structures. It preserves the raw JSON string for
 * potential future processing or error handling.
 * </p>
 * 
 * @param json the raw JSON string of the unrecognized data
 * 
 * @since 1.0
 */
public record Unknown(String json) {
}
