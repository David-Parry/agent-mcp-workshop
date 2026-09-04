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
 * @param arguments a map of argument names to their values, matching the tool's input schema
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
    Map<String, String> arguments,
    Map<String, Object> inputResponses,
    String requestState
) {

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
