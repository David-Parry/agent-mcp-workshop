package com.workshop.mcp.io;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogFileWriter implements LogFile {
    private static final Object lock = new Object();
    private final static String DEFAULT_LOG_DIR = "logs";
    private final static String DEFAULT_LOG_FILE_NAME = "agent-mcp-workshop";
    private final static String DEFAULT_LOG_FILE_EXTENSION = ".log";
    private static volatile LogFileWriter instance;
    private final SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    /**
     * Resolved once – cached for subsequent log rotations.
     */
    private final String processId = resolveProcessId();

    private PrintWriter logWriter;

    /**
     * Get the singleton instance of LogFileWriter
     *
     * @return LogFile interface instance
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

    private static String getDefaultLogFileName(String logDir, String processId) {
        return logDir + "/" + DEFAULT_LOG_FILE_NAME + "-" + processId + DEFAULT_LOG_FILE_EXTENSION;
    }

    /**
     * Initialize the log directory
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
            // Fallback for Java 8: parse RuntimeMXBean name (format pid@hostname)
            String jvmName = ManagementFactory.getRuntimeMXBean().getName();
            int idx = jvmName.indexOf('@');
            return (idx > 0) ? jvmName.substring(0, idx) : jvmName;
        }
    }
}