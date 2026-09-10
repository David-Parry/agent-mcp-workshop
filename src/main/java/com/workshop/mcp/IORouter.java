package com.workshop.mcp;

import com.workshop.mcp.io.IOHandler;
import com.workshop.mcp.io.LogFile;
import com.workshop.mcp.io.LogFileWriter;
import com.workshop.mcp.spec.*;
import com.workshop.mcp.spec.builders.*;

import java.util.EnumSet;
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
 * this server used to ask for are embedded in an {@link InputRequiredResult}
 * instead.
 * </p>
 *
 * @see RequestEnvelope
 * @see InputRequiredResult
 * @since 1.0
 */
public class IORouter implements Router {
    private static final LogFile logger = LogFileWriter.getInstance();
    private static final String JSON_RPC_VERSION = "2.0";
    private static final long LIST_TTL_MILLIS = 60_000L;



    /**
     * The methods this server accepts as inbound requests.
     * <p>
     * The revision also defines three methods that only ever appear embedded
     * in an {@link InputRequiredResult} — {@code roots/list},
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

    private final IOHandler io;
    private final JsonRpcMessageDeserializer deserializer = new JsonRpcMessageDeserializer();
    public IORouter(IOHandler io) {
        this.io = io;
    }

    public void route(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        Object object = deserializer.deserialize(message);
        switch (object) {
            case JsonRpcRequest request -> process(request);
            case JsonRpcNotification notification -> process(notification);
            case JsonRpcErrorResponse errorResponse -> process(errorResponse);
            default -> logger.log("[API][RECEIVED] unknown message type: " + object);
        }
    }

    private void success(RequestId id, Object message) {
        JsonRpcResponse response = new JsonRpcResponse(JSON_RPC_VERSION, id, message);
        io.emit(response);
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
        // Chapter 03: before anything is routed, answer
        // server/discover, reject a method this revision removed,
        // and validate the params._meta envelope. Declare the
        // uniqueKey and envelope the switch below needs.
        UniqueKeys uniqueKey = UniqueKeys.fromValue(message.method());
        RequestEnvelope envelope = deserializer.deserializeEnvelope(message);

        switch (uniqueKey) {




            case SUBSCRIPTIONS_LISTEN -> acknowledgeSubscription(message);
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
     * @param message the inbound request
     * @return the validated envelope, or null when an error has already been
     *         emitted and the caller should stop
     */
    private RequestEnvelope envelopeFor(JsonRpcRequest message) {
        // Chapter 03: implement envelopeFor(...).
        throw new UnsupportedOperationException(
                "Chapter 03: envelopeFor(...) is not implemented yet");
    }

    private DiscoverResult discoverResult() {
        // Chapter 03: implement discoverResult(...).
        throw new UnsupportedOperationException(
                "Chapter 03: discoverResult(...) is not implemented yet");
    }












    private void process(JsonRpcNotification message) {
        // Chapter 03: deserialize a cancellation's params
        // into NotificationCancelledParams and log it; ignore any
        // other notification rather than rejecting it.
    }

    private void process(JsonRpcErrorResponse message) {
        logger.log("[API][RECEIVED] error from client: " + message);
    }

    /**
     * Opens a {@code subscriptions/listen} stream.
     * <p>
     * Note what is not sent here: a JSON-RPC result. On stdio the listen
     * request stays open and unanswered for the life of the subscription,
     * while notifications are interleaved on stdout and demultiplexed by
     * {@link MetaKeys#SUBSCRIPTION_ID}. The subscription is acknowledged with
     * a notification instead, and that acknowledgment must carry the
     * subscription id — a client that does not find it waits forever, with no
     * error and no timeout.
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
     * The close is the one place in this revision where a {@code _meta} member
     * is genuinely required: it must carry the subscription id, because
     * closing without it reads to the client as a dropped connection.
     * </p>
     */
    private void acknowledgeSubscription(JsonRpcRequest message) {
        // Chapter 03: implement acknowledgeSubscription(...).
    }





}
