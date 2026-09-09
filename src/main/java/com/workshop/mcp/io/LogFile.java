package com.workshop.mcp.io;

/**
 * Interface for logging operations in the MCP workshop application.
 * 
 * <p>This interface defines the contract for logging services that can:
 * <ul>
 *   <li>Log simple text messages with automatic timestamping</li>
 *   <li>Log messages with associated exceptions and stack traces</li>
 *   <li>Log raw messages without automatic formatting</li>
 *   <li>Properly close and release logging resources</li>
 * </ul>
 * </p>
 * 
 * <p>Implementations should be thread-safe and handle logging failures gracefully
 * to prevent cascading errors in the application. The interface supports both
 * formatted logging (with timestamps) and raw logging for cases where the caller
 * wants full control over the message format.</p>
 * 
 * @author Workshop MCP Team
 * @version 1.0
 * @since 1.0
 * @see LogFileWriter
 */
public interface LogFile {

    /**
     * Closes the logger and releases any associated resources.
     * 
     * <p>This method should be called when the logger is no longer needed
     * to ensure proper cleanup of file handles, streams, or other resources.
     * After calling this method, subsequent logging operations may fail or
     * have undefined behavior.</p>
     * 
     * <p>Implementations should make this method idempotent (safe to call
     * multiple times) and should not throw exceptions during cleanup.</p>
     */
    void close();

    /**
     * Logs a message along with an exception and its stack trace.
     * 
     * <p>This method is typically used for error logging where both a
     * descriptive message and exception details are needed. The implementation
     * should include both the message and the full stack trace in the log output.</p>
     * 
     * <p>The message will typically be formatted with a timestamp, and the
     * exception stack trace will be included in the log entry.</p>
     * 
     * @param message the descriptive message to log. May be null.
     * @param exception the exception to log with its stack trace. May be null.
     */
    void log(String message, Throwable exception);

    /**
     * Logs a simple text message with automatic formatting.
     * 
     * <p>This is the primary logging method for general application messages.
     * The implementation typically adds a timestamp and any other standard
     * formatting before writing the message to the log destination.</p>
     * 
     * @param message the message to log. May be null.
     */
    void log(String message);

    /**
     * Logs a raw message without any automatic formatting or timestamping.
     * 
     * <p>This method provides direct control over the log output format.
     * The message is written exactly as provided, without any additional
     * formatting, timestamps, or prefixes. This is useful for logging
     * pre-formatted messages or when the caller needs complete control
     * over the output format.</p>
     * 
     * @param message the raw message to log exactly as provided. May be null.
     */
    void logRaw(String message);

}
