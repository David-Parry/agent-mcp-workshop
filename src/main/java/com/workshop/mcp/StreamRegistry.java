package com.workshop.mcp;

import com.workshop.mcp.spec.RequestId;
import com.workshop.mcp.spec.SubscriptionFilter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code subscriptions/listen} streams this server is holding open.
 * <p>
 * A stream lasts exactly as long as its listen request is unanswered, so
 * holding one open means remembering the request's id and answering it later.
 * The id is also the subscription id every notification on the stream
 * carries in its {@code _meta}, which is how the client tells streams apart
 * on a single stdout.
 * </p>
 * <p>
 * A stream ends in one of two ways. The client cancels its listen request,
 * and the server forgets the stream without answering, because a cancelled
 * request is never answered. Or the server ends it, and answers the listen
 * request with a closing result.
 * </p>
 */
final class StreamRegistry {

    private final Map<RequestId, SubscriptionFilter> streams = new ConcurrentHashMap<>();

    /**
     * Holds a stream open for what was agreed in its acknowledgement.
     *
     * @param id      the listen request's id, which is also the subscription id
     * @param honored the notifications the server agreed to send on it
     */
    void open(RequestId id, SubscriptionFilter honored) {
        streams.put(id, honored);
    }

    /**
     * Forgets a stream the client cancelled.
     *
     * @param id the cancelled request's id, which may name no stream at all
     * @return true when it named an open stream
     */
    boolean forget(RequestId id) {
        return id != null && streams.remove(id) != null;
    }

    /**
     * @return the streams that asked to hear when the tool list changes
     */
    List<RequestId> listeningForToolListChanges() {
        return streams.entrySet().stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getValue().toolsListChanged()))
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Removes every stream, for a server that is shutting down and must close
     * each one.
     *
     * @return the ids of the streams that were open
     */
    List<RequestId> closeAll() {
        List<RequestId> open = List.copyOf(streams.keySet());
        open.forEach(streams::remove);
        return open;
    }
}
