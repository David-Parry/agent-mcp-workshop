package com.workshop.mcp.spec;

import java.util.Map;

/**
 * Represents parameters for invoking a tool in the MCP (Model Context Protocol) system.
 * <p>
 * ToolCallParams contains all the information needed to execute a tool, including
 * the tool name and its arguments. The arguments are provided as a map of key-value
 * pairs that should match the tool's input schema.
 * </p>
 * <p>
 * The specification types each argument as {@code unknown}, so a value is whatever
 * JSON the client sent: a string, a number, a boolean, an object, an array or null.
 * Gson reads them as {@code String}, {@code Double}, {@code Boolean}, {@code Map},
 * {@code List} or {@code null}. Checking them against the schema is the tool's job,
 * and a value it cannot use is a tool error, not a malformed request.
 * </p>
 * <p>
 * The last two fields carry a Multi Round-Trip Requests retry. When the server
 * previously answered with an {@link InputRequiredResult}, the client re-sends
 * the same call with {@code inputResponses} holding the answers — keyed by the
 * identifiers the server chose — and {@code requestState} echoed back
 * verbatim. Both are absent on a first attempt.
 * </p>
 * <p>
 * There is no {@code task} field. Revision {@code 2026-07-28} removed
 * per-request task opt-in, and servers MUST ignore it if a client sends one;
 * whether a call becomes a task is now the server's decision.
 * </p>
 *
 * @param _meta optional metadata, which on this revision also carries the stateless lifecycle envelope
 * @param name the name of the tool to invoke
 * @param arguments a map of argument names to their values as the client sent them, to be checked against the tool's input schema
 * @param inputResponses answers to a previous {@link InputRequiredResult}, keyed by request identifier
 * @param requestState the opaque continuation state from that result, echoed back byte-exact
 *
 * @see Tool
 * @see ToolCallResult
 * @see InputRequiredResult
 * @since 1.0
 */
public record ToolCallParams(
    MetaInfo _meta,
    String name,
    Map<String, Object> arguments,
    Map<String, Object> inputResponses,
    String requestState
) {

    /**
     * Looks up an argument the tool's schema declares as a string.
     *
     * @param key the argument's name
     * @return the argument, or null when it is absent or not a JSON string
     */
    public String stringArgument(String key) {
        return arguments != null && arguments.get(key) instanceof String value ? value : null;
    }

    /**
     * Looks up one answer from a Multi Round-Trip Requests retry.
     *
     * @param key the identifier the server used in {@link InputRequiredResult#inputRequests()}
     * @return the raw answer, or null when the client did not provide one
     */
    public Object inputResponse(String key) {
        return inputResponses == null ? null : inputResponses.get(key);
    }
}
