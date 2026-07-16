package com.workshop.mcp.tasks;

import com.workshop.mcp.spec.Task;
import com.workshop.mcp.spec.TaskStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-memory store for {@link Task} state and underlying results. Kept
 * framework-free (no executor pool, no DB) to match the workshop's "raw
 * protocol, plain Java" pedagogical style.
 *
 * <p>Thread-safety: a {@link ConcurrentHashMap} backs the per-task entries
 * and each entry's mutable state is guarded by a small object monitor
 * (synchronized on the entry itself). Callbacks registered via
 * {@link #onStatusChange(Consumer)} fire after the entry's state has been
 * updated, outside the lock, so they cannot deadlock back into the store.</p>
 *
 * <p>Task IDs are randomly generated UUIDs — the spec requires them to be
 * unique among all tasks controlled by this receiver and unguessable when
 * authorization is unavailable. The workshop server is stdio-only with no
 * authorization, so the UUID's entropy is the only access control.</p>
 *
 * @since 1.0
 */
public class TaskStore {

    /** Default suggested polling interval if none is supplied. */
    public static final long DEFAULT_POLL_INTERVAL_MILLIS = 1000L;

    /** Hard ceiling on TTL — receivers MAY override the requested TTL per spec. */
    public static final long MAX_TTL_MILLIS = 5L * 60_000L;

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final List<Consumer<Task>> statusListeners = new CopyOnWriteArrayList<>();
    private final List<PendingResult> pendingResultDeliveries = new CopyOnWriteArrayList<>();

    /**
     * Register a callback to be invoked every time a task's status changes.
     * Used by the router to emit {@code notifications/tasks/status}.
     *
     * @param listener invoked with the post-change {@link Task} snapshot
     */
    public void onStatusChange(Consumer<Task> listener) {
        statusListeners.add(listener);
    }

    /**
     * Register a one-shot callback to be invoked when {@code taskId} reaches a
     * terminal status. If the task is already terminal, the callback fires
     * immediately on the calling thread. Used by the router to implement the
     * blocking semantics of {@code tasks/result} without blocking the routing
     * thread itself.
     *
     * @param taskId   task whose terminal state we are waiting for
     * @param callback receives the terminal {@link Task} snapshot
     */
    public void awaitTerminal(String taskId, Consumer<Task> callback) {
        Entry entry = entries.get(taskId);
        if (entry == null) {
            callback.accept(null);
            return;
        }
        Task snapshot;
        boolean terminal;
        synchronized (entry) {
            snapshot = entry.snapshot();
            terminal = TaskStatus.fromValue(snapshot.status()).isTerminal();
            if (!terminal) {
                pendingResultDeliveries.add(new PendingResult(taskId, callback));
                return;
            }
        }
        callback.accept(snapshot);
    }

    /**
     * Create a new task in {@link TaskStatus#WORKING WORKING} status.
     *
     * @param requestedTtlMillis client-requested TTL; may be {@code null}.
     *                           The store may override it (spec § TTL).
     * @return a snapshot of the freshly created task
     */
    public Task create(Long requestedTtlMillis) {
        String id = UUID.randomUUID().toString();
        long ttl = clampTtl(requestedTtlMillis);
        String now = Instant.now().toString();
        Entry entry = new Entry(
            id,
            TaskStatus.WORKING.getValue(),
            "The operation is now in progress.",
            now,
            now,
            ttl,
            DEFAULT_POLL_INTERVAL_MILLIS,
            null
        );
        entries.put(id, entry);
        Task snapshot = entry.snapshot();
        fireStatusChange(snapshot);
        return snapshot;
    }

    /**
     * @param taskId task identifier
     * @return the current task snapshot, or {@code null} if no such task
     */
    public Task get(String taskId) {
        Entry entry = entries.get(taskId);
        if (entry == null) return null;
        synchronized (entry) {
            return entry.snapshot();
        }
    }

    /**
     * @return snapshots of every task currently in the store
     */
    public List<Task> list() {
        List<Task> out = new ArrayList<>(entries.size());
        for (Entry entry : entries.values()) {
            synchronized (entry) {
                out.add(entry.snapshot());
            }
        }
        return out;
    }

    /**
     * @return {@code true} if the underlying request payload (success result
     *         or JSON-RPC error) has been stored for {@code taskId}
     */
    public boolean isTerminal(String taskId) {
        Entry entry = entries.get(taskId);
        if (entry == null) return false;
        synchronized (entry) {
            return TaskStatus.fromValue(entry.status).isTerminal();
        }
    }

    /**
     * @return the stored underlying-request result for a completed task, or
     *         {@code null} if no result has been recorded (task may be
     *         cancelled or not yet finished)
     */
    public Object resultFor(String taskId) {
        Entry entry = entries.get(taskId);
        if (entry == null) return null;
        synchronized (entry) {
            return entry.result;
        }
    }

    /**
     * Record successful completion of the underlying request.
     *
     * @param taskId target task
     * @param result the payload that the original request would have returned
     */
    public void complete(String taskId, Object result) {
        Task snapshot = transition(taskId, TaskStatus.COMPLETED, "The operation completed successfully.", result);
        if (snapshot != null) {
            fireStatusChange(snapshot);
            drainPending(taskId, snapshot);
        }
    }

    /**
     * Record failure of the underlying request.
     *
     * @param taskId target task
     * @param reason human-readable failure message — surfaced via
     *               {@code statusMessage}
     */
    public void fail(String taskId, String reason) {
        Task snapshot = transition(taskId, TaskStatus.FAILED, reason, null);
        if (snapshot != null) {
            fireStatusChange(snapshot);
            drainPending(taskId, snapshot);
        }
    }

    /**
     * Move the task to {@link TaskStatus#CANCELLED CANCELLED}. Returns
     * {@code null} if the task does not exist or is already in a terminal
     * state — callers should treat {@code null} as the {@code -32602}
     * Invalid params condition required by the spec.
     *
     * @param taskId target task
     * @return the post-cancellation snapshot, or {@code null} if the
     *         cancellation request is invalid
     */
    public Task cancel(String taskId) {
        Entry entry = entries.get(taskId);
        if (entry == null) return null;
        Task snapshot;
        synchronized (entry) {
            TaskStatus current = TaskStatus.fromValue(entry.status);
            if (current.isTerminal()) {
                return null;
            }
            entry.status = TaskStatus.CANCELLED.getValue();
            entry.statusMessage = "The task was cancelled by request.";
            entry.lastUpdatedAt = Instant.now().toString();
            snapshot = entry.snapshot();
        }
        fireStatusChange(snapshot);
        drainPending(taskId, snapshot);
        return snapshot;
    }

    private Task transition(String taskId, TaskStatus next, String message, Object result) {
        Entry entry = entries.get(taskId);
        if (entry == null) return null;
        synchronized (entry) {
            TaskStatus current = TaskStatus.fromValue(entry.status);
            if (current.isTerminal()) {
                return null;
            }
            entry.status = next.getValue();
            entry.statusMessage = message;
            entry.lastUpdatedAt = Instant.now().toString();
            if (result != null) {
                entry.result = result;
            }
            return entry.snapshot();
        }
    }

    private void fireStatusChange(Task snapshot) {
        for (Consumer<Task> listener : statusListeners) {
            listener.accept(snapshot);
        }
    }

    private void drainPending(String taskId, Task terminalSnapshot) {
        List<PendingResult> matches = new ArrayList<>();
        for (PendingResult pending : pendingResultDeliveries) {
            if (pending.taskId.equals(taskId)) {
                matches.add(pending);
            }
        }
        pendingResultDeliveries.removeAll(matches);
        for (PendingResult pending : matches) {
            pending.callback.accept(terminalSnapshot);
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
        final Long ttl;
        final Long pollInterval;
        Object result;

        Entry(String taskId, String status, String statusMessage,
              String createdAt, String lastUpdatedAt,
              Long ttl, Long pollInterval, Object result) {
            this.taskId = taskId;
            this.status = status;
            this.statusMessage = statusMessage;
            this.createdAt = createdAt;
            this.lastUpdatedAt = lastUpdatedAt;
            this.ttl = ttl;
            this.pollInterval = pollInterval;
            this.result = result;
        }

        Task snapshot() {
            return new Task(taskId, status, statusMessage, createdAt, lastUpdatedAt, ttl, pollInterval);
        }
    }

    private record PendingResult(String taskId, Consumer<Task> callback) {}
}
