package com.workshop.mcp.io;

import java.util.function.Consumer;

/**
 * Interface for handling input/output operations in the MCP (Model Context Protocol) server.
 * 
 * <p>This interface defines the contract for classes that manage bidirectional communication,
 * typically between the MCP server and its clients. Implementations should handle:
 * <ul>
 *   <li>Reading input lines and notifying registered listeners</li>
 *   <li>Emitting output messages (typically as JSON)</li>
 *   <li>Managing the lifecycle of the input reader</li>
 * </ul>
 * </p>
 * 
 * <p>Implementations of this interface should be thread-safe, as input reading typically
 * occurs on a separate thread while output operations may be called from multiple threads.</p>
 * 
 * @author Workshop MCP Team
 * @version 1.0
 * @since 1.0
 * @see IOHandlerImpl
 */
public interface IOHandler {
    
    /**
     * Registers a listener to be notified when a line of input is received.
     * 
     * <p>The listener will be called each time a complete line is read from the input source.
     * Multiple listeners can be registered and should be notified in the order they were added.</p>
     * 
     * @param listener the consumer that will process input lines. Must not be null.
     * @throws NullPointerException if the listener is null (implementation-dependent)
     */
    void addLineListener(Consumer<String> listener);
    
    /**
     * Unregisters a previously added line listener.
     * 
     * <p>If the listener was not previously registered, this method should have no effect.
     * If the same listener was added multiple times, implementations may remove only the
     * first occurrence or all occurrences (implementation-specific).</p>
     * 
     * @param listener the listener to remove. May be null (no effect).
     */
    void removeLineListener(Consumer<String> listener);
    
    //void publishLine(String line);
    
    /**
     * Emits a message to the output destination.
     * 
     * <p>The message is typically serialized to JSON before being sent. Implementations
     * should ensure that the message is delivered promptly and may choose to flush
     * output buffers after each emission.</p>
     * 
     * @param message the object to emit. May be null. The object should be serializable
     *                to the output format (typically JSON).
     */
    void emit(Object message);
    
    /**
     * Starts the input reader to begin processing input.
     * 
     * <p>This method typically starts a separate thread or process to continuously read
     * from the input source and notify registered listeners. If the reader is already
     * running, implementations should handle this gracefully (e.g., by returning
     * immediately without starting a new reader).</p>
     * 
     * <p>The input reader should continue running until {@link #stopRunning()} is called,
     * the input source is closed, or an unrecoverable error occurs.</p>
     */
    void startInputReader();
    
    /**
     * Requests the input reader to stop.
     * 
     * <p>This method should signal the input reader to terminate gracefully. It should
     * be safe to call from any thread. After calling this method, {@link #isRunning()}
     * should eventually return false, though the exact timing depends on the implementation.</p>
     * 
     * <p>Implementations should ensure that calling this method multiple times is safe
     * and has no additional effect after the first call.</p>
     */
    void stopRunning();
    
    /**
     * Checks whether the input reader is currently running.
     * 
     * <p>This method provides a way to query the current state of the input reader.
     * It should return true if the reader is actively processing input or waiting
     * for input, and false if the reader has stopped or has not yet been started.</p>
     * 
     * @return true if the input reader is currently active, false otherwise
     */
    boolean isRunning();

}
