package com.workshop.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.workshop.mcp.io.IOHandler;
import com.workshop.mcp.io.LogFile;
import com.workshop.mcp.io.LogFileWriter;
import com.workshop.mcp.spec.*;
import com.workshop.mcp.spec.builders.*;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Routes JSON-RPC messages for protocol revision {@code 2026-07-28}.
 * <p>
 * The shape of this class is dictated by what that revision removed. There is
 * no {@code initialize} handler, because there is no handshake: every request
 * re-states the protocol version and the client's capabilities in its
 * {@code _meta}, and {@link #envelopeFor} validates that before anything else
 * happens. There are no fields tracking what the client can do, because those
 * are per-request facts now. And there is no code that sends a request to the
 * client, because modern clients discard inbound requests — the three things
 * this server used to ask for are embedded in an input_required result
 * instead.
 * </p>
 *
 * @see RequestEnvelope
 * @since 1.0
 */
public class IORouter implements Router {
    private static final Gson GSON = McpGson.create();
    private static final String JSON_RPC_VERSION = "2.0";
    private static final long LIST_TTL_MILLIS = 60_000L;

    /**
     * The methods this server accepts as inbound requests.
     * <p>
     * The revision also defines three methods that only ever appear embedded
     * in an input_required result — {@code roots/list},
     * {@code sampling/createMessage}, and {@code elicitation/create}. Only the
     * last is something this server asks for; the other two are deprecated
     * under SEP-2577 and are not built here at all. None is ever answered, so
     * a client sending any of them gets {@link ErrorCodes#METHOD_NOT_FOUND}.
     * </p>
     * <p>
     * Every member of this set must have an arm in the switch in
     * {@link #process(JsonRpcRequest)}, or the request is accepted and then
     * silently dropped. That agreement is checked by a test rather than
     * guarded at runtime, because the only thing that can break it is an edit
     * to this file.
     * </p>
     */
    private static final Set<UniqueKeys> INBOUND_METHODS = EnumSet.of(
            UniqueKeys.SERVER_DISCOVER,
            UniqueKeys.SUBSCRIPTIONS_LISTEN);

    private final LogFile logger;
    private final IOHandler io;
    private final JsonRpcMessageDeserializer deserializer = new JsonRpcMessageDeserializer();

    public IORouter(IOHandler io) {
        this(io, LogFileWriter.getInstance());
    }

    IORouter(IOHandler io, LogFile logger) {
        this.logger = logger;
        this.io = io;
    }

    public void route(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        Object object;
        try {
            object = deserializer.deserialize(message);
        } catch (JsonParseException e) {
            // Text that is not JSON has no id the server could quote back, so
            // the error goes out without one, which the schema allows.
            logger.log("[API][SENT] unparseable message (returning -32700): " + e.getMessage());
            error(null, ErrorCodes.PARSE_ERROR, "Parse error: the message is not valid JSON");
            return;
        }
        switch (object) {
            case JsonRpcRequest request -> answer(request);
            case JsonRpcNotification notification -> process(notification);
            case JsonRpcErrorResponse errorResponse -> process(errorResponse);
            case Unknown unknown -> {
                logger.log("[API][SENT] not a JSON-RPC 2.0 message (returning -32600): " + unknown.json());
                error(null, ErrorCodes.INVALID_REQUEST, "Invalid Request: not a JSON-RPC 2.0 message");
            }
            default -> logger.log("[API][RECEIVED] unexpected message, which a client never sends: " + object);
        }
    }

    /**
     * Processes a request so that it is always answered.
     * <p>
     * An exception thrown while handling a request would otherwise reach the
     * transport, which can only log it, and the client would wait for a reply
     * that never comes. Params that do not fit the method's params record are
     * the client's mistake and get {@link ErrorCodes#INVALID_PARAMS}; anything
     * else is this server's and gets {@link ErrorCodes#INTERNAL_ERROR}.
     * </p>
     *
     * @param request the inbound request
     */
    private void answer(JsonRpcRequest request) {
        try {
            process(request);
        } catch (JsonParseException e) {
            logger.log("[API][SENT] " + request.method() + " — params do not fit (returning -32602): "
                       + e.getMessage());
            error(request.id(), ErrorCodes.INVALID_PARAMS, "Invalid params for " + request.method());
        } catch (RuntimeException e) {
            logger.log("[API][SENT] " + request.method() + " failed (returning -32603): " + e);
            error(request.id(), ErrorCodes.INTERNAL_ERROR, "Internal error while handling " + request.method());
        }
    }

    private void success(RequestId id, Object message) {
        JsonRpcResponse response = new JsonRpcResponse(JSON_RPC_VERSION, id, withServerInfo(message));
        io.emit(response);
    }

    /**
     * Adds this server's identity to a result's {@code _meta}.
     * <p>
     * The revision asks for it on every result, because with no connection
     * state a client cannot look it up anywhere else. Any {@code _meta} the
     * result already carries, such as a subscription id, is kept.
     * </p>
     *
     * @param result the result as built by the method's handler
     * @return the same result as a JSON tree, with {@link MetaKeys#SERVER_INFO} in its {@code _meta}
     */
    private static JsonObject withServerInfo(Object result) {
        JsonObject object = GSON.toJsonTree(result).getAsJsonObject();
        JsonObject meta = object.get("_meta") instanceof JsonObject existing ? existing : new JsonObject();
        meta.add(MetaKeys.SERVER_INFO, GSON.toJsonTree(ServerInfo.DEFAULT));
        object.add("_meta", meta);
        return object;
    }

    private void error(RequestId id, int code, String message) {
        error(id, code, message, null);
    }

    private void error(RequestId id, int code, String message, Object data) {
        JsonRpcErrorResponse response = new JsonRpcErrorResponse(
                JSON_RPC_VERSION, id, new JsonRpcError(code, message, data));
        io.emit(response);
    }

    private void process(JsonRpcRequest message) {
        UniqueKeys uniqueKey = UniqueKeys.fromValue(message.method());

        // Method existence is settled before the envelope is looked at.
        // Deletions in this revision are physical: a method absent from the
        // registry is -32601 by absence, and answering a removed method like
        // initialize with a complaint about its _meta would tell a legacy
        // client the wrong thing about why it failed.
        if (uniqueKey == UniqueKeys.NOT_FOUND || !INBOUND_METHODS.contains(uniqueKey)) {
            rejectUnknownMethod(message);
            return;
        }

        // server/discover is answered whatever version it names: a client uses
        // it precisely to find out which versions this server speaks, so
        // rejecting it for asking with the wrong one would be circular. It is
        // still a request, though, so it must carry the required envelope.
        RequestEnvelope envelope = envelopeFor(message, uniqueKey != UniqueKeys.SERVER_DISCOVER);
        if (envelope == null) {
            return;
        }
        if (uniqueKey == UniqueKeys.SERVER_DISCOVER) {
            success(message.id(), discoverResult());
            logger.log("[API][SENT] server/discover — advertised " + RequestEnvelope.SUPPORTED_VERSIONS);
            return;
        }

        switch (uniqueKey) {
            case SUBSCRIPTIONS_LISTEN -> acknowledgeSubscription(message);
        }
    }

    /**
     * Answers a method this server does not have with
     * {@link ErrorCodes#METHOD_NOT_FOUND}.
     * <p>
     * {@code initialize} gets more than the bare code. A client still on the
     * handshake has no way to move forward on its own, so the revision asks a
     * server that speaks only stateless revisions to name them in any error
     * it returns to {@code initialize}: this message may be the only
     * diagnostic the client can show its user.
     * </p>
     *
     * @param message the inbound request
     */
    private void rejectUnknownMethod(JsonRpcRequest message) {
        logger.log("[API][SENT] unsupported method '" + message.method() + "' (returning -32601)");
        if ("initialize".equals(message.method())) {
            error(message.id(), ErrorCodes.METHOD_NOT_FOUND,
                  "Method not found: initialize. This server only supports protocol versions without the "
                  + "initialize handshake: " + String.join(", ", RequestEnvelope.SUPPORTED_VERSIONS),
                  Map.of("supported", RequestEnvelope.SUPPORTED_VERSIONS));
        } else {
            error(message.id(), ErrorCodes.METHOD_NOT_FOUND, "Method not found: " + message.method());
        }
    }

    /**
     * Validates the stateless lifecycle envelope that replaced the handshake.
     * <p>
     * A request missing the protocol version or the client's capabilities
     * cannot be serviced, and one naming a revision this server does not speak
     * must be told which revisions it could use instead — that error is the
     * only way a client learns to renegotiate.
     * </p>
     *
     * @param message        the inbound request
     * @param versionMatters false for {@code server/discover}, which is answered
     *                       whatever version it names
     * @return the validated envelope, or null when an error has already been
     *         emitted and the caller should stop
     */
    private RequestEnvelope envelopeFor(JsonRpcRequest message, boolean versionMatters) {
        RequestEnvelope envelope = deserializer.deserializeEnvelope(message);
        if (!envelope.isComplete()) {
            logger.log("[API][SENT] " + message.method() + " — incomplete _meta envelope (returning -32602)");
            error(message.id(), ErrorCodes.INVALID_PARAMS,
                  "Requests must carry " + MetaKeys.PROTOCOL_VERSION + " and "
                  + MetaKeys.CLIENT_CAPABILITIES + " in params._meta");
            return null;
        }
        if (versionMatters && !envelope.isSupportedVersion()) {
            logger.log("[API][SENT] " + message.method() + " — unsupported protocol version '"
                       + envelope.protocolVersion() + "' (returning -32022)");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("supported", RequestEnvelope.SUPPORTED_VERSIONS);
            data.put("requested", envelope.protocolVersion());
            error(message.id(), ErrorCodes.UNSUPPORTED_PROTOCOL_VERSION, "Unsupported protocol version", data);
            return null;
        }
        return envelope;
    }

    /**
     * Builds the {@code server/discover} answer.
     * <p>
     * A client, or a shared cache, may reuse this answer, so it is derived
     * entirely from static configuration.
     * </p>
     *
     * @return the discovery result
     */
    private DiscoverResult discoverResult() {
        return DiscoverResultBuilder
                .builder()
                .withDefaultCapabilities()
                .withInstructions("Searches a project for a keyword.")
                .withCacheHints(LIST_TTL_MILLIS, CacheScope.PUBLIC)
                .withDefaultServerInfo()
                .build();
    }

    private void process(JsonRpcNotification message) {
        UniqueKeys uniqueKey = UniqueKeys.fromValue(message.method());
        switch (uniqueKey) {
            case NOTIFICATION_CANCELLED -> {
                NotificationCancelledParams params = deserializer.deserializeParams(message,
                                                                                    NotificationCancelledParams.class);
                // There is never an in-flight request to abandon: this server
                // answers every request before returning, and its one
                // long-lived request, subscriptions/listen, is closed as it is
                // acknowledged. So the cancellation is recorded and nothing else.
                logger.log("[API][RECEIVED] notifications/cancelled requestId=" + params.requestId()
                           + " reason=" + params.reason());
            }
            default -> logger.log("[API][RECEIVED] unhandled notification method: " + message.method()
                                  + " for message: " + message);
        }
    }

    private void process(JsonRpcErrorResponse message) {
        logger.log("[API][RECEIVED] error from client: " + message);
    }

    /**
     * Opens a {@code subscriptions/listen} stream, and closes it again.
     * <p>
     * A stream lasts as long as its listen request is unanswered: on stdio the
     * request stays open for the life of the subscription, while notifications
     * are interleaved on stdout and demultiplexed by
     * {@link MetaKeys#SUBSCRIPTION_ID}. So the subscription is acknowledged
     * with a notification, not a result, and that acknowledgment must carry
     * the subscription id — a client that does not find it waits forever, with
     * no error and no timeout.
     * </p>
     * <p>
     * The acknowledgment reports the intersection of what the client asked for
     * and what this server advertised, so the client is never left waiting on
     * a notification that will not come. This server advertises no
     * {@code listChanged} support and no resource subscriptions — its tool,
     * prompt, and resource lists are fixed — so that intersection is always
     * empty and the stream is closed as soon as it is acknowledged rather than
     * held open to deliver nothing. A server with something to say would keep
     * the id and answer later instead.
     * </p>
     * <p>
     * The close is an ordinary {@code complete} result for the listen request,
     * and the one place in this revision where a {@code _meta} member is
     * genuinely required: it must carry the subscription id, because closing
     * without it reads to the client as a dropped connection.
     * </p>
     */
    private void acknowledgeSubscription(JsonRpcRequest message) {
        SubscriptionsListenParams params = deserializer.deserializeParams(message,
                                                                         SubscriptionsListenParams.class);
        SubscriptionFilter requested = params.notifications() == null
                ? new SubscriptionFilter(null, null, null, null)
                : params.notifications();
        SubscriptionFilter honored = requested.honoredUnder(discoverResult().capabilities());

        logger.log("[API][SENT] subscriptions/listen — acknowledging id=" + message.id()
                   + " honored=" + honored);
        io.emit(new JsonRpcNotification(
                JSON_RPC_VERSION,
                UniqueKeys.NOTIFICATIONS_SUBSCRIPTIONS_ACKNOWLEDGED.getValue(),
                SubscriptionsAcknowledgedParams.of(message.id(), honored)));

        logger.log("[API][SENT] subscriptions/listen — nothing honored, closing immediately");
        success(message.id(), SubscriptionsListenResult.closing(message.id()));
    }
}
