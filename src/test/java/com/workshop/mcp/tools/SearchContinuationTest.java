package com.workshop.mcp.tools;

import com.google.gson.JsonObject;
import com.workshop.mcp.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the continuation state the keyword-search tool carries across a
 * Multi Round-Trip Requests exchange.
 */
@Tag("chapter05")
class SearchContinuationTest {

    private static final byte[] KEY = "a test key that is thirty-two by".getBytes(StandardCharsets.US_ASCII);

    private final MutableClock clock = new MutableClock();
    private final StateSigner signer = StateSigner.withKey(KEY, clock, Duration.ofMinutes(10));

    @Test
    void awaitingDirectoryCarriesOnlyTheDirectoryStage() {
        SearchContinuation state = SearchContinuation.awaitingDirectory();

        JsonObject opened = signer.open(state.encode(signer));

        assertEquals(SearchContinuation.STAGE_DIRECTORY, state.stage());
        assertEquals("{\"stage\":\"directory\"}", opened.toString(),
                     "the retry repeats the call's arguments, so the state carries none of them");
    }

    @Test
    void anEncodedContinuationDecodesBackToTheSameState() {
        SearchContinuation state = SearchContinuation.awaitingDirectory();

        SearchContinuation decoded = SearchContinuation.decode(state.encode(signer), signer);

        assertEquals(state, decoded);
    }

    @Test
    @Tag("stretch")
    void everyStageDecodesBackToItself() {
        for (SearchContinuation state : new SearchContinuation[] {
                SearchContinuation.awaitingDirectory(),
                SearchContinuation.awaitingKeywordAndDirectory(),
                SearchContinuation.awaitingPage("t0ken")}) {
            assertEquals(state, SearchContinuation.decode(state.encode(signer), signer));
        }
    }

    @Test
    @Tag("stretch")
    void eachStageSaysWhichAnswersToRead() {
        assertFalse(SearchContinuation.awaitingDirectory().askedForKeyword());
        assertFalse(SearchContinuation.awaitingDirectory().askedOnPage());
        assertTrue(SearchContinuation.awaitingKeywordAndDirectory().askedForKeyword());
        assertFalse(SearchContinuation.awaitingKeywordAndDirectory().askedOnPage());
        assertTrue(SearchContinuation.awaitingPage("t0ken").askedOnPage());
        assertEquals("t0ken", SearchContinuation.awaitingPage("t0ken").token());
    }

    @Test
    void decodeReturnsNullForAnAbsentRequestState() {
        assertNull(SearchContinuation.decode(null, signer));
    }

    @Test
    void decodeReturnsNullForABlankRequestState() {
        assertNull(SearchContinuation.decode("   ", signer));
    }

    @Test
    void decodeReturnsNullForAValueThatIsNotBase64() {
        assertNull(SearchContinuation.decode("!!! not base64 !!!", signer));
    }

    @Test
    @Tag("stretch")
    void decodeReturnsNullForAnUnsignedState() {
        String unsigned = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"stage\":\"directory\"}".getBytes(StandardCharsets.UTF_8));

        assertNull(SearchContinuation.decode(unsigned, signer),
                   "a state the client wrote itself must not pass for one this server wrote");
    }

    @Test
    @Tag("stretch")
    void decodeReturnsNullForAStateSignedWithAnotherKey() {
        StateSigner other = StateSigner.withKey(StateSigner.randomKey(), clock, Duration.ofMinutes(10));

        assertNull(SearchContinuation.decode(SearchContinuation.awaitingDirectory().encode(other), signer));
    }

    @Test
    @Tag("stretch")
    void decodeReturnsNullOnceTheStateHasExpired() {
        String sealed = SearchContinuation.awaitingDirectory().encode(signer);

        clock.advance(Duration.ofMinutes(10).plusMillis(1));

        assertNull(SearchContinuation.decode(sealed, signer));
    }
}
