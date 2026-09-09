package com.workshop.mcp.spec;

/**
 * Represents an error response in the JSON-RPC protocol used by the MCP (Model Context Protocol) system.
 * <p>
 * JsonRpcErrorResponse follows the JSON-RPC 2.0 specification for error responses.
 * It is sent when a request cannot be processed successfully, providing structured
 * error information back to the client.
 * </p>
 * 
 * @param jsonrpc the JSON-RPC protocol version, should be "2.0"
 * @param id the ID of the request that caused this error, matching the original request
 * @param error the error object containing details about what went wrong
 * 
 * @see JsonRpcError
 * @see JsonRpcRequest
 * @see JsonRpcResponse
 * @since 1.0
 */
public record JsonRpcErrorResponse(
    String jsonrpc,
    RequestId id,
    JsonRpcError error
) {}
