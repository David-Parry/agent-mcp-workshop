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
import com.workshop.mcp.tools.SearchContinuation;
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

    @Test
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

    // --- Multi Round-Trip Requests -------------------------------------

    @Test
    void theSearchToolHoldsNoStateSoADirectoryCannotOutliveItsCall() {
        for (Field field : KeyWordSearch.class.getDeclaredFields()) {
            assertTrue(Modifier.isStatic(field.getModifiers()),
                       "KeyWordSearch must stay stateless, but it declares the instance field '"
                       + field.getName() + "'. A directory resolved for one call must not be "
                       + "reachable from the next — pass it to call(params, directories) instead.");
        }
    }

    @Test
    void aDirectoryFromOneCallIsNotReusedByTheNext() {
        // Supplying a directory must not teach the server anything. The very
        // next bare call has to ask again.
        String meta = """
                "_meta":{
                  "io.modelcontextprotocol/protocolVersion":"2026-07-28",
                  "io.modelcontextprotocol/clientInfo":{"name":"test","version":"1.0"},
                  "io.modelcontextprotocol/clientCapabilities":{"elicitation":{"form":{}}}
                }""";
        router.route("""
                {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{
                  "name":"key_word_search",
                  "arguments":{"keyword":"keyword","directory":"%s"},%s}}"""
                             .formatted(System.getProperty("user.dir"), meta));
        assertEquals(ResultType.COMPLETE, io.only().getAsJsonObject("result")
                .get("resultType").getAsString(), "an explicit directory should just be searched");

        io.clear();
        router.route("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{
                  "name":"key_word_search","arguments":{"keyword":"keyword"},%s}}"""
                             .formatted(meta));
        assertEquals(ResultType.INPUT_REQUIRED, io.only().getAsJsonObject("result")
                             .get("resultType").getAsString(),
                     "the previous call's directory must not be remembered");
    }

    @Test
    void aClientDeclaringTheDeprecatedRootsAndSamplingCapabilitiesIsUnaffected() {
        // Roots and sampling are both deprecated under SEP-2577, which tells
        // clients to keep declaring what they support for the whole transition
        // period — so real clients, the Inspector included, still send these.
        // This server models neither, and a client must not be penalised for
        // saying so: the unknown keys are ignored and the capability it does
        // model still drives the exchange.
        router.route("""
                {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{
                  "name":"key_word_search","arguments":{"keyword":"Gson"},
                  "_meta":{
                    "io.modelcontextprotocol/protocolVersion":"2026-07-28",
                    "io.modelcontextprotocol/clientInfo":{"name":"test","version":"1.0"},
                    "io.modelcontextprotocol/clientCapabilities":{
                      "sampling":{},"roots":{"listChanged":true},
                      "elicitation":{"form":{}}
                    }
                  }}}""");

        JsonObject result = io.only().getAsJsonObject("result");
        assertEquals(ResultType.INPUT_REQUIRED, result.get("resultType").getAsString());
        assertEquals("elicitation/create", result.getAsJsonObject("inputRequests")
                             .getAsJsonObject(SearchContinuation.KEY_DIRECTORY)
                             .get("method").getAsString(),
                     "unmodelled deprecated declarations must not disturb elicitation");
    }

    @Test
    void aDeprecatedRootsDeclarationDoesNotMakeTheServerAskForRoots() {
        // The server used to embed a roots/list here. Roots is deprecated, so
        // it no longer does — and a client that can only answer roots is
        // therefore never asked anything at all.
        router.route("""
                {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{
                  "name":"key_word_search","arguments":{"keyword":"Gson"},
                  "_meta":{
                    "io.modelcontextprotocol/protocolVersion":"2026-07-28",
                    "io.modelcontextprotocol/clientCapabilities":{"roots":{"listChanged":true}}
                  }}}""");

        JsonObject result = io.only().getAsJsonObject("result");
        assertEquals(ResultType.COMPLETE, result.get("resultType").getAsString(),
                     "with nothing askable the search falls back to the working directory");
        assertNull(result.get("inputRequests"), "no roots/list may be embedded any more");
    }

    @Test
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
    void theRemovedRootsListChangedNotificationIsNotRoutable() {
        router.route("""
                {"jsonrpc":"2.0","id":1,"method":"notifications/roots/list_changed",
                 "params":{%s}}""".formatted(FULL_META));

        assertEquals(ErrorCodes.METHOD_NOT_FOUND, io.onlyError().get("code").getAsInt(),
                     "2026-07-28 removed this notification outright");
    }

    @Test
    void aToolCallWithNoDirectoryAsksTheUserForOne() {
        router.route(toolCall(1, null, null));

        JsonObject result = io.only().getAsJsonObject("result");
        assertEquals(ResultType.INPUT_REQUIRED, result.get("resultType").getAsString());

        JsonObject embedded = result.getAsJsonObject("inputRequests")
                .getAsJsonObject(SearchContinuation.KEY_DIRECTORY);
        assertEquals("elicitation/create", embedded.get("method").getAsString());
        assertEquals("form", embedded.getAsJsonObject("params").get("mode").getAsString());
        assertNull(embedded.get("id"), "an embedded request carries no JSON-RPC id");
        assertNull(embedded.get("jsonrpc"), "an embedded request carries no JSON-RPC version");

        // The keyword survives in the continuation state, since there is no
        // session to hold it in.
        SearchContinuation state = SearchContinuation.decode(result.get("requestState").getAsString());
        assertEquals("record", state.keyword());
        assertEquals(SearchContinuation.STAGE_DIRECTORY, state.stage());
    }

    @Test
    void theQuestionIsAskedOnceAndOnlyOnce() {
        // The stage on the continuation is what stops the server asking the
        // same thing again when the answer was unusable. Without it a client
        // that declined the form would be handed the same form forever.
        String state = SearchContinuation.awaitingDirectory("record").encode();
        router.route(toolCall(3, state, """
                "search_directory":{"action":"decline"}"""));

        JsonObject result = io.only().getAsJsonObject("result");
        assertEquals(ResultType.COMPLETE, result.get("resultType").getAsString());
        assertNull(result.get("inputRequests"), "a declined form must not be re-asked");
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
    void aClientThatCannotShowAFormIsNotAsked() {
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

        // The client can show a form, so the only reason it is not asked for a
        // directory is that the call already carried one.
        router.route("""
                {"jsonrpc":"2.0","id":8,"method":"tools/call","params":{
                  "_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28",
                           "io.modelcontextprotocol/clientCapabilities":{"elicitation":{"form":{}}}},
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
                  "inputResponses":{"search_directory":{"action":"accept",
                                     "content":{"directory":"%s"}}}}}"""
                             .formatted(SearchContinuation.awaitingDirectory("record").encode(), directory));

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
        // never enables auto-fulfilment. Note that this client can show a
        // form, so the tool would very much like to ask.
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
    @Timeout(30)
    void aTaskAsksForItsDirectoryAsAStatusAndResumesOnTasksUpdate(
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("hit.txt"), "the record speaks");

        router.route(taskCallWithoutADirectory(11, """
                "elicitation":{"form":{}}"""));
        String taskId = io.responses().get(0).getAsJsonObject("result").get("taskId").getAsString();

        // The question could not ride on the call's result, because that was
        // already spent on the handle. It surfaces as the task's own status.
        JsonObject asking = awaitTaskStatus(taskId, "input_required");
        assertEquals("elicitation/create",
                     asking.getAsJsonObject("inputRequests")
                             .getAsJsonObject(SearchContinuation.KEY_DIRECTORY).get("method").getAsString(),
                     "a task in input_required must publish what it is waiting for");

        // Answering it has to be accepted. Before requireInput was wired up,
        // no task ever reached input_required, so this always came back as
        // -32602 "Cannot update task: it is not waiting for input".
        io.clear();
        router.route("""
                {"jsonrpc":"2.0","id":12,"method":"tasks/update","params":{%s,
                  "taskId":"%s",
                  "inputResponses":{"search_directory":{"action":"accept",
                                     "content":{"directory":"%s"}}}}}"""
                             .formatted(FULL_META, taskId, directory));
        JsonObject update = io.responses().get(0);
        assertNull(update.get("error"), "answering what the task asked for must not be rejected");
        assertEquals(ResultType.COMPLETE, update.getAsJsonObject("result").get("resultType").getAsString());

        JsonObject done = awaitTaskStatus(taskId, "completed");
        assertFalse(done.getAsJsonObject("result").get("isError").getAsBoolean());
        assertTrue(done.getAsJsonObject("result").toString().contains("hit.txt"),
                   "the task must search the directory it was handed: " + done);
    }

    @Test
    @Timeout(30)
    void aTaskWhoseFormIsDeclinedStillFinishesFromTheWorkingDirectory() throws Exception {
        router.route(taskCallWithoutADirectory(13, """
                "elicitation":{"form":{},"url":{}}"""));
        String taskId = io.responses().get(0).getAsJsonObject("result").get("taskId").getAsString();

        awaitTaskStatus(taskId, "input_required");
        router.route("""
                {"jsonrpc":"2.0","id":14,"method":"tasks/update","params":{%s,
                  "taskId":"%s","inputResponses":{"search_directory":{"action":"decline"}}}}"""
                             .formatted(FULL_META, taskId));

        JsonObject done = awaitTaskStatus(taskId, "completed");
        assertFalse(done.getAsJsonObject("result").get("isError").getAsBoolean(),
                    "declining is not a failure — the task falls back to the working directory");
    }

    @Test
    @Timeout(30)
    void cancellingATaskThatIsWaitingForInputStopsItInsteadOfCompletingIt() throws Exception {
        router.route(taskCallWithoutADirectory(15, """
                "elicitation":{"form":{}}"""));
        String taskId = io.responses().get(0).getAsJsonObject("result").get("taskId").getAsString();

        awaitTaskStatus(taskId, "input_required");
        router.route("""
                {"jsonrpc":"2.0","id":16,"method":"tasks/cancel","params":{%s,"taskId":"%s"}}"""
                             .formatted(FULL_META, taskId));

        // The waiting thread has to notice and give up; a cancelled task must
        // not go on to report a result.
        JsonObject cancelled = awaitTaskStatus(taskId, "cancelled");
        assertNull(cancelled.get("result"), "a cancelled task must not produce a result: " + cancelled);
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

    @Test
    void aTaskHandleIsNeverGivenToAClientThatCannotPollForIt(
            @org.junit.jupiter.api.io.TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("hit.txt"), "the record speaks");

        router.route(toolCall(9, SearchContinuation.awaitingDirectory("record").encode(), """
                "search_directory":{"action":"accept","content":{"directory":"%s"}}"""
                                     .formatted(directory)));

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

    @Test
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
    void completingWithNoArgumentIsAnsweredRatherThanFaultedOn() {
        router.route("""
                {"jsonrpc":"2.0","id":60,"method":"completion/complete",
                 "params":{%s,"ref":{"type":"ref/prompt","name":"search_keyword"}}}""".formatted(FULL_META));

        assertEquals(ErrorCodes.INVALID_PARAMS, io.onlyError().get("code").getAsInt());
    }

    @Test
    void theTaskMethodsAnswerRatherThanFaultOnAMissingTaskId() {
        // Reading a null id into the store threw out of route(), and because
        // the throw escaped instead of becoming an error, the client was left
        // holding a request that was never going to be answered.
        for (String method : List.of("tasks/get", "tasks/update", "tasks/cancel")) {
            io.clear();
            router.route("""
                    {"jsonrpc":"2.0","id":61,"method":"%s","params":{%s}}""".formatted(method, FULL_META));

            assertEquals(ErrorCodes.INVALID_PARAMS, io.onlyError().get("code").getAsInt(),
                         method + " must answer a request with no taskId");
        }
    }

    @Test
    void aBlankLineIsNotAMessageAndIsIgnored() {
        router.route(null);
        router.route("");

        assertTrue(io.responses().isEmpty(), "nothing to answer: " + io.responses());
        assertTrue(io.notifications().isEmpty());
    }

    @Test
    void aResultFromTheClientIsUnsolicitedAndIgnored() {
        // This revision has the server ask for nothing, so a JSON-RPC result
        // arriving on stdin cannot be an answer to anything it sent.
        router.route("""
                {"jsonrpc":"2.0","id":1,"result":{"roots":[]}}""");

        assertTrue(io.responses().isEmpty(), "an unsolicited result must not be answered");
    }

    @Test
    void anErrorFromTheClientIsRecordedRatherThanAnsweredWithAnotherError() {
        router.route("""
                {"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"Method not found"}}""");

        assertTrue(io.responses().isEmpty(), "answering an error with an error would loop");
    }

    @Test
    void aPayloadThatIsNeitherRequestNorReplyIsIgnored() {
        router.route("""
                {"jsonrpc":"2.0"}""");

        assertTrue(io.responses().isEmpty());
    }

    @Test
    void aCancelledNotificationForSomeOtherRequestIsNoted() {
        router.route("""
                {"jsonrpc":"2.0","method":"notifications/cancelled",
                 "params":{"requestId":"not-a-subscription","reason":"user pressed stop"}}""");

        assertTrue(io.responses().isEmpty(), "a notification is never answered");
    }

    @Test
    void anUnrecognisedNotificationIsIgnoredRatherThanRejected() {
        // Notifications carry no id, so there is nobody to send -32601 to.
        router.route("""
                {"jsonrpc":"2.0","method":"notifications/progress","params":{"progressToken":1}}""");

        assertTrue(io.responses().isEmpty());
    }

    // --- prompts, resources, completion ---------------------------------

    @Test
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
    void resourcesListServesTheJavadocPagesAndTheAppAlongside() {
        router.route("""
                {"jsonrpc":"2.0","id":21,"method":"resources/list","params":{%s}}""".formatted(FULL_META));

        JsonArray resources = io.only().getAsJsonObject("result").getAsJsonArray("resources");
        assertTrue(resources.size() > 1, "the javadoc pages plus the app: " + resources.size());
        assertTrue(resources.asList().stream()
                           .anyMatch(r -> "ui://keyword-search/mcp-app.html"
                                   .equals(r.getAsJsonObject().get("uri").getAsString())),
                   "the app must be listed beside the javadoc");
    }

    @Test
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
    void readingTheAppUriReturnsTheHtmlUnderTheAppMimeType() {
        router.route(read(23, "ui://keyword-search/mcp-app.html"));

        JsonObject content = firstContent();
        assertEquals("text/html;profile=mcp-app", content.get("mimeType").getAsString());
        assertTrue(content.get("text").getAsString().contains("<"), "expected markup");
    }

    @Test
    void readingAJavadocPageReturnsItsMarkup() {
        router.route(read(24, "javadoc/com/workshop/mcp/spec/RequestId.html"));

        assertFalse(io.only().getAsJsonObject("result").get("isError").getAsBoolean());
        assertTrue(firstContent().get("text").getAsString().contains("RequestId"));
    }

    @Test
    void readingAResourceThatIsNotThereIsAnErrorResultRatherThanAProtocolError() {
        router.route(read(25, "javadoc/com/workshop/mcp/spec/NoSuchPage.html"));

        JsonObject result = io.only().getAsJsonObject("result");
        assertNull(io.only().get("error"), "a missing resource is a tool-level failure, not -32602");
        assertTrue(result.get("isError").getAsBoolean());
    }

    @Test
    void readingWithAnEmptyUriIsRejectedAsAnErrorResult() {
        router.route(read(26, ""));

        assertTrue(io.only().getAsJsonObject("result").get("isError").getAsBoolean());
    }

    @Test
    void readingWithNoUriAtAllIsRejectedAsAnErrorResult() {
        router.route("""
                {"jsonrpc":"2.0","id":27,"method":"resources/read","params":{%s}}""".formatted(FULL_META));

        assertTrue(io.only().getAsJsonObject("result").get("isError").getAsBoolean());
    }

    @Test
    void completingTheKeywordArgumentOffersSeveralWords() {
        router.route(complete(28, "keyword"));

        JsonObject result = io.only().getAsJsonObject("result");
        assertEquals(3, result.get("total").getAsInt());
        assertTrue(result.get("hasMore").getAsBoolean());
        assertEquals(3, result.getAsJsonObject("completion").getAsJsonArray("values").size());
    }

    @Test
    void completingAnyOtherArgumentOffersTheSingleFallback() {
        router.route(complete(29, "something-else"));

        JsonObject result = io.only().getAsJsonObject("result");
        assertEquals(1, result.get("total").getAsInt());
        assertFalse(result.get("hasMore").getAsBoolean());
    }

    // --- tools/call argument handling ------------------------------------

    @Test
    void callingAToolThisServerDoesNotHaveIsAnErrorResult() {
        router.route("""
                {"jsonrpc":"2.0","id":30,"method":"tools/call",
                 "params":{%s,"name":"nope","arguments":{}}}""".formatted(FULL_META));

        JsonObject result = io.only().getAsJsonObject("result");
        assertTrue(result.get("isError").getAsBoolean());
        assertTrue(result.getAsJsonArray("content").get(0).getAsJsonObject()
                           .get("text").getAsString().contains("nope"));
    }

    @Test
    void aCallWithNoArgumentsAtAllStillAsksForSomewhereToSearch() {
        // Both the keyword and the directory are read off a params.arguments
        // that is simply absent here.
        router.route("""
                {"jsonrpc":"2.0","id":31,"method":"tools/call",
                 "params":{%s,"name":"key_word_search"}}""".formatted(FULL_META));

        assertEquals(ResultType.INPUT_REQUIRED,
                     io.only().getAsJsonObject("result").get("resultType").getAsString());
    }

    @Test
    void aBlankDirectoryArgumentCountsAsNotHavingSuppliedOne() {
        router.route("""
                {"jsonrpc":"2.0","id":32,"method":"tools/call",
                 "params":{%s,"name":"key_word_search",
                           "arguments":{"keyword":"record","directory":"   "}}}""".formatted(FULL_META));

        assertEquals(ResultType.INPUT_REQUIRED,
                     io.only().getAsJsonObject("result").get("resultType").getAsString(),
                     "whitespace is not a directory");
    }

    // --- reading the answers back ----------------------------------------

    @Test
    void aFormAnswerThatIsNotEvenAnObjectIsTreatedAsNoAnswer() {
        router.route(toolCall(34, SearchContinuation.awaitingDirectory("record").encode(),
                              "\"search_directory\":null"));

        assertEquals(ResultType.COMPLETE,
                     io.only().getAsJsonObject("result").get("resultType").getAsString(),
                     "an unusable answer falls back rather than re-asking");
    }

    @Test
    void anAcceptedFormWithNoDirectoryInItFallsBackRatherThanSearchingNowhere() {
        router.route(toolCall(36, SearchContinuation.awaitingDirectory("record").encode(),
                              "\"search_directory\":{\"action\":\"accept\",\"content\":{}}"));

        assertEquals(ResultType.COMPLETE,
                     io.only().getAsJsonObject("result").get("resultType").getAsString());
    }

    @Test
    void anAcceptedFormWhoseDirectoryIsBlankFallsBackToo() {
        router.route(toolCall(37, SearchContinuation.awaitingDirectory("record").encode(),
                              "\"search_directory\":{\"action\":\"accept\",\"content\":{\"directory\":\"  \"}}"));

        assertEquals(ResultType.COMPLETE,
                     io.only().getAsJsonObject("result").get("resultType").getAsString());
    }

    @Test
    void anAcceptedFormWhoseContentIsNotAnObjectFallsBackToo() {
        router.route(toolCall(38, SearchContinuation.awaitingDirectory("record").encode(),
                              "\"search_directory\":{\"action\":\"accept\",\"content\":\"/tmp\"}"));

        assertEquals(ResultType.COMPLETE,
                     io.only().getAsJsonObject("result").get("resultType").getAsString());
    }

    @Test
    void aMissingFormAnswerFallsBackToTheWorkingDirectory() {
        router.route(toolCall(39, SearchContinuation.awaitingDirectory("record").encode(),
                              "\"search_directory\":null"));

        assertEquals(ResultType.COMPLETE,
                     io.only().getAsJsonObject("result").get("resultType").getAsString());
    }

    // --- tasks: rejected updates and cancels ------------------------------

    @Test
    void updatingATaskThatDoesNotExistIsInvalidParams() {
        router.route("""
                {"jsonrpc":"2.0","id":40,"method":"tasks/update",
                 "params":{%s,"taskId":"no-such-task","inputResponses":{}}}""".formatted(FULL_META));

        assertEquals(ErrorCodes.INVALID_PARAMS, io.onlyError().get("code").getAsInt());
    }

    @Test
    @Timeout(30)
    void answeringATaskThatIsNoLongerAskingAnythingIsRejected(
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        router.route(taskCallWithADirectory(41, directory.toString()));
        String taskId = io.responses().get(0).getAsJsonObject("result").get("taskId").getAsString();
        awaitTaskStatus(taskId, "completed");

        // The task exists, so this is not "task not found" — it is a task that
        // has no outstanding question for these answers to belong to.
        io.clear();
        router.route("""
                {"jsonrpc":"2.0","id":42,"method":"tasks/update",
                 "params":{%s,"taskId":"%s","inputResponses":{"search_directory":{"action":"decline"}}}}"""
                             .formatted(FULL_META, taskId));

        JsonObject error = io.onlyError();
        assertEquals(ErrorCodes.INVALID_PARAMS, error.get("code").getAsInt());
        assertTrue(error.get("message").getAsString().contains("not waiting for input"),
                   "the client is told the task was not asking, not that it vanished: " + error);
    }

    @Test
    void cancellingATaskThatDoesNotExistIsInvalidParams() {
        router.route("""
                {"jsonrpc":"2.0","id":43,"method":"tasks/cancel",
                 "params":{%s,"taskId":"no-such-task"}}""".formatted(FULL_META));

        assertEquals(ErrorCodes.INVALID_PARAMS, io.onlyError().get("code").getAsInt());
    }

    @Test
    @Timeout(30)
    void cancellingATaskTwiceIsRejectedTheSecondTime() {
        router.route(taskCallWithoutADirectory(44, "\"elicitation\":{\"form\":{}}"));
        String taskId = io.responses().get(0).getAsJsonObject("result").get("taskId").getAsString();

        router.route(cancel(45, taskId));
        io.clear();
        router.route(cancel(46, taskId));

        JsonObject error = io.onlyError();
        assertEquals(ErrorCodes.INVALID_PARAMS, error.get("code").getAsInt());
        assertTrue(error.get("message").getAsString().contains("terminal"),
                   "the client is told why, not just that it failed: " + error);
    }

    // --- tasks: the background thread's own outcomes ----------------------

    @Test
    @Timeout(30)
    void aTaskWithNothingLeftToAskRunsTheSearchAndCompletes(
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("hit.txt"), "the record speaks");

        router.route(taskCallWithADirectory(47, directory.toString()));
        String taskId = io.responses().get(0).getAsJsonObject("result").get("taskId").getAsString();

        JsonObject done = awaitTaskStatus(taskId, "completed");
        assertFalse(done.getAsJsonObject("result").get("isError").getAsBoolean());
        assertTrue(done.getAsJsonObject("result").toString().contains("hit.txt"), "" + done);
    }

    @Test
    @Timeout(30)
    void aTaskWhoseSearchBlowsUpIsFailedRatherThanLeftWorking() throws Exception {
        // A NUL byte cannot appear in a path, so resolving this one throws
        // straight out of the search and into the task's own catch.
        router.route(taskCallWithADirectory(48, "/tmp/\\u0000nope"));
        String taskId = io.responses().get(0).getAsJsonObject("result").get("taskId").getAsString();

        JsonObject failed = awaitTaskStatus(taskId, "failed");
        assertNotNull(failed.get("error"), "a failed task must say what went wrong: " + failed);
    }

    @Test
    @Timeout(30)
    void aTaskThatIsInterruptedMidFlightIsFailedRatherThanLeftWorking() throws Exception {
        router.route(taskCallWithADirectory(49, System.getProperty("user.dir")));
        String taskId = io.responses().get(0).getAsJsonObject("result").get("taskId").getAsString();

        interruptTaskThread(taskId);

        JsonObject failed = awaitTaskStatus(taskId, "failed");
        assertTrue(failed.getAsJsonObject("error").get("message").getAsString().contains("Interrupted"),
                   "" + failed);
    }

    @Test
    @Timeout(30)
    void aTaskWhoseFormIsAcceptedSearchesTheDirectoryItWasGiven(
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("hit.txt"), "the record speaks");

        router.route(taskCallWithoutADirectory(52, "\"elicitation\":{\"form\":{},\"url\":{}}"));
        String taskId = io.responses().get(0).getAsJsonObject("result").get("taskId").getAsString();

        awaitTaskStatus(taskId, "input_required");
        router.route("""
                {"jsonrpc":"2.0","id":53,"method":"tasks/update","params":{%s,"taskId":"%s",
                  "inputResponses":{"search_directory":{"action":"accept",
                                                        "content":{"directory":"%s"}}}}}"""
                             .formatted(FULL_META, taskId, directory));

        JsonObject done = awaitTaskStatus(taskId, "completed");
        assertTrue(done.getAsJsonObject("result").toString().contains("hit.txt"),
                   "an accepted form must be searched, not fallen back from: " + done);
    }

    @Test
    @Timeout(30)
    void aTaskForAClientThatCannotShowAFormNeverWaitsAndSearchesTheWorkingDirectory() throws Exception {
        // This client declares only the deprecated capability the server
        // dropped, so there is nothing the task can usefully ask. It must run
        // straight through rather than parking in input_required forever
        // waiting for an answer that was never requested.
        router.route(taskCallWithoutADirectory(59, "\"roots\":{\"listChanged\":true}"));
        String taskId = io.responses().get(0).getAsJsonObject("result").get("taskId").getAsString();

        JsonObject done = awaitTaskStatus(taskId, "completed");
        assertFalse(done.getAsJsonObject("result").get("isError").getAsBoolean(),
                    "an unaskable task still searches, from the working directory: " + done);
    }

    @Test
    @Timeout(30)
    void aTaskAnsweredWithNothingUsableFallsBackToTheWorkingDirectory() throws Exception {
        // The form came back with an answer that resolves to no directory at
        // all, and there is no second question to escalate to.
        router.route(taskCallWithoutADirectory(57, "\"elicitation\":{\"form\":{}}"));
        String taskId = io.responses().get(0).getAsJsonObject("result").get("taskId").getAsString();

        awaitTaskStatus(taskId, "input_required");
        router.route("""
                {"jsonrpc":"2.0","id":58,"method":"tasks/update","params":{%s,
                  "taskId":"%s","inputResponses":{"search_directory":{"action":"accept",
                                                   "content":{}}}}}"""
                             .formatted(FULL_META, taskId));

        JsonObject done = awaitTaskStatus(taskId, "completed");
        assertFalse(done.getAsJsonObject("result").get("isError").getAsBoolean(),
                    "an unusable answer is not a failure: " + done);
    }

    @Test
    @Timeout(30)
    void cancellingATaskThatIsWaitingOnItsFormStopsItToo() throws Exception {
        router.route(taskCallWithoutADirectory(55, "\"elicitation\":{\"form\":{},\"url\":{}}"));
        String taskId = io.responses().get(0).getAsJsonObject("result").get("taskId").getAsString();

        awaitTaskStatus(taskId, "input_required");
        router.route(cancel(56, taskId));

        JsonObject cancelled = awaitTaskStatus(taskId, "cancelled");
        assertNull(cancelled.get("result"), "a cancelled task must not fall back and search anyway: " + cancelled);
    }

    @Test
    void aCallThatNamesADirectoryButNoKeywordSearchesForNothingRatherThanFailing(
            @org.junit.jupiter.api.io.TempDir Path directory) {
        router.route("""
                {"jsonrpc":"2.0","id":54,"method":"tools/call",
                 "params":{%s,"name":"key_word_search",
                           "arguments":{"directory":"%s"}}}""".formatted(FULL_META, directory));

        JsonObject result = io.only().getAsJsonObject("result");
        assertEquals(ResultType.COMPLETE, result.get("resultType").getAsString());
        assertTrue(result.getAsJsonArray("content").isEmpty(), "an absent keyword matches nothing");
    }

    @Test
    @Timeout(30)
    void aTaskThatIsAnsweredWithAnImpossiblePathIsFailedRatherThanLeftWorking() throws Exception {
        router.route(taskCallWithoutADirectory(50, "\"elicitation\":{\"form\":{}}"));
        String taskId = io.responses().get(0).getAsJsonObject("result").get("taskId").getAsString();

        awaitTaskStatus(taskId, "input_required");
        router.route("""
                {"jsonrpc":"2.0","id":51,"method":"tasks/update","params":{%s,"taskId":"%s",
                  "inputResponses":{"search_directory":{"action":"accept",
                                     "content":{"directory":"/tmp/\\u0000nope"}}}}}"""
                             .formatted(FULL_META, taskId));

        JsonObject failed = awaitTaskStatus(taskId, "failed");
        assertNotNull(failed.get("error"));
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
