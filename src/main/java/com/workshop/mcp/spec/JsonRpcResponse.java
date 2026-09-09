package com.workshop.mcp.spec;

/**
 * Represents a successful response in the JSON-RPC protocol used by the MCP (Model Context Protocol) system.
 * <p>
 * JsonRpcSuccessResponse follows the JSON-RPC 2.0 specification for successful responses.
 * It is sent when a request has been processed successfully, containing the result
 * of the requested operation.
 * </p>
 * 
 * @param jsonrpc the JSON-RPC protocol version, should be "2.0"
 * @param id the ID of the request that this response is for, matching the original request
 * @param result the result of the successful operation, can be any JSON-serializable object
 * 
 * @see JsonRpcRequest
 * @see JsonRpcErrorResponse
 * @since 1.0
 */
public record JsonRpcResponse(
    String jsonrpc,
    RequestId id,
    Object result
) {}
