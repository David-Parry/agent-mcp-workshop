package com.workshop.mcp.tools;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.workshop.mcp.spec.McpGson;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * The continuation state the keyword-search tool carries across a Multi
 * Round-Trip Requests exchange.
 * <p>
 * This exists because revision {@code 2026-07-28} removed protocol sessions.
 * When the server answers a {@code tools/call} with an
 * {@code InputRequiredResult}, it has nowhere to remember how far through the
 * exchange it had got. So it encodes that here, hands it to the client as an
 * opaque {@code requestState} string, and reads it back off the retry.
 * </p>
 * <p>
 * The keyword is not part of it. The retry repeats the original call, so the
 * arguments come back on their own, and a client can send any
 * {@code requestState} it likes. A keyword read from here could be swapped for
 * one the arguments never asked for. A forged stage can only skip the
 * question, which declining it does anyway.
 * </p>
 * <p>
 * The protocol treats {@code requestState} as opaque bytes and guarantees only
 * that the client returns them unchanged. Base64-encoded JSON is the
 * convention the specification's own examples use.
 * </p>
 *
 * @param stage which question the server has already asked
 *
 * @see com.workshop.mcp.spec.InputRequiredResult
 * @since 1.0
 */
public record SearchContinuation(String stage) {

    /** The server has asked the user for a directory and is awaiting the answer. */
    public static final String STAGE_DIRECTORY = "directory";

    /** Key under which the embedded {@code elicitation/create} answer comes back. */
    public static final String KEY_DIRECTORY = "search_directory";

    private static final Gson GSON = McpGson.create();

    /**
     * Creates the state for a pending directory question.
     *
     * @return the continuation state
     */
    public static SearchContinuation awaitingDirectory() {
        return new SearchContinuation(STAGE_DIRECTORY);
    }

    /**
     * Encodes this state as the opaque string handed to the client.
     *
     * @return base64-encoded JSON, safe to place in {@code requestState}
     */
    public String encode() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                GSON.toJson(this).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes the {@code requestState} a client echoed back.
     * <p>
     * A malformed or absent value yields null, which the caller treats as a
     * first attempt rather than an error. The state came from this server, but
     * it made a round trip through a client and should not be trusted blindly.
     * </p>
     *
     * @param requestState the value from {@code params.requestState}, may be null
     * @return the decoded state, or null when there is nothing usable to decode
     */
    public static SearchContinuation decode(String requestState) {
        if (requestState == null || requestState.isBlank()) {
            return null;
        }
        try {
            String json = new String(Base64.getUrlDecoder().decode(requestState), StandardCharsets.UTF_8);
            SearchContinuation decoded = GSON.fromJson(json, SearchContinuation.class);
            return (decoded == null || decoded.stage == null) ? null : decoded;
        } catch (IllegalArgumentException | JsonSyntaxException e) {
            return null;
        }
    }
}
