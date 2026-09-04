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
 * {@link InputRequiredResult#inputRequests()}. Only
 * {@code elicitation/create}, {@code roots/list}, and
 * {@code sampling/createMessage} are permitted.
 * </p>
 *
 * @param method one of {@code elicitation/create}, {@code roots/list}, or
 *               {@code sampling/createMessage}
 * @param params the parameters for that method, or null when it takes none
 *
 * @see InputRequiredResult
 * @since 1.0
 */
public record InputRequest(String method, Object params) {

    /**
     * Builds an embedded {@code roots/list} request.
     * <p>
     * {@code roots/list} takes no parameters, and clients typically answer it
     * from their configured roots without prompting the user.
     * </p>
     *
     * @return the embedded request
     */
    public static InputRequest rootsList() {
        return new InputRequest(UniqueKeys.ROOTS_LIST.getValue(), null);
    }

    /**
     * Builds an embedded {@code elicitation/create} request.
     *
     * @param params the form to present to the user
     * @return the embedded request
     */
    public static InputRequest elicitation(ElicitationCreateParams params) {
        return new InputRequest(UniqueKeys.ELICITATION_CREATE.getValue(), params);
    }

    /**
     * Builds an embedded {@code sampling/createMessage} request.
     *
     * @param message the sampling request to hand to the client's model
     * @return the embedded request
     */
    public static InputRequest sampling(CreateSamplingMessage message) {
        return new InputRequest(UniqueKeys.SAMPLING_CREATE_MESSAGE.getValue(), message);
    }
}
