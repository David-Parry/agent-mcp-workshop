# Deep Dive into the Model Context Protocol

*Ever wondered how AI assistants like Claude actually communicate with external tools and services? While most tutorials focus on using pre-built SDKs and frameworks, this article takes a different approach—we'll dissect a production MCP server built from scratch using only Java's standard libraries and raw STDIO communication. By stripping away all the abstractions and implementing the Model Context Protocol directly, we'll uncover the surprisingly elegant mechanics that enable AI systems to discover, understand, and execute tools. Whether you're building AI integrations, debugging mysterious protocol errors, or simply curious about what really happens when an AI "uses a tool," this deep dive will transform JSON-RPC messages flowing through stdin/stdout from abstract concepts into concrete, debuggable reality. No frameworks, no magic—just the protocol in its purest form.*

> **Want to build this yourself?** This article is based on code from the [Agent MCP Workshop](https://github.com/David-Parry/agent-mcp-workshop), an instructor-led workshop that guides you through building a complete MCP server from scratch. The workshop includes hands-on exercises, detailed explanations, and practical examples that complement the concepts discussed in this article.

## Understanding MCP Through Raw STDIO Communication

The Model Context Protocol (MCP) represents a paradigm shift in how AI systems interact with external tools and data sources. This article dives deep into the protocol's STDIO-based communication layer, examining a real-world Java implementation built without frameworks to better understand how the protocol and communication actually work at the fundamental level.

By implementing MCP from scratch using only standard Java libraries, we gain invaluable insights into the protocol's inner workings—insights often hidden by higher-level abstractions and frameworks. This bare-metal approach reveals the elegant simplicity underlying MCP's powerful capabilities.

## Why STDIO? The Power of Universal Communication

Before diving into the implementation, it's crucial to understand why MCP chose STDIO (Standard Input/Output) as its primary transport mechanism. STDIO provides:

- **Universal compatibility**: Every programming language can read from stdin and write to stdout
- **Process isolation**: Natural security boundaries between the AI client and tool server
- **Simplicity**: No network configuration, firewall rules, or authentication complexity
- **Debugging ease**: Messages can be logged, inspected, and replayed

This implementation demonstrates these principles by building everything from scratch—no MCP SDK, no framework dependencies, just pure Java interacting with STDIO streams.

At the heart of any MCP implementation lies a robust transport layer. The Java implementation demonstrates a clean separation of concerns through its I/O handler architecture:

```java
public class IOHandlerImpl implements IOHandler {
    private final static LogFile logger = LogFileWriter.getInstance();
    private final PrintWriter writer;
    private final List<Consumer<String>> lineListeners;
    private final AtomicBoolean running;
    private final Gson gson = new Gson();

    @Override
    public void emit(Object message) {
        String text = gson.toJson(message);
        logger.log("[API][SENT]: " + text);
        writer.println(text);
        writer.flush();
    }

    @Override
    public void startInputReader() {
        if (running.get()) {
            return; // Already running
        }

        try (Scanner scanner = new Scanner(System.in)) {
            running.set(true);
            while (running.get()) {
                if (!scanner.hasNextLine()) {
                    running.set(false);
                    break;
                }
                String line = scanner.nextLine();
                logger.log("[API][RECEIVED]" + line);
                publishLine(line);
            }
        }
    }
}
```

This implementation showcases several critical design decisions:
- **Direct STDIO access**: Reading from `System.in` and writing to `System.out` without buffering frameworks
- **Thread-safe operations** using `AtomicBoolean` and `CopyOnWriteArrayList`
- **Event-driven architecture** with listener patterns for incoming messages
- **Line-based protocol**: Each JSON-RPC message is a complete line, enabling simple parsing
- **Comprehensive logging** that writes to files (never stdout!) for debugging

The beauty of this approach is its transparency. When the server emits a message:

```java
public void emit(Object message) {
    String text = gson.toJson(message);
    logger.log("[API][SENT]: " + text);  // Log to file, not stdout!
    writer.println(text);  // Send to stdout
    writer.flush();        // Ensure immediate delivery
}
```

The message goes directly to stdout as a single line of JSON. No framing, no length prefixes, no binary protocols—just newline-delimited JSON that any tool can read and debug.

## Understanding the JSON-RPC Message Flow

MCP uses JSON-RPC 2.0 over STDIO, which means every message is a self-contained JSON object on a single line. Let's trace through an actual message exchange to see how this works:

### Client → Server: Discovery Request
```json
{"jsonrpc":"2.0","method":"server/discover","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{"roots":{"listChanged":true}},"io.modelcontextprotocol/clientInfo":{"name":"inspector-cli","version":"2.5.0"}}},"id":"server-discover-probe-1"}
```

Note the id: a **string**, not a number. JSON-RPC has always allowed either, and modern clients use strings for out-of-band traffic like discovery probes. A server must echo it back in exactly the form it arrived.

This single line contains everything needed to describe the caller — there is no handshake to establish it once. The server reads it from stdin using:

```java
String line = scanner.nextLine();
logger.log("[API][RECEIVED]" + line);
publishLine(line);  // Notify the router
```

### Server → Client: Discovery Response
```json
{"jsonrpc":"2.0","id":"server-discover-probe-1","result":{"resultType":"complete","supportedVersions":["2026-07-28"],"capabilities":{"tools":{},"prompts":{},"resources":{}},"ttlMs":60000,"cacheScope":"public","_meta":{"io.modelcontextprotocol/serverInfo":{"name":"agent-mcp-workshop","version":"0.0.1"}}}}
```

The server writes this response directly to stdout. No HTTP headers, no WebSocket frames—just a line of JSON followed by a newline character.

### The Message Type Hierarchy

The implementation uses Java records to model the JSON-RPC message types:

```java
public record JsonRpcRequest(
    String jsonrpc,
    RequestId id,
    String method,
    Object params
) {}

public record JsonRpcNotification(
    String jsonrpc,
    String method,
    Object params
) {}  // Note: No id field for notifications!

public record JsonRpcResponse(
    String jsonrpc,
    RequestId id,
    Object result
) {}

public record JsonRpcErrorResponse(
    String jsonrpc,
    RequestId id,
    JsonRpcError error
) {}
```

`RequestId` is the interesting one. Typing `id` as a `Long` seems obvious right up until the first modern client connects and the server dies with `NumberFormatException: For input string: "server-discover-probe-1"`. So the id is modelled as exactly one of two things, and taught to Gson through a `TypeAdapter` that writes a bare scalar rather than an object:

```java
public record RequestId(String stringValue, Long numberValue) {
    public RequestId {
        if ((stringValue == null) == (numberValue == null)) {
            throw new IllegalArgumentException(
                    "A RequestId is either a string or a number, never both and never neither");
        }
    }
}
```

This type system directly maps to the JSON-RPC 2.0 specification, making the protocol implementation clear and type-safe.

## No Handshake: Where the Session Went

Earlier revisions opened every conversation with an `initialize` request that established a protocol version and exchanged capabilities once. Revision `2026-07-28` deleted it, along with `notifications/initialized` and `ping`. Two mechanisms replace it.

The first is discovery — a plain request that opens nothing:

```java
// server/discover is answered before any version check: a client uses
// it precisely to find out which versions this server speaks, so
// rejecting it for asking with the wrong one would be circular.
if (uniqueKey == UniqueKeys.SERVER_DISCOVER) {
    success(message.id(), discoverResult());
    return;
}
```

The second is the envelope. Every request re-declares who is calling and what they can do, in `params._meta`:

```java
private RequestEnvelope envelopeFor(JsonRpcRequest message) {
    RequestEnvelope envelope = deserializer.deserializeEnvelope(message);
    if (!envelope.isComplete()) {
        error(message.id(), ErrorCodes.INVALID_PARAMS,
              "Requests must carry the protocol version and client capabilities in params._meta");
        return null;
    }
    if (!envelope.isSupportedVersion()) {
        error(message.id(), ErrorCodes.UNSUPPORTED_PROTOCOL_VERSION, "Unsupported protocol version",
              Map.of("supported", RequestEnvelope.SUPPORTED_VERSIONS,
                     "requested", envelope.protocolVersion()));
        return null;
    }
    return envelope;
}
```

That `-32022` payload is doing real work. Without a handshake there is no moment in which to renegotiate, so the error itself has to tell the client what it could have said instead.

The deeper consequence is architectural: capability flags stop being fields. `hasRoots` and `hasSampling` used to be set once at initialization and read forever after. Now they are per-request facts — `envelope.supportsRoots()` — and a server that caches them has quietly reintroduced the session the revision just removed.

## One-Directional Communication: The Server Cannot Call You

Earlier revisions let a server send requests to a client, and this workshop used to do exactly that: ask for `roots/list` once the handshake completed, correlate the answer by a reserved negative id. On `2026-07-28` that is forbidden. A server MUST NOT send a request, and modern clients **silently discard** any that arrive — no error, the message simply vanishes.

That removes the only mechanism servers had for `roots/list`, `sampling/createMessage`, and `elicitation/create`. What replaces it is Multi Round-Trip Requests: the server asks by *answering*.

```java
// The tool needs a directory and has none. Ask for one — inside the result.
success(requestId, InputRequiredResult.of(
        SearchContinuation.KEY_ROOTS,
        InputRequest.rootsList(),
        SearchContinuation.awaitingRoots(keyword).encode()));
```

The client sees `resultType: "input_required"`, resolves the embedded request from its own configuration, and re-sends the **original** call as a brand new request with a brand new id, carrying the answers under `inputResponses` and the server's `requestState` echoed back byte-exact.

The `requestState` is the whole trick. The server has nowhere to remember what it was doing between two independent requests, so the continuation travels to the client and back:

```java
SearchContinuation continuation = SearchContinuation.decode(params.requestState());
String keyword = continuation != null ? continuation.keyword() : keywordArgument(params);
String stage = continuation != null ? continuation.stage() : null;
```

To the client it is an opaque string; this implementation happens to base64 a small JSON object into it. The protocol guarantees only that the exact bytes come back.

One design note worth internalising: MRTR is optional for clients, and not every one implements it. The Inspector's own task-augmented `tools/call` path rejects an `input_required` outright. So the keyword-search tool also accepts `directory` as an optional argument, completing in a single hop when one is supplied — and if the asking runs out entirely, it searches its own working directory rather than failing. A tool whose only route to an answer is a round trip is a tool those callers cannot use at all.

The routing mechanism demonstrates how MCP servers handle different message types:

```java
public void route(String message) {
    if (message == null || message.isEmpty()) {
        return;
    }
    Object object = deserializer.deserialize(message);
    switch (object) {
        case JsonRpcRequest request -> process(request);
        case JsonRpcNotification notification -> process(notification);
        case JsonRpcResponse successResponse -> process(successResponse);
        case JsonRpcErrorResponse errorResponse -> process(errorResponse);
        default -> logger.log("Unknown message type: " + object);
    }
}
```

This pattern matching approach (using Java's modern switch expressions) creates a clean, extensible routing system. Each message type has its own processing logic:

```java
private void process(JsonRpcRequest message) {
    UniqueKeys uniqueKey = UniqueKeys.fromValue(message.method());
    switch (uniqueKey) {
        case PROMPTS_LIST -> { /* ... */ }
        case PROMPTS_GET -> { /* ... */ }
        case TOOLS_LIST -> { /* ... */ }
        case TOOLS_CALL -> { /* ... */ }
        case RESOURCES_LIST -> { /* ... */ }
        case RESOURCES_READ -> { /* ... */ }
        case TASKS_GET -> { /* ... */ }
        case SUBSCRIPTIONS_LISTEN -> { /* ... */ }
        default -> logger.log("Unhandled RpcRequest method: " + uniqueKey);
    }
}
```

## The Complete STDIO Loop: Putting It All Together

Let's trace through a complete interaction to see how STDIO communication enables MCP:

### 1. Server Startup
```java
public void start() {
    // Register the router to handle incoming lines
    this.io.addLineListener(router::route);
    
    // Start reading from stdin in a separate thread
    this.io.startInputReader();
    
    // Keep the main thread alive
    keepRunning();
}
```

### 2. Client Connects (via process spawn)
The client spawns the server process and connects to its stdin/stdout:
```bash
java -jar agent-mcp-workshop-0.0.1.jar
```

### 3. Message Exchange Begins
```
→ [stdin]  {"jsonrpc":"2.0","method":"server/discover","params":{...},"id":"server-discover-probe-1"}
← [stdout] {"jsonrpc":"2.0","id":"server-discover-probe-1","result":{"resultType":"complete",...}}
→ [stdin]  {"jsonrpc":"2.0","method":"tools/call","params":{"_meta":{...},...},"id":1}
← [stdout] {"jsonrpc":"2.0","id":1,"result":{"resultType":"input_required","inputRequests":{...},"requestState":"..."}}
→ [stdin]  {"jsonrpc":"2.0","method":"tools/call","params":{...,"inputResponses":{...},"requestState":"..."},"id":2}
← [stdout] {"jsonrpc":"2.0","id":2,"result":{"resultType":"complete",...}}
```

Note that every arrow points the same way for requests: the client asks, the server answers. The round trip at the end is two independent exchanges, not a nested one — and the ids differ because the retry genuinely is a new request.

Each arrow represents a complete line written to stdin or stdout. The server never writes partial messages or multiple messages on one line—maintaining the protocol's simplicity.

### 4. Error Handling Without Exceptions
Since STDIO doesn't have error channels like HTTP status codes, errors are part of the protocol:

```java
success(message.id(), ToolCallResultBuilder
    .builder()
    .addTextContent("Tool not found: " + toolCallParams.name())
    .asError()
    .build());
```

This creates a valid response with an error flag, keeping the STDIO stream clean and the protocol predictable.

The keyword search tool demonstrates how to create self-describing, executable functionality:

```java
public class KeyWordSearch implements Tool {
    @Override
    public String name() {
        return "key_word_search";
    }

    @Override
    public String description() {
        return "Searches for a specified keyword across all files in a project. " +
               "Returns the total count of matches and the absolute file paths " +
               "of the files containing the keyword.";
    }

    @Override
    public InputSchema schema() {
        InputSchemaBuilder builder = InputSchemaBuilder
                .builder()
                .withType("object")
                .addProperty(PropertySchemaBuilder
                        .builder()
                        .withKey("keyword")
                        .withType("string")
                        .withDescription("the keyword to search for in a file")
                        .required());
        if (this.roots == null || this.roots.isEmpty()) {
            builder.addProperty(PropertySchemaBuilder
                    .builder()
                    .withKey("root_directory")
                    .withType("string")
                    .withDescription("The absolute path to the root directory")
                    .required());
        }
        return builder.build();
    }
}
```

The schema definition is particularly important—it enables AI clients to understand exactly how to invoke the tool. The actual implementation showcases robust file handling:

```java
public ToolCallResult call(ToolCallParams toolCallParams) {
    String keyword = toolCallParams.arguments().get("keyword");
    ToolCallResultBuilder builder = ToolCallResultBuilder.builder();
    
    if (roots.isEmpty()) {
        builder.addTextContent("No root directories specified for search.");
        builder.asError();
    } else {
        List<ContentItem> contentItems = searchKeywordInDirectories(roots, keyword);
        builder.withContent(contentItems);
    }
    return builder.build();
}
```

## Debugging STDIO Communication

One of the advantages of building MCP without frameworks is the ability to debug at the protocol level. The implementation includes comprehensive logging:

```java
logger.log("[API][RECEIVED]" + line);  // Every incoming message
logger.log("[API][SENT]: " + text);     // Every outgoing message
```

This creates a complete trace of the STDIO communication:

```
[2024-01-15 10:23:45] [API][RECEIVED]{"jsonrpc":"2.0","method":"tools/list","id":5}
[2024-01-15 10:23:45] [API][SENT]: {"jsonrpc":"2.0","id":5,"result":{"tools":[{"name":"key_word_search","description":"Searches for a specified keyword...","inputSchema":{...}}]}}
[2024-01-15 10:23:46] [API][RECEIVED]{"jsonrpc":"2.0","method":"tools/call","params":{"name":"key_word_search","arguments":{"keyword":"TODO"}},"id":6}
[2024-01-15 10:23:47] [API][SENT]: {"jsonrpc":"2.0","id":6,"result":{"content":[{"text":"/src/main/java/Server.java, keyword_count=3","type":"text"}]}}
```

This trace can be replayed for testing, analyzed for performance, or used to debug protocol issues—something much harder with framework-heavy implementations.

```java
case RESOURCES_LIST -> {
    ResourcesListResultBuilder builder = ResourcesListResultBuilder
            .builder()
            .withResources(JavadocResources.loadAllHtmlResourcesFromFolder(
                "javadoc/com/workshop/mcp/spec"
            ))
            .withNextCursor("pageNext");
    success(message.id(), builder.build());
}
```

The JavadocResources class shows sophisticated resource handling:

```java
public static String readResourceContent(String resourcePath) throws IOException {
    try (InputStream inputStream = JavadocResources.class.getClassLoader()
                                                         .getResourceAsStream(resourcePath)) {
        if (inputStream == null) {
            throw new IOException("Resource not found: " + resourcePath);
        }
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
```

This approach allows servers to bundle and serve documentation, configurations, or any other static content directly from the JAR file—all transmitted as JSON over STDIO. When a client requests a resource:

```
→ {"jsonrpc":"2.0","method":"resources/read","params":{"uri":"javadoc/com/workshop/mcp/spec/Tool.html"},"id":10}
← {"jsonrpc":"2.0","id":10,"result":{"contents":[{"uri":"javadoc/com/workshop/mcp/spec/Tool.html","mimeType":"text/html","text":"<!DOCTYPE HTML>..."}]}}
```

The entire HTML document is embedded in the JSON response, properly escaped and transmitted as a single line. This demonstrates STDIO's flexibility—it can handle everything from simple method calls to large content transfers.

## Prompts: Bridging Human Intent and Tool Execution

The prompt system creates user-friendly interfaces for tools:

```java
case PROMPTS_LIST -> {
    KeyWordSearch keyWordSearch = new KeyWordSearch(this.roots);
    PromptsListResultBuilder builder = PromptsListResultBuilder
            .builder()
            .withPrompt("search_keyword",
                        "Creates a prompt, to search for a word using the " + 
                        keyWordSearch.name() + " tool.")
            .withPromptArgument("keyword", "The word to search for", true)
            .withNextCursor("nextPage");
    success(message.id(), builder.build());
}
```

When retrieving a prompt, the server can provide intelligent guidance:

```java
case PROMPTS_GET -> {
    PromptsGetParams params = deserializer.deserializeParams(
        message, PromptsGetParams.class
    );
    PromptsGetResultBuilder builder = PromptsGetResultBuilder
            .builder()
            .withDescription("keyword")
            .addTextMessage("user", KEY_WORD_MESSAGE, params.arguments());
    success(message.id(), builder.build());
}
```

## Advanced Features: Notification Handling

The implementation includes sophisticated notification handling:

```java
private void process(JsonRpcNotification message) {
    UniqueKeys uniqueKey = UniqueKeys.fromValue(message.method());
    switch (uniqueKey) {
        case NOTIFICATION_CANCELLED -> {
            NotificationCancelledParams params = deserializer.deserializeParams(
                message, NotificationCancelledParams.class
            );
            logger.log("Notification cancelled reason " + params.reason());
        }
        default -> logger.log("Unhandled notification method: " + uniqueKey);
    }
}
```

This shows how servers can react to client-side events and maintain synchronized state.

## Server Lifecycle Management

The Server class demonstrates robust lifecycle management:

```java
public class Server {
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private final CountDownLatch shutdownLatch;
    
    public void start() {
        try {
            this.io.addLineListener(router::route);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (isShuttingDown.compareAndSet(false, true)) {
                    stop();
                    logger.close();
                    shutdownLatch.countDown();
                }
            }));
            
            this.io.startInputReader();
            keepRunning();
        } catch (Exception e) {
            logger.log("Error in main method", e);
            stop();
            System.exit(0);
        }
    }
}
```

The use of shutdown hooks and countdown latches ensures graceful termination even in complex scenarios.

## Key Architectural Patterns

### 1. Record Types for Protocol Messages
```java
public record JsonRpcRequest(
    String jsonrpc,
    Long id,
    String method,
    Object params
) {}
```

Java records provide immutable, self-documenting protocol structures.

### 2. Builder Pattern for Complex Responses
```java
DiscoverResult result = DiscoverResultBuilder.builder()
    .withDefaultCapabilities()
    .withInstructions("Searches a project for a keyword.")
    .withCacheHints(60_000L, CacheScope.PUBLIC)
    .withDefaultServerInfo()
    .build();
```

Builders ensure valid, complete responses while maintaining readability.

### 3. Enum-Based Method Routing
```java
public enum UniqueKeys {
    SERVER_DISCOVER("server/discover"),
    PROMPTS_LIST("prompts/list"),
    // ... more methods
    
    public static UniqueKeys fromValue(String value) {
        for (UniqueKeys key : UniqueKeys.values()) {
            if (key.value.equalsIgnoreCase(value)) {
                return key;
            }
        }
        return NOT_FOUND;
    }
}
```

This approach provides type-safe method handling with built-in validation.

## Why Building Without Frameworks Matters

This implementation deliberately avoids MCP SDKs or frameworks to reveal important insights:

### 1. **The Protocol is Simple**
At its core, MCP is just JSON-RPC 2.0 over newline-delimited streams. No magic, no hidden complexity—just structured messages over STDIO.

### 2. **Debugging is Straightforward**
Without framework abstractions, every message is visible and every routing decision is explicit. Problems can be traced directly to protocol-level issues.

### 3. **Portability is Maximized**
This implementation could be ported to any language that can read stdin and write stdout—no framework dependencies to worry about.

### 4. **Understanding is Complete**
By implementing from scratch, developers gain deep understanding of:
- How capability negotiation works
- Why message IDs matter
- How a stateless protocol pushes its lifecycle onto every message
- What makes MCP transport-agnostic

### 5. **Performance is Transparent**
Without framework overhead, the performance characteristics are clear:
- Message parsing time = JSON deserialization
- Routing overhead = switch statement
- I/O latency = STDIO buffering

## Conclusion: The Elegance of STDIO-Based Protocols

This deep dive into a framework-free MCP implementation reveals the elegant simplicity at the protocol's heart. By using STDIO as the transport layer, MCP achieves:

- **Universal compatibility**: Any language that can read and write text can implement MCP
- **Process isolation**: Natural security boundaries without complex authentication
- **Debugging transparency**: Every message is visible and reproducible
- **Deployment simplicity**: No ports, no certificates, no network configuration

The implementation demonstrates that building AI-integrated tools doesn't require complex frameworks or abstractions. At its core, MCP is about structured communication—JSON messages flowing over STDIO streams, enabling AI systems to discover and use tools in a standardized way.

By understanding these fundamentals through a bare-metal implementation, developers gain the knowledge to:
- Build MCP servers in any language or environment
- Debug protocol issues at the message level
- Optimize performance by understanding the actual costs
- Extend the protocol while maintaining compatibility

As AI continues to evolve, the ability to create tools that AI can understand and use becomes increasingly valuable. This implementation shows that such integration doesn't require magic—just careful attention to protocol details and disciplined STDIO handling.

The Model Context Protocol's choice of STDIO as its primary transport isn't just a technical decision—it's a philosophical one. It says that AI-tool integration should be simple, debuggable, and accessible to everyone. By building without frameworks, we see this philosophy in action: powerful capabilities emerging from simple, well-designed primitives.

Whether you're building the next generation of AI tools or simply understanding how AI systems communicate, the lessons from this STDIO-based implementation provide a solid foundation for creating robust, interoperable systems that bridge the gap between human intentions and machine capabilities.

---

## Learn By Building: The Agent MCP Workshop

If you found this deep dive valuable and want to build your own MCP server from the ground up, check out the [Agent MCP Workshop](https://github.com/David-Parry/agent-mcp-workshop) on GitHub. This instructor-led workshop takes you through five progressive lessons:

1. **Building the Transport Layer** - Create a robust STDIO communication foundation
2. **Understanding the Protocol** - Implement JSON-RPC message handling
3. **Discovery &amp; Routing** - Build `server/discover`, envelope validation, and message dispatch
4. **Implementing Capabilities** - Add Resources, Tools, and Prompts
5. **Agent Integration** - Connect your MCP server with AI agents

The workshop provides hands-on experience with the exact code examined in this article, along with exercises, debugging techniques, and best practices for production deployment. Whether you're learning solo or in a group setting, the workshop materials offer a structured path to MCP mastery.

Start building your own AI-integrated tools today: [github.com/David-Parry/agent-mcp-workshop](https://github.com/David-Parry/agent-mcp-workshop)