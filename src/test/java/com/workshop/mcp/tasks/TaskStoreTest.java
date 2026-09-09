package com.workshop.mcp.tasks;

import com.workshop.mcp.spec.ErrorCodes;
import com.workshop.mcp.spec.InputRequest;
import com.workshop.mcp.spec.Task;
import com.workshop.mcp.spec.TaskResult;
import com.workshop.mcp.spec.TaskStatus;
import com.workshop.mcp.spec.builders.ElicitationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the task state machine of the {@code io.modelcontextprotocol/tasks}
 * extension: every legal transition, every guard that rejects an illegal one,
 * and the two halves of task-level input that meet in {@code awaitInput}.
 */
class TaskStoreTest {

    private static final String UNKNOWN_ID = "no-such-task";

    private TaskStore store;
    private List<TaskResult> notifications;

    @BeforeEach
    void setUp() {
        store = new TaskStore();
        notifications = new CopyOnWriteArrayList<>();
        store.onStatusChange(notifications::add);
    }

    // --- create ---------------------------------------------------------

    @Test
    void createStartsATaskInWorkingStatus() {
        Task task = store.create(null);

        assertNotNull(task.taskId());
        assertEquals(TaskStatus.WORKING.getValue(), task.status());
        assertEquals("The operation is now in progress.", task.statusMessage());
        assertEquals(task.createdAt(), task.lastUpdatedAt());
        assertEquals(TaskStore.DEFAULT_POLL_INTERVAL_MILLIS, task.pollIntervalMs());
    }

    @Test
    void createGeneratesAnUnguessableIdPerTask() {
        assertNotEquals(store.create(null).taskId(), store.create(null).taskId());
    }

    @Test
    void createNotifiesStatusListeners() {
        Task task = store.create(null);

        assertEquals(1, notifications.size());
        assertEquals(task.taskId(), notifications.get(0).taskId());
        assertNull(notifications.get(0).resultType(), "a notification carries no resultType");
    }

    @Test
    void aNullTtlFallsBackToTheCeiling() {
        assertEquals(TaskStore.MAX_TTL_MILLIS, store.create(null).ttlMs());
    }

    @Test
    void aNonPositiveTtlFallsBackToTheCeiling() {
        assertEquals(TaskStore.MAX_TTL_MILLIS, store.create(0L).ttlMs());
        assertEquals(TaskStore.MAX_TTL_MILLIS, store.create(-1L).ttlMs());
    }

    @Test
    void aTtlUnderTheCeilingIsHonoured() {
        assertEquals(1_000L, store.create(1_000L).ttlMs());
    }

    @Test
    void aTtlOverTheCeilingIsClampedDown() {
        assertEquals(TaskStore.MAX_TTL_MILLIS, store.create(TaskStore.MAX_TTL_MILLIS + 1).ttlMs());
    }

    // --- get / detail ---------------------------------------------------

    @Test
    void getReturnsNullForAnUnknownTaskId() {
        assertNull(store.get(UNKNOWN_ID));
    }

    @Test
    void getReturnsTheCurrentSnapshot() {
        Task created = store.create(null);

        Task fetched = store.get(created.taskId());

        assertEquals(created.taskId(), fetched.taskId());
        assertEquals(TaskStatus.WORKING.getValue(), fetched.status());
    }

    @Test
    void detailReturnsNullForAnUnknownTaskId() {
        assertNull(store.detail(UNKNOWN_ID));
    }

    @Test
    void detailCarriesTheResultOnceTheTaskCompletes() {
        Task task = store.create(null);
        Object payload = Map.of("content", "done");

        store.complete(task.taskId(), payload);

        TaskResult detail = store.detail(task.taskId());
        assertSame(payload, detail.result());
        assertNull(detail.error());
        assertNull(detail.inputRequests());
    }

    @Test
    void detailCarriesTheErrorOnceTheTaskFails() {
        Task task = store.create(null);

        store.fail(task.taskId(), "the search blew up");

        TaskResult detail = store.detail(task.taskId());
        assertEquals(ErrorCodes.INTERNAL_ERROR, detail.error().code());
        assertEquals("the search blew up", detail.error().message());
        assertNull(detail.result());
    }

    @Test
    void detailCarriesTheInputRequestsWhileInputIsRequired() {
        Task task = store.create(null);

        store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));

        assertTrue(store.detail(task.taskId()).inputRequests().containsKey("directory"));
    }

    // --- complete -------------------------------------------------------

    @Test
    void completeMovesTheTaskToCompleted() {
        Task task = store.create(null);

        store.complete(task.taskId(), "payload");

        assertEquals(TaskStatus.COMPLETED.getValue(), store.get(task.taskId()).status());
        assertEquals("The operation completed successfully.", store.get(task.taskId()).statusMessage());
    }

    @Test
    void completeDropsAnyOutstandingInputRequests() {
        Task task = store.create(null);
        store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));

        store.complete(task.taskId(), "payload");

        assertNull(store.detail(task.taskId()).inputRequests());
    }

    @Test
    void completeIgnoresAnUnknownTaskId() {
        store.complete(UNKNOWN_ID, "payload");

        assertTrue(notifications.isEmpty());
    }

    @Test
    void completeIsIgnoredOnceTheTaskIsTerminal() {
        Task task = store.create(null);
        store.cancel(task.taskId());

        store.complete(task.taskId(), "payload");

        assertEquals(TaskStatus.CANCELLED.getValue(), store.get(task.taskId()).status());
        assertNull(store.detail(task.taskId()).result());
    }

    // --- fail -----------------------------------------------------------

    @Test
    void failMovesTheTaskToFailedAndSurfacesTheReasonTwice() {
        Task task = store.create(null);

        store.fail(task.taskId(), "no directory could be resolved");

        Task failed = store.get(task.taskId());
        assertEquals(TaskStatus.FAILED.getValue(), failed.status());
        assertEquals("no directory could be resolved", failed.statusMessage());
        assertEquals("no directory could be resolved", store.detail(task.taskId()).error().message());
    }

    @Test
    void failDropsAnyOutstandingInputRequests() {
        Task task = store.create(null);
        store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));

        store.fail(task.taskId(), "gave up waiting");

        assertNull(store.detail(task.taskId()).inputRequests());
    }

    @Test
    void failNotifiesStatusListenersWithTheError() {
        Task task = store.create(null);

        store.fail(task.taskId(), "boom");

        TaskResult last = notifications.get(notifications.size() - 1);
        assertEquals(TaskStatus.FAILED.getValue(), last.status());
        assertEquals(ErrorCodes.INTERNAL_ERROR, last.error().code());
    }

    @Test
    void failIgnoresAnUnknownTaskId() {
        store.fail(UNKNOWN_ID, "boom");

        assertTrue(notifications.isEmpty());
    }

    @Test
    void failIsIgnoredOnceTheTaskIsTerminal() {
        Task task = store.create(null);
        store.complete(task.taskId(), "payload");

        store.fail(task.taskId(), "boom");

        assertEquals(TaskStatus.COMPLETED.getValue(), store.get(task.taskId()).status());
        assertNull(store.detail(task.taskId()).error());
    }

    // --- requireInput ---------------------------------------------------

    @Test
    void requireInputPublishesWhatTheTaskNeeds() {
        Task task = store.create(null);

        Task waiting = store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));

        assertEquals(TaskStatus.INPUT_REQUIRED.getValue(), waiting.status());
        assertEquals("The operation is waiting for input.", waiting.statusMessage());
    }

    @Test
    void requireInputReturnsNullForAnUnknownTaskId() {
        assertNull(store.requireInput(UNKNOWN_ID, Map.of("directory", directoryRequest())));
    }

    @Test
    void requireInputReturnsNullOnceTheTaskIsTerminal() {
        Task task = store.create(null);
        store.fail(task.taskId(), "boom");

        assertNull(store.requireInput(task.taskId(), Map.of("directory", directoryRequest())));
    }

    // --- applyInput -----------------------------------------------------

    @Test
    void applyInputResumesTheTaskWithTheAnswersItAskedFor() {
        Task task = store.create(null);
        store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));

        Task resumed = store.applyInput(task.taskId(), Map.of("directory", "/srv/code"));

        assertEquals(TaskStatus.WORKING.getValue(), resumed.status());
        assertEquals("The operation resumed with the input provided.", resumed.statusMessage());
        assertEquals(Map.of("directory", "/srv/code"), store.inputResponsesFor(task.taskId()));
    }

    @Test
    void applyInputDiscardsAnswersUnderKeysTheTaskNeverAskedFor() {
        Task task = store.create(null);
        store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));

        store.applyInput(task.taskId(), Map.of("directory", "/srv/code", "smuggled", "ignored"));

        assertEquals(Map.of("directory", "/srv/code"), store.inputResponsesFor(task.taskId()));
    }

    @Test
    void applyInputSkipsAKeyTheClientLeftUnanswered() {
        Task task = store.create(null);
        store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));

        // A HashMap so the absent answer is a genuine null rather than a rejected Map.of entry.
        store.applyInput(task.taskId(), new HashMap<>());

        assertEquals(Map.of(), store.inputResponsesFor(task.taskId()));
    }

    @Test
    void applyInputAcceptsNothingWhenTheResponseMapIsNull() {
        Task task = store.create(null);
        store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));

        assertNotNull(store.applyInput(task.taskId(), null));
        assertEquals(Map.of(), store.inputResponsesFor(task.taskId()));
    }

    @Test
    void applyInputAcceptsNothingWhenTheTaskPublishedNoRequests() {
        Task task = store.create(null);
        store.requireInput(task.taskId(), null);

        store.applyInput(task.taskId(), Map.of("directory", "/srv/code"));

        assertEquals(Map.of(), store.inputResponsesFor(task.taskId()));
    }

    @Test
    void applyInputReturnsNullForAnUnknownTaskId() {
        assertNull(store.applyInput(UNKNOWN_ID, Map.of("directory", "/srv/code")));
    }

    @Test
    void applyInputReturnsNullWhenTheTaskIsNotWaitingForInput() {
        Task task = store.create(null);

        assertNull(store.applyInput(task.taskId(), Map.of("directory", "/srv/code")));
    }

    // --- awaitInput -----------------------------------------------------

    @Test
    void awaitInputReturnsNullForAnUnknownTaskId() {
        assertNull(store.awaitInput(UNKNOWN_ID, 10L));
    }

    @Test
    @Timeout(5)
    void awaitInputReturnsEmptyImmediatelyWhenTheTaskNeverAskedForInput() {
        Task task = store.create(null);

        assertEquals(Map.of(), store.awaitInput(task.taskId(), 10L));
    }

    @Test
    @Timeout(5)
    void awaitInputReturnsTheAnswersOnceTheyArrive() {
        Task task = store.create(null);
        store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));
        CompletableFuture.runAsync(() -> {
            pause();
            store.applyInput(task.taskId(), Map.of("directory", "/srv/code"));
        });

        assertEquals(Map.of("directory", "/srv/code"), store.awaitInput(task.taskId(), 4_000L));
    }

    @Test
    @Timeout(5)
    void awaitInputReturnsNullWhenTheTaskIsCancelledWhileWaiting() {
        Task task = store.create(null);
        store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));
        CompletableFuture.runAsync(() -> {
            pause();
            store.cancel(task.taskId());
        });

        assertNull(store.awaitInput(task.taskId(), 4_000L));
        assertEquals(TaskStatus.CANCELLED.getValue(), store.get(task.taskId()).status());
    }

    @Test
    @Timeout(5)
    void awaitInputReturnsNullWhenTheWaitExpires() {
        Task task = store.create(null);
        store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));

        assertNull(store.awaitInput(task.taskId(), 50L));
        assertEquals(TaskStatus.INPUT_REQUIRED.getValue(), store.get(task.taskId()).status(),
                     "an expired wait leaves the question standing so a later answer still lands");
    }

    @Test
    @Timeout(5)
    void awaitInputReturnsNullAndRestoresTheInterruptWhenTheWaitingThreadIsInterrupted() throws Exception {
        Task task = store.create(null);
        store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));
        AtomicReference<Map<String, Object>> answers = new AtomicReference<>(Map.of());
        AtomicBoolean interruptRestored = new AtomicBoolean();
        Thread waiter = new Thread(() -> {
            answers.set(store.awaitInput(task.taskId(), 60_000L));
            interruptRestored.set(Thread.currentThread().isInterrupted());
        });

        waiter.start();
        waiter.interrupt();
        waiter.join(4_000L);

        assertFalse(waiter.isAlive(), "the interrupt must break the wait rather than run out the timeout");
        assertNull(answers.get());
        assertTrue(interruptRestored.get(), "the interrupt has to survive for the caller to see");
    }

    // --- inputResponsesFor ----------------------------------------------

    @Test
    void inputResponsesForReturnsAnEmptyMapForAnUnknownTaskId() {
        assertEquals(Map.of(), store.inputResponsesFor(UNKNOWN_ID));
    }

    @Test
    void inputResponsesForReturnsAnEmptyMapBeforeAnyInputArrives() {
        assertEquals(Map.of(), store.inputResponsesFor(store.create(null).taskId()));
    }

    // --- cancel ---------------------------------------------------------

    @Test
    void cancelMovesTheTaskToCancelled() {
        Task task = store.create(null);

        Task cancelled = store.cancel(task.taskId());

        assertEquals(TaskStatus.CANCELLED.getValue(), cancelled.status());
        assertEquals("The task was cancelled by request.", cancelled.statusMessage());
    }

    @Test
    void cancelDropsAnyOutstandingInputRequests() {
        Task task = store.create(null);
        store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));

        store.cancel(task.taskId());

        assertNull(store.detail(task.taskId()).inputRequests());
    }

    @Test
    void cancelReturnsNullForAnUnknownTaskId() {
        assertNull(store.cancel(UNKNOWN_ID));
    }

    @Test
    void cancelReturnsNullOnceTheTaskIsTerminal() {
        Task task = store.create(null);
        store.cancel(task.taskId());

        assertNull(store.cancel(task.taskId()), "a second cancel is the -32602 Invalid params condition");
    }

    // --- listeners and the terminal guard --------------------------------

    @Test
    void everyTransitionReachesTheRegisteredListeners() {
        Task task = store.create(null);
        store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));
        store.applyInput(task.taskId(), Map.of("directory", "/srv/code"));
        store.complete(task.taskId(), "payload");

        assertEquals(List.of(TaskStatus.WORKING.getValue(),
                             TaskStatus.INPUT_REQUIRED.getValue(),
                             TaskStatus.WORKING.getValue(),
                             TaskStatus.COMPLETED.getValue()),
                     notifications.stream().map(TaskResult::status).toList());
    }

    @Test
    void aStatusOutsideTheEnumIsNotTreatedAsTerminal() throws Exception {
        Task task = store.create(null);
        forceStatus(task.taskId(), "not-a-status");

        assertNotNull(store.cancel(task.taskId()),
                      "an unrecognised status is not terminal, so the transition must still be allowed");
    }

    /**
     * The one embedded request this server still builds, now that {@code roots/list}
     * is deprecated under SEP-2577. The store is generic over the keys a task
     * publishes and never looks inside a request, so the transitions only need it
     * to be a real one.
     */
    private static InputRequest directoryRequest() {
        return InputRequest.elicitation(ElicitationBuilder.buildSearchDirectoryElicitation());
    }

    /**
     * Writes a status straight onto the entry, bypassing the transitions, so the
     * guard against a value outside {@link TaskStatus} can be exercised.
     */
    private void forceStatus(String taskId, String status) throws Exception {
        Field entriesField = TaskStore.class.getDeclaredField("entries");
        entriesField.setAccessible(true);
        Object entry = ((Map<?, ?>) entriesField.get(store)).get(taskId);
        Field statusField = entry.getClass().getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(entry, status);
    }

    private static void pause() {
        try {
            Thread.sleep(40L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
