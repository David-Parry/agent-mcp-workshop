# Chapter 03: Implementing Discovery and Core Message Routing

## Lesson Objective

**What you will build:** A working MCP server that answers discovery, validates the per-request envelope, and handles basic notifications.

**The files are already on this branch.** `RequestId`, `RequestIdTypeAdapter`, `McpGson`, `NotificationCancelledParams`, `inspector/config.json`, and `inspector/run.sh` shipped with Chapter 3. Your job is to **fill the hollowed methods**, not to create those types from scratch.

`deserializeParams` was Chapter 2. You will *call* it from the notification handler.

**Tasks to complete:**
1. Fill `discoverResult()` — versions, core capabilities, identity, cache hints
2. Fill the request prelude in `process(JsonRpcRequest)` — answer `server/discover` first, reject unknown methods, validate the envelope
3. Fill `envelopeFor()` — the three distinct rejections
4. Fill `process(JsonRpcNotification)` — deserialize a cancellation and log it
5. Fill `acknowledgeSubscription()`
6. Build and test with MCP Inspector in modern mode

**How to verify you're done:**
- Run `./gradlew chapterTest -Pchapter=03` and see it go green
- Run `./gradlew clean build` successfully
- Launch MCP Inspector and connect to your server
- Discovery completes (green status)
- Sending `initialize` returns `-32601`, not a crash
- **List Tools / List Resources fail** until Chapter 4 — that is expected. The server advertised those capabilities and has not implemented the handlers yet.

---

## Starting Message Routing to Meet the Protocol Specification

In this lesson we implement the foundational message routing for an MCP server on revision `2026-07-28`. That revision deleted the `initialize` handshake, and with it the session. What replaces it is discovery plus a per-request envelope — and everything below follows from that single change.

`RequestId` plus its Gson adapter are already registered through `McpGson.create()`. Chapter 1 used `new Gson()`; from here on, inbound parsing and outbound `emit` share that factory so a string id is echoed back as a string. You will register a second adapter in Chapter 4, once `PropertySchema` exists.

### First Implementation Task: The `server/discover` Handler

Open `src/main/java/com/workshop/mcp/IORouter.java`. `discoverResult()` is empty at **line 131**. Fill the method body:

```java
private DiscoverResult discoverResult() {
    return DiscoverResultBuilder
            .builder()
            .withDefaultCapabilities()
            .withInstructions("Searches a project for a keyword.")
            .withCacheHints(LIST_TTL_MILLIS, CacheScope.PUBLIC)
            .withDefaultServerInfo()
            .build();
}
```

## What this case is doing:

1. **Answers without a session**: there is nothing to open and nothing to remember. Discovery is a plain request with a plain result.

2. **Advertises versions**: `supportedVersions` is how a client learns what to put in its envelope on every later request.

3. **Advertises capabilities**: Resources, Prompts, Tools, and Completions — **note that we are advertising these but haven't implemented them yet.**

4. **Carries cache hints**: `ttlMs` and `cacheScope` let the client cache the result instead of re-probing. On stdio it matters more than it looks: the Inspector answers discovery with a *throwaway probe process*, which is exactly why the result must be derivable from static configuration and must not depend on anything a long-lived server would have accumulated.

5. **Identifies the server**: `serverInfo` moved into `_meta` under `io.modelcontextprotocol/serverInfo`, alongside every other piece of lifecycle metadata.

The hollowed prelude of `process(JsonRpcRequest)` starts at **line 95**. Fill the body (before `switch`) so it does this, in this order, then falls into the `switch`:

```java
UniqueKeys uniqueKey = UniqueKeys.fromValue(message.method());

// server/discover is answered before any version check: a client uses
// it precisely to find out which versions this server speaks, so
// rejecting it for asking with the wrong one would be circular.
if (uniqueKey == UniqueKeys.SERVER_DISCOVER) {
    success(message.id(), discoverResult());
    logger.log("[API][SENT] server/discover — advertised " + RequestEnvelope.SUPPORTED_VERSIONS);
    return;
}

// Method existence is settled before the envelope is looked at.
if (uniqueKey == UniqueKeys.NOT_FOUND || !INBOUND_METHODS.contains(uniqueKey)) {
    logger.log("[API][SENT] unsupported method '" + message.method() + "' (returning -32601)");
    error(message.id(), ErrorCodes.METHOD_NOT_FOUND, "Method not found: " + message.method());
    return;
}

RequestEnvelope envelope = envelopeFor(message);
if (envelope == null) {
    return;
}
```

`LIST_TTL_MILLIS` is already declared on the class. One minute is long enough that a client is not re-listing tools on every keystroke, short enough that a restart is picked up while the workshop is still on the same slide.

### Second Implementation Task: Envelope Validation

This is what replaced the handshake. Instead of agreeing on a version once, the server re-reads it from `params._meta` on every request.

**Action Required**: fill `envelopeFor` at **line 125** of `IORouter.java` (it is already on the class, empty):

```java
private RequestEnvelope envelopeFor(JsonRpcRequest message) {
    RequestEnvelope envelope = deserializer.deserializeEnvelope(message);
    if (!envelope.isComplete()) {
        error(message.id(), ErrorCodes.INVALID_PARAMS,
              "Requests must carry " + MetaKeys.PROTOCOL_VERSION + " and "
              + MetaKeys.CLIENT_CAPABILITIES + " in params._meta");
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

## What these checks are doing:

They produce **three distinct rejections**, and the ordering between them is the interesting part.

| Situation | Code | Why |
|---|---|---|
| `initialize`, `ping`, `tasks/result`, … | `-32601` | The method is gone. Removal in this revision is physical — absence from the registry. |
| No `_meta`, or missing a required key | `-32602` | `protocolVersion` and `clientCapabilities` are both required. |
| `_meta` names `2025-11-25` | `-32022` | The client is speaking a revision this server does not implement. |

Order matters. A legacy client sending `initialize` has *also* omitted the envelope, so both checks would fire — but only `-32601` tells it the truth about why it failed. Validating the envelope first would send it off to fix the wrong thing.

The `-32022` case is the one people get wrong: its `data` **must** carry `requested` and `supported`. Without them the client knows it failed but not what to renegotiate to, and there is no handshake left in which to find out.

Note also what the envelope means for the rest of your server. Capability flags like "can this client show a form" are no longer fields on the router set once at startup — they are per-request facts, read fresh from `envelope.supportsElicitationForm()` on each call. A server that caches them is reintroducing the session the revision just deleted.

### Fourth Implementation Task: Building and Testing with MCP Inspector

#### Step 4.1: Build the Application

**Action Required**: Open a terminal in the project root directory and run:

```bash
./gradlew clean build
```

This command will:
- Clean any previous build artifacts
- Compile the Java source code
- Run tests
- Package the application into a JAR at `build/libs/agent-mcp-workshop-0.0.1.jar`

#### Step 4.2: The Inspector configuration is already on the branch

`inspector/config.json` and `inspector/run.sh` shipped with this chapter. Open `config.json` and confirm `"protocolEra": "modern"` is a **direct sibling of `command` and `args`**, inside the `workshop` entry. Nested anywhere else, the Inspector silently falls back to the legacy era, sends `initialize`, and you spend twenty minutes debugging a `-32601` you asked for.

`run.sh` pins Inspector `2.5.0`:

```bash
npx @modelcontextprotocol/inspector@2.5.0 --web --catalog config.json
```

#### Step 4.3: Launch the MCP Inspector

**Action Required**:
1. In your terminal, navigate to the inspector folder:
   ```bash
   cd inspector
   ```

2. Run the launch script:
   ```bash
   ./run.sh
   ```

3. Look for console output that includes a URL (typically `http://127.0.0.1:6274`)

4. Copy the URL from the console output and paste it into your web browser

5. You should see the **MCP Inspector v2.5.0** application interface, with `workshop` listed as a server you can connect to

The MCP Inspector is a powerful debugging and testing tool that allows you to:
- Send requests to your MCP server
- View server responses in real-time
- Test the discovery and envelope handling you just implemented
- Monitor the communication between client and server

Once the Inspector is running, test your implementation by:
1. Checking that discovery completes successfully (notice that the server advertises Resources, Prompts, and Tools capabilities)
2. Reading the trace: the id on that first message is the string `"server-discover-probe-1"`. A string id must be echoed back as a string — that is why `RequestId` exists.
3. **Intentionally triggering an error**: Click "List Resources" **and** "List Tools". Both fail until Chapter 4, because discovery advertised those capabilities and the handlers are not on this branch yet.

**Important Learning Point**: This error is expected! It demonstrates two things:
- You'll see a cancellation notification from the client when it doesn't receive a response to its "List Resources" request
- Our server logs will show that we've missed handling this object type in our notification switch — let's implement that object to see how the mapping works

This shows the importance of implementing everything you advertise. In the discovery response we told the client we support Resources, Prompts, and Tools, but so far we have only implemented discovery and envelope validation.

### Fifth Implementation Task: Handle cancellations with the deserializer you already wrote

`deserializeParams(JsonRpcNotification, Class)` is the Chapter 2 method. `NotificationCancelledParams` is already in `com.workshop.mcp.spec`. Fill the empty `process(JsonRpcNotification)` at **line 148** of `IORouter.java` so it uses both:

```java
private void process(JsonRpcNotification message) {
    UniqueKeys uniqueKey = UniqueKeys.fromValue(message.method());
    switch (uniqueKey) {
        case NOTIFICATION_CANCELLED -> {
            NotificationCancelledParams params = deserializer.deserializeParams(message,
                                                                                NotificationCancelledParams.class);
            logger.log("[API][RECEIVED] notifications/cancelled requestId=" + params.requestId()
                       + " reason=" + params.reason());
        }
        default -> logger.log("[API][RECEIVED] unhandled notification method: " + message.method()
                              + " for message: " + message);
    }
}
```

The `[API][RECEIVED]` prefix matches the one `startInputReader` uses, so a single grep over the log file shows everything that arrived, in order.

Notice what is **not** in that switch: `notifications/initialized`. It went the same way as `initialize`.

Also fill `acknowledgeSubscription()` at **line 185** of `IORouter.java` — a `subscriptions/listen` is acknowledged and then closed, because this server advertises no `listChanged` support.

### Important Note: Error Handling and STDIO in MCP Servers

#### Critical Rule: Never Write to STDIO

**⚠️ WARNING**: In MCP servers, STDIO (standard input/output) is reserved exclusively for protocol communication. This means:

- **NO console.log, System.out.println, or print statements** to STDOUT
- **NO startup messages** to STDOUT
- **NO debugging output** to STDOUT
- **NO logging framework output** to STDOUT (like Log4j, SLF4J default configurations)

Any non-protocol data written to STDIO will corrupt the communication channel and cause the client to disconnect or behave unpredictably.

#### Error Handling in MCP

The MCP specification does include provisions for sending errors back to clients (see [JSON-RPC 2.0 Error Object specification](https://www.jsonrpc.org/specification#error_object)). Our approach in this workshop:

1. **Protocol errors are narrow and deliberate.** `-32601`, `-32602`, and `-32022` each mean one specific thing, and a client can diagnose all three from the error alone. Without a handshake to renegotiate in, these errors *are* the negotiation — so they carry real information rather than prose.

2. **Everything else is a tool-level result.** Use the `isError` flag inside a successful result for operational failures. A search that found nothing is not a protocol fault, and dressing it as one makes it harder for a model to recover.

3. **Debugging goes to files.** Implement file-based logging (like `LogFileWriter`) that writes to a separate log file, never STDIO.

Remember: the `LogFileWriter` we're using writes to `inspector/logs/` specifically to avoid STDIO contamination. This is a best practice for all MCP server implementations.

### Final Step: Build and Test Your Updated Implementation

#### Step 8: Rebuild and Test

**Action Required**:

1. **Build the project** with your changes:
   ```bash
   ./gradlew clean build
   ```

2. **Navigate to the inspector folder** (if not already there):
   ```bash
   cd inspector
   ```

3. **Prepare for a fresh test**:
   - If the Inspector is still running and shows "Connected", click the **Disconnect** button
   - In your terminal, press **Ctrl+C** to stop the Inspector server
   - Restart the Inspector by running `./run.sh`
   - **Refresh your webpage** to clear any cached state

4. **Test your implementation**:
   - Click **Connect** and verify discovery completes successfully
   - Click **List Resources** to intentionally trigger an error

5. **Check the improved logging**:
   - Open the latest log file in `inspector/logs/`
   - Search it for `notifications/cancelled`
   - You should now see the deserialized id and reason, for example
     `[API][RECEIVED] notifications/cancelled requestId=3 reason=McpError: MCP error -32001: Request timed out`,
     rather than the raw params map

6. **Try the three rejections** with `inspector/modern-probe.sh`, which sends them back to back:
   - `initialize` → `-32601`
   - a request with no `_meta` → `-32602`
   - a request declaring `2025-11-25` → `-32022` with `data.supported`

## What you should observe:

- Discovery continues to work as before
- When clicking "List Resources", the client still times out (expected)
- **NEW**: the server logs now show the actual cancellation reason from the client, not just raw JSON
- Three different failures produce three different diagnoses, all readable from the error alone

## Congratulations!

You've successfully implemented:
- ✅ The `server/discover` response
- ✅ Envelope validation with three distinct, correctly ordered rejections
- ✅ Type-safe handling of cancellation notifications
- ✅ Proper logging without STDIO contamination

In the next lesson, we'll build on this foundation to implement the actual Resources, Prompts, and Tools capabilities that we're advertising.
