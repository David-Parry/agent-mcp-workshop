package com.workshop.mcp.spec;

/**
 * Represents an error in the JSON-RPC protocol used by the MCP (Model Context Protocol) system.
 * <p>
 * JsonRpcError follows the JSON-RPC 2.0 specification for error objects, providing
 * standardized error reporting with error codes, human-readable messages, and optional
 * additional data. This ensures consistent error handling across the protocol.
 * </p>
 * 
 * @param code the error code as defined by JSON-RPC or application-specific codes
 * @param message a human-readable error message describing what went wrong
 * @param data optional additional information about the error, can be any JSON-serializable object
 * 
 * @see JsonRpcErrorResponse
 * @see ErrorCodes
 * @since 1.0
 */
public record JsonRpcError(
    int code,
    String message,
    Object data
) {}
