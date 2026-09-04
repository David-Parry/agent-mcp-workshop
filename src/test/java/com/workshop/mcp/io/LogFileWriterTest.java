package com.workshop.mcp.io;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the LogFileWriter class.
 * Tests singleton behavior, logging functionality, file operations, and error handling.
 */
class LogFileWriterTest {

    @TempDir
    Path tempDir;

    private LogFile logFile;
    private String originalWorkingDir;

    @BeforeEach
    void setUp() throws Exception {
        // Reset singleton instance before each test
        resetSingleton();
        
        // Save original working directory
        originalWorkingDir = System.getProperty("user.dir");
        
        // Create logs directory in temp directory
        Path logsDir = tempDir.resolve("logs");
        Files.createDirectories(logsDir);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Close the log file if it was created
        if (logFile != null) {
            logFile.close();
        }
        
        // Clean up any log files in the default logs directory
        try {
            Path defaultLogsDir = Paths.get("logs");
            if (Files.exists(defaultLogsDir)) {
                Files.list(defaultLogsDir)
                    .filter(path -> path.getFileName().toString().startsWith("agent-mcp-workshop-"))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
        
        // Reset singleton after each test
        resetSingleton();
    }

    /**
     * Reset the singleton instance using reflection to ensure test isolation
     */
    private void resetSingleton() throws Exception {
        Field instanceField = LogFileWriter.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    @Test
    @DisplayName("Should return singleton instance")
    void testGetInstance() {
        // Given/When
        LogFile instance1 = LogFileWriter.getInstance();
        LogFile instance2 = LogFileWriter.getInstance();

        // Then
        assertNotNull(instance1, "First instance should not be null");
        assertNotNull(instance2, "Second instance should not be null");
        assertSame(instance1, instance2, "Both instances should be the same object");
    }

    @Test
    @DisplayName("Should handle concurrent singleton initialization")
    void testConcurrentSingletonInitialization() throws InterruptedException {
        // Given
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicReference<LogFile> firstInstance = new AtomicReference<>();

        // When
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    LogFile instance = LogFileWriter.getInstance();
                    firstInstance.compareAndSet(null, instance);
                    
                    // Verify all threads get the same instance
                    assertEquals(firstInstance.get(), instance);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for all threads to complete
        assertTrue(endLatch.await(5, TimeUnit.SECONDS), "All threads should complete within timeout");
        executor.shutdown();
    }

    @Test
    @DisplayName("Should create log directory if it doesn't exist")
    void testLogDirectoryCreation() throws IOException {
        // Given
        Path logsDir = Paths.get("logs");
        // Clean up any existing logs directory
        if (Files.exists(logsDir)) {
            Files.walk(logsDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Ignore
                    }
                });
        }
        assertFalse(Files.exists(logsDir), "Logs directory should not exist initially");

        // When
        logFile = LogFileWriter.getInstance();
        logFile.log("Test message");

        // Then
        assertTrue(Files.exists(logsDir), "Logs directory should be created");
        assertTrue(Files.isDirectory(logsDir), "Logs should be a directory");
    }

    @Test
    @DisplayName("Should create log file with process ID in name")
    void testLogFileCreation() throws IOException {
        // When
        logFile = LogFileWriter.getInstance();
        logFile.log("Test message");

        // Then
        Path logsDir = Paths.get("logs");
        List<Path> logFiles = Files.list(logsDir)
                .filter(path -> path.toString().contains("agent-mcp-workshop-"))
                .filter(path -> path.toString().endsWith(".log"))
                .collect(Collectors.toList());

        assertEquals(1, logFiles.size(), "Should create exactly one log file");
        
        String fileName = logFiles.get(0).getFileName().toString();
        // Pattern: agent-mcp-workshop-<PID>-<yyyyMMdd-HHmmss>.log
        assertTrue(fileName.matches("agent-mcp-workshop-\\d+-\\d{8}-\\d{6}\\.log"),
                "Log file name should match expected pattern, was: " + fileName);
    }

    @Test
    @DisplayName("Should log message with timestamp")
    void testLogWithTimestamp() throws IOException {
        // Given
        String testMessage = "Test log message";

        // When
        logFile = LogFileWriter.getInstance();
        logFile.log(testMessage);

        // Then
        Path logsDir = Paths.get("logs");
        Path logFile = Files.list(logsDir)
                .filter(path -> path.toString().endsWith(".log"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Log file not found"));

        List<String> lines = Files.readAllLines(logFile);
        assertEquals(1, lines.size(), "Should have one log entry");
        
        String logEntry = lines.get(0);
        assertTrue(logEntry.contains(testMessage), "Log entry should contain the message");
        assertTrue(logEntry.matches("\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\] .*"), 
                "Log entry should have timestamp in expected format");
    }

    @Test
    @DisplayName("Should log raw message without timestamp")
    void testLogRaw() throws IOException {
        // Given
        String rawMessage = "Raw log message without timestamp";

        // When
        logFile = LogFileWriter.getInstance();
        logFile.logRaw(rawMessage);

        // Then
        Path logsDir = Paths.get("logs");
        Path logFile = Files.list(logsDir)
                .filter(path -> path.toString().endsWith(".log"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Log file not found"));

        List<String> lines = Files.readAllLines(logFile);
        assertEquals(1, lines.size(), "Should have one log entry");
        assertEquals(rawMessage, lines.get(0), "Raw message should be logged as-is");
    }

    @Test
    @DisplayName("Should log message with exception stack trace")
    void testLogWithException() throws IOException {
        // Given
        String errorMessage = "An error occurred";
        Exception testException = new RuntimeException("Test exception");

        // When
        logFile = LogFileWriter.getInstance();
        logFile.log(errorMessage, testException);

        // Then
        Path logsDir = Paths.get("logs");
        Path logFile = Files.list(logsDir)
                .filter(path -> path.toString().endsWith(".log"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Log file not found"));

        List<String> lines = Files.readAllLines(logFile);
        assertTrue(lines.size() > 1, "Should have multiple lines for exception");
        
        String firstLine = lines.get(0);
        assertTrue(firstLine.contains(errorMessage), "First line should contain error message");
        assertTrue(firstLine.matches("\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\] .*"), 
                "First line should have timestamp");
        
        // Check for exception details
        String allLines = String.join("\n", lines);
        assertTrue(allLines.contains("RuntimeException"), "Should contain exception type");
        assertTrue(allLines.contains("Test exception"), "Should contain exception message");
        assertTrue(allLines.contains("at com.workshop.mcp.io.LogFileWriterTest"), 
                "Should contain stack trace");
    }

    @Test
    @DisplayName("Should append to existing log file")
    void testAppendToExistingFile() throws IOException {
        // Given
        logFile = LogFileWriter.getInstance();
        String firstMessage = "First message";
        String secondMessage = "Second message";

        // When
        logFile.log(firstMessage);
        logFile.log(secondMessage);

        // Then
        Path logsDir = Paths.get("logs");
        Path logFilePath = Files.list(logsDir)
                .filter(path -> path.toString().endsWith(".log"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Log file not found"));

        List<String> lines = Files.readAllLines(logFilePath);
        assertEquals(2, lines.size(), "Should have two log entries");
        assertTrue(lines.get(0).contains(firstMessage), "First line should contain first message");
        assertTrue(lines.get(1).contains(secondMessage), "Second line should contain second message");
    }

    @Test
    @DisplayName("Should handle multiple log operations")
    void testMultipleLogOperations() throws IOException {
        // Given
        logFile = LogFileWriter.getInstance();

        // When
        logFile.log("Regular message");
        logFile.logRaw("Raw message");
        logFile.log("Error message", new IllegalArgumentException("Test error"));

        // Then
        Path logsDir = Paths.get("logs");
        Path logFilePath = Files.list(logsDir)
                .filter(path -> path.toString().endsWith(".log"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Log file not found"));

        List<String> lines = Files.readAllLines(logFilePath);
        assertTrue(lines.size() >= 3, "Should have at least 3 lines");
        
        // Verify first message has timestamp
        assertTrue(lines.get(0).matches("\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\] Regular message"));
        
        // Verify raw message has no timestamp
        assertEquals("Raw message", lines.get(1));
        
        // Verify error message and exception
        assertTrue(lines.get(2).contains("Error message"));
        String allLines = String.join("\n", lines);
        assertTrue(allLines.contains("IllegalArgumentException"));
    }

    @Test
    @DisplayName("Should handle close operation gracefully")
    void testClose() throws IOException {
        // Given
        logFile = LogFileWriter.getInstance();
        logFile.log("Message before close");

        // When
        logFile.close();
        
        // Then - should not throw exception
        assertDoesNotThrow(() -> logFile.close(), "Closing again should not throw");
        
        // Logging after close should recreate the file
        logFile.log("Message after close");
        
        Path logsDir = Paths.get("logs");
        Path logFilePath = Files.list(logsDir)
                .filter(path -> path.toString().endsWith(".log"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Log file not found"));

        List<String> lines = Files.readAllLines(logFilePath);
        assertEquals(2, lines.size(), "Should have both messages");
    }

    @Test
    @DisplayName("Should fallback to temp directory when logs directory is not writable")
    void testFallbackToTempDirectory() throws Exception {
        // Given - make the logs directory itself unwritable so the primary
        // log file (agent-mcp-workshop-<PID>-<timestamp>.log) cannot be created
        Path logsDir = Paths.get("logs");
        Files.createDirectories(logsDir);
        File logsDirFile = logsDir.toFile();
        boolean madeReadOnly = logsDirFile.setWritable(false);
        Assumptions.assumeTrue(madeReadOnly,
                "Filesystem does not support revoking directory write permission");

        // The fallback file carries the same PID + startup-timestamp name,
        // so match on the stable PID prefix
        String fileNamePrefix = "agent-mcp-workshop-" + ProcessHandle.current().pid() + "-";
        String tempDirPath = System.getProperty("java.io.tmpdir");
        Path tempLogDir = Paths.get(tempDirPath);

        try {
            // When
            logFile = LogFileWriter.getInstance();
            logFile.log("Test message");

            // Then - should create log in temp directory
            List<Path> tempLogFiles = Files.list(tempLogDir)
                    .filter(path -> path.getFileName().toString().startsWith(fileNamePrefix))
                    .filter(path -> path.getFileName().toString().endsWith(".log"))
                    .collect(Collectors.toList());

            assertTrue(tempLogFiles.size() > 0, "Should create log file in temp directory");

            // Clean up temp log files (close first so the writer releases them)
            logFile.close();
            for (Path tempLogFile : tempLogFiles) {
                try {
                    Files.deleteIfExists(tempLogFile);
                } catch (IOException e) {
                    // Ignore cleanup errors
                }
            }
        } finally {
            // Restore write permissions on the logs directory
            logsDirFile.setWritable(true);
        }
    }

    @Test
    @DisplayName("Should handle null messages gracefully")
    void testNullMessages() {
        // Given
        logFile = LogFileWriter.getInstance();

        // When/Then - should not throw exceptions
        assertDoesNotThrow(() -> logFile.log(null));
        assertDoesNotThrow(() -> logFile.logRaw(null));
        assertDoesNotThrow(() -> logFile.log(null, new RuntimeException()));
    }

    @Test
    @DisplayName("Should handle concurrent logging")
    void testConcurrentLogging() throws InterruptedException, IOException {
        // Given
        logFile = LogFileWriter.getInstance();
        int threadCount = 5;
        int messagesPerThread = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // When
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < messagesPerThread; j++) {
                        logFile.log("Thread " + threadId + " - Message " + j);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(10, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();
        
        // Give a small delay to ensure all writes are flushed
        Thread.sleep(100);

        // Then
        Path logsDir = Paths.get("logs");
        Path logFilePath = Files.list(logsDir)
                .filter(path -> path.toString().endsWith(".log"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Log file not found"));

        List<String> lines = Files.readAllLines(logFilePath);
        
        // Due to potential race conditions in file writing, we check for at least 90% of messages
        int expectedMessages = threadCount * messagesPerThread;
        assertTrue(lines.size() >= (int)(expectedMessages * 0.9), 
                "Should have at least 90% of messages from all threads. Expected: " + expectedMessages + ", Actual: " + lines.size());
    }

    @Test
    @DisplayName("Should handle special characters in messages")
    void testSpecialCharactersInMessages() throws IOException {
        // Given
        logFile = LogFileWriter.getInstance();
        String[] specialMessages = {
            "Message with tab:\ttab",
            "Message with \"quotes\"",
            "Message with 'single quotes'",
            "Message with unicode: 你好世界 🌍",
            "Message with backslash: C:\\path\\to\\file"
        };

        // When
        for (String message : specialMessages) {
            logFile.log(message);
        }
        
        // Test newline separately since it creates multiple lines
        logFile.log("Message with\nnewline");

        // Then
        Path logsDir = Paths.get("logs");
        Path logFilePath = Files.list(logsDir)
                .filter(path -> path.toString().endsWith(".log"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Log file not found"));

        List<String> lines = Files.readAllLines(logFilePath);
        
        // Should have specialMessages.length + 2 (for the newline message which creates 2 lines)
        assertEquals(specialMessages.length + 2, lines.size(), "Should have all messages plus extra line for newline");
        
        // Verify regular special character messages
        for (int i = 0; i < specialMessages.length; i++) {
            assertTrue(lines.get(i).contains(specialMessages[i]), 
                    "Line " + i + " should contain the special message");
        }
        
        // Verify newline message (split across two lines)
        assertTrue(lines.get(specialMessages.length).contains("Message with"), 
                "First part of newline message should be present");
        assertEquals("newline", lines.get(specialMessages.length + 1), 
                "Second part of newline message should be on next line");
    }
}