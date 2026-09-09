# Overview of Workshop Lesson 3: Implementing Discovery and Core Message Routing

## Your Goal

**By the end of this lesson, you will have a working MCP server that can:**
1. Answer `server/discover` with its versions, capabilities, and identity
2. Read and validate the per-request `_meta` envelope, rejecting requests it cannot honour
3. Handle notification messages with type-safe deserialization

**To verify success:** Run the MCP Inspector in modern mode, connect to your server, and confirm that discovery completes. When you click "List Resources" (which isn't implemented yet), you should see a properly logged cancellation reason instead of raw JSON.

---

## Core Objective

This lesson implements the foundational message routing for an MCP server on revision `2026-07-28`. That revision is **stateless**: it deleted the `initialize` handshake and the session that handshake used to open. Everything this lesson builds follows from that one change.

## Why There Is No Handshake

Older revisions were stateless in spirit and stateful in practice. An `initialize` request opened a session, both sides remembered what the other had declared, and every later message leaned on that memory. Revision `2026-07-28` removed it, along with `notifications/initialized`, `ping`, and the whole reserved-negative-id scheme for server-initiated requests.

Two mechanisms replace it:

- **`server/discover`** — a client asks a server what it can do, at any time, without opening anything. On stdio the Inspector answers this with a throwaway probe process, which is why the result must be derivable from static configuration alone.
- **The `_meta` envelope** — every single request re-declares the client's protocol version, capabilities, and identity in `params._meta`. Nothing persists between requests, so nothing needs to be remembered.

The practical consequence for your code: capability flags like "can this client show a form" stop being fields on the router and become per-request facts read out of the envelope.

## Key Implementation Tasks

### 1. **The `server/discover` Handler**

As shown in the **sequence diagram**, discovery is answered before anything else:
- The client sends `server/discover`, often with a **string** id such as `"server-discover-probe-1"`
- The server replies with `supportedVersions`, `capabilities`, `instructions`, cache hints, and its identity under `_meta["io.modelcontextprotocol/serverInfo"]`
- This is answered *before* any version check — a client calls discovery precisely to learn which versions the server speaks, so rejecting it for guessing wrong would be circular

The **class diagram** shows `DiscoverResultBuilder` constructing a `DiscoverResult` with `ServerCapabilities`.

### 2. **Envelope Parsing and Validation**

Where the old lesson had a ping handler, this one has the check that replaced it:
- `RequestEnvelope` is read from `params._meta` on every request
- A missing or incomplete envelope is `-32602`
- A revision this server does not speak is `-32022`, and the error `data` must carry both `requested` and `supported` so the client knows what to renegotiate to
- Method existence is settled **first**: a removed method like `initialize` is `-32601` by absence, and complaining about its envelope instead would tell a legacy client the wrong thing about why it failed

### 3. **Notification Deserialization Infrastructure**

The lesson builds type-safe handling for notifications:
- Adds a `deserializeParams` method to `JsonRpcMessageDeserializer`
- Creates a `NotificationCancelledParams` record to represent cancellation data
- Updates the notification handler to use typed parameters instead of raw JSON

The **sequence diagram** shows the cancellation notification flow where the client sends `notifications/cancelled` with a `requestId` and a `reason`.

### 4. **Testing with MCP Inspector**

Students will:
- Build the project using Gradle
- Use the MCP Inspector (v2.5.0) in **modern** mode to test their implementation
- Intentionally trigger errors by clicking "List Resources" to understand capability advertising vs. implementation

## Architecture Understanding

The **class diagram** reveals the overall architecture:
- **JSON-RPC Message Types**: `JsonRpcRequest`, `JsonRpcResponse`, `JsonRpcNotification`, `JsonRpcErrorResponse`
- **Protocol Classes**: `DiscoverResult`, `RequestEnvelope`, `NotificationCancelledParams`
- **Identity**: `RequestId` plus its Gson `TypeAdapter`, because a JSON-RPC id is a string *or* a number
- **Capability System**: how server and client capabilities are structured and communicated
- **Builder Pattern**: `DiscoverResultBuilder` for constructing complex responses

## Important Learning Points

1. **STDIO Discipline**: never write to standard output — it is reserved for protocol communication. All logging goes to files (`inspector/logs/`).

2. **Ids Are Not Numbers**: the very first message of this revision arrives with a string id. Typing `id` as a `Long` crashes the server with `NumberFormatException: For input string: "server-discover-probe-1"` before anything else can happen. A server must echo an id back in exactly the form it arrived.

3. **Capability Advertisement**: the server advertises Resources, Prompts, and Tools in its discovery result but hasn't implemented them yet — demonstrating the importance of implementing everything you advertise.

4. **Error Handling Philosophy**: the workshop keeps protocol-level errors narrow and deliberate. `-32601`, `-32602`, and `-32022` each mean one specific thing, and a client can diagnose all three from the error alone.

5. **Message Routing Pattern**: `IORouter` uses pattern matching with switch expressions to route message types (`JsonRpcRequest`, `JsonRpcNotification`, and so on) to the right handlers.

## What Students Will Achieve

By the end of this lesson, students will have:
- ✅ A working `server/discover` response
- ✅ Envelope validation with the three distinct rejection codes
- ✅ Polymorphic request-id handling
- ✅ Type-safe notification handling infrastructure
- ✅ Understanding of why a stateless protocol pushes state onto every message
- ✅ Experience using MCP Inspector for testing and debugging

This forms the foundation for implementing the actual Resources, Prompts, and Tools capabilities in later lessons, building on the established message routing and type-safe deserialization patterns.
