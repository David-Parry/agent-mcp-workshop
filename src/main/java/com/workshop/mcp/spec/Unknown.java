package com.workshop.mcp.spec;

/**
 * Represents an unknown or unrecognized JSON structure in the MCP (Model Context Protocol) system.
 * <p>
 * This record is used as a fallback when deserializing JSON data that doesn't match
 * any known message types or structures: an array, a bare value, an object without
 * {@code "jsonrpc": "2.0"}, one that is none of the four message shapes, or one
 * whose members have the wrong types, such as an id that is an object. It is
 * well-formed JSON that is not a valid message, which JSON-RPC answers with
 * {@link ErrorCodes#INVALID_REQUEST}. It preserves the raw JSON string for
 * potential future processing or error handling.
 * </p>
 * 
 * @param json the raw JSON string of the unrecognized data
 * 
 * @since 1.0
 */
public record Unknown(String json) {
}
