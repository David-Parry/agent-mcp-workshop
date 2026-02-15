# Overview of Workshop Lesson 3: Implementing MCP Protocol Handshake and Core Message Routing

## Your Goal

**By the end of this lesson, you will have a working MCP server that can:**
1. Complete the protocol handshake with MCP clients
2. Respond to ping requests
3. Handle notification messages with type-safe deserialization

**To verify success:** Run the MCP Inspector, connect to your server, and confirm that initialization completes and ping works. When you click "List Resources" (which isn't implemented yet), you should see a properly logged cancellation reason instead of raw JSON.

---

## Core Objective
This lesson focuses on implementing the foundational message routing system for an MCP (Model Context Protocol) server, establishing the critical communication infrastructure between MCP clients and servers.

## Key Implementation Tasks

### 1. **Initialize Handler Implementation**
As shown in the **sequence diagram**, the initialization phase is the required first step in MCP communication:
- The client sends an `initialize` request
- The server processes `InitializeParams` to check client capabilities (roots and sampling)
- The server responds with `InitializeResult` containing protocol version, server capabilities, and server info
- This establishes what features both sides support for subsequent communication

The **class diagram** shows how `InitializeParams` contains `ClientCapabilities` and the response uses `InitializeResultBuilder` to construct an `InitializeResult` with `ServerCapabilities`.

### 2. **Ping Handler Implementation**
The sequence diagram highlights the ping operation in a purple box:
- Simple heartbeat mechanism for connection verification
- Server responds with an empty object `{}`
- Used for keep-alive functionality and latency measurement

### 3. **Notification Deserialization Infrastructure**
The lesson builds proper type-safe handling for notifications:
- Adds a `deserializeParams` method to `JsonRpcMessageDeserializer`
- Creates `NotificationCancelledParams` record to represent cancellation data
- Updates the notification handler to use typed parameters instead of raw JSON

The **sequence diagram** shows the cancellation notification flow (orange box) where the client sends `notifications/cancelled` with requestId and reason.

### 4. **Testing with MCP Inspector**
Students will:
- Build the project using Gradle
- Use the MCP Inspector tool (v0.14.0) to test their implementation
- Intentionally trigger errors by clicking "List Resources" to understand capability advertising vs. implementation

## Architecture Understanding

The **class diagram** reveals the overall architecture:
- **JSON-RPC Message Types**: `JsonRpcRequest`, `JsonRpcResponse`, `JsonRpcNotification`, `JsonRpcErrorResponse`
- **Protocol Classes**: `InitializeParams`, `InitializeResult`, `NotificationCancelledParams`
- **Capability System**: How server and client capabilities are structured and communicated
- **Builder Pattern**: `InitializeResultBuilder` for constructing complex responses

## Important Learning Points

1. **STDIO Discipline**: The lesson emphasizes never writing to standard output since it's reserved for protocol communication. All logging goes to files (`inspector/logs/`).

2. **Capability Advertisement**: The server advertises Resources, Prompts, and Tools capabilities during initialization but hasn't implemented them yet - demonstrating the importance of implementing all advertised features.

3. **Error Handling Philosophy**: The workshop intentionally omits client error responses to focus on working functionality rather than debugging protocol issues.

4. **Message Routing Pattern**: The `IORouter` uses pattern matching with switch expressions to route different message types (`JsonRpcRequest`, `JsonRpcNotification`, etc.) to appropriate handlers.

## What Students Will Achieve
By the end of this lesson, students will have:
- ✅ A working MCP protocol handshake
- ✅ Basic request handling (ping)
- ✅ Type-safe notification handling infrastructure
- ✅ Understanding of MCP client-server communication flow
- ✅ Experience using MCP Inspector for testing and debugging

This forms the foundation for implementing the actual Resources, Prompts, and Tools capabilities in future lessons, building on the established message routing and type-safe deserialization patterns.