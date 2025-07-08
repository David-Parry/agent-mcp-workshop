package com.workshop.mcp;

import com.workshop.mcp.io.IOHandler;
import com.workshop.mcp.io.LogFile;
import com.workshop.mcp.io.LogFileWriter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Server {
    private static final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private final CountDownLatch shutdownLatch;
    private static final LogFile logger = LogFileWriter.getInstance();
    private final IOHandler io;
    private final Router router;


    public Server(IOHandler io, Router router, CountDownLatch shutdownLatch) {
        this.io = io;
        this.router = router;
        this.shutdownLatch = shutdownLatch;
    }

    public void start() {
        try {
            // Add a listener for individual lines
            io.addLineListener(router::route);

            // Create a shutdown hook to close resources properly
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (isShuttingDown.compareAndSet(false, true)) {
                    stop();
                    logger.close();
                    shutdownLatch.countDown();
                }
            }));
            // Start the async input reader
            io.startInputReader();
            keepRunning();
        } catch (Exception e) {
            logger.log("Error in main method", e);
            stop();
            System.exit(1);
        }
    }

    public void stop() {
        if (io != null) {
            io.stopRunning();
        }
        logger.log("Stopping and closing resources");
        logger.close();
    }

    /**
     * Keeps the application running until terminated by the user or until IO processing stops
     */
    public void keepRunning() {
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
            System.exit(0);
        } catch (InterruptedException e) {
            // Thread was interrupted, exit gracefully
            Thread.currentThread().interrupt();
            logger.log("Application interrupted during shutdown", e);
            // Force exit with error code
            System.exit(1);
        }
    }


}
