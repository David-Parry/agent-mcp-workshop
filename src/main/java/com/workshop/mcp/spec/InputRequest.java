package com.workshop.mcp.spec;

/**
 * A single request embedded in an {@link InputRequiredResult}.
 * <p>
 * This is the Multi Round-Trip Requests replacement for a server-initiated
 * JSON-RPC request. Revision {@code 2026-07-28} forbids a server from writing
 * requests to the client — modern clients silently discard them — so the three
 * things a server used to ask for are embedded in a result instead.
 * </p>
 * <p>
 * Note what is missing: there is no {@code jsonrpc} and no {@code id}. An
 * embedded request is "de-JSON-RPC'd", identified purely by its
 * {@link #method()}, and correlated by the key it sits under in
 * {@link InputRequiredResult#inputRequests()}.
 * </p>
 * <p>
 * The revision permits three embedded methods: {@code elicitation/create},
 * {@code roots/list}, and {@code sampling/createMessage}. This server builds
 * only the first. The other two are deprecated under the {@code 2026-07-28}
 * feature lifecycle policy — ask the user, take a tool parameter, or call a
 * provider API directly instead — so there is no factory for either here, and
 * a client that receives one from some other server is unaffected by their
 * absence.
 * </p>
 *
 * @param method always {@code elicitation/create} as built by this server
 * @param params the parameters for that method, or null when it takes none
 *
 * @see InputRequiredResult
 * @since 1.0
 */
public record InputRequest(String method, Object params) {

    /**
     * Builds an embedded {@code elicitation/create} request.
     *
     * @param params the form to present to the user
     * @return the embedded request
     */
    public static InputRequest elicitation(ElicitationCreateParams params) {
        return new InputRequest(UniqueKeys.ELICITATION_CREATE.getValue(), params);
    }

}
