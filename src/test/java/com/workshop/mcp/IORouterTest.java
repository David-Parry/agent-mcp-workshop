package com.workshop.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.workshop.mcp.io.IOHandler;
import com.workshop.mcp.io.LogFile;
import com.workshop.mcp.spec.CacheScope;
import com.workshop.mcp.spec.ErrorCodes;
import com.workshop.mcp.spec.McpGson;
import com.workshop.mcp.spec.MetaKeys;
import com.workshop.mcp.spec.RequestEnvelope;
import com.workshop.mcp.spec.ResultType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
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

    /** {@link #FULL_META} from a client that also declares the tasks extension, as every task method requires. */
    private static final String TASKS_META = """
            "_meta":{
              "io.modelcontextprotocol/protocolVersion":"2026-07-28",
              "io.modelcontextprotocol/clientInfo":{"name":"test","version":"1.0"},
              "io.modelcontextprotocol/clientCapabilities":{
                "roots":{"listChanged":true},
                "elicitation":{"form":{},"url":{}},
                "extensions":{"io.modelcontextprotocol/tasks":{}}
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
    @Tag("chapter03")
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
    @Tag("chapter03")
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
    @Tag("chapter03")
    void discoverIsAnsweredEvenWhenTheEnvelopeNamesAnUnsupportedRevision() {
        // A client calls discover precisely to learn which revisions the
        // server speaks, so rejecting it for guessing wrong would be circular.
        router.route("""
                {"jsonrpc":"2.0","id":"probe","method":"server/discover",
                 "params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2025-11-25",
                                    "io.modelcontextprotocol/clientCapabilities":{}}}}""");

        assertTrue(io.only().has("result"));
    }

    @Test
    @Tag("chapter03")
    void discoverWithoutTheRequiredEnvelopeIsInvalidParams() {
        // Any version will do, but discover is still a request, and a request
        // missing a required envelope member is malformed.
        for (String params : List.of("{}", """
                {"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28"}}""")) {
            io.clear();
            router.route("""
                    {"jsonrpc":"2.0","id":"probe","method":"server/discover","params":%s}""".formatted(params));

            assertEquals(ErrorCodes.INVALID_PARAMS, io.onlyError().get("code").getAsInt(), params);
        }
    }

    @Test
    @Tag("chapter03")
    void discoverDeclaresCapabilitiesWithoutTheRetiredTasksSlot() {
        router.route("""
                {"jsonrpc":"2.0","id":"probe","method":"server/discover","params":{%s}}""".formatted(FULL_META));

        JsonObject capabilities = io.only().getAsJsonObject("result").getAsJsonObject("capabilities");
        assertNull(capabilities.get("tasks"), "tasks moved out of capabilities and into extensions");
        assertFalse(capabilities.getAsJsonObject("tools").get("listChanged").getAsBoolean(),
                    "advertising listChanged would make clients hold a subscription open for nothing");
    }

    // --- the per-request envelope --------------------------------------

    @Test
    @Tag("chapter03")
    void aRequestWithoutTheEnvelopeIsRejectedAsInvalidParams() {
        router.route("""
                {"jsonrpc":"2.0","id":1,"method":"subscriptions/listen","params":{}}""");

        assertEquals(ErrorCodes.INVALID_PARAMS, io.onlyError().get("code").getAsInt());
    }

    @Test
    @Tag("chapter03")
    void aRequestDeclaringAnUnsupportedRevisionIsRejectedWithRenegotiationData() {
        router.route("""
                {"jsonrpc":"2.0","id":1,"method":"subscriptions/listen","params":{"_meta":{
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
    @Tag("chapter03")
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
    @Tag("chapter03")
    void initializeIsToldWhichVersionsThisServerSupports() {
        // A client still on the handshake cannot move forward on its own, so
        // this error may be the only diagnostic its user ever sees.
        router.route("""
                {"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2025-11-25"}}""");

        JsonObject error = io.onlyError();
        assertEquals(ErrorCodes.METHOD_NOT_FOUND, error.get("code").getAsInt());
        assertTrue(error.get("message").getAsString().contains(RequestEnvelope.SUPPORTED_VERSION),
                   error.toString());
        assertEquals(RequestEnvelope.SUPPORTED_VERSION,
                     error.getAsJsonObject("data").getAsJsonArray("supported").get(0).getAsString());
    }

    @Test
    @Tag("chapter03")
    void methodNamesAreCaseSensitive() {
        router.route("""
                {"jsonrpc":"2.0","id":1,"method":"SERVER/DISCOVER","params":{%s}}""".formatted(FULL_META));

        assertEquals(ErrorCodes.METHOD_NOT_FOUND, io.onlyError().get("code").getAsInt());
    }

    @Test
    @Tag("chapter03")
    void theThreeEmbeddedRequestMethodsAreNotAcceptedAsInboundRequests() {
        // These are things the server asks for, never things it answers.
        for (String method : List.of("roots/list", "sampling/createMessage", "elicitation/create")) {
            io.clear();
            router.route("""
                    {"jsonrpc":"2.0","id":1,"method":"%s","params":{%s}}""".formatted(method, FULL_META));
            assertEquals(ErrorCodes.METHOD_NOT_FOUND, io.onlyError().get("code").getAsInt(), method);
        }
    }

    @Test
    @Tag("chapter03")
    void allThreeEmbeddedOnlyMethodsAreRejectedInbound() {
        // Sampling stays rejected inbound even though the server never embeds
        // it, because the revision makes all three embedded-only.
        for (String method : List.of("roots/list", "sampling/createMessage", "elicitation/create")) {
            io.clear();
            router.route("""
                    {"jsonrpc":"2.0","id":1,"method":"%s","params":{%s}}"""
                                 .formatted(method, FULL_META));
            assertEquals(ErrorCodes.METHOD_NOT_FOUND, io.onlyError().get("code").getAsInt(), method);
        }
    }

    @Test
    @Tag("chapter03")
    void theRemovedRootsListChangedNotificationIsNotRoutable() {
        router.route("""
                {"jsonrpc":"2.0","id":1,"method":"notifications/roots/list_changed",
                 "params":{%s}}""".formatted(FULL_META));

        assertEquals(ErrorCodes.METHOD_NOT_FOUND, io.onlyError().get("code").getAsInt(),
                     "2026-07-28 removed this notification outright");
    }

    /** A {@code tools/call} from a task client that supplies no directory. */
    private static String taskCallWithoutADirectory(int id, String extraCapability) {
        return """
                {"jsonrpc":"2.0","id":%d,"method":"tools/call","params":{
                  "_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28",
                           "io.modelcontextprotocol/clientCapabilities":{
                             %s,
                             "extensions":{"io.modelcontextprotocol/tasks":{}}}},
                  "name":"key_word_search","arguments":{"keyword":"record"}}}"""
                .formatted(id, extraCapability);
    }

    /** Polls {@code tasks/get} the way a client would, until the status arrives. */
    private JsonObject awaitTaskStatus(String taskId, String status) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000L;
        JsonObject snapshot = null;
        while (System.currentTimeMillis() < deadline) {
            io.clear();
            router.route("""
                    {"jsonrpc":"2.0","id":"poll","method":"tasks/get","params":{%s,"taskId":"%s"}}"""
                                 .formatted(TASKS_META, taskId));
            snapshot = io.responses().get(0).getAsJsonObject("result");
            if (status.equals(snapshot.get("status").getAsString())) {
                return snapshot;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("task never reached " + status + "; last snapshot was " + snapshot);
    }

    // --- subscriptions -------------------------------------------------

    @Test
    @Tag("chapter03")
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

    @Test
    @Tag("chapter03")
    void aListenRequestThatNamesNoNotificationsIsStillAcknowledged() {
        router.route("""
                {"jsonrpc":"2.0","id":"listen:1","method":"subscriptions/listen",
                 "params":{%s}}""".formatted(FULL_META));

        assertEquals("notifications/subscriptions/acknowledged",
                     io.notifications().get(0).get("method").getAsString());
        assertEquals("listen:1", io.responses().get(0).get("id").getAsString(),
                     "asking for nothing closes the stream rather than dangling");
    }

    @Test
    @Tag("chapter03")
    void everyResultNamesTheServerInItsMeta() {
        // With no connection state, the result itself is the only place a
        // client can learn who answered.
        router.route("""
                {"jsonrpc":"2.0","id":"listen:3","method":"subscriptions/listen","params":{%s}}"""
                             .formatted(FULL_META));

        JsonObject meta = io.only().getAsJsonObject("result").getAsJsonObject("_meta");
        assertEquals("agent-mcp-workshop", meta.getAsJsonObject(MetaKeys.SERVER_INFO).get("name").getAsString());
        assertEquals("listen:3", meta.get(MetaKeys.SUBSCRIPTION_ID).getAsString(),
                     "the identity joins the _meta a result already has rather than replacing it");
    }

    // --- message dispatch ----------------------------------------------

    @Test
    @Tag("chapter03")
    @Tag("chapter04")
    @Tag("chapter05")
    @Tag("chapter06")
    @Tag("chapter07")
    void everyMethodTheServerAcceptsIsActuallyAnswered() throws Exception {
        // The registry that decides what is not -32601 and the switch that
        // handles what got past it are two lists that have to agree. There is
        // no runtime guard for them drifting apart, because the only thing
        // that can cause it is an edit to IORouter — so it is checked here.
        // Without this, adding a method to the registry and forgetting the
        // switch arm would accept the request and answer nothing at all.
        // Each chapter from 03 on adds to what the switch answers, so the
        // check belongs to all of them.
        Field field = IORouter.class.getDeclaredField("INBOUND_METHODS");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<Enum<?>> registered = (Set<Enum<?>>) field.get(null);

        // Nothing but the envelope is supplied, so this also pins that no
        // method faults on absent params. Several used to throw out of route()
        // on a null taskId or a missing argument, and because the exception
        // escaped rather than becoming an error, the client got no reply at
        // all and simply waited.
        for (Enum<?> method : registered) {
            String wireName = (String) method.getClass().getMethod("getValue").invoke(method);
            io.clear();
            router.route("""
                    {"jsonrpc":"2.0","id":"drift","method":"%s","params":{%s}}"""
                                 .formatted(wireName, TASKS_META));

            assertFalse(io.responses().isEmpty(),
                        "'" + wireName + "' is accepted by INBOUND_METHODS but answered nothing");
        }
    }

    @Test
    @Tag("chapter03")
    void aBlankLineIsNotAMessageAndIsIgnored() {
        router.route(null);
        router.route("");

        assertTrue(io.responses().isEmpty(), "nothing to answer: " + io.responses());
        assertTrue(io.notifications().isEmpty());
    }

    @Test
    @Tag("chapter03")
    void aResultFromTheClientIsUnsolicitedAndIgnored() {
        // This revision has the server ask for nothing, so a JSON-RPC result
        // arriving on stdin cannot be an answer to anything it sent.
        router.route("""
                {"jsonrpc":"2.0","id":1,"result":{"roots":[]}}""");

        assertTrue(io.responses().isEmpty(), "an unsolicited result must not be answered");
    }

    @Test
    @Tag("chapter03")
    void anErrorFromTheClientIsRecordedRatherThanAnsweredWithAnotherError() {
        router.route("""
                {"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"Method not found"}}""");

        assertTrue(io.responses().isEmpty(), "answering an error with an error would loop");
    }

    @Test
    @Tag("chapter03")
    void textThatIsNotJsonIsAParseErrorCarryingNoId() {
        router.route("""
                {"jsonrpc":"2.0","id":1,"method":""");

        JsonObject response = io.onlyMessage();
        assertEquals(ErrorCodes.PARSE_ERROR, response.getAsJsonObject("error").get("code").getAsInt());
        assertFalse(response.has("id"), "there is no id to quote back, and null is not a valid one");
    }

    @Test
    @Tag("chapter03")
    void wellFormedJsonThatIsNotAMessageIsAnInvalidRequestCarryingNoId() {
        for (String json : List.of(
                "{\"jsonrpc\":\"2.0\"}",
                "[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"server/discover\"}]",
                "{\"id\":1,\"method\":\"server/discover\"}",
                "{\"jsonrpc\":\"2.0\",\"id\":{\"n\":1},\"method\":\"server/discover\"}")) {
            io.clear();
            router.route(json);

            JsonObject response = io.onlyMessage();
            assertEquals(ErrorCodes.INVALID_REQUEST, response.getAsJsonObject("error").get("code").getAsInt(),
                         json);
            assertFalse(response.has("id"), json);
        }
    }

    @Test
    @Tag("chapter03")
    void paramsThatDoNotFitTheMethodAreInvalidParams() {
        router.route("""
                {"jsonrpc":"2.0","id":"listen:2","method":"subscriptions/listen",
                 "params":{%s,"notifications":"everything"}}""".formatted(FULL_META));

        JsonObject response = io.only();
        assertEquals("listen:2", response.get("id").getAsString());
        assertEquals(ErrorCodes.INVALID_PARAMS, response.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    @Tag("chapter03")
    void aRequestWhoseHandlingThrowsIsStillAnsweredWithItsId() {
        // Whatever throws, the client must not be left waiting for a reply
        // that never comes. Here the first attempt to write the answer fails.
        io.failNextEmit();
        router.route("""
                {"jsonrpc":"2.0","id":"probe","method":"server/discover","params":{%s}}""".formatted(FULL_META));

        JsonObject response = io.only();
        assertEquals("probe", response.get("id").getAsString());
        assertEquals(ErrorCodes.INTERNAL_ERROR, response.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    @Tag("chapter03")
    void aCancelledNotificationForSomeOtherRequestIsNoted() {
        RecordingLog log = new RecordingLog();
        new IORouter(io, log).route("""
                {"jsonrpc":"2.0","method":"notifications/cancelled",
                 "params":{"requestId":"not-a-subscription","reason":"user pressed stop"}}""");

        assertTrue(io.responses().isEmpty(), "a notification is never answered");
        assertTrue(log.logged("notifications/cancelled", "not-a-subscription", "user pressed stop"),
                   "the cancellation should be logged with its request id and reason: " + log.lines);
    }

    @Test
    @Tag("chapter03")
    void anUnrecognisedNotificationIsIgnoredRatherThanRejected() {
        // Notifications carry no id, so there is nobody to send -32601 to.
        RecordingLog log = new RecordingLog();
        new IORouter(io, log).route("""
                {"jsonrpc":"2.0","method":"notifications/progress","params":{"progressToken":1}}""");

        assertTrue(io.responses().isEmpty());
        assertTrue(log.logged("notifications/progress"),
                   "an unrecognised notification should still be logged: " + log.lines);
    }


    // --- helpers -------------------------------------------------------

    private static String read(int id, String uri) {
        return """
                {"jsonrpc":"2.0","id":%d,"method":"resources/read",
                 "params":{%s,"uri":"%s"}}""".formatted(id, FULL_META, uri);
    }

    private static String complete(int id, String argumentName) {
        return """
                {"jsonrpc":"2.0","id":%d,"method":"completion/complete",
                 "params":{%s,"ref":{"type":"ref/prompt","name":"search_keyword"},
                           "argument":{"name":"%s","value":"j"}}}""".formatted(id, FULL_META, argumentName);
    }

    /**
     * Asserts that a response is the empty result {@code tasks/update} and
     * {@code tasks/cancel} acknowledge with: nothing but its {@code resultType}
     * and the {@code _meta} every result carries.
     */
    private static void assertEmptyAcknowledgement(JsonObject response) {
        assertNull(response.get("error"), "expected an acknowledgement, got " + response);
        JsonObject result = response.getAsJsonObject("result");
        assertEquals(ResultType.COMPLETE, result.get("resultType").getAsString());
        assertEquals(Set.of("resultType", "_meta"), result.keySet(), "an acknowledgement carries no task: " + result);
    }

    private static String cancel(int id, String taskId) {
        return """
                {"jsonrpc":"2.0","id":%d,"method":"tasks/cancel",
                 "params":{%s,"taskId":"%s"}}""".formatted(id, TASKS_META, taskId);
    }

    /** A {@code tools/call} from a task client that supplies its directory. */
    private static String taskCallWithADirectory(int id, String directory) {
        return """
                {"jsonrpc":"2.0","id":%d,"method":"tools/call","params":{
                  "_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28",
                           "io.modelcontextprotocol/clientCapabilities":{
                             "extensions":{"io.modelcontextprotocol/tasks":{}}}},
                  "name":"key_word_search",
                  "arguments":{"keyword":"record","directory":"%s"}}}"""
                .formatted(id, directory);
    }

    private JsonObject firstContent() {
        return io.only().getAsJsonObject("result").getAsJsonArray("contents").get(0).getAsJsonObject();
    }

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
        // Tasks emit their status changes from their own thread, so this is
        // written to concurrently with the assertions reading it.
        private final List<JsonObject> emitted = new CopyOnWriteArrayList<>();
        // Never cleared, for a test that has to see everything a task ever sent.
        private final List<JsonObject> history = new CopyOnWriteArrayList<>();
        private volatile boolean failNextEmit;

        @Override
        public void emit(Object message) {
            if (failNextEmit) {
                failNextEmit = false;
                throw new IllegalStateException("the write failed");
            }
            JsonObject json = JsonParser.parseString(McpGson.create().toJson(message)).getAsJsonObject();
            emitted.add(json);
            history.add(json);
        }

        /** Makes the next emit throw, as a broken handler would. */
        void failNextEmit() {
            failNextEmit = true;
        }

        /**
         * The single message of any kind, asserting there was exactly one. An
         * error answering something with no usable id carries no id, so
         * {@link #responses()} would not count it.
         */
        JsonObject onlyMessage() {
            assertEquals(1, emitted.size(), "expected exactly one message, got " + emitted);
            return emitted.get(0);
        }

        List<JsonObject> responses() {
            return emitted.stream().filter(m -> m.has("id")).toList();
        }

        List<JsonObject> notifications() {
            return emitted.stream().filter(m -> !m.has("id")).toList();
        }

        /** Every notification since the handler was made, whatever {@link #clear()} has dropped. */
        List<JsonObject> notificationsEverSent() {
            return history.stream().filter(m -> !m.has("id")).toList();
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

    /** Keeps what the router logs, for the messages it notes rather than answers. */
    private static final class RecordingLog implements LogFile {
        private final List<String> lines = new CopyOnWriteArrayList<>();

        /** Whether one logged line contains every one of the parts. */
        boolean logged(String... parts) {
            return lines.stream().anyMatch(line -> List.of(parts).stream().allMatch(line::contains));
        }

        @Override
        public void log(String message) {
            lines.add(message);
        }

        @Override
        public void log(String message, Throwable exception) {
            lines.add(message);
        }

        @Override
        public void logRaw(String message) {
            lines.add(message);
        }

        @Override
        public void close() {
        }
    }
}
