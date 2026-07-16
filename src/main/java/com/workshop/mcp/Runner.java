package com.workshop.mcp;

import com.workshop.mcp.io.IOHandler;
import com.workshop.mcp.io.IOHandlerImpl;

import java.util.concurrent.CountDownLatch;

public class Runner {

    public static void main(String[] args) {
        IOHandler io = new IOHandlerImpl();
        Server server = new Server(io,new IORouter(io), new CountDownLatch(1));
        server.start();
    }
}
