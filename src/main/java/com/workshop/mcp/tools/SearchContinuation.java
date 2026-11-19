package com.workshop.mcp.tools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.workshop.mcp.spec.McpGson;

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
 * arguments come back on their own. When the keyword was missing and the user
 * was asked for it, the answer arrives in {@code inputResponses} instead.
 * </p>
 * <p>
 * The protocol treats {@code requestState} as opaque bytes and guarantees only
 * that the client returns them unchanged. A client is free to send anything,
 * so the state is sealed by a {@link StateSigner}: a value this server did not
 * write, or wrote more than ten minutes ago, decodes to nothing and the retry
 * is treated as a first attempt.
 * </p>
 *
 * @param stage which question the server has already asked
 * @param token the page that collects the answer, for a URL-mode question
 *
 * @see com.workshop.mcp.spec.InputRequiredResult
 * @since 1.0
 */
public record SearchContinuation(String stage, String token) {

    /** The server has asked the user for a directory and is awaiting the answer. */
    public static final String STAGE_DIRECTORY = "directory";

    /** The server has asked for both a keyword and a directory, in one round. */
    public static final String STAGE_KEYWORD_AND_DIRECTORY = "keyword_and_directory";

    /** The server has sent the user to a page to answer. */
    public static final String STAGE_PAGE = "page";

    /** Key under which the embedded {@code elicitation/create} answer comes back. */
    public static final String KEY_DIRECTORY = "search_directory";

    /** Key under which the answer to the keyword question comes back. */
    public static final String KEY_KEYWORD = "search_keyword";

    /** Key under which the client confirms it opened the page. */
    public static final String KEY_PAGE = "search_page";

    private static final Gson GSON = McpGson.create();

    /**
     * Creates the state for a pending directory question.
     *
     * @return the continuation state
     */
    public static SearchContinuation awaitingDirectory() {
        return new SearchContinuation(STAGE_DIRECTORY, null);
    }

    /**
     * Creates the state for a pending keyword and directory question.
     *
     * @return the continuation state
     */
    public static SearchContinuation awaitingKeywordAndDirectory() {
        return new SearchContinuation(STAGE_KEYWORD_AND_DIRECTORY, null);
    }

    /**
     * Creates the state for a question asked on a page.
     *
     * @param token the page's token
     * @return the continuation state
     */
    public static SearchContinuation awaitingPage(String token) {
        return new SearchContinuation(STAGE_PAGE, token);
    }

    /**
     * Whether the keyword was asked for, so its answer should be read.
     *
     * @return true for the two-question stage
     */
    public boolean askedForKeyword() {
        return STAGE_KEYWORD_AND_DIRECTORY.equals(stage);
    }

    /**
     * Whether the question was asked on a page rather than in a form.
     *
     * @return true for the URL-mode stage
     */
    public boolean askedOnPage() {
        return STAGE_PAGE.equals(stage);
    }

    /**
     * Encodes this state as the opaque string handed to the client.
     *
     * @param signer seals the value
     * @return a sealed value, safe to place in {@code requestState}
     */
    public String encode(StateSigner signer) {
        return signer.seal(GSON.toJsonTree(this).getAsJsonObject());
    }

    /**
     * Decodes the {@code requestState} a client echoed back.
     * <p>
     * An absent, altered, foreign, or expired value yields null, which the
     * caller treats as a first attempt rather than an error.
     * </p>
     *
     * @param requestState the value from {@code params.requestState}, may be null
     * @param signer       opens the value
     * @return the decoded state, or null when there is nothing usable to decode
     */
    public static SearchContinuation decode(String requestState, StateSigner signer) {
        JsonObject state = signer.open(requestState);
        return state == null ? null : GSON.fromJson(state, SearchContinuation.class);
    }
}
