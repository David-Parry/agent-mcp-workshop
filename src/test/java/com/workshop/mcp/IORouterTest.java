package com.workshop.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.workshop.mcp.io.IOHandler;
import com.workshop.mcp.spec.CacheScope;
import com.workshop.mcp.spec.ErrorCodes;
import com.workshop.mcp.spec.McpGson;
import com.workshop.mcp.spec.MetaKeys;
import com.workshop.mcp.spec.RequestEnvelope;
import com.workshop.mcp.spec.ResultType;
import com.workshop.mcp.tools.SearchContinuation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the wire contract of protocol revision {@code 2026-07-28}: the
 * discovery answer that replaced the handshake, the per-request envelope that
 * replaced session state, and the Multi Round-Trip Requests exchange that
 * replaced server-initiated requests.
 */
class IORouterTest {

    /** The client-capability envelope used by most tests. */
    private static final String FULL_META = """
            "_meta":{
              "io.modelcontextprotocol/protocolVersion":"2026-07-28",
              "io.modelcontextprotocol/clientInfo":{"name":"test","version":"1.0"},
              "io.modelcontextprotocol/clientCapabilities":{
                "roots":{"listChanged":true},
                "elicitation":{"form":{},"url":{}}
              }
            }""";

    private CapturingIOHandler io;
    private IORouter router;

    @BeforeEach
    void setUp() {
        io = new CapturingIOHandler();
        router = new IORouter(io);
    }

    // --- server/discover -----------------------------------------------

    @Test
    void discoverIsAnsweredWithAStringIdEchoedBackAsAString() {
        router.route("""
                {"jsonrpc":"2.0","id":"server-discover-probe-1","method":"server/discover","params":{%s}}"""
                             .formatted(FULL_META));

        JsonObject response = io.only();
        assertTrue(response.get("id").getAsJsonPrimitive().isString(),
                   "a string id must be echoed back as a string, not coerced to a number");
        assertEquals("server-discover-probe-1", response.get("id").getAsString());
    }

    @Test
    void discoverAdvertisesTheSupportedRevisionAndServerIdentityInMeta() {
        router.route("""
                {"jsonrpc":"2.0","id":"probe","method":"server/discover","params":{%s}}""".formatted(FULL_META));

        JsonObject result = io.only().getAsJsonObject("result");
        assertEquals(ResultType.COMPLETE, result.get("resultType").getAsString());

        JsonArray versions = result.getAsJsonArray("supportedVersions");
        assertEquals(1, versions.size());
        assertEquals(RequestEnvelope.SUPPORTED_VERSION, versions.get(0).getAsString());

        // serverInfo is no longer a body field; it moved into _meta.
        assertNull(result.get("serverInfo"));
        assertEquals("agent-mcp-workshop", result.getAsJsonObject("_meta")
                .getAsJsonObject(MetaKeys.SERVER_INFO).get("name").getAsString());
    }

    @Test
    void discoverIsAnsweredEvenWhenTheEnvelopeNamesAnUnsupportedRevision() {
        // A client calls discover precisely to learn which revisions the
        // server speaks, so rejecting it for guessing wrong would be circular.
        router.route("""
                {"jsonrpc":"2.0","id":"probe","method":"server/discover",
                 "params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2025-11-25"}}}""");

        assertTrue(io.only().has("result"));
    }

    @Test
    void discoverDeclaresCapabilitiesWithoutTheRetiredTasksSlot() {
        router.route("""
                {"jsonrpc":"2.0","id":"probe","method":"server/discover","params":{%s}}""".formatted(FULL_META));

        JsonObject capabilities = io.only().getAsJsonObject("result").getAsJsonObject("capabilities");
        assertNull(capabilities.get("tasks"), "tasks moved out of capabilities and into extensions");
        assertTrue(capabilities.getAsJsonObject("extensions").has(MetaKeys.TASKS_EXTENSION));
        assertFalse(capabilities.getAsJsonObject("tools").get("listChanged").getAsBoolean(),
                    "advertising listChanged would make clients hold a subscription open for nothing");
    }

    // --- the per-request envelope --------------------------------------

    @Test
    void aRequestWithoutTheEnvelopeIsRejectedAsInvalidParams() {
        router.route("""
                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""");

        assertEquals(ErrorCodes.INVALID_PARAMS, io.onlyError().get("code").getAsInt());
    }

    @Test
    void aRequestDeclaringAnUnsupportedRevisionIsRejectedWithRenegotiationData() {
        router.route("""
                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{"_meta":{
                  "io.modelcontextprotocol/protocolVersion":"2025-11-25",
                  "io.modelcontextprotocol/clientCapabilities":{}}}}""");

        JsonObject error = io.onlyError();
        assertEquals(ErrorCodes.UNSUPPORTED_PROTOCOL_VERSION, error.get("code").getAsInt());

        // The data is what lets a client renegotiate from the error alone.
        JsonObject data = error.getAsJsonObject("data");
        assertEquals("2025-11-25", data.get("requested").getAsString());
        assertEquals(RequestEnvelope.SUPPORTED_VERSION, data.getAsJsonArray("supported").get(0).getAsString());
    }

    @Test
    void methodsRemovedByThisRevisionAreNotFoundRatherThanInvalid() {
        // Method existence is settled before the envelope is looked at, so a
        // legacy client is told the method is gone, not that its _meta is off.
        for (String method : List.of("initialize", "ping", "logging/setLevel",
                                     "resources/subscribe", "tasks/result", "tasks/list")) {
            io.clear();
            router.route("""
                    {"jsonrpc":"2.0","id":1,"method":"%s","params":{}}""".formatted(method));
            assertEquals(ErrorCodes.METHOD_NOT_FOUND, io.onlyError().get("code").getAsInt(),
                         method + " was removed in 2026-07-28 and must be -32601");
        }
    }

    @Test
    void theThreeEmbeddedRequestMethodsAreNotAcceptedAsInboundRequests() {
        // These are things the server asks for, never things it answers.
        for (String method : List.of("roots/list", "sampling/createMessage", "elicitation/create")) {
            io.clear();
            router.route("""
                    {"jsonrpc":"2.0","id":1,"method":"%s","params":{%s}}""".formatted(method, FULL_META));
            assertEquals(ErrorCodes.METHOD_NOT_FOUND, io.onlyError().get("code").getAsInt(), method);
        }
    }

    // --- cacheable results ---------------------------------------------

    @Test
    void cacheableResultsCarryFreshnessHintsAndUncacheableOnesDoNot() {
        router.route("""
                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{%s}}""".formatted(FULL_META));
        JsonObject toolsList = io.only().getAsJsonObject("result");
        assertTrue(toolsList.get("ttlMs").getAsLong() >= 0);
        assertEquals(CacheScope.PUBLIC, toolsList.get("cacheScope").getAsString());

        io.clear();
        router.route("""
                {"jsonrpc":"2.0","id":2,"method":"prompts/get",
                 "params":{%s,"name":"search_keyword","arguments":{"keyword":"x"}}}""".formatted(FULL_META));
        JsonObject promptsGet = io.only().getAsJsonObject("result");
        assertEquals(ResultType.COMPLETE, promptsGet.get("resultType").getAsString());
        assertNull(promptsGet.get("ttlMs"), "prompts/get is not a cacheable method");
        assertNull(promptsGet.get("cacheScope"), "prompts/get is not a cacheable method");
    }

    @Test
    void appToolsCarryTheResourceUriUnderBothMetaKeys() {
        router.route("""
                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{%s}}""".formatted(FULL_META));

        JsonObject meta = io.only().getAsJsonObject("result")
                .getAsJsonArray("tools").get(0).getAsJsonObject()
                .getAsJsonObject("_meta");
        String expected = "ui://keyword-search/mcp-app.html";
        assertEquals(expected, meta.getAsJsonObject("ui").get("resourceUri").getAsString());
        assertEquals(expected, meta.get("ui/resourceUri").getAsString());
    }

    // --- Multi Round-Trip Requests -------------------------------------

    @Test
    void aToolCallWithNoDirectoryAsksTheClientForItsRoots() {
        router.route(toolCall(1, null, null));

        JsonObject result = io.only().getAsJsonObject("result");
        assertEquals(ResultType.INPUT_REQUIRED, result.get("resultType").getAsString());

        JsonObject embedded = result.getAsJsonObject("inputRequests")
                .getAsJsonObject(SearchContinuation.KEY_ROOTS);
        assertEquals("roots/list", embedded.get("method").getAsString());
        assertNull(embedded.get("id"), "an embedded request carries no JSON-RPC id");
        assertNull(embedded.get("jsonrpc"), "an embedded request carries no JSON-RPC version");

        // The keyword survives in the continuation state, since there is no
        // session to hold it in.
        SearchContinuation state = SearchContinuation.decode(result.get("requestState").getAsString());
        assertEquals("record", state.keyword());
        assertEquals(SearchContinuation.STAGE_ROOTS, state.stage());
    }

    @Test
    void aRetryCarryingRootsRunsTheSearch(@org.junit.jupiter.api.io.TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("hit.txt"), "the record speaks");

        String state = SearchContinuation.awaitingRoots("record").encode();
        router.route(toolCall(2, state, """
                "search_roots":{"roots":[{"uri":"file://%s"}]}""".formatted(directory)));

        JsonObject result = io.only().getAsJsonObject("result");
        assertEquals(ResultType.COMPLETE, result.get("resultType").getAsString());
        assertFalse(result.get("isError").getAsBoolean());
        assertTrue(result.getAsJsonArray("content").get(0).getAsJsonObject()
                           .get("text").getAsString().contains("hit.txt"));
    }

    @Test
    void emptyRootsEscalateToAskingTheUserForADirectory() {
        String state = SearchContinuation.awaitingRoots("record").encode();
        router.route(toolCall(3, state, """
                "search_roots":{"roots":[]}"""));

        JsonObject result = io.only().getAsJsonObject("result");
        assertEquals(ResultType.INPUT_REQUIRED, result.get("resultType").getAsString());

        JsonObject embedded = result.getAsJsonObject("inputRequests")
                .getAsJsonObject(SearchContinuation.KEY_DIRECTORY);
        assertEquals("elicitation/create", embedded.get("method").getAsString());
        assertEquals("form", embedded.getAsJsonObject("params").get("mode").getAsString());

        assertEquals(SearchContinuation.STAGE_DIRECTORY,
                     SearchContinuation.decode(result.get("requestState").getAsString()).stage());
    }

    @Test
    void anElicitedDirectoryRunsTheSearch(@org.junit.jupiter.api.io.TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("hit.txt"), "the record speaks");

        String state = SearchContinuation.awaitingDirectory("record").encode();
        router.route(toolCall(4, state, """
                "search_directory":{"action":"accept","content":{"directory":"%s"}}""".formatted(directory)));

        JsonObject result = io.only().getAsJsonObject("result");
        assertEquals(ResultType.COMPLETE, result.get("resultType").getAsString());
        assertFalse(result.get("isError").getAsBoolean());
    }

    @Test
    void aDeclinedElicitationFallsBackToTheWorkingDirectory() {
        String state = SearchContinuation.awaitingDirectory("record").encode();
        router.route(toolCall(5, state, """
                "search_directory":{"action":"decline"}"""));

        JsonObject result = io.only().getAsJsonObject("result");
        assertEquals(ResultType.COMPLETE, result.get("resultType").getAsString());
        assertFalse(result.get("isError").getAsBoolean(),
                    "declining the form is not a failure — the search falls back to the working directory");
    }

    @Test
    void aClientThatCanNeitherAnswerRootsNorShowAFormIsNotAsked() {
        router.route("""
                {"jsonrpc":"2.0","id":6,"method":"tools/call","params":{
                  "_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28",
                           "io.modelcontextprotocol/clientCapabilities":{}},
                  "name":"key_word_search","arguments":{"keyword":"record"}}}""");

        JsonObject result = io.only().getAsJsonObject("result");
        assertEquals(ResultType.COMPLETE, result.get("resultType").getAsString());
        assertFalse(result.get("isError").getAsBoolean(),
                    "a client that cannot be asked still gets a search, rooted at the working directory");
    }

    @Test
    void aDirectoryArgumentSkipsTheRoundTripEntirely(@org.junit.jupiter.api.io.TempDir Path directory)
            throws IOException {
        Files.writeString(directory.resolve("hit.txt"), "the record speaks");

        router.route("""
                {"jsonrpc":"2.0","id":8,"method":"tools/call","params":{
                  "_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28",
                           "io.modelcontextprotocol/clientCapabilities":{"roots":{"listChanged":true}}},
                  "name":"key_word_search",
                  "arguments":{"keyword":"record","directory":"%s"}}}""".formatted(directory));

        JsonObject result = io.only().getAsJsonObject("result");
        assertEquals(ResultType.COMPLETE, result.get("resultType").getAsString(),
                     "a supplied directory must not trigger an input_required round trip");
        assertFalse(result.get("isError").getAsBoolean());
        assertTrue(result.getAsJsonArray("content").toString().contains("hit.txt"));
    }

    @Test
    void aCorruptRequestStateIsTreatedAsAFirstAttemptRatherThanAnError() {
        // The state came from this server, but it made a round trip through a
        // client and cannot be trusted to come back intact.
        assertNull(SearchContinuation.decode("not-base64-at-all!!"));

        router.route(toolCall(7, "bm90LWpzb24", null));
        assertEquals(ResultType.INPUT_REQUIRED,
                     io.only().getAsJsonObject("result").get("resultType").getAsString());
    }

    // --- tasks ---------------------------------------------------------

    @Test
    @Timeout(10)
    void aTaskCapableClientGetsAHandleItCanPoll(@org.junit.jupiter.api.io.TempDir Path directory)
            throws IOException {
        Files.writeString(directory.resolve("hit.txt"), "the record speaks");

        router.route("""
                {"jsonrpc":"2.0","id":8,"method":"tools/call","params":{
                  "_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28",
                           "io.modelcontextprotocol/clientCapabilities":{
                             "extensions":{"io.modelcontextprotocol/tasks":{}}}},
                  "name":"key_word_search","arguments":{"keyword":"record"},
                  "requestState":"%s",
                  "inputResponses":{"search_roots":{"roots":[{"uri":"file://%s"}]}}}}"""
                             .formatted(SearchContinuation.awaitingRoots("record").encode(), directory));

        JsonObject handle = io.responses().get(0).getAsJsonObject("result");
        assertEquals(ResultType.TASK, handle.get("resultType").getAsString());
        assertNotNull(handle.get("taskId"));
        assertTrue(handle.has("ttlMs"), "renamed from ttl in the tasks extension");
        assertTrue(handle.has("pollIntervalMs"), "renamed from pollInterval in the tasks extension");

        // The status notification is the bare notifications/tasks, not
        // notifications/tasks/status — that prefix is reserved.
        JsonObject notification = io.notifications().get(0);
        assertEquals("notifications/tasks", notification.get("method").getAsString());
        assertEquals("working", notification.getAsJsonObject("params").get("status").getAsString());
    }

    @Test
    @Timeout(10)
    void aTaskClientIsNeverAskedForInputOnTheCallItself() {
        // input_required and task are alternative result types for the same
        // response, so a request already committed to a handle cannot also
        // carry a question. The Inspector's task path rejects the attempt with
        // "Unsupported result type 'input_required' for tools/call", and it
        // never enables auto-fulfilment. Note that this client declares roots
        // and elicitation, so the tool would very much like to ask.
        router.route("""
                {"jsonrpc":"2.0","id":10,"method":"tools/call","params":{
                  "_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28",
                           "io.modelcontextprotocol/clientCapabilities":{
                             "roots":{"listChanged":true},
                             "elicitation":{"form":{},"url":{}},
                             "extensions":{"io.modelcontextprotocol/tasks":{}}}},
                  "name":"key_word_search","arguments":{"keyword":"record"}}}""");

        JsonObject result = io.responses().get(0).getAsJsonObject("result");
        assertEquals(ResultType.TASK, result.get("resultType").getAsString(),
                     "a task-declaring client must get a handle, not a question");
        assertNotNull(result.get("taskId"));
    }

    @Test
    void aTaskHandleIsNeverGivenToAClientThatCannotPollForIt(
            @org.junit.jupiter.api.io.TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("hit.txt"), "the record speaks");

        router.route(toolCall(9, SearchContinuation.awaitingRoots("record").encode(), """
                "search_roots":{"roots":[{"uri":"file://%s"}]}""".formatted(directory)));

        assertEquals(ResultType.COMPLETE,
                     io.only().getAsJsonObject("result").get("resultType").getAsString());
    }

    @Test
    void pollingAnUnknownTaskIsInvalidParams() {
        router.route("""
                {"jsonrpc":"2.0","id":"ext-1","method":"tasks/get",
                 "params":{%s,"taskId":"no-such-task"}}""".formatted(FULL_META));

        assertEquals(ErrorCodes.INVALID_PARAMS, io.onlyError().get("code").getAsInt());
    }

    // --- subscriptions -------------------------------------------------

    @Test
    void aSubscriptionIsAcknowledgedAndClosedWithItsSubscriptionId() {
        router.route("""
                {"jsonrpc":"2.0","id":"listen:0","method":"subscriptions/listen",
                 "params":{%s,"notifications":{"toolsListChanged":true}}}""".formatted(FULL_META));

        // The acknowledgment must be tagged, or the client waits forever with
        // no error and no timeout.
        JsonObject acknowledged = io.notifications().get(0);
        assertEquals("notifications/subscriptions/acknowledged", acknowledged.get("method").getAsString());
        assertEquals("listen:0", acknowledged.getAsJsonObject("params")
                .getAsJsonObject("_meta").get(MetaKeys.SUBSCRIPTION_ID).getAsString());

        // Nothing was honored, because this server advertises no listChanged
        // support, so the stream closes rather than dangling.
        assertTrue(acknowledged.getAsJsonObject("params").getAsJsonObject("notifications").isEmpty());

        JsonObject close = io.responses().get(0);
        assertEquals("listen:0", close.get("id").getAsString());
        assertEquals("listen:0", close.getAsJsonObject("result")
                .getAsJsonObject("_meta").get(MetaKeys.SUBSCRIPTION_ID).getAsString());
    }

    // --- helpers -------------------------------------------------------

    private static String toolCall(int id, String requestState, String inputResponses) {
        StringBuilder params = new StringBuilder(FULL_META);
        params.append(",\"name\":\"key_word_search\",\"arguments\":{\"keyword\":\"record\"}");
        if (requestState != null) {
            params.append(",\"requestState\":\"").append(requestState).append('"');
        }
        if (inputResponses != null) {
            params.append(",\"inputResponses\":{").append(inputResponses).append('}');
        }
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"tools/call\",\"params\":{" + params + "}}";
    }

    /**
     * Collects what the router writes, splitting responses from notifications
     * so a test can assert on either without depending on interleaving.
     */
    private static final class CapturingIOHandler implements IOHandler {
        private final List<JsonObject> emitted = new ArrayList<>();

        @Override
        public void emit(Object message) {
            emitted.add(JsonParser.parseString(McpGson.create().toJson(message)).getAsJsonObject());
        }

        List<JsonObject> responses() {
            return emitted.stream().filter(m -> m.has("id")).toList();
        }

        List<JsonObject> notifications() {
            return emitted.stream().filter(m -> !m.has("id")).toList();
        }

        /** The single response, asserting there was exactly one. */
        JsonObject only() {
            List<JsonObject> responses = responses();
            assertEquals(1, responses.size(), "expected exactly one response, got " + emitted);
            return responses.get(0);
        }

        /** The error object of the single response. */
        JsonObject onlyError() {
            JsonObject error = only().getAsJsonObject("error");
            assertNotNull(error, "expected an error response, got " + emitted);
            return error;
        }

        void clear() {
            emitted.clear();
        }

        @Override
        public void addLineListener(Consumer<String> listener) {
        }

        @Override
        public void removeLineListener(Consumer<String> listener) {
        }

        @Override
        public void startInputReader() {
        }

        @Override
        public void stopRunning() {
        }

        @Override
        public boolean isRunning() {
            return false;
        }
    }
}
