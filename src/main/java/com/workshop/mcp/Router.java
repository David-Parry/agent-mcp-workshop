package com.workshop.mcp;

public interface Router {
    void route(String message);

    /**
     * Releases whatever the router holds open, such as streams it has not
     * yet answered. Called once, as the server stops.
     */
    default void close() {
    }
}
