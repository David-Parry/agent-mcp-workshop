package com.workshop.mcp.tools;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers the continuation state the keyword-search tool carries across a
 * Multi Round-Trip Requests exchange.
 */
@Tag("chapter05")
class SearchContinuationTest {

    @Test
    void awaitingDirectoryCarriesOnlyTheDirectoryStage() {
        SearchContinuation state = SearchContinuation.awaitingDirectory();

        assertEquals(SearchContinuation.STAGE_DIRECTORY, state.stage());
        assertEquals("{\"stage\":\"directory\"}",
                     new String(Base64.getUrlDecoder().decode(state.encode()), StandardCharsets.UTF_8),
                     "the retry repeats the call's arguments, so the state carries none of them");
    }

    @Test
    void anEncodedContinuationDecodesBackToTheSameState() {
        SearchContinuation state = SearchContinuation.awaitingDirectory();

        SearchContinuation decoded = SearchContinuation.decode(state.encode());

        assertEquals(state, decoded);
    }

    @Test
    void anEncodedContinuationIsUnpaddedUrlSafeBase64() {
        // Standard base64 writes this state as eyJzdGFnZSI6Ij8/In0=.
        String encoded = new SearchContinuation("??").encode();

        assertEquals("eyJzdGFnZSI6Ij8_In0", encoded,
                     "requestState travels in JSON, so padding buys nothing");
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
        assertNull(SearchContinuation.decode(base64("{\"keyword\":\"needle\"}")),
                   "a state that does not say which question was asked is not usable");
    }

    private static String base64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
