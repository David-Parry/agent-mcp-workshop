package com.workshop.mcp.spec;

/**
 * Defines standard JSON-RPC error codes used in the MCP (Model Context Protocol) system.
 * <p>
 * This class contains the standard error codes as defined by the JSON-RPC 2.0 specification.
 * These codes are used in {@link JsonRpcError} objects to indicate specific types of errors
 * that can occur during request processing. The codes are negative integers in the range
 * -32768 to -32000, which are reserved for pre-defined errors.
 * </p>
 * <p>
 * Error codes from -32000 to -32099 are reserved for implementation-defined server errors,
 * while application-specific errors should use codes outside the reserved ranges.
 * </p>
 * 
 * @see JsonRpcError
 * @see JsonRpcErrorResponse
 * @since 1.0
 */
public final class ErrorCodes {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ErrorCodes() {
        throw new AssertionError("ErrorCodes class should not be instantiated");
    }

    /**
     * Invalid JSON was received by the server.
     * <p>
     * This error occurs when the server receives malformed JSON that cannot be parsed.
     * This is a critical error that prevents any further processing of the request.
     * </p>
     */
    public static final int PARSE_ERROR = -32700;

    /**
     * The JSON sent is not a valid Request object.
     * <p>
     * This error indicates that while the JSON is valid, it does not conform to the
     * JSON-RPC request structure (e.g., missing required fields like jsonrpc, method, or id).
     * </p>
     */
    public static final int INVALID_REQUEST = -32600;

    /**
     * The method does not exist / is not available.
     * <p>
     * This error is returned when the client attempts to call a method that the server
     * does not recognize or support. Check {@link UniqueKeys} for valid method names.
     * </p>
     */
    public static final int METHOD_NOT_FOUND = -32601;

    /**
     * Invalid method parameter(s).
     * <p>
     * This error indicates that the method exists but the provided parameters are invalid,
     * missing required fields, or have incorrect types. The error data should provide
     * details about which parameters are invalid.
     * </p>
     */
    public static final int INVALID_PARAMS = -32602;

    /**
     * Internal JSON-RPC error.
     * <p>
     * This is a generic error for internal JSON-RPC protocol errors that don't fit into
     * the other categories. This typically indicates a problem with the JSON-RPC
     * implementation itself rather than the application logic.
     * </p>
     */
    public static final int INTERNAL_ERROR = -32603;

}
