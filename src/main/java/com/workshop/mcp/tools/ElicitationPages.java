package com.workshop.mcp.tools;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Serves the page a URL-mode elicitation sends the user to.
 * <p>
 * A client that declares only {@code "url": {}} cannot show a form, so the
 * server shows one itself: a small page on the loopback interface, one per
 * question, at an address nobody can guess. The user opens it, fills it in,
 * and submits. The page keeps the answer until the client retries the call,
 * when the router takes it, once.
 * </p>
 * <p>
 * The HTTP server starts with the first question and listens on 127.0.0.1
 * only, so a page is reachable from the user's own machine and nowhere else.
 * That is where a stdio server's user is.
 * </p>
 */
public final class ElicitationPages implements AutoCloseable {

    /** The largest form body read; a directory path and a keyword fit easily. */
    static final int MAX_BODY_BYTES = 16 * 1024;

    private static final String HOST = "127.0.0.1";
    private static final String PATH = "/elicit/";
    private static final String GONE = "<p>This question has expired or was already answered.</p>";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final int port;
    private final Clock clock;
    private final Duration lifetime;
    private final Map<String, Page> pages = new HashMap<>();
    private HttpServer server;

    /**
     * A question waiting on a page.
     *
     * @param token identifies the page; carried in the sealed {@code requestState}
     * @param url   the address to send the user to
     */
    public record Question(String token, String url) {
    }

    private record Page(boolean askKeyword, long expiresAt, Map<String, String> answers) {
    }

    /**
     * Creates the pages without starting anything; the HTTP server starts with
     * the first question.
     *
     * @param port     the loopback port to listen on, or 0 for any free port
     * @param clock    where a page's time limit is read from
     * @param lifetime how long a page waits for its answer to be taken
     */
    public ElicitationPages(int port, Clock clock, Duration lifetime) {
        this.port = port;
        this.clock = clock;
        this.lifetime = lifetime;
    }

    /**
     * Opens a page for one question.
     *
     * @param askKeyword whether the page asks for a keyword as well as a directory
     * @return the page's token and address
     * @throws IOException if the HTTP server cannot listen on the port
     */
    public synchronized Question open(boolean askKeyword) throws IOException {
        if (server == null) {
            HttpServer started = HttpServer.create(new InetSocketAddress(HOST, port), 0);
            started.createContext(PATH, this::handle);
            started.start();
            server = started;
        }
        byte[] random = new byte[16];
        RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        pages.put(token, new Page(askKeyword, clock.millis() + lifetime.toMillis(), null));
        return new Question(token, url(token));
    }

    /**
     * The address of a page, to send the user back to it.
     *
     * @param token a token {@link #open} returned while the server is running
     * @return the page's address
     */
    public synchronized String url(String token) {
        return "http://" + HOST + ":" + server.getAddress().getPort() + PATH + token;
    }

    /**
     * Takes the answers a page collected. A page can be taken once.
     *
     * @param token the page's token
     * @return the submitted fields, or null if nothing was submitted, the page
     *         expired, or its answers were already taken
     */
    public synchronized Map<String, String> take(String token) {
        Page page = live(token);
        if (page == null || page.answers() == null) {
            return null;
        }
        pages.remove(token);
        return page.answers();
    }

    /**
     * Whether a page is still open and has not been answered.
     *
     * @param token the page's token
     * @return true while the user can still answer
     */
    public synchronized boolean isWaiting(String token) {
        Page page = live(token);
        return page != null && page.answers() == null;
    }

    /**
     * Forgets a page, answered or not.
     *
     * @param token the page's token
     */
    public synchronized void discard(String token) {
        pages.remove(token);
    }

    /**
     * Stops the HTTP server and forgets every page.
     */
    @Override
    public synchronized void close() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        pages.clear();
    }

    private Page live(String token) {
        Page page = pages.get(token);
        if (page != null && clock.millis() > page.expiresAt()) {
            pages.remove(token);
            return null;
        }
        return page;
    }

    private synchronized Boolean asksForKeyword(String token) {
        Page page = live(token);
        return page == null ? null : page.askKeyword();
    }

    private synchronized boolean record(String token, Map<String, String> answers) {
        Page page = live(token);
        if (page == null) {
            return false;
        }
        pages.put(token, new Page(page.askKeyword(), page.expiresAt(), answers));
        return true;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String token = exchange.getRequestURI().getPath().substring(PATH.length());
            switch (exchange.getRequestMethod()) {
                case "GET" -> {
                    Boolean askKeyword = asksForKeyword(token);
                    if (askKeyword == null) {
                        respond(exchange, 404, document(GONE));
                    } else {
                        respond(exchange, 200, form(token, askKeyword));
                    }
                }
                case "POST" -> {
                    Map<String, String> answers = parseForm(
                            new String(exchange.getRequestBody().readNBytes(MAX_BODY_BYTES), StandardCharsets.UTF_8));
                    if (answers == null) {
                        respond(exchange, 400, document("<p>That form could not be read.</p>"));
                    } else if (record(token, answers)) {
                        respond(exchange, 200, document(
                                "<p>Thanks. Go back to your MCP client and confirm to run the search.</p>"));
                    } else {
                        respond(exchange, 404, document(GONE));
                    }
                }
                default -> {
                    exchange.getResponseHeaders().set("Allow", "GET, POST");
                    respond(exchange, 405, document("<p>Use GET or POST.</p>"));
                }
            }
        }
    }

    static Map<String, String> parseForm(String body) {
        Map<String, String> fields = new HashMap<>();
        try {
            for (String pair : body.split("&")) {
                int equals = pair.indexOf('=');
                if (equals > 0) {
                    fields.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                               URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8).trim());
                }
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
        return fields;
    }

    private static String form(String token, boolean askKeyword) {
        String keyword = askKeyword
                ? "<label>Keyword <input name=\"keyword\" required></label>"
                : "";
        return document("<h1>Keyword search</h1>"
                        + "<form method=\"post\" action=\"" + PATH + token + "\">"
                        + keyword
                        + "<label>Directory to search <input name=\"directory\" required"
                        + " placeholder=\"/absolute/path\"></label>"
                        + "<button type=\"submit\">Submit</button>"
                        + "</form>");
    }

    private static String document(String body) {
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
               + "<title>Keyword search</title>"
               + "<style>body{font:16px system-ui;margin:3rem auto;max-width:32rem}"
               + "label{display:block;margin:1rem 0}input{display:block;width:100%}</style>"
               + "</head><body>" + body + "</body></html>";
    }

    private static void respond(HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/html; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
