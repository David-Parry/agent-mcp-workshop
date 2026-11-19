package com.workshop.mcp.legacy;

import com.workshop.mcp.spec.Capability;
import com.workshop.mcp.spec.ClientCapabilities;
import com.workshop.mcp.spec.ClientInfo;
import com.workshop.mcp.spec.Elicitation;
import com.workshop.mcp.spec.JsonRpcRequest;
import com.workshop.mcp.spec.MetaKeys;
import com.workshop.mcp.spec.RequestEnvelope;
import com.workshop.mcp.spec.RequestId;
import com.workshop.mcp.spec.ServerCapabilities;
import com.workshop.mcp.spec.ServerInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the legacy half of the dual-era server: the revision agreed with a
 * client that opens with {@code initialize}, what its requests are served
 * under, and which requests are its.
 */
@Tag("bonus")
@Tag("stretch")
class LegacySessionTest {

    private static final ServerCapabilities OFFERED =
            new ServerCapabilities(null, null, null, null, null, new Capability(false, null), null);
    private static final ClientInfo CLIENT = new ClientInfo("legacy-client", "1.0");
    private static final RequestEnvelope NO_ENVELOPE = new RequestEnvelope(null, null, null, null);

    private final LegacySession session = new LegacySession();

    @Test
    void aClientOnAHandshakeRevisionThisServerSpeaksIsAgreedToIt() {
        for (String version : LegacySession.VERSIONS) {
            InitializeResult result = session.open(params(version, new ClientCapabilities(null, null)), OFFERED, "i");

            assertEquals(version, result.protocolVersion());
            assertEquals(version, session.envelope().protocolVersion());
        }
    }

    @Test
    void aClientOnAnyOtherRevisionIsOfferedTheNewest() {
        for (String version : List.of("2024-10-07", "2026-07-28")) {
            assertEquals("2025-11-25", session.open(params(version, new ClientCapabilities(null, null)), OFFERED, "i")
                    .protocolVersion(), version);
        }
    }

    @Test
    void theResultSaysWhatThisServerOffersAndWhoItIs() {
        InitializeResult result = session.open(params("2025-11-25", new ClientCapabilities(null, null)), OFFERED,
                                               "Pass the directory to search");

        assertSame(OFFERED, result.capabilities());
        assertEquals(ServerInfo.DEFAULT, result.serverInfo());
        assertEquals("Pass the directory to search", result.instructions());
    }

    @Test
    void initializeWithoutAVersionOrCapabilitiesOpensNothing() {
        assertNull(session.open(null, OFFERED, "i"));
        assertNull(session.open(params(null, new ClientCapabilities(null, null)), OFFERED, "i"));
        assertNull(session.open(params("2025-11-25", null), OFFERED, "i"));

        assertNull(session.envelope(), "nothing was opened");
    }

    @Test
    void requestsAreServedUnderWhatTheClientDeclaredButWithoutElicitationOrTasks() {
        Map<String, Object> declared = Map.of(MetaKeys.TASKS_EXTENSION, Map.of(),
                                              MetaKeys.UI_EXTENSION, Map.of("mimeTypes", List.of("text/html")));
        Elicitation formsAndUrls = new Elicitation(new Capability(null, null), new Capability(null, null));
        session.open(params("2025-06-18", new ClientCapabilities(formsAndUrls, declared)), OFFERED, "i");

        RequestEnvelope envelope = session.envelope();
        assertEquals("2025-06-18", envelope.protocolVersion());
        assertEquals(CLIENT, envelope.clientInfo());
        assertNull(envelope.logLevel(), "a legacy client sets its log level once, with logging/setLevel");
        assertNull(envelope.clientCapabilities().elicitation(), "those revisions ask by sending the client a request");
        assertEquals(Map.of(MetaKeys.UI_EXTENSION, Map.of("mimeTypes", List.of("text/html"))),
                     envelope.clientCapabilities().extensions());
        assertFalse(envelope.supportsTasks());
    }

    @Test
    void aClientThatDeclaredNoExtensionsIsServedWithNone() {
        session.open(params("2025-11-25", new ClientCapabilities(null, null)), OFFERED, "i");

        assertEquals(Map.of(), session.envelope().clientCapabilities().extensions());
    }

    @Test
    void aSecondInitializeReplacesTheFirst() {
        session.open(params("2024-11-05", new ClientCapabilities(null, null)), OFFERED, "i");
        session.open(params("2025-11-25", new ClientCapabilities(null, null)), OFFERED, "i");

        assertEquals("2025-11-25", session.envelope().protocolVersion());
    }

    @Test
    void onlyARequestThatNamesNoModernRevisionIsTheLegacyClientsAndOnlyOnceOneHasOpened() {
        assertFalse(session.serves(NO_ENVELOPE), "no client has opened with initialize");

        session.open(params("2025-11-25", new ClientCapabilities(null, null)), OFFERED, "i");

        assertTrue(session.serves(NO_ENVELOPE));
        assertFalse(session.serves(new RequestEnvelope("2026-07-28", null, null, null)),
                    "a request that names a modern revision is served by that revision's rules");
    }

    @Test
    void aRequestThatLeftOutItsParamsIsReadWithEmptyOnes() {
        JsonRpcRequest bare = new JsonRpcRequest("2.0", RequestId.of(1), "tools/list", null);

        JsonRpcRequest read = LegacySession.withParams(bare);

        assertEquals(new JsonRpcRequest("2.0", RequestId.of(1), "tools/list", Map.of()), read);
        JsonRpcRequest withParams = new JsonRpcRequest("2.0", RequestId.of(2), "tools/list", Map.of("cursor", "c"));
        assertSame(withParams, LegacySession.withParams(withParams));
    }

    @Test
    void initializeParamsCarryWhatTheClientSent() {
        InitializeParams params = params("2025-11-25", new ClientCapabilities(null, null));

        assertEquals("2025-11-25", params.protocolVersion());
        assertEquals(new ClientCapabilities(null, null), params.capabilities());
        assertEquals(CLIENT, params.clientInfo());
    }

    private static InitializeParams params(String version, ClientCapabilities capabilities) {
        return new InitializeParams(version, capabilities, CLIENT);
    }
}
