package com.workshop.mcp.spec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Deserializer for JSON-RPC messages in the MCP (Model Context Protocol) system.
 * <p>
 * This class provides functionality to deserialize JSON strings into appropriate
 * JSON-RPC message types based on their structure. It handles the polymorphic
 * nature of JSON-RPC messages by examining the presence of specific fields to
 * determine the correct message type.
 * </p>
 * <p>
 * The deserializer supports the following message types:
 * <ul>
 *   <li>{@link JsonRpcRequest} - Messages with "method" and "id" fields</li>
 *   <li>{@link JsonRpcNotification} - Messages with "method" but no "id" field</li>
 *   <li>{@link JsonRpcResponse} - Messages with a "result" field</li>
 *   <li>{@link JsonRpcErrorResponse} - Messages with an "error" field</li>
 *   <li>{@link Unknown} - Messages that don't match any known pattern</li>
 * </ul>
 * 
 * @see JsonRpcRequest
 * @see JsonRpcNotification
 * @see JsonRpcResponse
 * @see JsonRpcErrorResponse
 * @see Unknown
 * @since 1.0
 */
public class JsonRpcMessageDeserializer {
    private final Gson gson;

    /**
     * Constructs a new JsonRpcMessageDeserializer with a default Gson configuration.
     * <p>
     * The deserializer uses a standard Gson instance for JSON parsing and
     * object mapping operations.
     * </p>
     */
    public JsonRpcMessageDeserializer() {
        this.gson = new GsonBuilder().create();
    }

    /**
     * Deserializes a JSON string into the appropriate JSON-RPC message type.
     * <p>
     * This method examines the structure of the JSON to determine the correct
     * message type:
     * <ul>
     *   <li>If the JSON contains "method" and "id", it's a {@link JsonRpcRequest}</li>
     *   <li>If the JSON contains "method" without "id", it's a {@link JsonRpcNotification}</li>
     *   <li>If the JSON contains "result", it's a {@link JsonRpcResponse}</li>
     *   <li>If the JSON contains "error", it's a {@link JsonRpcErrorResponse}</li>
     *   <li>Otherwise, it's wrapped in an {@link Unknown} object</li>
     * </ul>
     * 
     * @param json the JSON string to deserialize
     * @return the deserialized message object, or {@link Unknown} if the format is unrecognized
     * @throws com.google.gson.JsonSyntaxException if the JSON is malformed
     * @throws IllegalStateException if the JSON string is not a valid JSON object
     */
    public Object deserialize(String json) {
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        if (jsonObject.has("method")) {
            if (jsonObject.has("id")) {
                return gson.fromJson(json, JsonRpcRequest.class);
            } else {
                return gson.fromJson(json, JsonRpcNotification.class);
            }
        } else if (jsonObject.has("result")) {
            return gson.fromJson(json, JsonRpcResponse.class);
        } else if (jsonObject.has("error")) {
            return gson.fromJson(json, JsonRpcErrorResponse.class);
        } else {
            return new Unknown(json);
        }
    }

    /**
     * Deserializes the params field of a JSON-RPC request into a specific type.
     * <p>
     * This method is useful for converting the generic params Map from a
     * {@link JsonRpcRequest} into a strongly-typed parameter object specific
     * to the method being called.
     * </p>
     *
     * @param <T> the type to deserialize the params into
     * @param request the JSON-RPC request containing the params to deserialize
     * @param paramsClass the class of the params type
     * @return the deserialized params object
     * @throws com.google.gson.JsonSyntaxException if the params cannot be deserialized to the specified type
     */
    public <T> T deserializeParams(JsonRpcRequest request, Class<T> paramsClass) {
        return gson.fromJson(gson.toJson(request.params()), paramsClass);
    }

    /**
     * Deserializes the result field of a JSON-RPC success response into a specific type.
     * <p>
     * This method is useful for converting the generic result Object from a
     * {@link JsonRpcResponse} into a strongly-typed result object specific
     * to the method that was called.
     * </p>
     *
     * @param <T> the type to deserialize the result into
     * @param response the JSON-RPC success response containing the result to deserialize
     * @param resultClass the class of the result type
     * @return the deserialized result object
     * @throws com.google.gson.JsonSyntaxException if the result cannot be deserialized to the specified type
     */
    public <T> T deserializeResult(JsonRpcResponse response, Class<T> resultClass) {
        return gson.fromJson(gson.toJson(response.result()), resultClass);
    }

    /**
     * Deserializes the params field of a JSON-RPC notification into a specific type.
     * <p>
     * This method is useful for converting the generic params Map from a
     * {@link JsonRpcNotification} into a strongly-typed parameter object specific
     * to the notification being sent.
     * </p>
     *
     * @param <T>         the type to deserialize the params into
     * @param request     the JSON-RPC notification containing the params to deserialize
     * @param paramsClass the class of the params type
     * @return the deserialized params object
     * @throws com.google.gson.JsonSyntaxException if the params cannot be deserialized to the specified type
     */
    public <T> T deserializeParams(JsonRpcNotification request, Class<T> paramsClass) {
        return gson.fromJson(gson.toJson(request.params()), paramsClass);
    }
}
