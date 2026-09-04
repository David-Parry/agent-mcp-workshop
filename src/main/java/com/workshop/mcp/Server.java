package com.workshop.mcp;

import com.workshop.mcp.io.IOHandler;
import com.workshop.mcp.io.LogFile;
import com.workshop.mcp.io.LogFileWriter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The main server class that manages the lifecycle of the MCP (Model Context Protocol) server.
 * <p>
 * This class is responsible for:
 * <ul>
 *   <li>Initializing and managing the I/O handler for processing input/output streams</li>
 *   <li>Setting up routing for incoming messages through the Router</li>
 *   <li>Managing graceful shutdown through shutdown hooks and latches</li>
 *   <li>Maintaining the server's running state and handling interruptions</li>
 * </ul>
 * </p>
 * <p>
 * The server uses a polling mechanism to monitor the I/O handler's state and coordinates
 * shutdown through a {@link CountDownLatch} to ensure all resources are properly cleaned up.
 * </p>
 * 
 * @see IOHandler
 * @see Router
 * @since 1.0
 */
public class Server {
    /**
     * Atomic flag to ensure shutdown operations are performed only once.
     * Prevents race conditions during concurrent shutdown attempts.
     */
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    
    /**
     * Latch used to coordinate graceful shutdown across different threads.
     * When counted down to zero, signals that shutdown should proceed.
     */
    private final CountDownLatch shutdownLatch;
    
    /**
     * Singleton logger instance for recording server events and errors.
     */
    private static final LogFile logger = LogFileWriter.getInstance();
    
    /**
     * Handler for managing input/output operations and message processing.
     */
    private final IOHandler io;
    
    /**
     * Router responsible for directing incoming messages to appropriate handlers.
     */
    private final Router router;

    /**
     * Constructs a new Server instance with the specified dependencies.
     * 
     * @param ioHandler the I/O handler for managing input/output streams and message processing
     * @param routerImpl the router implementation for directing messages to appropriate handlers
     * @param countDownLatch the latch used for coordinating graceful shutdown
     * @throws NullPointerException if countDownLatch is null (ioHandler and routerImpl may be null for testing)
     */
    public Server(IOHandler ioHandler, Router routerImpl, CountDownLatch countDownLatch) {
        this.io = ioHandler;
        this.router = routerImpl;
        this.shutdownLatch = countDownLatch;
    }

    /**
     * Starts the server and begins processing messages.
     * <p>
     * This method performs the following operations:
     * <ol>
     *   <li>Registers the router as a line listener with the I/O handler</li>
     *   <li>Installs a JVM shutdown hook for graceful termination</li>
     *   <li>Starts the asynchronous input reader</li>
     *   <li>Enters the main server loop via {@link #keepRunning()}</li>
     * </ol>
     * </p>
     * <p>
     * If any exception occurs during startup, the server will attempt to stop gracefully
     * and exit with status code 0.
     * </p>
     * <p>
     * This method blocks until the server is shut down either by:
     * <ul>
     *   <li>The I/O handler stopping</li>
     *   <li>The shutdown latch being triggered</li>
     *   <li>Thread interruption</li>
     *   <li>JVM shutdown</li>
     * </ul>
     * </p>
     * 
     * @throws NullPointerException if ioHandler or router is null
     */
    public void start() {
        try {
            // Add a listener for individual lines
            this.io.addLineListener(router::route);

            // Create a shutdown hook to close resources properly
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (isShuttingDown.compareAndSet(false, true)) {
                    stop();
                    logger.close();
                    shutdownLatch.countDown();
                }
            }));
            // Start the async input reader
            this.io.startInputReader();
            keepRunning();
        } catch (Exception e) {
            logger.log("Error in main method", e);
            stop();
            exit();
        }
    }

    /**
     * Stops the server and releases all resources.
     * <p>
     * This method:
     * <ul>
     *   <li>Signals the I/O handler to stop processing (if not null)</li>
     *   <li>Logs the shutdown event</li>
     *   <li>Closes the logger to ensure all log entries are flushed</li>
     * </ul>
     * </p>
     * <p>
     * This method is safe to call multiple times and handles null I/O handler gracefully.
     * It is automatically called during JVM shutdown if registered via the shutdown hook.
     * </p>
     */
    void stop() {
        if (io != null) {
            io.stopRunning();
        }
        logger.log("Stopping and closing resources");
        logger.close();
    }

    /**
     * Maintains the server's main execution loop until shutdown is triggered.
     * <p>
     * This method implements a polling mechanism that:
     * <ul>
     *   <li>Monitors the I/O handler's running state every 500ms</li>
     *   <li>Initiates shutdown if the I/O handler stops</li>
     *   <li>Responds to shutdown signals via the shutdown latch</li>
     *   <li>Handles thread interruption gracefully</li>
     * </ul>
     * </p>
     * <p>
     * The method will exit and terminate the JVM with {@code System.exit(0)} when:
     * <ul>
     *   <li>The I/O handler reports it is no longer running</li>
     *   <li>The shutdown latch is triggered (counts down to zero)</li>
     *   <li>The thread is interrupted</li>
     * </ul>
     * </p>
     * <p>
     * Thread safety is ensured through the use of {@link AtomicBoolean} for the
     * shutdown flag and proper synchronization via the {@link CountDownLatch}.
     * </p>
     * 
     * @throws InterruptedException if the thread is interrupted while waiting on the latch
     *         (caught and handled internally with graceful shutdown)
     */
    void keepRunning() {
        try {
            // Create a polling mechanism to check if IO is still running
            while (!isShuttingDown.get()) {
                // Check if IO has stopped running
                if (io != null && !io.isRunning()) {
                    logger.log("IO processing has stopped. Initiating shutdown.");
                    if (isShuttingDown.compareAndSet(false, true)) {
                        shutdownLatch.countDown();
                    }
                }

                // Check if shutdown has been triggered
                if (shutdownLatch.await(500, TimeUnit.MILLISECONDS)) {
                    // Shutdown was triggered, exit the loop
                    break;
                }
            }

            // Give a small delay to allow final messages to be printed
            Thread.sleep(1);

            // Force exit with success code
            exit();
        } catch (InterruptedException e) {
            // Thread was interrupted, exit gracefully
            Thread.currentThread().interrupt();
            logger.log("Application interrupted during shutdown", e);
            // Force exit with error code
            exit();
        }
    }

    /**
     * Terminates the JVM with a success status code.
     * <p>
     * Extracted into a package-private method so unit tests can override it
     * with a no-op: calling {@link System#exit(int)} from a test kills the
     * test runner JVM mid-suite, silently skipping every remaining test.
     * Production behavior is unchanged.
     * </p>
     */
    void exit() {
        System.exit(0);
    }


}
