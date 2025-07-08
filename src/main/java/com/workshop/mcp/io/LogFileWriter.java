package com.workshop.mcp.io;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * File-based implementation of the LogFile interface that provides persistent logging capabilities.
 *
 * <p>This class implements a singleton pattern to ensure a single logging instance across the application.
 * It creates process-specific log files in a dedicated logs directory and handles log file creation,
 * rotation, and cleanup automatically.</p>
 *
 * <p>Key features:
 * <ul>
 *   <li>Singleton pattern for application-wide logging consistency</li>
 *   <li>Process-specific log file naming using PID</li>
 *   <li>Automatic log directory creation</li>
 *   <li>Fallback to system temp directory if primary log directory is unavailable</li>
 *   <li>Thread-safe initialization using double-checked locking</li>
 *   <li>Graceful error handling to prevent logging failures from affecting application flow</li>
 * </ul>
 * </p>
 *
 * <p>Log files are created with the naming pattern: {@code agent-mcp-workshop-<PID>.log}
 * in the {@code logs/} directory relative to the application's working directory.</p>
 *
 * <p>Thread Safety: This class is thread-safe for initialization. However, individual
 * logging operations are not synchronized, so concurrent logging from multiple threads
 * may result in interleaved output.</p>
 *
 * @author Workshop MCP Team
 * @version 1.0
 * @since 1.0
 * @see LogFile
 */
public class LogFileWriter implements LogFile {
    /** Synchronization lock for thread-safe singleton initialization */
    private static final Object lock = new Object();

    /** Default directory name for log files */
    private final static String DEFAULT_LOG_DIR = "logs";

    /** Base name for log files (without PID suffix and extension) */
    private final static String DEFAULT_LOG_FILE_NAME = "agent-mcp-workshop";

    /** File extension for log files */
    private final static String DEFAULT_LOG_FILE_EXTENSION = ".log";

    /** Singleton instance, marked volatile for thread safety */
    private static volatile LogFileWriter instance;

    /** Date formatter for timestamp generation in log entries */
    private final SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * Process ID resolved once at initialization and cached for subsequent log operations.
     * This ensures consistent file naming throughout the application lifecycle.
     */
    private final String processId = resolveProcessId();

    /** PrintWriter for writing log entries to the file */
    private PrintWriter logWriter;

    /**
     * Gets the singleton instance of LogFileWriter using double-checked locking.
     *
     * <p>This method implements the double-checked locking pattern to ensure
     * thread-safe lazy initialization of the singleton instance. The first
     * call will create the instance, and subsequent calls will return the
     * same instance.</p>
     *
     * @return the singleton LogFile instance
     */
    public static LogFile getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new LogFileWriter();
                }
            }
        }
        return instance;
    }

    /**
     * Constructs the full log file path using the specified directory and process ID.
     *
     * <p>The resulting path follows the pattern: {@code <logDir>/<baseName>-<processId>.log}</p>
     *
     * @param logDir the directory where the log file should be created
     * @param processId the process ID to include in the filename
     * @return the complete file path for the log file
     */
    private static String getDefaultLogFileName(String logDir, String processId) {
        return logDir + "/" + DEFAULT_LOG_FILE_NAME + "-" + processId + DEFAULT_LOG_FILE_EXTENSION;
    }

    /**
     * Initializes the log directory by creating it if it doesn't exist.
     *
     * <p>This method ensures that the default log directory exists before
     * attempting to create log files. If the directory doesn't exist,
     * it will be created along with any necessary parent directories.</p>
     */
    private void initialize() {
        File logDir = new File(DEFAULT_LOG_DIR);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
    }

    /**
     * Create a new log file with a PID-specific name. If the file already exists,
     * archive the previous one by appending a timestamp before starting a new
     * log for the current process.
     *
     * @throws IOException If there's an error creating the log file
     */
    private void createNewLogFile() throws IOException {
        if (logWriter != null) {
            logWriter.close();
        }
        initialize();
        String currentLogFile = getDefaultLogFileName(DEFAULT_LOG_DIR, processId);
        try {
            logWriter = new PrintWriter(new FileWriter(currentLogFile, true));
        } catch (IOException e) {
            // If we can't write to the specified log directory, try the system temp directory
            String tempLogFile = getDefaultLogFileName(System.getProperty("java.io.tmpdir"), processId);
            logWriter = new PrintWriter(new FileWriter(tempLogFile, true));
        }
    }

    public void log(String message) {
        String timestamp = timestampFormat.format(new Date());
        logRaw("[" + timestamp + "] " + message);
    }

    /**
     * Log a raw message without any timestamp or formatting.
     *
     * <p>This method is intended for low-level logging where the caller
     * provides the complete message to be logged. It does not add any
     * additional formatting or timestamps.</p>
     *
     * @param message the raw message to log
     */
    public void logRaw(String message) {
        try {
            if (logWriter == null) {
                createNewLogFile();
            }
            logWriter.println(message);
            logWriter.flush();
        } catch (IOException e) {
            // Ignore logging failures – prevent cascading errors
        }
    }

    /**
     * Log a message with an associated exception stack trace.
     *
     * <p>This method logs the message along with the stack trace of the provided
     * exception. It ensures that both the message and exception details are
     * captured in the log file for debugging purposes.</p>
     *
     * @param message the message to log
     * @param exception the exception whose stack trace should be logged
     */
    public void log(String message, Throwable exception) {
        try {
            if (logWriter == null) {
                createNewLogFile();
            }

            String timestamp = timestampFormat.format(new Date());
            logWriter.println("[" + timestamp + "] " + message);
            exception.printStackTrace(logWriter);
            logWriter.flush();
        } catch (IOException e) {
            // Ignore logging failures – prevent cascading errors
        }
    }

    /**
     * Close the logger and release resources
     */
    public void close() {
        try {
            if (logWriter != null) {
                logWriter.close();
                logWriter = null;
            }
        } catch (Exception ignored) {
            // Ignore any exceptions during close
        }
    }

    /**
     * Obtain the current process ID in a JVM-agnostic way (works on Java 8+).
     *
     * @return a non-empty string representing the process ID
     */
    private String resolveProcessId() {
        // Attempt to use the Java 9+ API reflectively to avoid compile-time dependency
        try {
            Class<?> processHandleClass = Class.forName("java.lang.ProcessHandle");
            Object currentHandle = processHandleClass.getMethod("current").invoke(null);
            long pid = (long) processHandleClass.getMethod("pid").invoke(currentHandle);
            return String.valueOf(pid);
        } catch (Exception ignored) {
            return "unknown";
        }
    }
}