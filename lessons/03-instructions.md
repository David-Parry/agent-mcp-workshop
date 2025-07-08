# Chapter 03: Implementing MCP Protocol Handshake and Core Message Routing

## Starting Message Routing to Meet the Protocol Specification

In this lesson, we'll implement the foundational message routing system for the MCP (Model Context Protocol) server. This includes handling the critical initial handshake, ping requests, and basic notifications - the core building blocks that establish communication between MCP clients and servers.

### First Implementation Task: The Initialize Handler

**Action Required**: Copy the code below and paste it into `IORouter.java` starting at line 43. This code implements the INITIALIZE case in the switch statement:

```java
case INITIALIZE -> {
InitializeParams initializeParams = deserializer.deserializeParams(message, InitializeParams.class);
ClientCapabilities clientCapabilities = initializeParams.capabilities();
                if (clientCapabilities.roots() != null) {
hasRoots = true;
        }
        if (clientCapabilities.sampling() != null) {
hasSampling = true;
        }
InitializeResultBuilder builder = InitializeResultBuilder
        .builder()
        .withProtocolVersion(initializeParams.protocolVersion())
        .withDefaultCapabilities()
        .withDefaultServerInfo();
success(message.id(), builder.build());
        }
```

## What this case is doing:

This INITIALIZE case handles the initialization request from the MCP client. Here's what happens step by step:

1. **Deserializes the parameters**: It extracts the `InitializeParams` from the incoming message using the deserializer.

2. **Checks client capabilities**: It examines the client's capabilities to determine what features the client supports:
   - If the client supports "roots", it sets `hasRoots = true`
   - If the client supports "sampling", it sets `hasSampling = true`

3. **Builds the initialization response**: Using the `InitializeResultBuilder`, it creates a response that includes:
   - The protocol version from the client's request
   - Default server capabilities (which includes Resources, Prompts, and Tools - **Note: We're advertising these capabilities but haven't implemented them yet!**)
   - Default server information

4. **Sends success response**: Finally, it sends the built initialization result back to the client using the `success()` method with the original message ID.

This initialization handshake is crucial as it establishes the protocol version and capabilities that both the client and server will use for their subsequent communication.

### Second Implementation Task: The Ping Handler

**Action Required**: Copy the code below and paste it into `IORouter.java` after the INITIALIZE case in the switch statement:

```java
case PING -> {
    success(message.id(), new Object());
}
```

## What the PING case is doing:

The PING case is a simple but essential handler that implements a heartbeat mechanism in the MCP protocol:

1. **Receives ping request**: When the client sends a ping request, this case is triggered.

2. **Sends empty response**: It immediately responds by calling `success(message.id(), new Object())`, which:
   - Uses the same message ID from the request to maintain request-response correlation
   - Sends an empty object (`new Object()`) as the response payload

3. **Purpose**: The ping mechanism serves several important functions:
   - **Connection verification**: Allows the client to check if the server is still alive and responsive
   - **Keep-alive**: Prevents connection timeouts in long-running sessions
   - **Latency measurement**: Clients can measure round-trip time by timing ping responses

The simplicity of this implementation (just echoing back an empty success response) is intentional - ping requests should be lightweight and fast to ensure accurate health checks and minimal overhead on the server.

### Third Implementation Task: Building and Testing with MCP Inspector

Now that we've implemented the basic message routing, let's build the application and test it using the MCP Inspector tool.

#### Step 3.1: Build the Application

**Action Required**: Open a terminal in the project root directory and run:

```bash
./gradlew clean build
```

This command will:
- Clean any previous build artifacts
- Compile the Java source code
- Run tests (if any)
- Package the application into a JAR file located at `build/libs/agent-mcp-workshop-0.0.1.jar`

#### Step 3.2: Understanding the Inspector Configuration

Navigate to the `inspector` folder where you'll find two important files:

1. **`config.json`** - MCP Server Configuration
   ```json
   {
     "mcpServers": {
       "workshop": {
         "command": "java",
         "args": [
           "-jar ../build/libs/agent-mcp-workshop-0.0.1.jar"
         ],
         "env": {
         }
       }
     }
   }
   ```
   **Purpose**: This file tells the MCP Inspector how to launch your server. It defines:
   - A server named "workshop"
   - The command to run (`java`)
   - Arguments to pass (the path to your JAR file)
   - Any environment variables (currently empty)

2. **`run.sh`** - Inspector Launch Script
   ```bash
   npx @modelcontextprotocol/inspector@0.14.0 --config config.json --server workshop
   ```
   **Purpose**: This shell script launches the MCP Inspector tool. It:
   - Uses `npx` to run the MCP Inspector version 0.14.0
   - Points to the `config.json` file for server configuration
   - Specifies "workshop" as the server to connect to

#### Step 3.3: Launch the MCP Inspector

**Action Required**: 
1. In your terminal, navigate to the inspector folder:
   ```bash
   cd inspector
   ```

2. Run the launch script:
   ```bash
   ./run.sh
   ```

3. Look for console output that includes a URL (typically something like `http://localhost:5173` or similar)

4. Copy the URL from the console output and paste it into your web browser

5. You should see the **MCP Inspector v0.14.0** application interface

The MCP Inspector is a powerful debugging and testing tool that allows you to:
- Send requests to your MCP server
- View server responses in real-time
- Test the initialize handshake and ping functionality you just implemented
- Monitor the communication between client and server

Once the Inspector is running, you can test your implementation by:
1. Checking that the initialization handshake completes successfully (notice that the server advertises Resources, Prompts, and Tools capabilities)
2. Sending ping requests and verifying you receive responses
3. **Intentionally triggering an error**: Click on "List Resources" in the Inspector. This will fail because we advertised resource capabilities during initialization but haven't implemented the resources handler yet. This demonstrates how the client sends requests based on advertised capabilities and how our server handles (or fails to handle) unimplemented features.

**Important Learning Point**: This error is expected! It demonstrates two things:
- You'll see a cancellation error from the client when it doesn't receive a response to its "List Resources" request
- Our server logs will show that we've missed handling this object type in our notification switch, lets move on and implement that Object to see how the mapping works.

This shows the importance of implementing all advertised capabilities. In the initialization response, we told the client we support Resources, Prompts, and Tools, but we've only implemented Initialize and Ping so far.

### Fourth Implementation Task: Handling Notification Deserialization

#### Step 4.1: Examine the Logs

**Action Required**: Look at the [server logs](inspector/logs) when errors occur. You'll notice that we're printing out the raw JSON but haven't properly deserialized the notification parameters. This makes it difficult to work with the notification data in a type-safe manner.

#### Step 4.2: Add Notification Deserialization Support

**Action Required**: Copy the code below and paste it into `JsonRpcMessageDeserializer.java` starting at line 118:

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

This method provides a way to convert the generic `params` field from a JsonRpcNotification into a strongly-typed Java object:

1. **Takes a JsonRpcNotification**: Contains the raw params as a generic Map
2. **Converts to JSON**: Uses Gson to serialize the params Map back to JSON
3. **Deserializes to target type**: Uses Gson again to deserialize that JSON into the specified class type
4. **Returns typed object**: Provides a type-safe object that can be used in your notification handlers

This two-step serialization/deserialization process ensures that the params are properly converted from the generic Map structure to your specific parameter classes, enabling type-safe handling of notification data.

### Fifth Implementation Task: Create Notification Parameter Types

Now that we can deserialize notification parameters, we need to create the Java record types that represent these parameters.

#### Step 5: Create NotificationCancelledParams Record

**Action Required**: Create a new file `NotificationCancelledParams.java` in the `com.workshop.mcp.spec` package and copy the following code into it:

```java
package com.workshop.mcp.spec;

public record NotificationCancelledParams(Long requestId, String reason) {
}
```

## What this record represents:

This record defines the structure of parameters sent with a "cancelled" notification:

- **`requestId`**: The ID of the request that was cancelled (Long type to match JSON-RPC message IDs)
- **`reason`**: A string explaining why the request was cancelled

When a client cancels a pending request (like when we clicked "List Resources" and it timed out), it sends a notification with these parameters. Having this strongly-typed record allows us to:
1. Deserialize the notification parameters using the method we just added
2. Access the cancellation details in a type-safe manner
3. Handle cancellations appropriately in our server logic

### Sixth Implementation Task: Use the Deserializer for Notifications

Now let's use the deserializer method and the NotificationCancelledParams record to properly handle cancellation notifications.

#### Step 6: Update the Notification Handler

**Action Required**: In `IORouter.java`, locate the `private void process(JsonRpcNotification message)` method and update the `NOTIFICATION_CANCELLED` case by adding the following line:

```java
NotificationCancelledParams params = deserializer.deserializeParams(message, NotificationCancelledParams.class);
```

After making this change, your `process(JsonRpcNotification message)` method should look like this:

```java
private void process(JsonRpcNotification message) {
    UniqueKeys uniqueKey = UniqueKeys.fromValue(message.method());
    switch (uniqueKey) {
        case NOTIFICATIONS_INITIALIZED -> {
            logger.log("Initializing notifications must wait for this before calling the client." + message);
        }
        case NOTIFICATION_CANCELLED -> {
           NotificationCancelledParams params = deserializer.deserializeParams(message, NotificationCancelledParams.class);
           logger.log("Notification cancelled reason " + params.reason());
        }
        default -> logger.log("Unhandled notification method: " + uniqueKey + " for message: " + message);
    }
}
```

## What this change accomplishes:

By adding the deserializer call in the `NOTIFICATION_CANCELLED` case:
1. We're now properly deserializing the cancellation parameters into our strongly-typed `NotificationCancelledParams` record
2. This demonstrates how to use the deserializer infrastructure we set up in previous steps
3. Although we're not storing or using the result yet (we'll do that in future lessons), this proves our deserialization is working correctly

This is an important step in building robust MCP servers - converting raw JSON data into type-safe Java objects that can be processed reliably.

### Important Note: Error Handling and STDIO in MCP Servers

#### Critical Rule: Never Write to STDIO

**⚠️ WARNING**: In MCP servers, STDIO (standard input/output) is reserved exclusively for protocol communication. This means:

- **NO console.log, System.out.println, or print statements** to STDOUT
- **NO startup messages** to STDOUT
- **NO debugging output** to STDOUT
- **NO logging framework output** to STDOUT (like Log4j, SLF4J default configurations)

Any non-protocol data written to STDIO will corrupt the communication channel and cause the client to disconnect or behave unpredictably.

#### Error Handling in MCP

The MCP specification does include provisions for sending errors back to clients (see [JSON-RPC 2.0 Error Object specification](https://www.jsonrpc.org/specification#error_object)). However, there are important considerations:

1. **Implementation Choice**: In this workshop, we've intentionally omitted implementing error responses to clients. Experience shows that:
   - Error handling behavior varies significantly between different MCP clients
   - Sending detailed errors can lead to excessive token usage when troubleshooting protocol issues
   - It's often better to use proper logging (to files, not STDIO) for debugging

2. **Better Alternatives**:
   - Use the `isError` flag and other status mechanisms for reporting operational issues
   - Implement file-based logging (like we do with `LogFileWriter`) that writes to a separate log file
   - Return empty or minimal success responses for unimplemented features rather than errors

3. **Why This Matters**: When building MCP servers, you want to:
   - Focus on implementing working functionality rather than debugging protocol issues
   - Avoid confusing protocol-level errors with application-level errors
   - Maintain clean separation between debugging/logging and protocol communication

Remember: The `LogFileWriter` we're using writes to `inspector/logs/` specifically to avoid STDIO contamination. This is a best practice for all MCP server implementations.


### Final Step: Build and Test Your Updated Implementation

Now that we've implemented all the changes, let's rebuild the project and test our improvements.

#### Step 7: Rebuild and Test

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
   - Click **Connect** to establish a new connection
   - Verify the initialization handshake completes successfully
   - Click **Ping** to test that ping responses are working
   - Click **List Resources** to intentionally trigger an error

5. **Check the improved logging**:
   - Open the latest log file in `inspector/logs/`
   - Look for the cancellation notification
   - You should now see the properly deserialized cancellation reason being logged

## What you should observe:

- The initialization and ping functionality continue to work as before
- When clicking "List Resources", the client still times out (expected)
- **NEW**: The server logs now show the actual cancellation reason from the client, not just raw JSON
- This demonstrates that our notification deserialization is working correctly

## Congratulations!

You've successfully implemented:
- ✅ The MCP protocol handshake (Initialize)
- ✅ Basic request handling (Ping)
- ✅ Notification deserialization infrastructure
- ✅ Type-safe handling of cancellation notifications
- ✅ Proper logging without STDIO contamination

In the next lesson, we'll build on this foundation to implement the actual Resources, Prompts, and Tools capabilities that we're advertising.

