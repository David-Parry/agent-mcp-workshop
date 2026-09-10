package com.workshop.mcp.tools;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers the continuation state the keyword-search tool carries across a
 * Multi Round-Trip Requests exchange.
 */
@Tag("chapter05")
class SearchContinuationTest {

    private static final String KEYWORD = "needle";

    @Test
    void awaitingDirectoryCarriesTheKeywordAndTheDirectoryStage() {
        SearchContinuation state = SearchContinuation.awaitingDirectory(KEYWORD);

        assertEquals(KEYWORD, state.keyword());
        assertEquals(SearchContinuation.STAGE_DIRECTORY, state.stage());
    }

    @Test
    void anEncodedContinuationDecodesBackToTheSameState() {
        SearchContinuation state = SearchContinuation.awaitingDirectory(KEYWORD);

        SearchContinuation decoded = SearchContinuation.decode(state.encode());

        assertEquals(state, decoded);
    }

    @Test
    void anEncodedContinuationIsUnpaddedUrlSafeBase64() {
        String encoded = SearchContinuation.awaitingDirectory(KEYWORD).encode();

        assertFalse(encoded.contains("="), "requestState travels in JSON, so padding buys nothing");
        assertFalse(encoded.contains("+") || encoded.contains("/"));
        assertNotNull(SearchContinuation.decode(encoded));
    }

    @Test
    void decodeReturnsNullForAnAbsentRequestState() {
        assertNull(SearchContinuation.decode(null));
    }

    @Test
    void decodeReturnsNullForABlankRequestState() {
        assertNull(SearchContinuation.decode("   "));
    }

    @Test
    void decodeReturnsNullForAValueThatIsNotBase64() {
        assertNull(SearchContinuation.decode("!!! not base64 !!!"));
    }

    @Test
    void decodeReturnsNullForBase64ThatIsNotJson() {
        assertNull(SearchContinuation.decode(base64("{{{ not json")));
    }

    @Test
    void decodeReturnsNullForBase64ThatDecodesToJsonNull() {
        assertNull(SearchContinuation.decode(base64("null")));
    }

    @Test
    void decodeReturnsNullWhenTheStateNamesNoStage() {
        assertNull(SearchContinuation.decode(base64("{\"keyword\":\"" + KEYWORD + "\"}")),
                   "a state that does not say which question was asked is not usable");
    }

    private static String base64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
