package com.workshop.mcp.io;

import com.google.gson.Gson;

import java.io.PrintWriter;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Implementation of the IOHandler interface that provides methods for handling input and output operations.
 * This class supports console input/output operations and can publish events when input is received.
 * 
 * <p>The implementation uses a separate thread to read input from System.in and notifies registered
 * listeners when new lines are received. Output is written to System.out using a PrintWriter.</p>
 * 
 * <p>Thread Safety: This class is thread-safe. The line listeners are stored in a CopyOnWriteArrayList
 * and the running state is managed using an AtomicBoolean.</p>
 * 
 * @author Workshop MCP Team
 * @version 1.0
 * @since 1.0
 */
public class IOHandlerImpl implements IOHandler {
    /** Logger instance for recording IO operations and errors */
    private final static LogFile logger = LogFileWriter.getInstance();
    
    /** PrintWriter for sending output to System.out */
    private final PrintWriter writer;
    
    /** Thread-safe list of consumers that are notified when input lines are received */
    private final List<Consumer<String>> lineListeners;
    
    /** Atomic flag indicating whether the input reader thread is running */
    private final AtomicBoolean running;
    
    /** Gson instance for JSON serialization of output messages */
    private final Gson gson = new Gson();

    /**
     * Constructs an IOHandlerImpl that uses System.in for input and System.out for output.
     * 
     * <p>The constructor initializes:
     * <ul>
     *   <li>A PrintWriter with auto-flush enabled for immediate output</li>
     *   <li>An empty list of line listeners</li>
     *   <li>The running flag set to false</li>
     * </ul>
     * </p>
     */
    public IOHandlerImpl() {
        this.writer = new PrintWriter(System.out, true);
        this.lineListeners = new CopyOnWriteArrayList<>();
        this.running = new AtomicBoolean(false);
    }

    /**
     * Adds a listener that will be notified when a line of input is read.
     * 
     * <p>The listener will be called on the input reader thread each time a complete
     * line is read from the input stream. Multiple listeners can be registered and
     * they will be called in the order they were added.</p>
     *
     * @param listener Consumer that will receive the line of input. Must not be null.
     * @throws NullPointerException if listener is null
     */
    public void addLineListener(Consumer<String> listener) {
        lineListeners.add(listener);
    }

    /**
     * Removes a previously registered line listener.
     * 
     * <p>If the listener was not previously registered, this method has no effect.
     * If the same listener was added multiple times, only the first occurrence
     * is removed.</p>
     *
     * @param listener The listener to remove. Can be null (no effect).
     */
    public void removeLineListener(Consumer<String> listener) {
        lineListeners.remove(listener);
    }

    /**
     * Notifies all registered line listeners of a new line of input.
     * 
     * <p>This method iterates through all registered listeners and calls each one
     * with the provided line. If a listener throws an exception, the error is
     * logged but does not prevent other listeners from being notified.</p>
     *
     * @param line The line to publish to all listeners. Can be null.
     */
    private void publishLine(String line) {
        // >>> STEP 2: paste the listener notification loop here (lessons/presentation-3hr/walkthrough.md)
    }

    /**
     * Emits a message to the output stream as JSON.
     * 
     * <p>The message object is serialized to JSON using Gson, logged with an
     * [API][SENT] prefix, and then written to the output stream. The output
     * is flushed immediately to ensure timely delivery.</p>
     *
     * @param message The object to emit. Will be serialized to JSON. Can be null.
     */
    @Override
    public void emit(Object message) {
        // >>> STEP 3: paste the JSON serialize-log-write-flush body here (lessons/presentation-3hr/walkthrough.md)
    }

    /**
     * Starts the input reader thread that continuously reads lines from System.in.
     * 
     * <p>This method creates a Scanner to read from System.in and enters a loop that:
     * <ul>
     *   <li>Reads lines from the input stream</li>
     *   <li>Logs each received line with an [API][RECEIVED] prefix</li>
     *   <li>Publishes each line to all registered listeners</li>
     * </ul>
     * </p>
     * 
     * <p>The reader continues until:
     * <ul>
     *   <li>The input stream is closed</li>
     *   <li>stopRunning() is called</li>
     *   <li>An unrecoverable error occurs</li>
     * </ul>
     * </p>
     * 
     * <p>If the reader is already running, this method returns immediately without
     * starting a new reader.</p>
     * 
     * @throws RuntimeException if a fatal error occurs during reading
     */
    @Override
    public void startInputReader() {
        // >>> STEP 1: paste the Scanner read loop here (lessons/presentation-3hr/walkthrough.md)
    }

    /**
     * Stops the input reader thread.
     * 
     * <p>This method sets the running flag to false, which will cause the input
     * reader loop to exit on its next iteration. This is a safe way to stop
     * the reader from any thread.</p>
     */
    @Override
    public void stopRunning() {
        running.set(false);
    }

    /**
     * Checks whether the input reader is currently running.
     * 
     * @return true if the input reader thread is active, false otherwise
     */
    @Override
    public boolean isRunning() {
        return running.get();
    }

}
