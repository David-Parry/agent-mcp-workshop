package com.workshop.mcp.io;

import com.google.gson.Gson;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@Tag("chapter01")
class IOHandlerImplTest {

    private IOHandlerImpl ioHandler;
    private ByteArrayOutputStream outputStream;
    private PrintStream originalOut;
    private InputStream originalIn;
    private Gson gson;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        originalOut = System.out;
        originalIn = System.in;
        System.setOut(new PrintStream(outputStream));
        // Create IOHandlerImpl after redirecting System.out
        ioHandler = new IOHandlerImpl();
        gson = new Gson();
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setIn(originalIn);
        if (ioHandler.isRunning()) {
            ioHandler.stopRunning();
        }
    }

    @Test
    void testEmit_WithStringMessage() {
        // Given
        String message = "Hello, World!";

        // When
        ioHandler.emit(message);

        // Then
        String output = outputStream.toString().trim();
        assertEquals("\"Hello, World!\"", output);
    }

    @Test
    void testEmit_WithObjectMessage() {
        // Given
        TestMessage message = new TestMessage("test", 123);

        // When
        ioHandler.emit(message);

        // Then
        String output = outputStream.toString().trim();
        TestMessage deserializedMessage = gson.fromJson(output, TestMessage.class);
        assertEquals("test", deserializedMessage.name);
        assertEquals(123, deserializedMessage.value);
    }

    @Test
    void testEmit_WithNullMessage() {
        // When
        ioHandler.emit(null);

        // Then
        String output = outputStream.toString().trim();
        assertEquals("null", output);
    }

    @Test
    void testAddLineListener() throws InterruptedException {
        // Given
        String testInput = "Test line\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(testInput.getBytes());
        System.setIn(inputStream);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedLine = new AtomicReference<>();
        Consumer<String> listener = line -> {
            receivedLine.set(line);
            latch.countDown();
        };

        // When
        ioHandler.addLineListener(listener);
        Thread readerThread = new Thread(() -> ioHandler.startInputReader());
        readerThread.start();

        // Then
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals("Test line", receivedLine.get());
        
        ioHandler.stopRunning();
        readerThread.join(1000);
    }

    @Test
    void testMultipleLineListeners() throws InterruptedException {
        // Given
        String testInput = "Test line\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(testInput.getBytes());
        System.setIn(inputStream);

        CountDownLatch latch = new CountDownLatch(3);
        List<String> receivedLines = new ArrayList<>();
        
        Consumer<String> listener1 = line -> {
            receivedLines.add("Listener1: " + line);
            latch.countDown();
        };
        Consumer<String> listener2 = line -> {
            receivedLines.add("Listener2: " + line);
            latch.countDown();
        };
        Consumer<String> listener3 = line -> {
            receivedLines.add("Listener3: " + line);
            latch.countDown();
        };

        // When
        ioHandler.addLineListener(listener1);
        ioHandler.addLineListener(listener2);
        ioHandler.addLineListener(listener3);
        
        Thread readerThread = new Thread(() -> ioHandler.startInputReader());
        readerThread.start();

        // Then
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(3, receivedLines.size());
        assertTrue(receivedLines.contains("Listener1: Test line"));
        assertTrue(receivedLines.contains("Listener2: Test line"));
        assertTrue(receivedLines.contains("Listener3: Test line"));
        
        ioHandler.stopRunning();
        readerThread.join(1000);
    }

    @Test
    void testRemoveLineListener() throws InterruptedException {
        // Given
        String testInput = "Test line\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(testInput.getBytes());
        System.setIn(inputStream);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean listener1Called = new AtomicBoolean(false);
        AtomicBoolean listener2Called = new AtomicBoolean(false);
        
        Consumer<String> listener1 = line -> listener1Called.set(true);
        Consumer<String> listener2 = line -> {
            listener2Called.set(true);
            latch.countDown();
        };

        // When
        ioHandler.addLineListener(listener1);
        ioHandler.addLineListener(listener2);
        ioHandler.removeLineListener(listener1);
        
        Thread readerThread = new Thread(() -> ioHandler.startInputReader());
        readerThread.start();

        // Then
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertFalse(listener1Called.get());
        assertTrue(listener2Called.get());
        
        ioHandler.stopRunning();
        readerThread.join(1000);
    }

    @Test
    void testRemoveNullListener() {
        // Should not throw exception
        assertDoesNotThrow(() -> ioHandler.removeLineListener(null));
    }

    @Test
    void testListenerExceptionHandling() throws InterruptedException {
        // Given
        String testInput = "Test line\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(testInput.getBytes());
        System.setIn(inputStream);

        CountDownLatch latch = new CountDownLatch(2);
        AtomicBoolean listener2Called = new AtomicBoolean(false);
        
        Consumer<String> listener1 = line -> {
            latch.countDown();
            throw new RuntimeException("Listener 1 error");
        };
        Consumer<String> listener2 = line -> {
            listener2Called.set(true);
            latch.countDown();
        };

        // When
        ioHandler.addLineListener(listener1);
        ioHandler.addLineListener(listener2);
        
        Thread readerThread = new Thread(() -> ioHandler.startInputReader());
        readerThread.start();

        // Then
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(listener2Called.get()); // Listener 2 should still be called despite listener 1 throwing
        
        ioHandler.stopRunning();
        readerThread.join(1000);
    }

    @Test
    void testStartInputReader_AlreadyRunning() throws InterruptedException {
        // Given
        String testInput = "Line 1\nLine 2\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(testInput.getBytes());
        System.setIn(inputStream);

        CountDownLatch latch = new CountDownLatch(2);
        List<String> receivedLines = new ArrayList<>();
        
        Consumer<String> listener = line -> {
            receivedLines.add(line);
            latch.countDown();
        };
        ioHandler.addLineListener(listener);

        // When
        Thread readerThread1 = new Thread(() -> ioHandler.startInputReader());
        readerThread1.start();
        Thread.sleep(100); // Give first thread time to start
        
        Thread readerThread2 = new Thread(() -> ioHandler.startInputReader());
        readerThread2.start();

        // Then
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(2, receivedLines.size()); // Should only receive lines once
        
        ioHandler.stopRunning();
        readerThread1.join(1000);
        readerThread2.join(1000);
    }

    @Test
    void testStopRunning() throws InterruptedException {
        // Given
        String testInput = "Line 1\nLine 2\nLine 3\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(testInput.getBytes());
        System.setIn(inputStream);

        CountDownLatch latch = new CountDownLatch(1);
        Consumer<String> listener = line -> latch.countDown();
        ioHandler.addLineListener(listener);

        // When
        Thread readerThread = new Thread(() -> ioHandler.startInputReader());
        readerThread.start();
        
        assertTrue(latch.await(2, TimeUnit.SECONDS)); // Wait for at least one line
        assertTrue(ioHandler.isRunning());
        
        ioHandler.stopRunning();
        readerThread.join(1000);

        // Then
        assertFalse(ioHandler.isRunning());
    }

    @Test
    void testIsRunning() throws InterruptedException {
        // Given
        assertFalse(ioHandler.isRunning());

        String testInput = "Test\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(testInput.getBytes());
        System.setIn(inputStream);

        CountDownLatch startedLatch = new CountDownLatch(1);
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        
        // Add a listener to know when the reader is actually processing
        ioHandler.addLineListener(line -> {
            listenerCalled.set(true);
            startedLatch.countDown();
        });

        // When
        Thread readerThread = new Thread(() -> ioHandler.startInputReader());
        readerThread.start();
        
        // Wait for the reader to actually start processing
        assertTrue(startedLatch.await(2, TimeUnit.SECONDS));

        // Then
        assertTrue(listenerCalled.get());
        
        ioHandler.stopRunning();
        readerThread.join(1000);
        assertFalse(ioHandler.isRunning());
    }

    @Test
    void testEmptyInputStream() throws InterruptedException {
        // Given
        ByteArrayInputStream emptyStream = new ByteArrayInputStream(new byte[0]);
        System.setIn(emptyStream);

        // When
        Thread readerThread = new Thread(() -> ioHandler.startInputReader());
        readerThread.start();
        readerThread.join(1000);

        // Then
        assertFalse(ioHandler.isRunning());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testMultipleLines() throws InterruptedException {
        // Given
        String testInput = "Line 1\nLine 2\nLine 3\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(testInput.getBytes());
        System.setIn(inputStream);

        CountDownLatch latch = new CountDownLatch(3);
        List<String> receivedLines = new ArrayList<>();
        
        Consumer<String> listener = line -> {
            receivedLines.add(line);
            latch.countDown();
        };
        ioHandler.addLineListener(listener);

        // When
        Thread readerThread = new Thread(() -> ioHandler.startInputReader());
        readerThread.start();

        // Then
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(3, receivedLines.size());
        assertEquals("Line 1", receivedLines.get(0));
        assertEquals("Line 2", receivedLines.get(1));
        assertEquals("Line 3", receivedLines.get(2));
        
        ioHandler.stopRunning();
        readerThread.join(1000);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testStartInputReader_ReentrantCall_ReturnsWithoutDisturbingTheReader() {
        // Given
        String testInput = "only line\n";
        System.setIn(new ByteArrayInputStream(testInput.getBytes()));

        List<String> receivedLines = new ArrayList<>();
        AtomicBoolean stillRunningAfterReentrantCall = new AtomicBoolean();
        Consumer<String> listener = line -> {
            receivedLines.add(line);
            // The running flag is set while listeners are notified, so this
            // nested call has to bail out instead of draining the stream again
            ioHandler.startInputReader();
            stillRunningAfterReentrantCall.set(ioHandler.isRunning());
        };
        ioHandler.addLineListener(listener);

        // When
        ioHandler.startInputReader();

        // Then
        assertEquals(1, receivedLines.size());
        assertEquals("only line", receivedLines.get(0));
        assertTrue(stillRunningAfterReentrantCall.get());
        assertFalse(ioHandler.isRunning());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testStopRunning_FromListener_HaltsTheReaderLoop() {
        // Given
        String testInput = "first\nsecond\n";
        System.setIn(new ByteArrayInputStream(testInput.getBytes()));

        List<String> receivedLines = new ArrayList<>();
        Consumer<String> listener = line -> {
            receivedLines.add(line);
            ioHandler.stopRunning();
        };
        ioHandler.addLineListener(listener);

        // When
        ioHandler.startInputReader();

        // Then
        assertEquals(1, receivedLines.size(), "The loop must not read past the stop request");
        assertEquals("first", receivedLines.get(0));
        assertFalse(ioHandler.isRunning());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testStartInputReader_WithFailingInputStream_SwallowsTheFailure() {
        // Given
        System.setIn(new FailingInputStream(() -> { }));

        // When & Then
        assertDoesNotThrow(() -> ioHandler.startInputReader());
        assertFalse(ioHandler.isRunning());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testStartInputReader_WithFailingInputStreamAfterStopRunning_SwallowsTheFailure() {
        // Given - the reader is told to stop before the read blows up, so the
        // failure is expected rather than logged
        System.setIn(new FailingInputStream(ioHandler::stopRunning));

        // When & Then
        assertDoesNotThrow(() -> ioHandler.startInputReader());
        assertFalse(ioHandler.isRunning());
    }

    // Input stream that fails on the first read, running a hook just beforehand
    private static class FailingInputStream extends InputStream {
        private final Runnable beforeFailure;

        FailingInputStream(Runnable beforeFailure) {
            this.beforeFailure = beforeFailure;
        }

        @Override
        public int read() {
            return fail();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return fail();
        }

        private int fail() {
            beforeFailure.run();
            throw new IllegalStateException("input stream failure");
        }
    }

    // Helper class for testing JSON serialization
    private static class TestMessage {
        String name;
        int value;

        TestMessage(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }
}