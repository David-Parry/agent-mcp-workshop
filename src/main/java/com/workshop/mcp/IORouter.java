package com.workshop.mcp;

import com.workshop.mcp.io.IOHandler;

/**
 * Routes inbound stdio lines. Chapter 3 is where those lines become JSON-RPC
 * messages and are dispatched to handlers.
 */
public class IORouter implements Router {

    @SuppressWarnings("unused")
    private final IOHandler io;

    public IORouter(IOHandler io) {
        this.io = io;
    }

    @Override
    public void route(String message) {
        // Chapter 03: classify the line and dispatch it.
    }
}
