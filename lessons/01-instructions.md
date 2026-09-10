# Chapter 01: Starting the Input Reader

## First Implementation Task

The first step in building our MCP server is to implement the input reading functionality. This code handles reading lines from System.in (standard input) and distributing them to registered listeners.

### Code to Add to IOHandlerImpl

Paste at **line 138** of `src/main/java/com/workshop/mcp/io/IOHandlerImpl.java`, inside the empty `startInputReader()` body:

```java
// Claim the running flag in a single atomic step. If we read the flag here and
// set it further down, two threads could both get past this check and open two
// Scanners over the same System.in.
if (!running.compareAndSet(false, true)) {
    return; // Already running
}

try (Scanner scanner = new Scanner(System.in)) {
    try {
        while (running.get()) {
            if (!scanner.hasNextLine()) {
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
```

### Understanding the Code

This code block implements the core input reading loop for the MCP server. Let's break down what each part does:

1. **Scanner Creation**: 
   - Uses a try-with-resources block to create a Scanner that reads from `System.in`
   - This ensures the Scanner is properly closed when done

2. **Running State Management**:
   - `running.compareAndSet(false, true)` marks the reader active and tells us whether we were the thread that claimed it
   - If it returns false another thread is already reading, so we return rather than open a second Scanner over the same `System.in`
   - Doing the check and the set as one atomic operation is the whole point: a separate `running.get()` followed by `running.set(true)` leaves a window where two threads both believe they won
   - The `running` AtomicBoolean allows thread-safe control of the reading loop

3. **Main Reading Loop**:
   - Continues while `running.get()` returns true
   - Checks if there's a next line available with `scanner.hasNextLine()`
   - If no more input is available, sets running to false and breaks

4. **Line Processing**:
   - Reads each line with `scanner.nextLine()`
   - Logs the received line with `[API][RECEIVED]` prefix for debugging
   - Publishes the line to all registered listeners via `publishLine(line)`

5. **Error Handling**:
   - Inner try-catch handles errors during the reading loop
   - Only logs errors if the reader is still supposed to be running
   - Re-throws exceptions to ensure proper cleanup

6. **Cleanup**:
   - The `finally` block ensures `stopRunning()` is called
   - Outer catch handles any fatal errors and ensures shutdown

### Key Concepts

- **Thread Safety**: The use of AtomicBoolean for the running flag ensures thread-safe state management
- **Resource Management**: Try-with-resources ensures the Scanner is properly closed
- **Error Recovery**: Multiple levels of error handling ensure graceful shutdown
- **Event Publishing**: The `publishLine()` method distributes input to all registered listeners

This implementation forms the foundation of the MCP server's ability to receive and process JSON-RPC messages from clients.

## Second Implementation Task: Publishing Lines to Listeners

The next critical component is the `publishLine` method, which distributes received input lines to all registered listeners.

### Code to Add to the IOHandlerImpl

Paste at **line 95** of `src/main/java/com/workshop/mcp/io/IOHandlerImpl.java`, inside the empty `publishLine(String line)` body:

```java
for (Consumer<String> listener : lineListeners) {
    try {
        listener.accept(line);
    } catch (Exception e) {
        logger.log("Error in line listener", e);
    }
}
```

### Understanding the Code

This method implements the observer pattern to notify all registered listeners when a new line of input is received. Here's what each part does:

1. **Iteration Through Listeners**:
   - Uses a for-each loop to iterate through all registered `Consumer<String>` listeners
   - The `lineListeners` collection is thread-safe (CopyOnWriteArrayList)

2. **Listener Invocation**:
   - Calls `listener.accept(line)` to pass the received line to each listener
   - Each listener processes the line according to its own logic

3. **Error Isolation**:
   - Wraps each listener call in a try-catch block
   - If one listener throws an exception, it doesn't affect other listeners
   - Logs any errors with context for debugging

### Key Concepts

- **Observer Pattern**: This implements a classic observer pattern where multiple listeners can react to input events
- **Fault Tolerance**: Each listener is isolated - if one fails, others continue to receive notifications
- **Decoupling**: The input reader doesn't need to know what each listener does with the data
- **Thread Safety**: Works safely with the CopyOnWriteArrayList to handle concurrent modifications

### Why This Matters

The `publishLine` method is crucial because it:
- Enables the router to receive and process incoming JSON-RPC messages
- Allows multiple components to react to input without tight coupling
- Provides a clean separation between input handling and message processing
- Ensures robust error handling so one faulty listener doesn't crash the server

This pattern allows the MCP server to be extensible - new listeners can be added without modifying the core input handling logic.

## Third Implementation Task: Emitting JSON Responses

The `emit` method is responsible for sending JSON-formatted responses back to the MCP client through standard output.

### Code to Add to the IOHandlerImpl

Paste at **line 109** of `src/main/java/com/workshop/mcp/io/IOHandlerImpl.java`, inside the empty `emit(Object message)` body:

```java
String text = gson.toJson(message);
logger.log("[API][SENT]: " + text);
writer.println(text);
writer.flush();
```

### Understanding the Code

This method handles the critical task of sending responses back to the MCP client. Here's what each line does:

1. **JSON Serialization**:
   - `gson.toJson(message)` converts any Java object into its JSON representation
   - Gson handles complex object graphs, collections, and nested structures automatically

2. **Logging**:
   - `logger.log("[API][SENT]: " + text)` records the outgoing message for debugging
   - The `[API][SENT]` prefix helps distinguish outgoing messages in logs

3. **Output Writing**:
   - `writer.println(text)` sends the JSON string to System.out
   - The PrintWriter was initialized with auto-flush, but we still explicitly flush

4. **Explicit Flush**:
   - `writer.flush()` ensures the message is immediately sent to the output stream
   - Critical for real-time communication with the MCP client

### Key Concepts

- **JSON Serialization**: Gson automatically handles converting Java objects to JSON format
- **Logging Strategy**: All outgoing messages are logged for debugging and monitoring
- **Stream Management**: Explicit flushing ensures messages aren't buffered
- **Protocol Compliance**: MCP requires JSON-RPC messages to be sent as complete lines

### Why This Matters

The `emit` method is essential because it:
- Completes the bidirectional communication loop with the MCP client
- Ensures all responses are properly formatted as JSON
- Provides visibility into server responses through logging
- Guarantees timely delivery of responses through explicit flushing

### Implementation Notes

- The `writer` is a PrintWriter initialized with `System.out` and auto-flush enabled
- The `gson` instance is reused for all serialization to avoid overhead
- The method accepts `Object` to allow flexibility in what can be sent
- Error handling for serialization failures should be considered in production

This method works in conjunction with the input reader to create a complete request-response cycle for the MCP protocol.

## You are done when the tests pass

All three methods are graded by tests that ship on this branch. Run them:

```bash
./gradlew chapterTest -Pchapter=01
```

Before you write anything they fail, which is expected — that is the red starting state. When `IOHandlerImplTest` and `LogFileWriterTest` are green, the transport layer is finished and you can move on to chapter 2.

If you want to see the full failure output for a single test while debugging:

```bash
./gradlew chapterTest -Pchapter=01 --info
```

A few failures worth recognising:

- `testStartInputReader_AlreadyRunning` or `testStartInputReader_ReentrantCall_...` failing usually means the `compareAndSet` guard is missing, so a second call starts a second Scanner.
- `testListenerExceptionHandling` failing means the `try`/`catch` inside the `publishLine` loop is missing, so one broken listener stops the rest from being notified.
- `testEmit_*` failing usually means the `writer.flush()` call is missing and the message is still sitting in the buffer.