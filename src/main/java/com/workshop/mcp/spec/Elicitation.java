package com.workshop.mcp.spec;

/**
 * The elicitation modes a client is able to render.
 * <p>
 * A client that can show an in-conversation form declares {@code form}; one
 * that can open an out-of-band page declares {@code url}. Both arrive as empty
 * JSON objects, so presence is the whole signal.
 * </p>
 * <p>
 * In revision {@code 2026-07-28} a server no longer sends
 * {@code elicitation/create} as a request — modern clients discard inbound
 * requests. It instead embeds one in an {@link InputRequiredResult}.
 * </p>
 *
 * @param form non-null when the client can render an in-conversation form
 * @param url  non-null when the client can open an out-of-band URL
 *
 * @since 1.0
 */
public record Elicitation(Capability form, Capability url) {

    /**
     * Reports whether the client can render an in-conversation form, which is
     * the mode this workshop uses to ask for a search directory.
     *
     * @return true when form mode is available
     */
    public boolean supportsForm() {
        return form != null;
    }
}
