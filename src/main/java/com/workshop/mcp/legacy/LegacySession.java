package com.workshop.mcp.legacy;

import com.workshop.mcp.spec.ClientCapabilities;
import com.workshop.mcp.spec.JsonRpcRequest;
import com.workshop.mcp.spec.MetaKeys;
import com.workshop.mcp.spec.RequestEnvelope;
import com.workshop.mcp.spec.ServerCapabilities;
import com.workshop.mcp.spec.ServerInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The legacy half of a dual-era server: the handshake revisions before
 * {@code 2026-07-28}, for a client that opens with {@code initialize}.
 * <p>
 * A server may speak both eras, and picks one from how each client opens. A
 * request carrying the modern {@code _meta} envelope is served statelessly,
 * like every other. An {@code initialize} selects the legacy revision it
 * agrees for the rest of the stdio process, so this is the one place the
 * server remembers a client: what it declared there, which every later
 * request that names no modern revision is served under.
 * </p>
 * <p>
 * A legacy client is asked nothing. Those revisions ask through requests the
 * server sends the client, which this server never does, and their tasks are
 * a different design from the extension's. So a legacy request is served as
 * if the client had declared neither elicitation nor the tasks extension, and
 * the search runs in one hop: in the directory the call names, or else in the
 * server's working directory.
 * </p>
 */
public final class LegacySession {

    /** The request a legacy client opens with. */
    public static final String INITIALIZE = "initialize";

    /** The notification a legacy client sends once it has read the {@code initialize} result. */
    public static final String INITIALIZED = "notifications/initialized";

    /** The legacy revisions' liveness check, answered with an empty result. */
    public static final String PING = "ping";

    /** The handshake revisions this server answers {@code initialize} for, newest first. */
    public static final List<String> VERSIONS = List.of("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05");

    /** What the legacy client declared, as the envelope its requests leave out; null until one opens. */
    private volatile RequestEnvelope envelope;

    /**
     * Opens the session for a client that sent {@code initialize}.
     * <p>
     * The client's revision is agreed when this server speaks it, and
     * otherwise the newest one it does, which the client may accept or
     * disconnect over. A second {@code initialize} replaces the first.
     * </p>
     *
     * @param params       the {@code initialize} params, null when the request had none
     * @param capabilities what to tell the client this server offers
     * @param instructions guidance for the client's model
     * @return the {@code initialize} result, or null when the params lack the
     *         protocol version or the capabilities, and nothing was opened
     */
    public InitializeResult open(InitializeParams params, ServerCapabilities capabilities, String instructions) {
        if (params == null || params.protocolVersion() == null || params.capabilities() == null) {
            return null;
        }
        String agreed = VERSIONS.contains(params.protocolVersion()) ? params.protocolVersion() : VERSIONS.get(0);
        envelope = new RequestEnvelope(agreed, askingNothing(params.capabilities()), params.clientInfo(), null);
        return new InitializeResult(agreed, capabilities, ServerInfo.DEFAULT, instructions);
    }

    /**
     * Reports whether a request is the legacy client's: one has opened, and
     * the request names no modern revision.
     *
     * @param declared the envelope the request carries, complete or not
     * @return true when the request is served under {@link #envelope()}
     */
    public boolean serves(RequestEnvelope declared) {
        return envelope != null && declared.protocolVersion() == null;
    }

    /**
     * @return the envelope the legacy client's requests are served under, or
     *         null when no client has opened with {@code initialize}
     */
    public RequestEnvelope envelope() {
        return envelope;
    }

    /**
     * A legacy client's request as a handler reads it. JSON-RPC lets a request
     * leave its params out, and legacy clients do; a modern request always has
     * them, since they carry its envelope.
     *
     * @param request a legacy client's request
     * @return the same request, with empty params when it had none
     */
    public static JsonRpcRequest withParams(JsonRpcRequest request) {
        return request.params() != null
                ? request
                : new JsonRpcRequest(request.jsonrpc(), request.id(), request.method(), Map.of());
    }

    private static ClientCapabilities askingNothing(ClientCapabilities declared) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        if (declared.extensions() != null) {
            extensions.putAll(declared.extensions());
        }
        extensions.remove(MetaKeys.TASKS_EXTENSION);
        return new ClientCapabilities(null, extensions);
    }
}
