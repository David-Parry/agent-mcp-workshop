# Chapter 03: Implementing Discovery and Core Message Routing

## Lesson Objective

**What you will build:** A working MCP server that answers discovery, validates the per-request envelope, and handles basic notifications.

**Tasks to complete:**
1. Make request ids polymorphic (a JSON-RPC id is a string *or* a number)
2. Implement the `server/discover` handler
3. Implement envelope validation — the check that replaced the handshake
4. Build and test with MCP Inspector in modern mode
5. Add notification deserialization infrastructure
6. Create the `NotificationCancelledParams` record and wire up type-safe handling

**How to verify you're done:**
- Run `./gradlew chapterTest -Pchapter=03` and see it go green
- Run `./gradlew clean build` successfully
- Launch MCP Inspector and connect to your server
- Discovery completes (green status)
- Sending `initialize` returns `-32601`, not a crash
- Click "List Resources" and check `inspector/logs/` — you should see a line starting `[API][RECEIVED] notifications/cancelled requestId=` followed by the actual reason text, not raw JSON

---

## Starting Message Routing to Meet the Protocol Specification

In this lesson we implement the foundational message routing for an MCP server on revision `2026-07-28`. That revision deleted the `initialize` handshake, and with it the session. What replaces it is discovery plus a per-request envelope — and everything below follows from that single change.

### First Implementation Task: Make Ids Polymorphic

Before any handler, one piece of plumbing. The first message a modern client sends looks like this:

```json
{"jsonrpc":"2.0","id":"server-discover-probe-1","method":"server/discover", ...}
```

That id is a **string**. Older revisions of this workshop typed `id` as a `Long`, and that one assumption crashes the server on the very first message:

```
com.google.gson.JsonSyntaxException: java.lang.NumberFormatException:
    For input string: "server-discover-probe-1"
```

JSON-RPC has always allowed either form; what changed is that clients started using strings for out-of-band traffic — discovery probes, subscription streams, extension polling. A server MUST echo an id back in exactly the form it arrived, so coercing one to the other is not an option either.

**Action Required**: Create `RequestId.java` in the `com.workshop.mcp.spec` package:

```java
package com.workshop.mcp.spec;

public record RequestId(String stringValue, Long numberValue) {

    public RequestId {
        if ((stringValue == null) == (numberValue == null)) {
            throw new IllegalArgumentException(
                    "A RequestId is either a string or a number, never both and never neither");
        }
    }

    public static RequestId of(String value) {
        return new RequestId(value, null);
    }

    public static RequestId of(long value) {
        return new RequestId(null, value);
    }

    public boolean isString() {
        return stringValue != null;
    }

    @Override
    public String toString() {
        return isString() ? stringValue : String.valueOf(numberValue);
    }
}
```

Gson cannot serialize that record usefully on its own — it would emit `{"stringValue":...,"numberValue":null}` rather than a bare scalar. It needs a `TypeAdapter`.

**Action Required**: Create `RequestIdTypeAdapter.java` in the same package:

```java
package com.workshop.mcp.spec;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class RequestIdTypeAdapter extends TypeAdapter<RequestId> {

    @Override
    public void write(JsonWriter out, RequestId value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else if (value.isString()) {
            out.value(value.stringValue());
        } else {
            out.value(value.numberValue());
        }
    }

    @Override
    public RequestId read(JsonReader in) throws IOException {
        return switch (in.peek()) {
            case NULL -> {
                in.nextNull();
                yield null;
            }
            case STRING -> RequestId.of(in.nextString());
            case NUMBER -> RequestId.of(in.nextLong());
            default -> throw new JsonParseException(
                    "A JSON-RPC id must be a string or a number, but was " + in.peek());
        };
    }
}
```

Note `case NUMBER -> RequestId.of(in.nextLong())`. Typing the field as plain `Object` instead would have let Gson parse every number as a `Double`, and the server would echo the id `1` back as `1.0` — which no client will match to its pending request.

The adapter is useless unless **every** Gson instance in the process has it registered. `JsonRpcMessageDeserializer` reads ids and `IOHandlerImpl` writes them; if only one of them knows about the adapter, you get a server that can parse a string id but cannot answer it. So there is one factory that both call.

**Action Required**: Create `McpGson.java`:

```java
package com.workshop.mcp.spec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class McpGson {

    private McpGson() {
    }

    public static Gson create() {
        return new GsonBuilder()
                .registerTypeAdapter(RequestId.class, new RequestIdTypeAdapter())
                .create();
    }
}
```

Then replace every `new Gson()` in `JsonRpcMessageDeserializer` and `IOHandlerImpl` with `McpGson.create()`.

> You will register a second adapter here in chapter 4, once `PropertySchema` exists and needs to be written as valid JSON Schema.

Finally, retype `id` from `Long` to `RequestId` on `JsonRpcRequest`, `JsonRpcResponse`, and `JsonRpcErrorResponse`.

### Second Implementation Task: The `server/discover` Handler

**Action Required**: Add this case to the request switch in `IORouter.java`. Note where it goes — **before** anything else:

```java
// server/discover is answered before any version check: a client uses
// it precisely to find out which versions this server speaks, so
// rejecting it for asking with the wrong one would be circular.
if (uniqueKey == UniqueKeys.SERVER_DISCOVER) {
    success(message.id(), discoverResult());
    logger.log("[API][SENT] server/discover — advertised " + RequestEnvelope.SUPPORTED_VERSIONS);
    return;
}
```

with the result itself built from static configuration. It refers to a constant you need to declare alongside the other fields at the top of `IORouter`:

```java
/** How long a client may cache a list result before asking again. */
private static final long LIST_TTL_MILLIS = 60_000L;
```

One minute is a deliberate compromise: long enough that a client is not re-listing tools on every keystroke, short enough that a server restart with a changed tool set is picked up while the workshop is still on the same slide. You will reuse this same constant for every list result in chapter 4, so all of them expire together.

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

### Third Implementation Task: Envelope Validation

This is what replaced the handshake. Instead of agreeing on a version once, the server re-reads it from `params._meta` on every request.

**Action Required**: Add these two checks to `process(JsonRpcRequest)`, in this order:

```java
// Method existence is settled before the envelope is looked at.
// Deletions in this revision are physical: a method absent from the
// registry is -32601 by absence, and answering a removed method like
// initialize with a complaint about its _meta would tell a legacy
// client the wrong thing about why it failed.
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

and the validator itself:

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

#### Step 4.2: Understanding the Inspector Configuration

Navigate to the `inspector` folder where you'll find two important files:

1. **`config.json`** — MCP Server Configuration. Copy and paste the following into `config.json`:
   ```json
   {
     "mcpServers": {
       "workshop": {
         "command": "java",
         "args": [
           "-jar",
           "../build/libs/agent-mcp-workshop-0.0.1.jar"
         ],
         "env": {
         },
         "protocolEra": "modern",
         "modernLogLevel": "off"
       }
     }
   }
   ```
   **Purpose**: This tells the MCP Inspector how to launch your server. It defines:
   - A server named "workshop"
   - The command to run (`java`)
   - Arguments to pass (each one its own array element — `-jar` and the JAR path are two separate arguments, not one string)
   - Any environment variables (currently empty)
   - `"protocolEra": "modern"`, which is what makes the Inspector send `server/discover` instead of `initialize`

   > **Watch the nesting.** `protocolEra` must be a direct sibling of `command` and `args`, inside the server's own entry. Put it anywhere else and the Inspector silently falls back to the legacy era, sends `initialize`, and you spend twenty minutes debugging a `-32601` you asked for.

2. **`run.sh`** — Inspector Launch Script
   ```bash
   #!/usr/bin/env bash
   set -euo pipefail
   cd "$(dirname "${BASH_SOURCE[0]}")"

   npx @modelcontextprotocol/inspector@2.5.0 --web --catalog config.json
   ```
   **Purpose**: This shell script launches the MCP Inspector tool. It:
   - Uses `npx` to run the MCP Inspector, pinned to version 2.5.0
   - `cd`s into the `inspector` folder first, so the server's working directory is always `inspector/` and its logs land in `inspector/logs/`
   - Points to `config.json` for server configuration via `--catalog`, which opens a *writable* session — you can add or edit servers from the UI. (`--config` also works but opens a read-only session, and the UI will say so.)
   - `--web` selects the web UI; the Inspector also has `--cli` and `--tui` modes

   > **Note**: the web UI lists every server in the catalog, so there is no `--server` flag here. `--server workshop` only applies to `--cli` mode.

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
2. Reading the trace: the id on that first message is the string `"server-discover-probe-1"`. If you skipped the `RequestId` work, this is where it crashes.
3. **Intentionally triggering an error**: Click "List Resources" in the Inspector. This will fail because we advertised resource capabilities during discovery but haven't implemented the resources handler yet. This demonstrates how the client sends requests based on advertised capabilities and how our server handles (or fails to handle) unimplemented features.

**Important Learning Point**: This error is expected! It demonstrates two things:
- You'll see a cancellation notification from the client when it doesn't receive a response to its "List Resources" request
- Our server logs will show that we've missed handling this object type in our notification switch — let's implement that object to see how the mapping works

This shows the importance of implementing everything you advertise. In the discovery response we told the client we support Resources, Prompts, and Tools, but so far we have only implemented discovery and envelope validation.

### Fifth Implementation Task: Handling Notification Deserialization

#### Step 5.1: Examine the Logs

**Action Required**: Look at the [server logs](../inspector/logs) when errors occur. You'll notice that we're printing out the raw JSON but haven't properly deserialized the notification parameters. This makes it difficult to work with the notification data in a type-safe manner.

#### Step 5.2: Add Notification Deserialization Support

**Action Required**: Copy the code below into `JsonRpcMessageDeserializer.java`:

```java
/**
 * Deserializes the params field of a JSON-RPC notification into a specific type.
 * <p>
 * This method is useful for converting the generic params Map from a
 * {@link JsonRpcNotification} into a strongly-typed parameter object specific
 * to the notification being sent.
 * </p>
 *
 * @param <T>         the type to deserialize the params into
 * @param request     the JSON-RPC notification containing the params to deserialize
 * @param paramsClass the class of the params type
 * @return the deserialized params object
 * @throws com.google.gson.JsonSyntaxException if the params cannot be deserialized to the specified type
 */
public <T> T deserializeParams(JsonRpcNotification request, Class<T> paramsClass) {
    return gson.fromJson(gson.toJson(request.params()), paramsClass);
}
```

## What this method does:

This method converts the generic `params` field from a `JsonRpcNotification` into a strongly-typed Java object:

1. **Takes a JsonRpcNotification**: Contains the raw params as a generic Map
2. **Converts to JSON**: Uses Gson to serialize the params Map back to JSON
3. **Deserializes to target type**: Uses Gson again to deserialize that JSON into the specified class type
4. **Returns typed object**: Provides a type-safe object that can be used in your notification handlers

Note that `gson` here is the shared instance from `McpGson.create()`. That is not incidental — a cancellation notification carries a `requestId`, and without the registered `RequestIdTypeAdapter` this round trip cannot read a string one.

### Sixth Implementation Task: Create Notification Parameter Types

Now that we can deserialize notification parameters, we need the Java record types that represent them.

#### Step 6: Create NotificationCancelledParams Record

**Action Required**: Create a new file `NotificationCancelledParams.java` in the `com.workshop.mcp.spec` package:

```java
package com.workshop.mcp.spec;

public record NotificationCancelledParams(RequestId requestId, String reason) {
}
```

## What this record represents:

This record defines the structure of parameters sent with a "cancelled" notification:

- **`requestId`**: the id of the request that was cancelled — a `RequestId`, not a `Long`, for exactly the reason covered in the first task
- **`reason`**: a string explaining why the request was cancelled

When a client cancels a pending request (like when we clicked "List Resources" and it timed out), it sends a notification with these parameters. Having this strongly-typed record allows us to:
1. Deserialize the notification parameters using the method we just added
2. Access the cancellation details in a type-safe manner
3. Handle cancellations appropriately in our server logic

### Seventh Implementation Task: Use the Deserializer for Notifications

#### Step 7: Update the Notification Handler

**Action Required**: In `IORouter.java`, locate `private void process(JsonRpcNotification message)` and update the `NOTIFICATION_CANCELLED` case:

```java
private void process(JsonRpcNotification message) {
    UniqueKeys uniqueKey = UniqueKeys.fromValue(message.method());
    switch (uniqueKey) {
        case NOTIFICATION_CANCELLED -> {
            NotificationCancelledParams params = deserializer.deserializeParams(message,
                                                                                NotificationCancelledParams.class);
            // There is never an in-flight request to abandon: this server
            // answers every request before returning, and its one
            // long-lived request, subscriptions/listen, is closed as it is
            // acknowledged. So the cancellation is recorded and nothing else.
            logger.log("[API][RECEIVED] notifications/cancelled requestId=" + params.requestId()
                       + " reason=" + params.reason());
        }
        default -> logger.log("[API][RECEIVED] unhandled notification method: " + message.method()
                              + " for message: " + message);
    }
}
```

The `[API][RECEIVED]` prefix matches the one `startInputReader` uses, so a single grep over the log file shows everything that arrived, in order.

## What this change accomplishes:

1. We're now properly deserializing the cancellation parameters into our strongly-typed `NotificationCancelledParams` record
2. This demonstrates how to use the deserializer infrastructure we set up in previous steps
3. Although we're not storing or using the result yet (we'll do that in future lessons), this proves our deserialization is working correctly

Notice what is **not** in that switch any more: `notifications/initialized`. It went the same way as `initialize`, for the same reason — there is no initialization to be notified about.

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
- ✅ Polymorphic request ids, string or number
- ✅ The `server/discover` response
- ✅ Envelope validation with three distinct, correctly ordered rejections
- ✅ Notification deserialization infrastructure
- ✅ Type-safe handling of cancellation notifications
- ✅ Proper logging without STDIO contamination

In the next lesson, we'll build on this foundation to implement the actual Resources, Prompts, and Tools capabilities that we're advertising.
