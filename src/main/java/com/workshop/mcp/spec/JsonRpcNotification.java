package com.workshop.mcp.spec;

/**
 * Represents a JSON-RPC notification in the MCP (Model Context Protocol) system.
 * <p>
 * JsonRpcNotification follows the JSON-RPC 2.0 specification for notification messages.
 * Unlike requests, notifications do not expect a response and do not have an ID field.
 * They are used for one-way communication to inform the other party about events or
 * state changes.
 * </p>
 * 
 * @param jsonrpc the JSON-RPC protocol version, should be "2.0"
 * @param method the name of the notification method
 * @param params optional parameters for the notification, can be any JSON-serializable object
 * 
 * @see JsonRpcRequest
 * @since 1.0
 */
public record JsonRpcNotification(
    String jsonrpc,
    String method,
    Object params
) {}
