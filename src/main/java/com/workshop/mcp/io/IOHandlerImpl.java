package com.workshop.mcp.io;

import com.workshop.mcp.spec.InitializeResult;
import com.workshop.mcp.spec.JsonRpcResponse;
import com.google.gson.Gson;

import java.io.PrintWriter;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * IOHandler provides methods for handling input and output operations.
 * It supports console input/output, file operations, and formatted output.
 * It can also publish events when input is received.
 */
public class IOHandlerImpl implements IOHandler {
    private final static LogFile logger = LogFileWriter.getInstance();
    private final PrintWriter writer;
    private final List<Consumer<String>> lineListeners;
    private final AtomicBoolean running;
    private final Gson gson = new Gson();

    /**
     * Constructs an IOHandler that uses System.in and System.out
     */
    public IOHandlerImpl() {
        this.writer = new PrintWriter(System.out, true);
        this.lineListeners = new CopyOnWriteArrayList<>();
        this.running = new AtomicBoolean(false);
    }


    /**
     * Adds a listener that will be notified when a line of input is read
     *
     * @param listener Consumer that will receive the line of input
     */
    public void addLineListener(Consumer<String> listener) {
        lineListeners.add(listener);
    }

    /**
     * Removes a line listener
     *
     * @param listener The listener to remove
     */
    public void removeLineListener(Consumer<String> listener) {
        lineListeners.remove(listener);
    }

    /**
     * Notifies all line listeners of a new line of input
     *
     * @param line The line to publish
     */
    private void publishLine(String line) {
        for (Consumer<String> listener : lineListeners) {
            try {
                listener.accept(line);
            } catch (Exception e) {
                logger.log("Error in line listener", e);
            }
        }
    }

    public void emit(Object message) {
        String text = gson.toJson(message);
        logger.log("[API][SENT]: " + text);
        writer.println(text);
        writer.flush();
    }

    /**
     * Checks if the message is an InitializeResult response
     *
     * @param message The JSON message to check
     * @return true if this is an InitializeResult message
     */
    private boolean isInitializeResult(Object message) {
        // Simple check for InitializeResult - looking for the specific structure
        return message instanceof JsonRpcResponse && ((JsonRpcResponse) message).result() instanceof InitializeResult;
    }


    public void startInputReader() {
        if (running.get()) {
            return; // Already running
        }

        try (Scanner scanner = new Scanner(System.in)) {
            running.set(true);
            try {
                while (running.get()) {
                    if (!scanner.hasNextLine()) {
                        logger.log("Input stream closed. Exiting.");
                        running.set(false);
                        break;
                    }
                    String line = scanner.nextLine();
                    logger.log("[API][RECEIVED]" + line);
                    publishLine(line);
                }
                logger.log("Input stream closed.");
            } catch (Exception e) {
                if (running.get()) { // Only log if we're still supposed to be running
                    logger.log("Error while reading next line from input reader: ", e);
                }
                throw e; // Re-throw to ensure outer catch handles it
            } finally {
                stopRunning();
            }
        } catch (Exception e) {
            logger.log("Fatal error in startInputReader: ", e);
            stopRunning(); // Ensure shutdown on any exception
        }
    }

    public void stopRunning() {
        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }

}
