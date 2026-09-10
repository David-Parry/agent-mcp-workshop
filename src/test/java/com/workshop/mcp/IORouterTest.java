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
import com.workshop.mcp.tools.KeyWordSearch;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
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
                 "params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2025-11-25"}}}""");

        assertTrue(io.only().has("result"));
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
    @Tag("chapter04")
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
    @Tag("chapter04")
    void anInputSchemaWritesOnlyJsonSchemaKeywords() {
        router.route("""
                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{%s}}""".formatted(FULL_META));

        JsonObject schema = io.only().getAsJsonObject("result")
                .getAsJsonArray("tools").get(0).getAsJsonObject()
                .getAsJsonObject("inputSchema");
        JsonObject properties = schema.getAsJsonObject("properties");

        // PropertySchema carries a key and a requiredness flag for the
        // builder's benefit. Both are already expressed by the enclosing
        // schema, and neither is a JSON Schema keyword, so neither may appear
        // on the property itself.
        for (String name : properties.keySet()) {
            JsonObject property = properties.getAsJsonObject(name);
            assertNull(property.get("key"),
                       name + " must be named by its position, not by a 'key' field");
            assertNull(property.get("isRequired"),
                       name + " must state requiredness through the schema's 'required' array");
            assertEquals(List.of("type", "description"), List.copyOf(property.keySet()),
                         name + " must write only JSON Schema keywords");
        }

        // Requiredness survives the omission — expressed once, in the one place
        // JSON Schema defines for it.
        assertEquals(List.of("keyword"), schema.getAsJsonArray("required").asList().stream()
                             .map(element -> element.getAsString()).toList(),
                     "the required keyword must still be advertised as required");
        assertTrue(properties.has("directory"), "the optional property must still be advertised");
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
                                 .formatted(FULL_META, taskId));
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

    // --- message dispatch ----------------------------------------------

    @Test
    @Tag("chapter03")
    void everyMethodTheServerAcceptsIsActuallyAnswered() throws Exception {
        // The registry that decides what is not -32601 and the switch that
        // handles what got past it are two lists that have to agree. There is
        // no runtime guard for them drifting apart, because the only thing
        // that can cause it is an edit to IORouter — so it is checked here.
        // Without this, adding a method to the registry and forgetting the
        // switch arm would accept the request and answer nothing at all.
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
                                 .formatted(wireName, FULL_META));

            assertFalse(io.responses().isEmpty(),
                        "'" + wireName + "' is accepted by INBOUND_METHODS but answered nothing");
        }
    }

    @Test
    @Tag("chapter04")
    void aPromptWithArgumentsThatNameNoKeywordDoesNotSendTheModelAfterTheWordNull() {
        router.route("""
                {"jsonrpc":"2.0","id":59,"method":"prompts/get",
                 "params":{%s,"name":"search_keyword","arguments":{"unexpected":"value"}}}"""
                             .formatted(FULL_META));

        String text = io.only().getAsJsonObject("result").getAsJsonArray("messages").get(0).getAsJsonObject()
                .getAsJsonObject("content").get("text").getAsString();
        assertFalse(text.contains("'null'"), "the prompt told the model to search for the word null: " + text);
        assertTrue(text.endsWith("keyword:"), "" + text);
    }

    @Test
    @Tag("chapter04")
    void completingWithNoArgumentIsAnsweredRatherThanFaultedOn() {
        router.route("""
                {"jsonrpc":"2.0","id":60,"method":"completion/complete",
                 "params":{%s,"ref":{"type":"ref/prompt","name":"search_keyword"}}}""".formatted(FULL_META));

        assertEquals(ErrorCodes.INVALID_PARAMS, io.onlyError().get("code").getAsInt());
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
    void aPayloadThatIsNeitherRequestNorReplyIsIgnored() {
        router.route("""
                {"jsonrpc":"2.0"}""");

        assertTrue(io.responses().isEmpty());
    }

    @Test
    @Tag("chapter03")
    void aCancelledNotificationForSomeOtherRequestIsNoted() {
        router.route("""
                {"jsonrpc":"2.0","method":"notifications/cancelled",
                 "params":{"requestId":"not-a-subscription","reason":"user pressed stop"}}""");

        assertTrue(io.responses().isEmpty(), "a notification is never answered");
    }

    @Test
    @Tag("chapter03")
    void anUnrecognisedNotificationIsIgnoredRatherThanRejected() {
        // Notifications carry no id, so there is nobody to send -32601 to.
        router.route("""
                {"jsonrpc":"2.0","method":"notifications/progress","params":{"progressToken":1}}""");

        assertTrue(io.responses().isEmpty());
    }


    @Test
    @Tag("chapter04")
    void resourcesListDoesNotAdvertiseTheAppYet() {
        router.route("""
                {"jsonrpc":"2.0","id":21,"method":"resources/list","params":{%s}}""".formatted(FULL_META));

        JsonArray resources = io.only().getAsJsonObject("result").getAsJsonArray("resources");
        assertFalse(resources.asList().stream()
                            .anyMatch(r -> r.getAsJsonObject().get("uri").getAsString().startsWith("ui://")),
                    "the MCP App resource belongs to chapter 6");
        assertTrue(resources.asList().stream()
                           .anyMatch(r -> r.getAsJsonObject().get("uri").getAsString().startsWith("javadoc/")),
                   "Javadoc pages must still be listed");
    }

    @Test
    @Tag("chapter04")
    void aToolCallWithoutADirectoryIsAToolLevelError() {
        router.route("""
                {"jsonrpc":"2.0","id":31,"method":"tools/call",
                 "params":{%s,"name":"key_word_search","arguments":{"keyword":"class"}}}"""
                             .formatted(FULL_META));

        JsonObject result = io.only().getAsJsonObject("result");
        assertTrue(result.get("isError").getAsBoolean(),
                   "chapter 4 has no round trip yet, so a missing directory is a tool error");
        assertTrue(result.getAsJsonArray("content").get(0).getAsJsonObject()
                           .get("text").getAsString().toLowerCase().contains("directory"));
    }

    // --- prompts, resources, completion ---------------------------------

    @Test
    @Tag("chapter04")
    void promptsListNamesTheSearchPromptAndItsRequiredArgument() {
        router.route("""
                {"jsonrpc":"2.0","id":20,"method":"prompts/list","params":{%s}}""".formatted(FULL_META));

        JsonObject result = io.only().getAsJsonObject("result");
        JsonObject prompt = result.getAsJsonArray("prompts").get(0).getAsJsonObject();
        assertEquals("search_keyword", prompt.get("name").getAsString());
        JsonObject argument = prompt.getAsJsonArray("arguments").get(0).getAsJsonObject();
        assertEquals("keyword", argument.get("name").getAsString());
        assertTrue(argument.get("required").getAsBoolean());
        assertEquals(CacheScope.PUBLIC, result.get("cacheScope").getAsString());
    }

    @Test
    @Tag("chapter04")
    void resourcesListServesTheJavadocPages() {
        router.route("""
                {"jsonrpc":"2.0","id":21,"method":"resources/list","params":{%s}}""".formatted(FULL_META));

        JsonArray resources = io.only().getAsJsonObject("result").getAsJsonArray("resources");
        assertTrue(resources.size() >= 1, "expected Javadoc pages, got " + resources.size());
        assertTrue(resources.asList().stream()
                           .anyMatch(r -> r.getAsJsonObject().get("uri").getAsString().startsWith("javadoc/")),
                   "Javadoc pages must be listed");
    }

    @Test
    @Tag("chapter04")
    void theTemplateListIsAnsweredEmptyRatherThanNotAtAll() {
        // Clients fetch this whenever a server declares any resource
        // capability, so leaving it unhandled would strand them on -32601.
        router.route("""
                {"jsonrpc":"2.0","id":22,"method":"resources/templates/list",
                 "params":{%s}}""".formatted(FULL_META));

        JsonObject result = io.only().getAsJsonObject("result");
        assertEquals(ResultType.COMPLETE, result.get("resultType").getAsString());
        assertTrue(result.getAsJsonArray("resourceTemplates").isEmpty());
    }

    @Test
    @Tag("chapter04")
    void readingAJavadocPageReturnsItsMarkup() {
        router.route(read(24, "javadoc/com/workshop/mcp/spec/RequestId.html"));

        assertFalse(io.only().getAsJsonObject("result").get("isError").getAsBoolean());
        assertTrue(firstContent().get("text").getAsString().contains("RequestId"));
    }

    @Test
    @Tag("chapter04")
    void readingAResourceThatIsNotThereIsAnErrorResultRatherThanAProtocolError() {
        router.route(read(25, "javadoc/com/workshop/mcp/spec/NoSuchPage.html"));

        JsonObject result = io.only().getAsJsonObject("result");
        assertNull(io.only().get("error"), "a missing resource is a tool-level failure, not -32602");
        assertTrue(result.get("isError").getAsBoolean());
    }

    @Test
    @Tag("chapter04")
    void readingWithAnEmptyUriIsRejectedAsAnErrorResult() {
        router.route(read(26, ""));

        assertTrue(io.only().getAsJsonObject("result").get("isError").getAsBoolean());
    }

    @Test
    @Tag("chapter04")
    void readingWithNoUriAtAllIsRejectedAsAnErrorResult() {
        router.route("""
                {"jsonrpc":"2.0","id":27,"method":"resources/read","params":{%s}}""".formatted(FULL_META));

        assertTrue(io.only().getAsJsonObject("result").get("isError").getAsBoolean());
    }

    @Test
    @Tag("chapter04")
    void completingTheKeywordArgumentOffersSeveralWords() {
        router.route(complete(28, "keyword"));

        JsonObject result = io.only().getAsJsonObject("result");
        assertEquals(3, result.get("total").getAsInt());
        assertTrue(result.get("hasMore").getAsBoolean());
        assertEquals(3, result.getAsJsonObject("completion").getAsJsonArray("values").size());
    }

    @Test
    @Tag("chapter04")
    void completingAnyOtherArgumentOffersTheSingleFallback() {
        router.route(complete(29, "something-else"));

        JsonObject result = io.only().getAsJsonObject("result");
        assertEquals(1, result.get("total").getAsInt());
        assertFalse(result.get("hasMore").getAsBoolean());
    }

    // --- tools/call argument handling ------------------------------------

    @Test
    @Tag("chapter04")
    void callingAToolThisServerDoesNotHaveIsAnErrorResult() {
        router.route("""
                {"jsonrpc":"2.0","id":30,"method":"tools/call",
                 "params":{%s,"name":"nope","arguments":{}}}""".formatted(FULL_META));

        JsonObject result = io.only().getAsJsonObject("result");
        assertTrue(result.get("isError").getAsBoolean());
        assertTrue(result.getAsJsonArray("content").get(0).getAsJsonObject()
                           .get("text").getAsString().contains("nope"));
    }

    /**
     * Interrupts the thread a task runs on, by the name
     * {@code runToolAsTask} gives it.
     */
    private static void interruptTaskThread(String taskId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                if (("task-" + taskId).equals(thread.getName())) {
                    thread.interrupt();
                    return;
                }
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("no thread was ever running task " + taskId);
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

    private static String cancel(int id, String taskId) {
        return """
                {"jsonrpc":"2.0","id":%d,"method":"tasks/cancel",
                 "params":{%s,"taskId":"%s"}}""".formatted(id, FULL_META, taskId);
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
