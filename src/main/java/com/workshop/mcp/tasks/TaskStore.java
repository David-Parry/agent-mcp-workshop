package com.workshop.mcp.tasks;

import com.workshop.mcp.spec.ErrorCodes;
import com.workshop.mcp.spec.InputRequest;
import com.workshop.mcp.spec.JsonRpcError;
import com.workshop.mcp.spec.Task;
import com.workshop.mcp.spec.TaskResult;
import com.workshop.mcp.spec.TaskStatus;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * In-memory store for {@link Task} state and the payloads tasks produce. Kept
 * framework-free (no executor pool, no DB) to match the workshop's "raw
 * protocol, plain Java" pedagogical style.
 *
 * <p>Thread-safety: a {@link ConcurrentHashMap} backs the per-task entries
 * and each entry's mutable state is guarded by a small object monitor
 * (synchronized on the entry itself). Each change also publishes a snapshot
 * of the task, which {@link #get(String)} and {@link #detail(String)} read
 * without taking the monitor.</p>
 *
 * <p>Nothing here outlives the process. A task is kept for its {@code ttlMs}
 * from when it was created. Once that has passed its id is answered like one
 * that was never issued, and the next change or creation to reach the store
 * purges it, failing it first if it was still running. The extension lets a
 * server mark an expired task failed and later delete it, and this store does
 * both at once. A server that had to survive a restart would keep these
 * entries somewhere durable, so that a client holding a task id could go on
 * polling it.</p>
 *
 * <p>Every change to a task is reported to the listener the store was made
 * with, carrying what {@link #detail(String)} answers from then on, so a
 * caller can push the change to a client that subscribed to it.</p>
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

    /**
     * How long every task is advertised to live. The TTL is the server's
     * choice alone: the extension gives a client no way to ask for one.
     */
    public static final long TTL_MILLIS = 5L * 60_000L;

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Consumer<TaskResult> listener;

    /**
     * Creates an empty store.
     *
     * @param clock    where task times are read from, and so when a task expires
     * @param listener told of every change to a task while the task's monitor
     *                 is held, so that one task's changes arrive in the order
     *                 they happened. It may read the store but must not change it.
     */
    public TaskStore(Clock clock, Consumer<TaskResult> listener) {
        this.clock = clock;
        this.listener = listener;
    }

    /**
     * Create a new task in {@link TaskStatus#WORKING WORKING} status.
     *
     * @return a snapshot of the freshly created task
     */
    public Task create() {
        purgeExpired();
        String id = UUID.randomUUID().toString();
        long now = clock.millis();
        String nowText = clock.instant().toString();
        Entry entry = new Entry(
            id,
            TaskStatus.WORKING.getValue(),
            "The operation is now in progress.",
            nowText,
            nowText,
            TTL_MILLIS,
            DEFAULT_POLL_INTERVAL_MILLIS,
            now + TTL_MILLIS
        );
        entries.put(id, entry);
        return entry.published.task();
    }

    /**
     * @param taskId task identifier
     * @return the current task snapshot, or {@code null} if no such task
     */
    public Task get(String taskId) {
        Published current = published(taskId);
        return current == null ? null : current.task();
    }

    /**
     * Projects a task for {@code tasks/get}, including the payload its status
     * calls for.
     *
     * @param taskId task identifier
     * @return the detailed snapshot, or {@code null} if no such task
     */
    public TaskResult detail(String taskId) {
        Published current = published(taskId);
        return current == null ? null : current.detail();
    }

    /**
     * Record successful completion of the underlying request.
     *
     * @param taskId target task
     * @param result the payload that the original request would have returned
     */
    public void complete(String taskId, Object result) {
        Entry entry = live(taskId);
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            if (entry.isTerminal()) {
                return;
            }
            entry.status = TaskStatus.COMPLETED.getValue();
            entry.statusMessage = "The operation completed successfully.";
            entry.lastUpdatedAt = clock.instant().toString();
            entry.result = result;
            entry.inputRequests = null;
            entry.notifyAll();
            entry.publish();
        }
    }

    /**
     * Record failure of the underlying request.
     *
     * @param taskId target task
     * @param reason human-readable failure message, surfaced both as
     *               {@code statusMessage} and as the {@code error} payload
     */
    public void fail(String taskId, String reason) {
        Entry entry = live(taskId);
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            if (entry.isTerminal()) {
                return;
            }
            entry.failWith(reason);
        }
    }

    /**
     * Changes what a working task says about itself, without changing its
     * status.
     * <p>
     * The extension rules out {@code notifications/progress} for a task, so
     * progress goes in the status message, which a client reads on every
     * poll. {@code lastUpdatedAt} is left alone, because it marks status
     * changes and this is not one. A task that is not working keeps its
     * message: input_required and every terminal status say something more
     * important.
     * </p>
     *
     * @param taskId  target task
     * @param message what the task is doing, e.g. {@code "Searched 120 of 300 files"}
     */
    public void updateStatusMessage(String taskId, String message) {
        Entry entry = live(taskId);
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            if (!TaskStatus.WORKING.getValue().equals(entry.status)) {
                return;
            }
            entry.statusMessage = message;
            entry.publish();
        }
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
        Entry entry = live(taskId);
        if (entry == null) {
            return null;
        }
        synchronized (entry) {
            if (entry.isTerminal()) {
                return null;
            }
            entry.status = TaskStatus.INPUT_REQUIRED.getValue();
            entry.statusMessage = "The operation is waiting for input.";
            entry.lastUpdatedAt = clock.instant().toString();
            entry.inputRequests = inputRequests;
            entry.publish();
            return entry.published.task();
        }
    }

    /**
     * Deliver {@code tasks/update} input to a task, moving it back to
     * {@link TaskStatus#WORKING WORKING} once it has an answer to what it
     * asked.
     * <p>
     * Anything else is ignored rather than refused, as the extension asks of
     * a server: answers under keys the task never asked for, and every answer
     * when the task is not waiting at all, as it may no longer be by the time
     * an update arrives. An update with nothing usable in it leaves a waiting
     * task waiting.
     * </p>
     *
     * @param taskId         target task
     * @param inputResponses the answers, keyed by the identifiers the task asked under
     * @return the task as it stands after the update, or {@code null} if
     *         there is no such task
     */
    public Task applyInput(String taskId, Map<String, Object> inputResponses) {
        Entry entry = live(taskId);
        if (entry == null) {
            return null;
        }
        synchronized (entry) {
            Map<String, Object> accepted = new HashMap<>();
            if (TaskStatus.INPUT_REQUIRED.getValue().equals(entry.status)
                && inputResponses != null && entry.inputRequests != null) {
                for (String key : entry.inputRequests.keySet()) {
                    Object answer = inputResponses.get(key);
                    if (answer != null) {
                        accepted.put(key, answer);
                    }
                }
            }
            if (!accepted.isEmpty()) {
                entry.status = TaskStatus.WORKING.getValue();
                entry.statusMessage = "The operation resumed with the input provided.";
                entry.lastUpdatedAt = clock.instant().toString();
                entry.inputRequests = null;
                entry.inputResponses = accepted;
                entry.notifyAll();
                entry.publish();
            }
            return entry.published.task();
        }
    }

    /**
     * Blocks the task's own background work until the client answers what
     * {@link #requireInput} asked for.
     * <p>
     * This is the half of task-level input that lives on the server's side of
     * the round trip. {@code requireInput} publishes the question and returns
     * immediately, because the thread that publishes it is answering a
     * {@code tasks/get} poll and must not be held; the work itself then waits
     * here until a {@code tasks/update} arrives on another thread and calls
     * {@link #applyInput}.
     * </p>
     * <p>
     * A {@code null} return means the task is not going to get an answer, and
     * the two reasons are worth telling apart: the task may have gone terminal
     * while waiting (a {@code tasks/cancel}), in which case there is nothing
     * left to do, or the wait may simply have run out, in which case the work
     * is free to carry on without the input. Callers distinguish them by
     * re-reading the status.
     * </p>
     *
     * @param taskId        target task
     * @param timeoutMillis how long to wait for an answer
     * @return the accepted answers, or {@code null} if the task went terminal
     *         or the wait expired
     */
    public Map<String, Object> awaitInput(String taskId, long timeoutMillis) {
        Entry entry = live(taskId);
        if (entry == null) {
            return null;
        }
        long deadline = System.currentTimeMillis() + timeoutMillis;
        synchronized (entry) {
            while (TaskStatus.INPUT_REQUIRED.getValue().equals(entry.status)) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                try {
                    entry.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            if (entry.isTerminal()) {
                return null;
            }
            return entry.inputResponses == null ? Map.of() : Map.copyOf(entry.inputResponses);
        }
    }

    /**
     * Move the task to {@link TaskStatus#CANCELLED CANCELLED}, unless it has
     * already finished.
     * <p>
     * A cancel is a request the server may decline, and a finished task
     * declines it, because a terminal status never changes. The caller
     * acknowledges the request either way, and the client learns the outcome
     * by polling.
     * </p>
     *
     * @param taskId target task
     * @return the task as it stands after the request, or {@code null} if
     *         there is no such task
     */
    public Task cancel(String taskId) {
        Entry entry = live(taskId);
        if (entry == null) {
            return null;
        }
        synchronized (entry) {
            if (!entry.isTerminal()) {
                entry.status = TaskStatus.CANCELLED.getValue();
                entry.statusMessage = "The task was cancelled by request.";
                entry.lastUpdatedAt = clock.instant().toString();
                entry.inputRequests = null;
                entry.notifyAll();
                entry.publish();
            }
            return entry.published.task();
        }
    }

    /** What a task last published, unless its time is up. Takes no monitor. */
    private Published published(String taskId) {
        Entry entry = entries.get(taskId);
        return entry == null || entry.hasExpired(clock.millis()) ? null : entry.published;
    }

    /** Purges what has expired, then looks the task up. */
    private Entry live(String taskId) {
        purgeExpired();
        return entries.get(taskId);
    }

    private void purgeExpired() {
        long now = clock.millis();
        entries.values().removeIf(entry -> entry.retireIfExpired(now));
    }

    /** A task as it stood after one change, in both of the shapes callers read. */
    private record Published(Task task, TaskResult detail) {
    }

    /** Mutable per-task record. Access guarded by {@code synchronized (this)}. */
    private final class Entry {
        final String taskId;
        String status;
        String statusMessage;
        final String createdAt;
        String lastUpdatedAt;
        final Long ttlMs;
        final Long pollIntervalMs;
        final long expiresAtMillis;
        Object result;
        JsonRpcError error;
        Map<String, InputRequest> inputRequests;
        Map<String, Object> inputResponses;
        volatile Published published;

        Entry(String taskId, String status, String statusMessage,
              String createdAt, String lastUpdatedAt,
              Long ttlMs, Long pollIntervalMs, long expiresAtMillis) {
            this.taskId = taskId;
            this.status = status;
            this.statusMessage = statusMessage;
            this.createdAt = createdAt;
            this.lastUpdatedAt = lastUpdatedAt;
            this.ttlMs = ttlMs;
            this.pollIntervalMs = pollIntervalMs;
            this.expiresAtMillis = expiresAtMillis;
            this.published = snapshot();
        }

        boolean isTerminal() {
            TaskStatus current = TaskStatus.fromValue(status);
            return current != null && current.isTerminal();
        }

        boolean hasExpired(long now) {
            return now > expiresAtMillis;
        }

        void failWith(String reason) {
            status = TaskStatus.FAILED.getValue();
            statusMessage = reason;
            lastUpdatedAt = clock.instant().toString();
            error = new JsonRpcError(ErrorCodes.INTERNAL_ERROR, reason, null);
            inputRequests = null;
            notifyAll();
            publish();
        }

        /**
         * Retires an entry whose time is up. One still running is failed
         * first, so a worker waiting for input wakes and finds nothing left to
         * do, and whoever is listening hears how the task ended.
         *
         * @return true when the entry has expired and should be removed
         */
        synchronized boolean retireIfExpired(long now) {
            if (!hasExpired(now)) {
                return false;
            }
            if (!isTerminal()) {
                failWith("The task expired.");
            }
            return true;
        }

        /** Publishes the task as it now stands and reports it. Called holding the monitor. */
        void publish() {
            published = snapshot();
            listener.accept(published.detail());
        }

        private Published snapshot() {
            Task task = new Task(taskId, status, statusMessage, createdAt, lastUpdatedAt, ttlMs, pollIntervalMs);
            return new Published(task, TaskResult.detail(task, result, error, inputRequests));
        }
    }
}
