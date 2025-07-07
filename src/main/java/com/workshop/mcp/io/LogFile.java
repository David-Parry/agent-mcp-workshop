package com.workshop.mcp.io;


public interface LogFile {

    void close();

    void log(String message, Throwable exception);

    void log(String message);

    void logRaw(String message);

}
