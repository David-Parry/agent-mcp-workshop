package com.workshop.mcp.spec;

import java.util.List;

/**
 * The stateless lifecycle envelope carried in {@code params._meta} on every
 * request of protocol revision {@code 2026-07-28}.
 * <p>
 * This record is what replaced {@code InitializeParams}. Because the handshake
 * is gone, a server cannot remember who it is talking to or what that peer can
 * do; it re-reads both from each request. That has a concrete consequence for
 * this workshop: capability flags such as "can this client show an elicitation
 * form" are now per-request facts rather than fields on the router.
 * </p>
 * <p>
 * {@link #protocolVersion()} and {@link #clientCapabilities()} are required;
 * {@link #clientInfo()} is merely recommended. {@link #logLevel()} governs
 * whether the server may emit {@code notifications/message} for this request —
 * when it is absent, the server must stay silent.
 * </p>
 *
 * @param protocolVersion   the revision the client is speaking, required
 * @param clientCapabilities what the client can be asked to do, required
 * @param clientInfo        which client software is calling, optional
 * @param logLevel          the requested log level for this request, optional
 *
 * @see MetaKeys
 * @since 1.0
 */
public record RequestEnvelope(
    String protocolVersion,
    ClientCapabilities clientCapabilities,
    ClientInfo clientInfo,
    String logLevel
) {

    /** The only protocol revision this server implements. */
    public static final String SUPPORTED_VERSION = "2026-07-28";

    /**
     * Every revision this server can speak, as advertised by
     * {@code server/discover} and by the {@code -32022} error data.
     */
    public static final List<String> SUPPORTED_VERSIONS = List.of(SUPPORTED_VERSION);

    /**
     * Reports whether the two required envelope fields are present.
     *
     * @return true when the envelope can be acted on
     */
    public boolean isComplete() {
        return protocolVersion != null && clientCapabilities != null;
    }

    /**
     * Reports whether the declared revision is one this server implements.
     *
     * @return true when the revision is supported
     */
    public boolean isSupportedVersion() {
        return protocolVersion != null && SUPPORTED_VERSIONS.contains(protocolVersion);
    }

    /**
     * Reports whether the client can render an elicitation form on this request.
     *
     * @return true when form-mode elicitation is available
     */
    public boolean supportsElicitationForm() {
        return clientCapabilities != null
               && clientCapabilities.elicitation() != null
               && clientCapabilities.elicitation().supportsForm();
    }

    /**
     * Reports whether the client declared the tasks extension on this request.
     * <p>
     * A server must not hand back a task handle to a client that did not
     * declare the extension on the very request being answered.
     * </p>
     *
     * @return true when the tasks extension is available
     */
    public boolean supportsTasks() {
        return clientCapabilities != null && clientCapabilities.hasExtension(MetaKeys.TASKS_EXTENSION);
    }
}
