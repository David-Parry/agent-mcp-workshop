package com.workshop.mcp.spec;

import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the one place inbound bytes become objects: which of the four
 * JSON-RPC shapes a message is read as, and how the stateless envelope of
 * protocol revision {@code 2026-07-28} is lifted out of {@code params._meta}.
 */
@Tag("chapter02")
class JsonRpcMessageDeserializerTest {

    /** The client-capability envelope a well-formed request of this revision carries. */
    private static final String FULL_META = """
            "_meta":{
              "io.modelcontextprotocol/protocolVersion":"2026-07-28",
              "io.modelcontextprotocol/clientInfo":{"name":"test","version":"1.0"},
              "io.modelcontextprotocol/clientCapabilities":{"elicitation":{"form":{}},"roots":{"listChanged":true}},
              "io.modelcontextprotocol/logLevel":"debug"
            }""";

    private JsonRpcMessageDeserializer deserializer;

    @BeforeEach
    void setUp() {
        deserializer = new JsonRpcMessageDeserializer();
    }

    // --- classification -------------------------------------------------

    @Test
    void aMessageWithBothAMethodAndAnIdIsARequest() {
        JsonRpcRequest request = request("""
                {"jsonrpc":"2.0","id":"probe","method":"tools/list","params":{%s}}""".formatted(FULL_META));

        assertEquals("2.0", request.jsonrpc());
        assertEquals("tools/list", request.method());
        assertEquals(RequestId.of("probe"), request.id());
    }

    @Test
    void aMessageWithAMethodButNoIdIsANotification() {
        JsonRpcNotification notification = assertInstanceOf(JsonRpcNotification.class, deserializer.deserialize("""
                {"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":"listen:0"}}"""));

        assertEquals("notifications/cancelled", notification.method());
    }

    @Test
    void aMessageWhoseIdIsExplicitlyNullIsStillANotification() {
        // Gson's has() reports a key that is present but null as present, so
        // testing for the key alone classified this as a request — and the
        // server went on to answer a notification with a null id.
        JsonRpcNotification notification = assertInstanceOf(JsonRpcNotification.class, deserializer.deserialize("""
                {"jsonrpc":"2.0","method":"notifications/cancelled","id":null,"params":{"requestId":"x"}}"""));

        assertEquals("notifications/cancelled", notification.method());
    }

    @Test
    void aMessageCarryingAResultIsAResponse() {
        JsonRpcResponse response = assertInstanceOf(JsonRpcResponse.class, deserializer.deserialize("""
                {"jsonrpc":"2.0","id":4,"result":{"action":"decline"}}"""));

        assertEquals(RequestId.of(4L), response.id());
        assertEquals(Map.of("action", "decline"), response.result());
    }

    @Test
    void aMessageCarryingAnErrorIsAnErrorResponse() {
        JsonRpcErrorResponse response = assertInstanceOf(JsonRpcErrorResponse.class, deserializer.deserialize("""
                {"jsonrpc":"2.0","id":4,"error":{"code":-32601,"message":"Method not found"}}"""));

        assertEquals(ErrorCodes.METHOD_NOT_FOUND, response.error().code());
        assertEquals("Method not found", response.error().message());
    }

    @Test
    void aMessageThatIsNoneOfTheFourShapesKeepsItsRawText() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1}";

        assertEquals(new Unknown(json), deserializer.deserialize(json));
    }

    @Test
    void malformedJsonIsRejected() {
        assertThrows(JsonSyntaxException.class, () -> deserializer.deserialize("{\"method\":"));
    }

    @Test
    void jsonThatIsNotAnObjectIsRejected() {
        // A JSON-RPC batch arrives as an array and this server does not accept
        // one, so the array must not be mistaken for a lone message.
        for (String json : List.of("[]", "\"tools/list\"", "17", "null")) {
            assertThrows(IllegalStateException.class, () -> deserializer.deserialize(json), json);
        }
    }

    // --- params ----------------------------------------------------------

    @Test
    void requestParamsAreReadIntoTheirParamsType() {
        TasksGetParams params = deserializer.deserializeParams(request("""
                {"jsonrpc":"2.0","id":1,"method":"tasks/get","params":{%s,"taskId":"task-7"}}"""
                                                                               .formatted(FULL_META)),
                                                               TasksGetParams.class);

        assertEquals("task-7", params.taskId());
    }

    @Test
    void notificationParamsAreReadIntoTheirParamsType() {
        JsonRpcNotification notification = assertInstanceOf(JsonRpcNotification.class, deserializer.deserialize("""
                {"jsonrpc":"2.0","method":"notifications/cancelled",
                 "params":{"requestId":"listen:0","reason":"user closed the stream"}}"""));

        NotificationCancelledParams params =
                deserializer.deserializeParams(notification, NotificationCancelledParams.class);

        assertEquals(RequestId.of("listen:0"), params.requestId());
        assertEquals("user closed the stream", params.reason());
    }

    @Test
    void absentParamsReadAsNullRatherThanAnEmptyParamsObject() {
        JsonRpcRequest request = request("""
                {"jsonrpc":"2.0","id":1,"method":"tools/list"}""");

        assertNull(deserializer.deserializeParams(request, TasksGetParams.class));
    }

    // --- convert ----------------------------------------------------------

    @Test
    void thereIsNothingToConvertWhenTheAnswerIsAbsent() {
        assertNull(deserializer.convert(null, ElicitationCreateResult.class));
    }

    @Test
    void oneAnswerOutOfAHeterogeneousMapIsConvertedOnDemand() {
        // What a single entry of params.inputResponses looks like once the
        // enclosing params have been parsed into a plain Map. Since roots was
        // deprecated this is the only answer shape the keyword search asks
        // for, and its content stays untyped because the form is built at
        // runtime rather than declared as a record.
        Object answer = Map.of("action", "accept", "content", Map.of("directory", "/srv/code"));

        ElicitationCreateResult elicited = deserializer.convert(answer, ElicitationCreateResult.class);

        assertEquals("accept", elicited.action());
        assertEquals(Map.of("directory", "/srv/code"), elicited.content());
    }

    // --- the params._meta envelope ----------------------------------------

    @Test
    void theEnvelopeIsReadOutOfParamsMeta() {
        RequestEnvelope envelope = envelopeOf("""
                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{%s}}""".formatted(FULL_META));

        assertTrue(envelope.isComplete());
        assertEquals(RequestEnvelope.SUPPORTED_VERSION, envelope.protocolVersion());
        assertEquals(new ClientInfo("test", "1.0"), envelope.clientInfo());
        assertEquals("debug", envelope.logLevel());
        // SEP-2577 asks clients to go on declaring roots for the whole
        // deprecation period, and this server stopped modelling it, so the
        // stray declaration has to be skipped rather than sink the envelope
        // and take the capability the server does read down with it.
        assertTrue(envelope.supportsElicitationForm());
    }

    @Test
    void anEnvelopeWithoutItsOptionalFieldsStillCarriesTheRequiredOnes() {
        RequestEnvelope envelope = envelopeOf("""
                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{"_meta":{
                  "io.modelcontextprotocol/protocolVersion":"2026-07-28",
                  "io.modelcontextprotocol/clientCapabilities":{}}}}""");

        assertTrue(envelope.isComplete());
        assertNull(envelope.clientInfo());
        assertNull(envelope.logLevel(), "no log level means the server must stay silent");
    }

    @Test
    void anEnvelopeFieldThatIsNotAPrimitiveIsReadAsAbsent() {
        RequestEnvelope envelope = envelopeOf("""
                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{"_meta":{
                  "io.modelcontextprotocol/protocolVersion":{"major":2026},
                  "io.modelcontextprotocol/logLevel":["debug"],
                  "io.modelcontextprotocol/clientCapabilities":{}}}}""");

        assertNull(envelope.protocolVersion());
        assertNull(envelope.logLevel());
        assertFalse(envelope.isComplete(), "a version that is not a string is no version at all");
    }

    @Test
    void aRequestWithNoParamsHasAnIncompleteEnvelopeRatherThanNone() {
        assertEnvelopeIsEmpty(envelopeOf("""
                {"jsonrpc":"2.0","id":1,"method":"tools/list"}"""));
    }

    @Test
    void paramsThatAreNotAnObjectCarryNoEnvelope() {
        // JSON-RPC allows positional params; the envelope has nowhere to live
        // in one, so such a request can never be complete.
        assertEnvelopeIsEmpty(envelopeOf("""
                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":["tools/list"]}"""));
    }

    @Test
    void paramsWithoutAMetaCarryNoEnvelope() {
        assertEnvelopeIsEmpty(envelopeOf("""
                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}"""));
    }

    @Test
    void aMetaThatIsNotAnObjectCarriesNoEnvelope() {
        assertEnvelopeIsEmpty(envelopeOf("""
                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{"_meta":"2026-07-28"}}"""));
    }

    // --- helpers ------------------------------------------------------------

    private JsonRpcRequest request(String json) {
        return assertInstanceOf(JsonRpcRequest.class, deserializer.deserialize(json));
    }

    private RequestEnvelope envelopeOf(String json) {
        return deserializer.deserializeEnvelope(request(json));
    }

    private static void assertEnvelopeIsEmpty(RequestEnvelope envelope) {
        assertEquals(new RequestEnvelope(null, null, null, null), envelope);
        assertFalse(envelope.isComplete());
    }
}
