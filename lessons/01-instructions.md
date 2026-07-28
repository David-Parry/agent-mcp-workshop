# Chapter 01: Starting the Input Reader

This chapter fills the three methods that make `IOHandlerImpl` a transport: one reads lines from standard input, one writes JSON to standard output, and one hands each line it reads to whoever is listening.

The line numbers below are where each method sits in `IOHandlerImpl.java` as you checked it out. The tasks run from the bottom of the file up, so pasting one method never moves a method you have not reached yet. Each empty body holds a single `// Chapter 01:` comment; replace it with the code.

## First Implementation Task: Reading Standard Input

The first step in building our MCP server is to implement the input reading functionality. This code handles reading lines from System.in (standard input) and distributing them to registered listeners.

### Code to Add to IOHandlerImpl

Paste at **line 143** of `src/main/java/com/workshop/mcp/io/IOHandlerImpl.java`, inside the empty `startInputReader()` body:

```java
// Claim the running flag in a single atomic step. Reading the flag here
// and setting it further down would let two threads both get past this
// point and open two Scanners over the same System.in.
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
   - `scanner.hasNextLine()` waits until a line arrives or the input closes
   - If the input has closed, sets running to false and breaks

4. **Line Processing**:
   - Reads each line with `scanner.nextLine()`
   - Logs the received line with `[API][RECEIVED]` prefix for debugging
   - Publishes the line to all registered listeners via `publishLine(line)`, which you fill in the third task

5. **Error Handling**:
   - Inner try-catch handles errors during the reading loop
   - Only logs errors if the reader is still supposed to be running
   - Re-throws so the outer catch logs the failure as fatal

6. **Cleanup**:
   - The `finally` block ensures `stopRunning()` is called
   - The outer catch swallows the failure, so the method always returns normally; the caller learns the reader stopped from `isRunning()`

### Key Concepts

- **It blocks**: `Server.start()` calls this on the main thread, and it does not return until standard input closes. The server has nothing else to do while it waits, so there is no reader thread to manage.
- **Thread Safety**: The AtomicBoolean is what makes a second, concurrent call harmless
- **Resource Management**: Try-with-resources ensures the Scanner is properly closed
- **Error Recovery**: A failure ends the loop exactly the way a closed input does

This implementation forms the foundation of the MCP server's ability to receive and process JSON-RPC messages from clients.

## Second Implementation Task: Emitting JSON Responses

The `emit` method is responsible for sending JSON-formatted responses back to the MCP client through standard output.

### Code to Add to the IOHandlerImpl

Paste at **line 111** of `src/main/java/com/workshop/mcp/io/IOHandlerImpl.java`, inside the empty `emit(Object message)` body:

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
   - Gson writes compact JSON with no newlines inside it, which matters for the next step

2. **Logging**:
   - `logger.log("[API][SENT]: " + text)` records the outgoing message in the log file, never on standard output
   - The `[API][SENT]` prefix helps distinguish outgoing messages in logs

3. **Output Writing**:
   - `writer.println(text)` sends the JSON string to System.out followed by a newline
   - On the stdio transport a newline is the message boundary, so one message must be exactly one line

4. **Flushing**:
   - The PrintWriter was built with auto-flush turned on, so `println` already pushes the line out
   - The explicit `writer.flush()` changes nothing today; it keeps the method correct if someone later builds the writer without auto-flush

### Key Concepts

- **JSON Serialization**: Gson automatically handles converting Java objects to JSON format
- **Logging Strategy**: All outgoing messages are logged for debugging and monitoring
- **Framing**: One compact JSON message per line, nothing else on stdout
- **Prompt delivery**: A line left sitting in a buffer is a client waiting for an answer that never comes

### Why This Matters

The `emit` method is essential because it:
- Completes the communication loop with the MCP client
- Ensures all responses are properly formatted as JSON
- Provides visibility into server responses through logging
- Delivers each response as soon as it is written

### Implementation Notes

- The `writer` is a PrintWriter initialized with `System.out` and auto-flush enabled
- The `gson` instance is reused for all serialization to avoid overhead
- The method accepts `Object` to allow flexibility in what can be sent
- Error handling for serialization failures should be considered in production

## Third Implementation Task: Publishing Lines to Listeners

The last piece is the `publishLine` method, which distributes received input lines to all registered listeners. The reader you wrote in the first task already calls it.

### Code to Add to the IOHandlerImpl

Paste at **line 96** of `src/main/java/com/workshop/mcp/io/IOHandlerImpl.java`, inside the empty `publishLine(String line)` body:

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
   - Each listener runs on the reader's thread, so the next line is not read until every listener has returned

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

## You are done when the tests pass

All three methods are graded by tests that ship on this branch. Run them:

```bash
./gradlew chapterTest -Pchapter=01
```

The run reports 58 tests, and 13 of them fail before you start, all of them in `IOHandlerImplTest`. `LogFileWriterTest`, the `Server` tests, and 4 of the 17 `IOHandlerImplTest` cases already pass, because they test code this branch gives you, or input that is empty or broken before a line is ever read. When all 58 are green, the transport layer is finished and you can move on to chapter 2.

If you want to see the full failure output for a single test while debugging:

```bash
./gradlew chapterTest -Pchapter=01 --info
```

A few failures worth recognising:

- `testStartInputReader_AlreadyRunning` or `testStartInputReader_ReentrantCall_...` failing usually means the `compareAndSet` guard is missing, so a second call starts a second Scanner.
- `testListenerExceptionHandling` failing means the `try`/`catch` inside the `publishLine` loop is missing, so one broken listener stops the rest from being notified.
- `testEmit_*` failing usually means the message was written without going through `gson.toJson`. A `String` has to come out quoted, because what goes on the wire is JSON.

## Where this goes next

The server can now read lines and write JSON, but it cannot tell one message from another. Chapter 2 turns each line into a typed JSON-RPC message. Start it with `./clean-checkout.sh 02-chapter`, or keep your own code: commit it and run `git merge origin/02-chapter`. The README explains both.
