package com.workshop.mcp.tools;

import com.workshop.mcp.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the page a URL-mode elicitation sends the user to, served from the
 * loopback interface.
 */
@Tag("chapter05")
@Tag("stretch")
class ElicitationPagesTest {

    private final MutableClock clock = new MutableClock();
    private final ElicitationPages pages = new ElicitationPages(0, clock, Duration.ofMinutes(10));
    private final HttpClient http = HttpClient.newHttpClient();

    @AfterEach
    void tearDown() {
        pages.close();
    }

    @Test
    void aPageIsServedOnTheLoopbackInterfaceAtAnUnguessableAddress() throws Exception {
        ElicitationPages.Question first = pages.open(false);
        ElicitationPages.Question second = pages.open(false);

        assertTrue(first.url().matches("http://127\\.0\\.0\\.1:\\d+/elicit/[A-Za-z0-9_-]{22}"), first.url());
        assertTrue(first.url().endsWith(first.token()));
        assertNotEquals(first.token(), second.token());
        assertEquals(first.url().substring(0, first.url().lastIndexOf('/')),
                     second.url().substring(0, second.url().lastIndexOf('/')),
                     "one server serves every page");
    }

    @Test
    void thePageAsksForADirectoryAndOnlyAsksForAKeywordWhenToldTo() throws Exception {
        HttpResponse<String> directoryOnly = get(pages.open(false).url());
        HttpResponse<String> both = get(pages.open(true).url());

        assertEquals(200, directoryOnly.statusCode());
        assertTrue(directoryOnly.body().contains("name=\"directory\""), directoryOnly.body());
        assertFalse(directoryOnly.body().contains("name=\"keyword\""), directoryOnly.body());
        assertTrue(both.body().contains("name=\"keyword\""), both.body());
        assertTrue(both.body().contains("name=\"directory\""), both.body());
    }

    @Test
    void thePageIsNeverCachedAndRunsNoScript() throws Exception {
        HttpResponse<String> response = get(pages.open(false).url());

        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertTrue(response.headers().firstValue("Content-Security-Policy").orElseThrow()
                           .startsWith("default-src 'none'"));
        assertEquals("text/html; charset=utf-8", response.headers().firstValue("Content-Type").orElseThrow());
    }

    @Test
    void whatTheUserSubmitsCanBeTakenOnce() throws Exception {
        ElicitationPages.Question question = pages.open(true);
        assertTrue(pages.isWaiting(question.token()));
        assertNull(pages.take(question.token()), "nothing has been submitted yet");

        HttpResponse<String> response = post(question.url(), "keyword=needle&directory=%2Ftmp%2Fa+b");

        assertEquals(200, response.statusCode());
        assertFalse(pages.isWaiting(question.token()), "an answered page is no longer waiting");
        assertEquals(Map.of("keyword", "needle", "directory", "/tmp/a b"), pages.take(question.token()));
        assertNull(pages.take(question.token()), "an answer is taken once");
        assertEquals(404, get(question.url()).statusCode(), "a taken page is gone");
    }

    @Test
    void anUnknownPageIsNotFound() throws Exception {
        String unknown = pages.open(false).url().replaceAll("[^/]+$", "unknown");

        assertEquals(404, get(unknown).statusCode());
        assertEquals(404, post(unknown, "directory=/tmp").statusCode());
        assertFalse(pages.isWaiting("unknown"));
    }

    @Test
    void aDiscardedPageIsGone() throws Exception {
        ElicitationPages.Question question = pages.open(false);

        pages.discard(question.token());

        assertFalse(pages.isWaiting(question.token()));
        assertEquals(404, get(question.url()).statusCode());
    }

    @Test
    void aPageExpiresWithItsState() throws Exception {
        ElicitationPages.Question unanswered = pages.open(false);
        ElicitationPages.Question answered = pages.open(false);
        post(answered.url(), "directory=/tmp");

        clock.advance(Duration.ofMinutes(10).plusMillis(1));

        assertFalse(pages.isWaiting(unanswered.token()));
        assertEquals(404, post(unanswered.url(), "directory=/tmp").statusCode());
        assertNull(pages.take(answered.token()), "an expired answer is not handed over");
    }

    @Test
    void aFormThatCannotBeDecodedIsABadRequest() throws Exception {
        ElicitationPages.Question question = pages.open(false);

        assertEquals(400, post(question.url(), "directory=%zz").statusCode());
        assertTrue(pages.isWaiting(question.token()), "the user can try again");
    }

    @Test
    void onlyGetAndPostAreAllowed() throws Exception {
        ElicitationPages.Question question = pages.open(false);

        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(question.url())).PUT(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(405, response.statusCode());
        assertEquals("GET, POST", response.headers().firstValue("Allow").orElseThrow());
    }

    @Test
    void formFieldsWithoutANameAreIgnored() {
        assertEquals(Map.of("directory", "/tmp"), ElicitationPages.parseForm("directory=+/tmp+&=orphan&flag"));
        assertEquals(Map.of(), ElicitationPages.parseForm(""));
        assertNull(ElicitationPages.parseForm("directory=%zz"));
    }

    @Test
    void aPortThatIsTakenFailsToOpenAPage() throws Exception {
        try (ServerSocket taken = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            ElicitationPages blocked = new ElicitationPages(taken.getLocalPort(), clock, Duration.ofMinutes(10));

            assertThrows(IOException.class, () -> blocked.open(false));
            blocked.close();
        }
    }

    @Test
    void closingStopsTheServerAndForgetsEveryPage() throws Exception {
        ElicitationPages.Question question = pages.open(false);

        pages.close();
        pages.close();

        assertFalse(pages.isWaiting(question.token()));
        assertThrows(IOException.class, () -> get(question.url()));
        assertEquals(200, get(pages.open(false).url()).statusCode(), "the next question starts a new server");
    }

    private HttpResponse<String> get(String url) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String url, String form) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create(url))
                                 .header("Content-Type", "application/x-www-form-urlencoded")
                                 .POST(HttpRequest.BodyPublishers.ofString(form))
                                 .build(),
                         HttpResponse.BodyHandlers.ofString());
    }
}
