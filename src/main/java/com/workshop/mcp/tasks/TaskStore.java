package com.workshop.mcp.tasks;

import com.workshop.mcp.spec.ErrorCodes;
import com.workshop.mcp.spec.InputRequest;
import com.workshop.mcp.spec.JsonRpcError;
import com.workshop.mcp.spec.Task;
import com.workshop.mcp.spec.TaskResult;
import com.workshop.mcp.spec.TaskStatus;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-memory store for {@link Task} state and the payloads tasks produce. Kept
 * framework-free (no executor pool, no DB) to match the workshop's "raw
 * protocol, plain Java" pedagogical style.
 *
 * <p>Thread-safety: a {@link ConcurrentHashMap} backs the per-task entries
 * and each entry's mutable state is guarded by a small object monitor
 * (synchronized on the entry itself). Callbacks registered via
 * {@link #onStatusChange(Consumer)} fire after the entry's state has been
 * updated, outside the lock, so they cannot deadlock back into the store.</p>
 *
 * <p>Task IDs are randomly generated UUIDs — the extension requires them to be
 * unique among all tasks controlled by this receiver and unguessable when
 * authorization is unavailable. The workshop server is stdio-only with no
 * authorization, so the UUID's entropy is the only access control. That
 * unguessability carries more weight now that protocol sessions are gone:
 * there is no session to scope a task to, which is also why the extension
 * dropped {@code tasks/list} — a listing would leak other callers' task
 * ids.</p>
 *
 * @since 1.0
 */
public class TaskStore {

    /** Default suggested polling interval if none is supplied. */
    public static final long DEFAULT_POLL_INTERVAL_MILLIS = 1000L;

    /** Hard ceiling on TTL — receivers MAY override the requested TTL. */
    public static final long MAX_TTL_MILLIS = 5L * 60_000L;

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<TaskResult>> statusListeners = new CopyOnWriteArrayList<>();

    /**
     * Register a callback invoked every time a task's status changes.
     * Used by the router to emit {@code notifications/tasks}.
     *
     * @param listener invoked with the post-change notification projection
     */
    public void onStatusChange(Consumer<TaskResult> listener) {
        statusListeners.add(listener);
    }

    /**
     * Create a new task in {@link TaskStatus#WORKING WORKING} status.
     *
     * @param requestedTtlMillis a requested TTL; may be {@code null}. The
     *                           store may override it.
     * @return a snapshot of the freshly created task
     */
    public Task create(Long requestedTtlMillis) {
        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        Entry entry = new Entry(
            id,
            TaskStatus.WORKING.getValue(),
            "The operation is now in progress.",
            now,
            now,
            clampTtl(requestedTtlMillis),
            DEFAULT_POLL_INTERVAL_MILLIS
        );
        entries.put(id, entry);
        Task snapshot = entry.snapshot();
        fireStatusChange(entry, snapshot);
        return snapshot;
    }

    /**
     * @param taskId task identifier
     * @return the current task snapshot, or {@code null} if no such task
     */
    public Task get(String taskId) {
        Entry entry = entries.get(taskId);
        if (entry == null) {
            return null;
        }
        synchronized (entry) {
            return entry.snapshot();
        }
    }

    /**
     * Projects a task for {@code tasks/get}, including the payload its status
     * calls for.
     *
     * @param taskId task identifier
     * @return the detailed snapshot, or {@code null} if no such task
     */
    public TaskResult detail(String taskId) {
        Entry entry = entries.get(taskId);
        if (entry == null) {
            return null;
        }
        synchronized (entry) {
            return TaskResult.detail(entry.snapshot(), entry.result, entry.error, entry.inputRequests);
        }
    }

    /**
     * Record successful completion of the underlying request.
     *
     * @param taskId target task
     * @param result the payload that the original request would have returned
     */
    public void complete(String taskId, Object result) {
        Entry entry = entries.get(taskId);
        if (entry == null) {
            return;
        }
        Task snapshot;
        synchronized (entry) {
            if (entry.isTerminal()) {
                return;
            }
            entry.status = TaskStatus.COMPLETED.getValue();
            entry.statusMessage = "The operation completed successfully.";
            entry.lastUpdatedAt = Instant.now().toString();
            entry.result = result;
            entry.inputRequests = null;
            snapshot = entry.snapshot();
        }
        fireStatusChange(entry, snapshot);
    }

    /**
     * Record failure of the underlying request.
     *
     * @param taskId target task
     * @param reason human-readable failure message, surfaced both as
     *               {@code statusMessage} and as the {@code error} payload
     */
    public void fail(String taskId, String reason) {
        Entry entry = entries.get(taskId);
        if (entry == null) {
            return;
        }
        Task snapshot;
        synchronized (entry) {
            if (entry.isTerminal()) {
                return;
            }
            entry.status = TaskStatus.FAILED.getValue();
            entry.statusMessage = reason;
            entry.lastUpdatedAt = Instant.now().toString();
            entry.error = new JsonRpcError(ErrorCodes.INTERNAL_ERROR, reason, null);
            entry.inputRequests = null;
            snapshot = entry.snapshot();
        }
        fireStatusChange(entry, snapshot);
    }

    /**
     * Move the task into {@link TaskStatus#INPUT_REQUIRED INPUT_REQUIRED},
     * publishing what it needs so a client polling {@code tasks/get} can see
     * it and answer with {@code tasks/update}.
     *
     * @param taskId        target task
     * @param inputRequests what the task needs, keyed by identifier
     * @return the post-transition snapshot, or {@code null} if the task does
     *         not exist or is already terminal
     */
    public Task requireInput(String taskId, Map<String, InputRequest> inputRequests) {
        Entry entry = entries.get(taskId);
        if (entry == null) {
            return null;
        }
        Task snapshot;
        synchronized (entry) {
            if (entry.isTerminal()) {
                return null;
            }
            entry.status = TaskStatus.INPUT_REQUIRED.getValue();
            entry.statusMessage = "The operation is waiting for input.";
            entry.lastUpdatedAt = Instant.now().toString();
            entry.inputRequests = inputRequests;
            snapshot = entry.snapshot();
        }
        fireStatusChange(entry, snapshot);
        return snapshot;
    }

    /**
     * Deliver {@code tasks/update} input to a task that asked for it, moving
     * it back to {@link TaskStatus#WORKING WORKING}.
     * <p>
     * Only answers the task actually asked for are retained; anything else is
     * ignored, since the keys are chosen by this server and must not be
     * reused across a task's lifetime.
     * </p>
     *
     * @param taskId         target task
     * @param inputResponses the answers, keyed by the identifiers the task asked under
     * @return the post-transition snapshot, or {@code null} if the task does
     *         not exist or was not waiting for input
     */
    public Task applyInput(String taskId, Map<String, Object> inputResponses) {
        Entry entry = entries.get(taskId);
        if (entry == null) {
            return null;
        }
        Task snapshot;
        synchronized (entry) {
            if (!TaskStatus.INPUT_REQUIRED.getValue().equals(entry.status)) {
                return null;
            }
            Map<String, Object> accepted = new HashMap<>();
            if (inputResponses != null && entry.inputRequests != null) {
                for (String key : entry.inputRequests.keySet()) {
                    Object answer = inputResponses.get(key);
                    if (answer != null) {
                        accepted.put(key, answer);
                    }
                }
            }
            entry.status = TaskStatus.WORKING.getValue();
            entry.statusMessage = "The operation resumed with the input provided.";
            entry.lastUpdatedAt = Instant.now().toString();
            entry.inputRequests = null;
            entry.inputResponses = accepted;
            snapshot = entry.snapshot();
        }
        fireStatusChange(entry, snapshot);
        return snapshot;
    }

    /**
     * Returns the input a task received through {@code tasks/update}, so the
     * background work can pick it up and continue.
     *
     * @param taskId target task
     * @return the accepted answers, or an empty map when there are none
     */
    public Map<String, Object> inputResponsesFor(String taskId) {
        Entry entry = entries.get(taskId);
        if (entry == null) {
            return Map.of();
        }
        synchronized (entry) {
            return entry.inputResponses == null ? Map.of() : Map.copyOf(entry.inputResponses);
        }
    }

    /**
     * Move the task to {@link TaskStatus#CANCELLED CANCELLED}. Returns
     * {@code null} if the task does not exist or is already in a terminal
     * state — callers should treat {@code null} as the {@code -32602}
     * Invalid params condition.
     *
     * @param taskId target task
     * @return the post-cancellation snapshot, or {@code null} if the
     *         cancellation request is invalid
     */
    public Task cancel(String taskId) {
        Entry entry = entries.get(taskId);
        if (entry == null) {
            return null;
        }
        Task snapshot;
        synchronized (entry) {
            if (entry.isTerminal()) {
                return null;
            }
            entry.status = TaskStatus.CANCELLED.getValue();
            entry.statusMessage = "The task was cancelled by request.";
            entry.lastUpdatedAt = Instant.now().toString();
            entry.inputRequests = null;
            snapshot = entry.snapshot();
        }
        fireStatusChange(entry, snapshot);
        return snapshot;
    }

    private void fireStatusChange(Entry entry, Task snapshot) {
        Object result;
        JsonRpcError error;
        synchronized (entry) {
            result = entry.result;
            error = entry.error;
        }
        TaskResult projection = TaskResult.notification(snapshot, result, error);
        for (Consumer<TaskResult> listener : statusListeners) {
            listener.accept(projection);
        }
    }

    private static long clampTtl(Long requested) {
        if (requested == null || requested <= 0) {
            return MAX_TTL_MILLIS;
        }
        return Math.min(requested, MAX_TTL_MILLIS);
    }

    /** Mutable per-task record. Access guarded by {@code synchronized (this)}. */
    private static final class Entry {
        final String taskId;
        String status;
        String statusMessage;
        final String createdAt;
        String lastUpdatedAt;
        final Long ttlMs;
        final Long pollIntervalMs;
        Object result;
        JsonRpcError error;
        Map<String, InputRequest> inputRequests;
        Map<String, Object> inputResponses;

        Entry(String taskId, String status, String statusMessage,
              String createdAt, String lastUpdatedAt,
              Long ttlMs, Long pollIntervalMs) {
            this.taskId = taskId;
            this.status = status;
            this.statusMessage = statusMessage;
            this.createdAt = createdAt;
            this.lastUpdatedAt = lastUpdatedAt;
            this.ttlMs = ttlMs;
            this.pollIntervalMs = pollIntervalMs;
        }

        boolean isTerminal() {
            TaskStatus current = TaskStatus.fromValue(status);
            return current != null && current.isTerminal();
        }

        Task snapshot() {
            return new Task(taskId, status, statusMessage, createdAt, lastUpdatedAt, ttlMs, pollIntervalMs);
        }
    }
}
