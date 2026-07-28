package com.workshop.mcp.spec;

import java.util.Map;

/**
 * An interim result asking the client for information the server needs before
 * it can finish the request.
 * <p>
 * This is the Multi Round-Trip Requests pattern. Instead of the server calling
 * the client — impossible in revision {@code 2026-07-28}, where clients drop
 * inbound requests — the server answers with what it still needs. The client
 * gathers the answers and re-sends the original request, as a brand new
 * request with a brand new id, carrying {@code inputResponses} and the
 * {@code requestState} echoed back verbatim.
 * </p>
 * <p>
 * {@code requestState} is an opaque string, not JSON. It exists because the
 * server has nowhere else to keep continuation state: sessions are gone, so
 * anything the server learned while producing this interim result has to
 * travel to the client and back. Conventionally it is base64-encoded JSON, but
 * the protocol only guarantees the client returns the exact bytes.
 * </p>
 * <p>
 * At least one of {@code inputRequests} and {@code requestState} must be
 * present. Servers may only return this from {@code tools/call},
 * {@code prompts/get}, and {@code resources/read}.
 * </p>
 *
 * @param resultType    always {@link ResultType#INPUT_REQUIRED}
 * @param inputRequests the embedded requests, keyed by server-chosen
 *                      identifiers that the responses come back under
 * @param requestState  opaque continuation state, echoed back byte-exact
 *
 * @see InputRequest
 * @since 1.0
 */
public record InputRequiredResult(
        String resultType,
        Map<String, InputRequest> inputRequests,
        String requestState
) {
    /**
     * Creates an input-required result.
     *
     * @param inputRequests the embedded requests, keyed by identifier
     * @param requestState  opaque continuation state, echoed back byte-exact
     */
    public InputRequiredResult(Map<String, InputRequest> inputRequests, String requestState) {
        this(ResultType.INPUT_REQUIRED, inputRequests, requestState);
    }

    /**
     * Creates an input-required result carrying a single embedded request.
     *
     * @param key          the identifier the response will come back under
     * @param request      the embedded request
     * @param requestState opaque continuation state
     * @return the interim result
     */
    public static InputRequiredResult of(String key, InputRequest request, String requestState) {
        return new InputRequiredResult(Map.of(key, request), requestState);
    }
}
