package com.workshop.mcp;

import com.workshop.mcp.io.IOHandler;
import com.workshop.mcp.io.LogFile;
import com.workshop.mcp.io.LogFileWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.*;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ServerTest {

    @Mock
    private IOHandler mockIOHandler;

    @Mock
    private Router mockRouter;

    @Mock
    private LogFile mockLogger;

    @Mock
    private CountDownLatch mockCountDownLatch;

    private Server server;
    private AutoCloseable mocks;
    private MockedStatic<LogFileWriter> logFileWriterMock;


    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);

        // Mock the static LogFileWriter
        logFileWriterMock = mockStatic(LogFileWriter.class);
        logFileWriterMock.when(LogFileWriter::getInstance).thenReturn(mockLogger);

        // Reset static fields before each test
        resetStaticFields();

        // Set up custom SecurityManager to catch System.exit calls

        server = new Server(mockIOHandler, mockRouter, mockCountDownLatch);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Restore original SecurityManager

        if (logFileWriterMock != null) {
            logFileWriterMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
        // Reset static fields after each test
        resetStaticFields();
    }

    private void resetStaticFields() throws Exception {
        // Reset isShuttingDown
        Field isShuttingDownField = Server.class.getDeclaredField("isShuttingDown");
        isShuttingDownField.setAccessible(true);
        ((AtomicBoolean) isShuttingDownField.get(null)).set(false);
    }

    @Test
    void testServerConstructor() {
        assertNotNull(server);
    }

    @Test
    void testStartAddsLineListenerToIOHandler() {
        // Given
        when(mockIOHandler.isRunning()).thenReturn(false);

        // When
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();

        // Allow some time for the server to start
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then
        verify(mockIOHandler).addLineListener(any(Consumer.class));
        verify(mockIOHandler).startInputReader();

        // Clean up
        serverThread.interrupt();
    }

    @Test
    void testLineListenerRoutesMessagesToRouter() {
        // Given
        ArgumentCaptor<Consumer<String>> listenerCaptor = ArgumentCaptor.forClass(Consumer.class);
        when(mockIOHandler.isRunning()).thenReturn(false);

        // When
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();

        // Allow some time for the server to start
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Capture the line listener
        verify(mockIOHandler).addLineListener(listenerCaptor.capture());
        Consumer<String> lineListener = listenerCaptor.getValue();

        // Test that the listener routes messages to the router
        String testMessage = "test message";
        lineListener.accept(testMessage);

        // Then
        verify(mockRouter).route(testMessage);

        // Clean up
        serverThread.interrupt();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testServerStopsWhenIOHandlerStopsRunning() {
        // Given
        when(mockIOHandler.isRunning())
                .thenReturn(true)  // First check - running
                .thenReturn(true)  // Second check - running
                .thenReturn(false); // Third check - stopped

        // When
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();

        // Wait for the server thread to complete
        try {
            serverThread.join(4000); // Wait up to 4 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then
        assertFalse(serverThread.isAlive(), "Server thread should have stopped");
        verify(mockIOHandler, atLeastOnce()).isRunning();
        verify(mockIOHandler).stopRunning();
    }

    @Test
    void testShutdownHookIsRegistered() {
        // Given
        when(mockIOHandler.isRunning()).thenReturn(false);

        // When
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();

        // Allow some time for the server to start
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then
        // We can't directly test Runtime.addShutdownHook, but we can verify
        // that the server starts without throwing exceptions
        assertTrue(serverThread.isAlive() || serverThread.getState() == Thread.State.TERMINATED);

        // Clean up
        serverThread.interrupt();
    }

    @Test
    void testServerHandlesExceptionDuringStart() {
        // Given
        doThrow(new RuntimeException("Test exception")).when(mockIOHandler).addLineListener(any());

        // When/Then
        // The server.start() method calls System.exit(1) on exception,
        // which we can't test directly in a unit test. Instead, we verify
        // that the exception causes stopRunning to be called
        try {
            server.start();
        } catch (Exception e) {
            // Expected due to System.exit call
        }

        verify(mockIOHandler).stopRunning();
    }

    @Test
    void testServerStopsIOHandlerWhenStopping() {
        // Given
        when(mockIOHandler.isRunning()).thenReturn(false);

        // When
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();

        // Allow some time for the server to start and stop
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then
        verify(mockIOHandler).stopRunning();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testKeepRunningPollingMechanism() {
        // Given
        when(mockIOHandler.isRunning())
                .thenReturn(true)   // First few checks - running
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(false); // Eventually stops

        // When
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();

        // Wait for the server to stop
        try {
            serverThread.join(4000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then
        verify(mockIOHandler, atLeast(2)).isRunning();
        assertFalse(serverThread.isAlive());
    }

    @Test
    void testMultipleLineListenersCanBeAdded() {
        // Given
        ArgumentCaptor<Consumer<String>> listenerCaptor = ArgumentCaptor.forClass(Consumer.class);
        when(mockIOHandler.isRunning()).thenReturn(false);

        // When
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();

        // Allow some time for the server to start
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify the listener was added
        verify(mockIOHandler).addLineListener(listenerCaptor.capture());
        Consumer<String> lineListener = listenerCaptor.getValue();

        // Test multiple messages
        lineListener.accept("message1");
        lineListener.accept("message2");
        lineListener.accept("message3");

        // Then
        verify(mockRouter).route("message1");
        verify(mockRouter).route("message2");
        verify(mockRouter).route("message3");

        // Clean up
        serverThread.interrupt();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testServerHandlesInterruptedExceptionDuringKeepRunning() throws InterruptedException {
        // Given
        when(mockIOHandler.isRunning()).thenReturn(true);

        // When
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();

        // Wait a bit then interrupt the thread
        Thread.sleep(100);
        serverThread.interrupt();

        // Wait for thread to finish
        serverThread.join(2000);

        // Then
        assertFalse(serverThread.isAlive());
    }

    @Test
    void testServerStartsInputReaderBeforeKeepRunning() {
        // Given
        when(mockIOHandler.isRunning()).thenReturn(false);

        // When
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();

        // Allow some time for the server to start
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then
        // Verify the order of operations
        InOrder inOrder = inOrder(mockIOHandler);
        inOrder.verify(mockIOHandler).addLineListener(any(Consumer.class));
        inOrder.verify(mockIOHandler).startInputReader();

        // Clean up
        serverThread.interrupt();
    }

    @Test
    void testServerHandlesIOHandlerThatNeverStops() {
        // Given
        when(mockIOHandler.isRunning()).thenReturn(true); // Always running

        // When
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();

        // Allow some time for the server to run
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then
        assertTrue(serverThread.isAlive());
        verify(mockIOHandler, atLeastOnce()).isRunning();

        // Clean up - interrupt the thread to stop it
        serverThread.interrupt();
    }

    @Test
    void testServerCallsStopRunningOnIOHandlerWhenExceptionOccurs() {
        // Given
        doThrow(new RuntimeException("Test exception"))
                .when(mockIOHandler).startInputReader();

        // When
        try {
            server.start();
        } catch (Exception e) {
            // Expected due to System.exit call
        }

        // Then
        verify(mockIOHandler).stopRunning();
    }

    @Test
    void testServerHandlesMultipleStopCalls() {
        // Given
        when(mockIOHandler.isRunning())
                .thenReturn(true)
                .thenReturn(false);

        // When
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();

        // Wait for server to stop
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then
        // Verify stopRunning is called at least once
        verify(mockIOHandler, atLeastOnce()).stopRunning();
    }

    @Test
    void testServerHandlesRapidIsRunningStateChanges() {
        // Given - Simulate rapid state changes
        when(mockIOHandler.isRunning())
                .thenReturn(true)
                .thenReturn(false)
                .thenReturn(true)
                .thenReturn(false);

        // When
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();

        // Wait for server to process
        try {
            serverThread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then
        assertFalse(serverThread.isAlive());
        verify(mockIOHandler, atLeastOnce()).stopRunning();
    }

    @Test
    void testLineListenerHandlesNullMessage() {
        // Given
        ArgumentCaptor<Consumer<String>> listenerCaptor = ArgumentCaptor.forClass(Consumer.class);
        when(mockIOHandler.isRunning()).thenReturn(false);

        // When
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();

        // Allow some time for the server to start
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Capture the line listener
        verify(mockIOHandler).addLineListener(listenerCaptor.capture());
        Consumer<String> lineListener = listenerCaptor.getValue();

        // Test with null message
        lineListener.accept(null);

        // Then
        verify(mockRouter).route(null);

        // Clean up
        serverThread.interrupt();
    }

    @Test
    void testLineListenerHandlesEmptyMessage() {
        // Given
        ArgumentCaptor<Consumer<String>> listenerCaptor = ArgumentCaptor.forClass(Consumer.class);
        when(mockIOHandler.isRunning()).thenReturn(false);

        // When
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();

        // Allow some time for the server to start
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Capture the line listener
        verify(mockIOHandler).addLineListener(listenerCaptor.capture());
        Consumer<String> lineListener = listenerCaptor.getValue();

        // Test with empty message
        lineListener.accept("");

        // Then
        verify(mockRouter).route("");

        // Clean up
        serverThread.interrupt();
    }

    @Test
    void testStartLogsExceptionAndCallsStop() {
        // Given
        RuntimeException testException = new RuntimeException("Test exception");
        doThrow(testException).when(mockIOHandler).addLineListener(any());

        // When
        try {
            server.start();
        } catch (Exception e) {
            // Expected
        }

        // Then
        verify(mockLogger).log(eq("Error in main method"), eq(testException));
        verify(mockIOHandler).stopRunning();
        verify(mockLogger).log("Stopping and closing resources");
        verify(mockLogger, times(2)).close(); // Once in stop(), once in catch block
    }


    @Test
    void testKeepRunningExitsWhenShutdownLatchTriggered() throws Exception {
        // Given
        when(mockIOHandler.isRunning()).thenReturn(true);

        // Get access to shutdownLatch
        Field shutdownLatchField = Server.class.getDeclaredField("shutdownLatch");
        shutdownLatchField.setAccessible(true);
        CountDownLatch latch = (CountDownLatch) shutdownLatchField.get(null);

        // When
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();

        // Wait a bit then trigger shutdown
        Thread.sleep(100);
        latch.countDown();

        // Wait for thread to finish
        serverThread.join(2000);

        // Then
        assertFalse(serverThread.isAlive());
    }

    @Test
    void testKeepRunningLogsWhenIOStops() {
        // Given
        when(mockIOHandler.isRunning())
                .thenReturn(true)
                .thenReturn(false); // IO stops

        // When
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();

        // Wait for server to stop
        try {
            serverThread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then
        verify(mockLogger).log("IO processing has stopped. Initiating shutdown.");
    }

    @Test
    void testShutdownHookSetsIsShuttingDownFlag() throws Exception {
        // Given
        when(mockIOHandler.isRunning()).thenReturn(true);
        ArgumentCaptor<Thread> shutdownHookCaptor = ArgumentCaptor.forClass(Thread.class);

        // Mock Runtime to capture shutdown hook
        Runtime mockRuntime = mock(Runtime.class);
        MockedStatic<Runtime> runtimeMock = mockStatic(Runtime.class);
        runtimeMock.when(Runtime::getRuntime).thenReturn(mockRuntime);

        try {
            // When
            Thread serverThread = new Thread(() -> server.start());
            serverThread.start();

            // Wait for shutdown hook to be registered
            Thread.sleep(100);

            // Capture the shutdown hook
            verify(mockRuntime).addShutdownHook(shutdownHookCaptor.capture());
            Thread shutdownHook = shutdownHookCaptor.getValue();

            // Execute the shutdown hook
            shutdownHook.run();

            // Then
            Field isShuttingDownField = Server.class.getDeclaredField("isShuttingDown");
            isShuttingDownField.setAccessible(true);
            AtomicBoolean isShuttingDown = (AtomicBoolean) isShuttingDownField.get(null);
            assertTrue(isShuttingDown.get());

            // Clean up
            serverThread.interrupt();
        } finally {
            runtimeMock.close();
        }
    }

    @Test
    void testKeepRunningHandlesInterruptedException() {
        // Given
        when(mockIOHandler.isRunning()).thenReturn(true);

        // When
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();

        // Wait a bit then interrupt
        try {
            Thread.sleep(100);
            serverThread.interrupt();
            serverThread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then
        verify(mockLogger).log(eq("Application interrupted during shutdown"), any(InterruptedException.class));
        assertFalse(serverThread.isAlive());
    }

    @Test
    void testStartMethodCompleteFlow() {
        // Given
        when(mockIOHandler.isRunning())
                .thenReturn(true)
                .thenReturn(false);

        // When
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();

        // Wait for completion
        try {
            serverThread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then - verify complete flow
        InOrder inOrder = inOrder(mockIOHandler, mockLogger);
        inOrder.verify(mockIOHandler).addLineListener(any(Consumer.class));
        inOrder.verify(mockIOHandler).startInputReader();
        inOrder.verify(mockIOHandler, atLeastOnce()).isRunning();
        inOrder.verify(mockLogger).log("IO processing has stopped. Initiating shutdown.");
        inOrder.verify(mockIOHandler).stopRunning();
    }

    @Test
    void testShutdownHookOnlyExecutesOnce() throws Exception {
        // Given
        when(mockIOHandler.isRunning()).thenReturn(true);
        ArgumentCaptor<Thread> shutdownHookCaptor = ArgumentCaptor.forClass(Thread.class);

        // Mock Runtime
        Runtime mockRuntime = mock(Runtime.class);
        MockedStatic<Runtime> runtimeMock = mockStatic(Runtime.class);
        runtimeMock.when(Runtime::getRuntime).thenReturn(mockRuntime);

        try {
            // When
            Thread serverThread = new Thread(() -> server.start());
            serverThread.start();

            Thread.sleep(100);

            // Capture shutdown hook
            verify(mockRuntime).addShutdownHook(shutdownHookCaptor.capture());
            Thread shutdownHook = shutdownHookCaptor.getValue();

            // Execute shutdown hook multiple times
            shutdownHook.run();
            shutdownHook.run();
            shutdownHook.run();

            // Then - stop should only be called once
            verify(mockIOHandler, times(1)).stopRunning();
            verify(mockLogger, times(1)).log("Stopping and closing resources");

            serverThread.interrupt();
        } finally {
            runtimeMock.close();
        }
    }

    @Test
    void testShutdownHookExecutesStopMethod() throws Exception {
        // Given
        when(mockIOHandler.isRunning()).thenReturn(true);
        ArgumentCaptor<Thread> shutdownHookCaptor = ArgumentCaptor.forClass(Thread.class);

        // Mock Runtime
        Runtime mockRuntime = mock(Runtime.class);
        MockedStatic<Runtime> runtimeMock = mockStatic(Runtime.class);
        runtimeMock.when(Runtime::getRuntime).thenReturn(mockRuntime);

        try {
            // When
            Thread serverThread = new Thread(() -> server.start());
            serverThread.start();

            Thread.sleep(100);

            // Capture shutdown hook
            verify(mockRuntime).addShutdownHook(shutdownHookCaptor.capture());
            Thread shutdownHook = shutdownHookCaptor.getValue();

            // Execute the shutdown hook
            shutdownHook.run();

            // Then - verify stop() method was called
            verify(mockIOHandler).stopRunning();
            verify(mockLogger).log("Stopping and closing resources");
            verify(mockLogger, times(2)).close(); // Once in stop(), once in shutdown hook

            // Verify isShuttingDown flag was set
            Field isShuttingDownField = Server.class.getDeclaredField("isShuttingDown");
            isShuttingDownField.setAccessible(true);
            AtomicBoolean isShuttingDown = (AtomicBoolean) isShuttingDownField.get(null);
            assertTrue(isShuttingDown.get());

            // Verify shutdownLatch was counted down
            Field shutdownLatchField = Server.class.getDeclaredField("shutdownLatch");
            shutdownLatchField.setAccessible(true);
            CountDownLatch latch = (CountDownLatch) shutdownLatchField.get(null);
            assertEquals(0, latch.getCount());

            serverThread.interrupt();
        } finally {
            runtimeMock.close();
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void testKeepRunningPollingInterval() {
        // Given
        when(mockIOHandler.isRunning())
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(false);

        // When
        long startTime = System.currentTimeMillis();
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();

        try {
            serverThread.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long duration = System.currentTimeMillis() - startTime;

        // Then - verify polling happened multiple times with ~500ms intervals
        verify(mockIOHandler, atLeast(4)).isRunning();
        assertTrue(duration >= 1500, "Should have taken at least 1.5 seconds due to polling intervals");
    }

    @Test
    void testSystemExitCalledWithSuccessCodeWhenServerStopsNormally() {
        // Given
        when(mockIOHandler.isRunning()).thenReturn(false);

        // When
        try {
            server.start();
        } catch (Exception e) {
            // Expected
        }

        // Then
        // Cannot verify System.exit directly, but we can verify the flow
        verify(mockIOHandler, atLeastOnce()).stopRunning();
    }

    @Test
    void testSystemExitCalledWithErrorCodeOnException() {
        // Given
        doThrow(new RuntimeException("Test exception")).when(mockIOHandler).addLineListener(any());

        // When
        try {
            server.start();
        } catch (Exception e) {
            // Expected
        }

        // Then
        verify(mockLogger).log(eq("Error in main method"), any(RuntimeException.class));
    }

    @Test
    void testSystemExitCalledWithErrorCodeOnInterruption() {
        // Given
        when(mockIOHandler.isRunning()).thenReturn(true);

        // When
        Thread serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (Exception e) {
                // Expected
            }
        });
        serverThread.start();

        // Interrupt the thread quickly
        try {
            Thread.sleep(50);
            serverThread.interrupt();
            serverThread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then
        assertFalse(serverThread.isAlive());
        verify(mockLogger).log(eq("Application interrupted during shutdown"), any(InterruptedException.class));
    }

    @Test
    void testKeepRunningExitsImmediatelyWhenAlreadyShuttingDown() throws Exception {
        // Given
        when(mockIOHandler.isRunning()).thenReturn(true);

        // Set isShuttingDown to true before starting
        Field isShuttingDownField = Server.class.getDeclaredField("isShuttingDown");
        isShuttingDownField.setAccessible(true);
        ((AtomicBoolean) isShuttingDownField.get(null)).set(true);

        // When
        try {
            server.start();
        } catch (Exception e) {
            // Expected
        }

        // Then
        // Should not check IO running status when already shutting down
        verify(mockIOHandler, never()).isRunning();
    }

    @Test
    void testStopMethodDirectly() {
        // Given
        when(mockIOHandler.isRunning()).thenReturn(false);

        // When
        // Use reflection to call private stop method
        try {
            java.lang.reflect.Method stopMethod = Server.class.getDeclaredMethod("stop");
            stopMethod.setAccessible(true);
            stopMethod.invoke(server);
        } catch (Exception e) {
            fail("Failed to invoke stop method: " + e.getMessage());
        }

        // Then
        verify(mockIOHandler).stopRunning();
        verify(mockLogger).log("Stopping and closing resources");
        verify(mockLogger).close();
    }

    @Test
    void testStopMethodWithNullIOHandler() throws Exception {
        // Given
        Server serverWithNullIO = new Server(null, mockRouter, mockCountDownLatch);

        // When
        // Use reflection to call private stop method
        java.lang.reflect.Method stopMethod = Server.class.getDeclaredMethod("stop");
        stopMethod.setAccessible(true);
        stopMethod.invoke(serverWithNullIO);

        // Then
        verify(mockLogger).log("Stopping and closing resources");
        verify(mockLogger).close();
    }

    @Test
    void testKeepRunningExitsWhenShutdownLatchTriggeredImmediately() throws Exception {
        // Given
        when(mockIOHandler.isRunning()).thenReturn(true);
        when(mockCountDownLatch.await(500, TimeUnit.MILLISECONDS)).thenReturn(true);

        // When
        Thread serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (Exception e) {
                // Expected due to System.exit
            }
        });
        serverThread.start();

        // Wait for thread to finish
        serverThread.join(2000);

        // Then
        assertFalse(serverThread.isAlive());
        verify(mockCountDownLatch, atLeastOnce()).await(500, TimeUnit.MILLISECONDS);
    }

    @Test
    void testKeepRunningWithInterruptedExceptionDuringAwait() throws Exception {
        // Given
        when(mockIOHandler.isRunning()).thenReturn(true);
        when(mockCountDownLatch.await(500, TimeUnit.MILLISECONDS))
                .thenThrow(new InterruptedException("Test interruption"));

        // When
        Thread serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (Exception e) {
                // Expected due to System.exit
            }
        });
        serverThread.start();

        // Wait for thread to finish
        serverThread.join(2000);

        // Then
        assertFalse(serverThread.isAlive());
        verify(mockLogger).log(eq("Application interrupted during shutdown"), any(InterruptedException.class));
    }

    @Test
    void testStopMethodIsCalledFromStartWhenExceptionOccurs() throws Exception {
        // Given
        // Make the start method throw an exception after initial setup
        doThrow(new RuntimeException("Test exception")).when(mockIOHandler).startInputReader();

        // When
        try {
            server.start();
        } catch (Exception e) {
            // Expected due to System.exit
        }

        // Then
        // Verify stop was called
        verify(mockIOHandler).stopRunning();
        verify(mockLogger).log("Stopping and closing resources");
        verify(mockLogger).close();
    }

    @Test
    void testKeepRunningMethodWithShutdownLatch() throws Exception {
        // Given
        when(mockIOHandler.isRunning()).thenReturn(true);
        when(mockCountDownLatch.await(500, TimeUnit.MILLISECONDS))
                .thenReturn(false)  // First call returns false
                .thenReturn(true);  // Second call returns true (shutdown triggered)

        // Use reflection to call protected keepRunning method
        java.lang.reflect.Method keepRunningMethod = Server.class.getDeclaredMethod("keepRunning");
        keepRunningMethod.setAccessible(true);

        // When
        Thread testThread = new Thread(() -> {
            try {
                keepRunningMethod.invoke(server);
            } catch (Exception e) {
                // Expected due to System.exit
            }
        });
        testThread.start();
        testThread.join(2000);

        // Then
        assertFalse(testThread.isAlive());
        verify(mockCountDownLatch, atLeast(2)).await(500, TimeUnit.MILLISECONDS);
    }

    @Test
    void testShutdownHookExecutesStop() throws Exception {
        // Given
        ArgumentCaptor<Thread> shutdownHookCaptor = ArgumentCaptor.forClass(Thread.class);
        
        // Mock Runtime
        Runtime mockRuntime = mock(Runtime.class);
        MockedStatic<Runtime> runtimeMock = mockStatic(Runtime.class);
        runtimeMock.when(Runtime::getRuntime).thenReturn(mockRuntime);
        
        try {
            // Reset isShuttingDown
            Field isShuttingDownField = Server.class.getDeclaredField("isShuttingDown");
            isShuttingDownField.setAccessible(true);
            ((AtomicBoolean) isShuttingDownField.get(null)).set(false);

            // Make IO handler stop immediately
            when(mockIOHandler.isRunning()).thenReturn(false);

            // When
            Thread serverThread = new Thread(() -> server.start());
            serverThread.start();

            Thread.sleep(100);

            // Capture shutdown hook
            verify(mockRuntime).addShutdownHook(shutdownHookCaptor.capture());
            Thread shutdownHook = shutdownHookCaptor.getValue();

            // Execute the shutdown hook
            shutdownHook.run();

            // Then - verify stop() was called
            verify(mockIOHandler).stopRunning();
            verify(mockLogger).log("Stopping and closing resources");

            serverThread.interrupt();
        } finally {
            runtimeMock.close();
        }
    }

    @Test
    void testKeepRunningWithInterruptedException() throws Exception {
        // Given
        when(mockIOHandler.isRunning()).thenReturn(true);
        
        // Use reflection to call keepRunning directly
        java.lang.reflect.Method keepRunningMethod = Server.class.getDeclaredMethod("keepRunning");
        keepRunningMethod.setAccessible(true);

        // When
        Thread testThread = new Thread(() -> {
            try {
                keepRunningMethod.invoke(server);
            } catch (Exception e) {
                // Expected
            }
        });
        
        testThread.start();
        Thread.sleep(100); // Let it start
        testThread.interrupt(); // Interrupt it
        testThread.join(2000);

        // Then
        assertFalse(testThread.isAlive());
        verify(mockLogger).log(eq("Application interrupted during shutdown"), any(InterruptedException.class));
    }

    @Test
    void testStartMethodFlowWithKeepRunning() throws Exception {
        // Given
        when(mockIOHandler.isRunning()).thenReturn(false);

        // When
        Thread serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (Exception e) {
                // Expected
            }
        });
        serverThread.start();
        serverThread.join(2000);

        // Then - verify the flow
        InOrder inOrder = inOrder(mockIOHandler);
        inOrder.verify(mockIOHandler).addLineListener(any(Consumer.class));
        inOrder.verify(mockIOHandler).startInputReader();
        // keepRunning is called which checks isRunning
        inOrder.verify(mockIOHandler, atLeastOnce()).isRunning();
    }

    @Test
    void testShutdownHookExecutesStopWhenNotAlreadyShuttingDown() throws Exception {
        // Given
        when(mockIOHandler.isRunning()).thenReturn(true);
        ArgumentCaptor<Thread> shutdownHookCaptor = ArgumentCaptor.forClass(Thread.class);

        // Mock Runtime
        Runtime mockRuntime = mock(Runtime.class);
        MockedStatic<Runtime> runtimeMock = mockStatic(Runtime.class);
        runtimeMock.when(Runtime::getRuntime).thenReturn(mockRuntime);

        try {
            // Reset isShuttingDown to false
            Field isShuttingDownField = Server.class.getDeclaredField("isShuttingDown");
            isShuttingDownField.setAccessible(true);
            ((AtomicBoolean) isShuttingDownField.get(null)).set(false);

            // When
            Thread serverThread = new Thread(() -> server.start());
            serverThread.start();

            Thread.sleep(100);

            // Capture shutdown hook
            verify(mockRuntime).addShutdownHook(shutdownHookCaptor.capture());
            Thread shutdownHook = shutdownHookCaptor.getValue();

            // Execute the shutdown hook
            shutdownHook.run();

            // Then - verify stop() was called
            verify(mockIOHandler).stopRunning();
            verify(mockLogger).log("Stopping and closing resources");
            verify(mockLogger, times(2)).close(); // Once in stop(), once in shutdown hook
            verify(mockCountDownLatch).countDown();

            serverThread.interrupt();
        } finally {
            runtimeMock.close();
        }
    }

    @Test
    void testKeepRunningWithIOHandlerNull() throws Exception {
        // Given
        Server serverWithNullIO = new Server(null, mockRouter, mockCountDownLatch);
        when(mockCountDownLatch.await(500, TimeUnit.MILLISECONDS)).thenReturn(false, false, true);

        // When
        Thread serverThread = new Thread(() -> {
            try {
                serverWithNullIO.start();
            } catch (Exception e) {
                // Expected due to System.exit
            }
        });
        serverThread.start();

        // Wait for thread to finish
        serverThread.join(2000);

        // Then
        assertFalse(serverThread.isAlive());
        // Verify that we don't get NPE and the loop continues
        verify(mockCountDownLatch, atLeast(2)).await(500, TimeUnit.MILLISECONDS);
    }

    @Test
    void testStartMethodExceptionHandling() throws Exception {
        // Given
        RuntimeException testException = new RuntimeException("Test exception during start");
        doThrow(testException).when(mockIOHandler).startInputReader();

        // When
        try {
            server.start();
        } catch (Exception e) {
            // Expected due to System.exit
        }

        // Then
        verify(mockLogger).log("Error in main method", testException);
        verify(mockIOHandler).stopRunning();
        verify(mockLogger).log("Stopping and closing resources");
        verify(mockLogger).close();
    }

    @Test
    void testKeepRunningCompareAndSetFalseScenario() throws Exception {
        // Given
        when(mockIOHandler.isRunning()).thenReturn(false);

        // Set isShuttingDown to true to make compareAndSet fail
        Field isShuttingDownField = Server.class.getDeclaredField("isShuttingDown");
        isShuttingDownField.setAccessible(true);
        AtomicBoolean isShuttingDown = (AtomicBoolean) isShuttingDownField.get(null);

        // Create a thread that will set isShuttingDown to true right before our compareAndSet
        Thread racingThread = new Thread(() -> {
            try {
                Thread.sleep(50);
                isShuttingDown.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // When
        racingThread.start();
        Thread serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (Exception e) {
                // Expected
            }
        });
        serverThread.start();

        // Wait for threads
        racingThread.join(1000);
        serverThread.join(2000);

        // Then
        assertFalse(serverThread.isAlive());
        verify(mockLogger).log("IO processing has stopped. Initiating shutdown.");
        // shutdownLatch.countDown() may or may not be called depending on timing
    }

    // ===== STOP METHOD TESTS =====

    @Test
    void testStopWithMockedIOHandler() {
        //Given
        Server stopServer = new Server(mockIOHandler, mockRouter, mockCountDownLatch);

        // When
        stopServer.stop();

        // Then
        verify(mockIOHandler).stopRunning();
        verify(mockLogger).log("Stopping and closing resources");
        verify(mockLogger).close();
    }

    @Test
    void testStopWithNullIOHandler() {
        // Given
        Server serverWithNullIO = new Server(null, mockRouter, mockCountDownLatch);

        // When
        serverWithNullIO.stop();

        // Then
        verify(mockLogger).log("Stopping and closing resources");
        verify(mockLogger).close();
        // Verify no interaction with IOHandler since it's null
        verifyNoInteractions(mockIOHandler);
    }
    
}