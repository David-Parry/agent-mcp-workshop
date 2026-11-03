package com.workshop.mcp.spec;

/**
 * The {@code _meta} keys that carry the stateless lifecycle of protocol
 * revision {@code 2026-07-28}.
 * <p>
 * Revision {@code 2026-07-28} removed the {@code initialize} handshake. There
 * is no longer a moment at which the client and server agree on a protocol
 * version and exchange capabilities once; instead every single request
 * re-states them inside {@code params._meta}, and every result identifies its
 * sender the same way. These constants name those slots.
 * </p>
 * <p>
 * A {@code _meta} key is an optional dot-separated prefix ending in a slash,
 * followed by a name. Any prefix whose second label is {@code modelcontextprotocol}
 * or {@code mcp} is reserved for the specification, which is why every key here
 * carries {@code io.modelcontextprotocol/}. The W3C trace-context keys
 * ({@link #TRACEPARENT}, {@link #TRACESTATE}, {@link #BAGGAGE}) are the one
 * documented exception: they are reserved with no prefix at all.
 * </p>
 *
 * @see RequestEnvelope
 * @since 1.0
 */
public final class MetaKeys {

    private MetaKeys() {
        throw new AssertionError("MetaKeys class should not be instantiated");
    }

    /** Required on every inbound request: the revision the client is speaking. */
    public static final String PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion";

    /** Required on every inbound request: what the client can be asked to do. */
    public static final String CLIENT_CAPABILITIES = "io.modelcontextprotocol/clientCapabilities";

    /** Optional on inbound requests: which client software is calling. */
    public static final String CLIENT_INFO = "io.modelcontextprotocol/clientInfo";

    /**
     * Optional on inbound requests. When absent, the server MUST NOT emit
     * {@code notifications/message} for that request.
     */
    public static final String LOG_LEVEL = "io.modelcontextprotocol/logLevel";

    /** Emitted on every outbound result so the client can identify this server. */
    public static final String SERVER_INFO = "io.modelcontextprotocol/serverInfo";

    /**
     * Correlates a notification, acknowledgment, or close result with the
     * {@code subscriptions/listen} request that opened the stream. Omitting it
     * from the acknowledgment leaves the client waiting forever.
     */
    public static final String SUBSCRIPTION_ID = "io.modelcontextprotocol/subscriptionId";

    /** Identifier of the tasks extension, declared under {@code capabilities.extensions}. */
    public static final String TASKS_EXTENSION = "io.modelcontextprotocol/tasks";

    /** Identifier of the MCP Apps (UI) extension, declared under {@code capabilities.extensions}. */
    public static final String UI_EXTENSION = "io.modelcontextprotocol/ui";

    /**
     * W3C trace context for the request, carried unprefixed by exception.
     * A server that participates in tracing reads this to continue the
     * caller's trace rather than starting a new one.
     */
    public static final String TRACEPARENT = "traceparent";

    /** Vendor-specific trace state travelling alongside {@link #TRACEPARENT}. */
    public static final String TRACESTATE = "tracestate";

    /** W3C baggage: key-value context propagated across the call graph. */
    public static final String BAGGAGE = "baggage";
}
