package com.workshop.mcp.io;

import java.util.function.Consumer;

public interface IOHandler {
    void addLineListener(Consumer<String> listener);
    void removeLineListener(Consumer<String> listener);
    //void publishLine(String line);
    void emit(Object message);
    void startInputReader();
    void stopRunning();
    boolean isRunning();

}
