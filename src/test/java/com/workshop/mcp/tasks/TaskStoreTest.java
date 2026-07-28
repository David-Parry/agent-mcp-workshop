package com.workshop.mcp.tasks;

import com.workshop.mcp.spec.ErrorCodes;
import com.workshop.mcp.spec.InputRequest;
import com.workshop.mcp.spec.Task;
import com.workshop.mcp.spec.TaskResult;
import com.workshop.mcp.spec.TaskStatus;
import com.workshop.mcp.spec.builders.ElicitationBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
@Tag("chapter07")
class TaskStoreTest {

    private static final String UNKNOWN_ID = "no-such-task";

    private TaskStore store;

    @BeforeEach
    void setUp() {
        store = new TaskStore();
    }

    // --- create ---------------------------------------------------------

    @Test
    void createStartsATaskInWorkingStatus() {
        Task task = store.create();

        assertNotNull(task.taskId());
        assertEquals(TaskStatus.WORKING.getValue(), task.status());
        assertEquals("The operation is now in progress.", task.statusMessage());
        assertEquals(task.createdAt(), task.lastUpdatedAt());
        assertEquals(TaskStore.DEFAULT_POLL_INTERVAL_MILLIS, task.pollIntervalMs());
    }

    @Test
    void createGeneratesAnUnguessableIdPerTask() {
        assertNotEquals(store.create().taskId(), store.create().taskId());
    }

    @Test
    void everyTaskIsGivenTheStoresTtl() {
        assertEquals(TaskStore.TTL_MILLIS, store.create().ttlMs());
    }

    // --- get / detail ---------------------------------------------------

    @Test
    void getReturnsNullForAnUnknownTaskId() {
        assertNull(store.get(UNKNOWN_ID));
    }

    @Test
    void getReturnsTheCurrentSnapshot() {
        Task created = store.create();

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
        Task task = store.create();
        Object payload = Map.of("content", "done");

        store.complete(task.taskId(), payload);

        TaskResult detail = store.detail(task.taskId());
        assertSame(payload, detail.result());
        assertNull(detail.error());
        assertNull(detail.inputRequests());
    }

    @Test
    void detailCarriesTheErrorOnceTheTaskFails() {
        Task task = store.create();

        store.fail(task.taskId(), "the search blew up");

        TaskResult detail = store.detail(task.taskId());
        assertEquals(ErrorCodes.INTERNAL_ERROR, detail.error().code());
        assertEquals("the search blew up", detail.error().message());
        assertNull(detail.result());
    }

    @Test
    void detailCarriesTheInputRequestsWhileInputIsRequired() {
        Task task = store.create();

        store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));

        assertTrue(store.detail(task.taskId()).inputRequests().containsKey("directory"));
    }

    // --- complete -------------------------------------------------------

    @Test
    void completeMovesTheTaskToCompleted() {
        Task task = store.create();

        store.complete(task.taskId(), "payload");

        assertEquals(TaskStatus.COMPLETED.getValue(), store.get(task.taskId()).status());
        assertEquals("The operation completed successfully.", store.get(task.taskId()).statusMessage());
    }

    @Test
    void completeDropsAnyOutstandingInputRequests() {
        Task task = store.create();
        store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));

        store.complete(task.taskId(), "payload");

        assertNull(store.detail(task.taskId()).inputRequests());
    }

    @Test
    void completeIgnoresAnUnknownTaskId() {
        store.complete(UNKNOWN_ID, "payload");

        assertNull(store.get(UNKNOWN_ID), "completing an unknown id must not create a task");
    }

    @Test
    void completeIsIgnoredOnceTheTaskIsTerminal() {
        Task task = store.create();
        store.cancel(task.taskId());

        store.complete(task.taskId(), "payload");

        assertEquals(TaskStatus.CANCELLED.getValue(), store.get(task.taskId()).status());
        assertNull(store.detail(task.taskId()).result());
    }

    // --- fail -----------------------------------------------------------

    @Test
    void failMovesTheTaskToFailedAndSurfacesTheReasonTwice() {
        Task task = store.create();

        store.fail(task.taskId(), "no directory could be resolved");

        Task failed = store.get(task.taskId());
        assertEquals(TaskStatus.FAILED.getValue(), failed.status());
        assertEquals("no directory could be resolved", failed.statusMessage());
        assertEquals("no directory could be resolved", store.detail(task.taskId()).error().message());
    }

    @Test
    void failDropsAnyOutstandingInputRequests() {
        Task task = store.create();
        store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));

        store.fail(task.taskId(), "gave up waiting");

        assertNull(store.detail(task.taskId()).inputRequests());
    }

    @Test
    void failIgnoresAnUnknownTaskId() {
        store.fail(UNKNOWN_ID, "boom");

        assertNull(store.get(UNKNOWN_ID), "failing an unknown id must not create a task");
    }

    @Test
    void failIsIgnoredOnceTheTaskIsTerminal() {
        Task task = store.create();
        store.complete(task.taskId(), "payload");

        store.fail(task.taskId(), "boom");

        assertEquals(TaskStatus.COMPLETED.getValue(), store.get(task.taskId()).status());
        assertNull(store.detail(task.taskId()).error());
    }

    // --- requireInput ---------------------------------------------------

    @Test
    void requireInputPublishesWhatTheTaskNeeds() {
        Task task = store.create();

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
        Task task = store.create();
        store.fail(task.taskId(), "boom");

        assertNull(store.requireInput(task.taskId(), Map.of("directory", directoryRequest())));
    }

    // --- applyInput -----------------------------------------------------

    @Test
    void applyInputResumesTheTaskWithTheAnswersItAskedFor() {
        Task task = store.create();
        store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));

        Task resumed = store.applyInput(task.taskId(), Map.of("directory", "/srv/code"));

        assertEquals(TaskStatus.WORKING.getValue(), resumed.status());
        assertEquals("The operation resumed with the input provided.", resumed.statusMessage());
        assertNull(store.detail(task.taskId()).inputRequests(), "an answered question is no longer outstanding");
        assertEquals(Map.of("directory", "/srv/code"), store.awaitInput(task.taskId(), 10L));
    }

    @Test
    void applyInputDiscardsAnswersUnderKeysTheTaskNeverAskedFor() {
        Task task = store.create();
        store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));

        store.applyInput(task.taskId(), Map.of("directory", "/srv/code", "smuggled", "ignored"));

        assertEquals(Map.of("directory", "/srv/code"), store.awaitInput(task.taskId(), 10L));
    }

    @Test
    void anUpdateThatAnswersNothingTheTaskAskedLeavesItWaiting() {
        Task task = store.create();
        store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));

        Task after = store.applyInput(task.taskId(), Map.of("smuggled", "ignored"));

        assertEquals(TaskStatus.INPUT_REQUIRED.getValue(), after.status());
        assertTrue(store.detail(task.taskId()).inputRequests().containsKey("directory"),
                   "the question is still standing, so a later answer can still land");
    }

    @Test
    void anUpdateWithNoAnswersAtAllLeavesTheTaskWaiting() {
        Task task = store.create();
        store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));

        assertEquals(TaskStatus.INPUT_REQUIRED.getValue(), store.applyInput(task.taskId(), null).status());
    }

    @Test
    void aTaskThatPublishedNoRequestsHasNothingAnUpdateCanAnswer() {
        Task task = store.create();
        store.requireInput(task.taskId(), null);

        Task after = store.applyInput(task.taskId(), Map.of("directory", "/srv/code"));

        assertEquals(TaskStatus.INPUT_REQUIRED.getValue(), after.status());
    }

    @Test
    void applyInputReturnsNullForAnUnknownTaskId() {
        assertNull(store.applyInput(UNKNOWN_ID, Map.of("directory", "/srv/code")));
    }

    @Test
    void anUpdateForATaskThatIsNotWaitingIsIgnored() {
        Task task = store.create();

        Task after = store.applyInput(task.taskId(), Map.of("directory", "/srv/code"));

        assertEquals(TaskStatus.WORKING.getValue(), after.status());
        assertEquals(task.lastUpdatedAt(), after.lastUpdatedAt(), "an ignored update changes nothing");
        assertEquals(Map.of(), store.awaitInput(task.taskId(), 10L), "and leaves no answers behind");
    }

    // --- awaitInput -----------------------------------------------------

    @Test
    void awaitInputReturnsNullForAnUnknownTaskId() {
        assertNull(store.awaitInput(UNKNOWN_ID, 10L));
    }

    @Test
    @Timeout(5)
    void awaitInputReturnsEmptyImmediatelyWhenTheTaskNeverAskedForInput() {
        Task task = store.create();

        assertEquals(Map.of(), store.awaitInput(task.taskId(), 10L));
    }

    @Test
    @Timeout(5)
    void awaitInputReturnsTheAnswersOnceTheyArrive() {
        Task task = store.create();
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
        Task task = store.create();
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
        Task task = store.create();
        store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));

        assertNull(store.awaitInput(task.taskId(), 50L));
        assertEquals(TaskStatus.INPUT_REQUIRED.getValue(), store.get(task.taskId()).status(),
                     "an expired wait leaves the question standing so a later answer still lands");
    }

    @Test
    @Timeout(5)
    void awaitInputReturnsNullAndRestoresTheInterruptWhenTheWaitingThreadIsInterrupted() throws Exception {
        Task task = store.create();
        store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));
        AtomicReference<Map<String, Object>> answers = new AtomicReference<>(Map.of());
        AtomicBoolean interruptRestored = new AtomicBoolean();
        Thread waiter = Thread.ofVirtual().start(() -> {
            answers.set(store.awaitInput(task.taskId(), 60_000L));
            interruptRestored.set(Thread.currentThread().isInterrupted());
        });
        waiter.interrupt();
        waiter.join(4_000L);

        assertFalse(waiter.isAlive(), "the interrupt must break the wait rather than run out the timeout");
        assertNull(answers.get());
        assertTrue(interruptRestored.get(), "the interrupt has to survive for the caller to see");
    }

    // --- cancel ---------------------------------------------------------

    @Test
    void cancelMovesTheTaskToCancelled() {
        Task task = store.create();

        Task cancelled = store.cancel(task.taskId());

        assertEquals(TaskStatus.CANCELLED.getValue(), cancelled.status());
        assertEquals("The task was cancelled by request.", cancelled.statusMessage());
    }

    @Test
    void cancelDropsAnyOutstandingInputRequests() {
        Task task = store.create();
        store.requireInput(task.taskId(), Map.of("directory", directoryRequest()));

        store.cancel(task.taskId());

        assertNull(store.detail(task.taskId()).inputRequests());
    }

    @Test
    void cancelReturnsNullForAnUnknownTaskId() {
        assertNull(store.cancel(UNKNOWN_ID));
    }

    @Test
    void cancellingAFinishedTaskLeavesItAsItWas() {
        Task task = store.create();
        store.complete(task.taskId(), "payload");

        Task after = store.cancel(task.taskId());

        assertEquals(TaskStatus.COMPLETED.getValue(), after.status(), "a terminal status never changes");
        assertEquals("payload", store.detail(task.taskId()).result());
    }

    @Test
    void aSecondCancelChangesNothing() {
        Task task = store.create();
        Task first = store.cancel(task.taskId());

        assertEquals(first, store.cancel(task.taskId()));
    }

    // --- the terminal guard ---------------------------------------------

    @Test
    void aStatusOutsideTheEnumIsNotTreatedAsTerminal() throws Exception {
        Task task = store.create();
        forceStatus(task.taskId(), "not-a-status");

        assertEquals(TaskStatus.CANCELLED.getValue(), store.cancel(task.taskId()).status(),
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
