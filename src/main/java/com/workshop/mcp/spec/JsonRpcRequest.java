package com.workshop.mcp.spec;

import java.util.Map;

/**
 * Represents a JSON-RPC request in the MCP (Model Context Protocol) system.
 * <p>
 * JsonRpcRequest follows the JSON-RPC 2.0 specification for request objects.
 * It encapsulates all the necessary information for making a remote procedure call,
 * including the protocol version, request identifier, method name, and parameters.
 * </p>
 * 
 * @param jsonrpc the JSON-RPC protocol version, should be "2.0"
 * @param id the unique identifier for this request, used to match responses
 * @param method the name of the method to be invoked
 * @param params the parameters for the method call, can be a Map or List
 * 
 * @see JsonRpcResponse
 * @see JsonRpcErrorResponse
 * @since 1.0
 */
public record JsonRpcRequest(
    String jsonrpc,
    RequestId id,
    String method,
    Object params
) {}
