# Overview of Workshop Lesson 3: Implementing Discovery and Core Message Routing

## Your Goal

**By the end of this lesson, you will have a working MCP server that can:**
1. Answer `server/discover` with its versions, capabilities, and identity
2. Read and validate the per-request `_meta` envelope, rejecting requests it cannot honour
3. Acknowledge a `subscriptions/listen` stream, and close it
4. Handle notification messages with type-safe deserialization

**To verify success:** `./gradlew chapterTest -Pchapter=03` is green, and so is the harness. Then connect the MCP Inspector to your server and confirm that discovery completes, and that the Resources tab shows `Method not found: resources/list`, which is `-32601`: the server advertises resources, but their handlers arrive in chapter 04.

---

## Core Objective

This lesson implements the foundational message routing for an MCP server on revision `2026-07-28`. That revision is **stateless**: it deleted the `initialize` handshake and the session that handshake used to open. Everything this lesson builds follows from that one change.

## Why There Is No Handshake

Older revisions were stateless in spirit and stateful in practice. An `initialize` request opened a session, both sides remembered what the other had declared, and every later message leaned on that memory. Revision `2026-07-28` removed it, along with `notifications/initialized`, `ping`, and the whole reserved-negative-id scheme for server-initiated requests.

Two mechanisms replace it:

- **`server/discover`** — a client asks a server what it can do, at any time, without opening anything. The answer carries cache hints, and a shared cache may hand it to other clients, which is why the result must be derivable from static configuration alone.
- **The `_meta` envelope** — every single request re-declares the client's protocol version, capabilities, and identity in `params._meta`. Nothing persists between requests, so nothing needs to be remembered.

The practical consequence for your code: capability flags like "can this client show a form" stop being fields on the router and become per-request facts read out of the envelope.

### What statelessness changed on the other transport

This workshop builds a stdio server, so the rest of the revision's transport work never reaches us. It is worth a paragraph anyway, because it is the same statelessness argument carried through to HTTP, and it is what you will meet first if you port this server to Streamable HTTP:

- **Sessions are gone.** The `Mcp-Session-Id` header was removed outright. A server that needs state across calls mints an explicit handle and takes it back as an ordinary tool argument — the same move the tasks extension makes with `taskId`. One consequence is that list endpoints no longer vary per connection.
- **The GET notification stream is gone**, replaced by `subscriptions/listen` — the same method we implement here, so this part does transfer.
- **Stream resumability is gone.** `Last-Event-ID` and SSE event IDs were removed. A broken response stream loses the in-flight request, and the client must reissue it under a **new** request id rather than resuming.
- **Two request headers became mandatory** on POST: `Mcp-Method` and `Mcp-Name`, mirroring the JSON-RPC body so intermediaries can route and rate-limit without parsing it. A header that disagrees with the body is `-32020` — the one error code in our table a stdio server can never emit. Relatedly, a tool may annotate an argument with `x-mcp-header` to have the client mirror its value into an `Mcp-Param-*` header.
- **Cancellation differs.** On modern Streamable HTTP, cancelling means closing that request's own response stream. Over stdio it is still `notifications/cancelled`, which is what we implement.
- **Authorization moved too** — authorization servers should return RFC 9207's `iss` and clients must validate it, client credentials are bound to the issuer that minted them, and Dynamic Client Registration is now deprecated in favour of Client ID Metadata Documents. None of it applies here: a stdio server takes credentials from its environment rather than following the authorization framework at all.

## Key Implementation Tasks

### 1. **The `server/discover` Handler**

As shown in the **sequence diagram**, discovery is answered before anything else:
- The client sends `server/discover`, often with a **string** id such as `"server-discover-probe-1"`
- The server replies with `resultType`, `supportedVersions`, `capabilities`, `instructions`, the cache hints `ttlMs` and `cacheScope`, and its identity under `_meta["io.modelcontextprotocol/serverInfo"]`. `resultType` is required on every result, and so are the cache hints on a result that can be cached
- This is answered whatever version it names — a client calls discovery precisely to learn which versions the server speaks, so rejecting it for guessing wrong would be circular. It must still carry the required envelope

The **class diagram** shows `DiscoverResultBuilder` constructing a `DiscoverResult` with `ServerCapabilities`.

### 2. **Envelope Parsing and Validation**

Where the old lesson had a ping handler, this one has the check that replaced it:
- `RequestEnvelope` is read from `params._meta` on every request
- A missing or incomplete envelope is `-32602`
- A revision this server does not speak is `-32022`, and the error `data` must carry both `requested` and `supported` so the client knows what to renegotiate to
- Method existence is settled **first**: a removed method like `initialize` is `-32601` by absence, and complaining about its envelope instead would tell a legacy client the wrong thing about why it failed

### 3. **Subscriptions**

On this revision a server sends list-changed notifications only on a stream the client opened with `subscriptions/listen`:
- The server acknowledges the stream first, with `notifications/subscriptions/acknowledged`, naming what it will honour and tagged with the listen request's id as `subscriptionId`
- Nothing this server lists ever changes, so it honours nothing and closes the stream at once, with a `complete` result for the listen request

The **sequence diagram** shows the acknowledgement and the close.

### 4. **Notification handling**

The lesson fills the hollowed notification switch:
- Calls Chapter 2's `deserializeParams` on a `NotificationCancelledParams` that is already on the branch
- Logs the cancellation id and reason instead of raw JSON

The **sequence diagram** shows the cancellation notification flow where the client sends `notifications/cancelled` with a `requestId` and a `reason`.

### 5. **Testing with MCP Inspector**

Students will:
- Build the project using Gradle
- Use the MCP Inspector (v2.5.0) with the workshop's `modern` era, which pins `2026-07-28` and sends `server/discover` with no fallback to `initialize`
- Open the Resources tab and read its `Method not found` error to understand capability advertising vs. implementation
- Send malformed requests on purpose with `inspector/modern-probe.sh`

## Architecture Understanding

The **class diagram** reveals the overall architecture:
- **JSON-RPC Message Types**: `JsonRpcRequest`, `JsonRpcResponse`, `JsonRpcNotification`, `JsonRpcErrorResponse`
- **Protocol Classes**: `DiscoverResult`, `RequestEnvelope`, `NotificationCancelledParams`
- **Subscriptions**: `SubscriptionsListenParams`, `SubscriptionFilter`, `SubscriptionsAcknowledgedParams`, `SubscriptionsListenResult`
- **Identity**: `RequestId` plus its Gson `TypeAdapter`, from Chapter 2 with the shared `McpGson` factory, and `ProgressToken`, new here, which is a string or an integer the same way
- **Capability System**: how server and client capabilities are structured and communicated
- **Builder Pattern**: `DiscoverResultBuilder` for constructing complex responses

## Important Learning Points

1. **STDIO Discipline**: never write to standard output — it is reserved for protocol communication. All logging goes to files under `logs/` in the server's working directory, which is `inspector/logs/` when the Inspector starts it.

2. **Ids Are Not Numbers**: the very first message of this revision arrives with a string id. Typing `id` as a `Long` crashes the server with `NumberFormatException: For input string: "server-discover-probe-1"` before anything else can happen. A server must echo an id back in exactly the form it arrived.

3. **Capability Advertisement**: the server advertises Resources, Prompts, and Tools in its discovery result but hasn't implemented them yet — demonstrating the importance of implementing everything you advertise.

4. **Error Handling Philosophy**: the workshop keeps protocol-level errors narrow and deliberate. `-32700`, `-32600`, `-32601`, `-32602`, `-32603` and `-32022` each mean one specific thing, and a client can diagnose every one of them from the error alone.

5. **Message Routing Pattern**: `IORouter` uses pattern matching with switch expressions to route message types (`JsonRpcRequest`, `JsonRpcNotification`, and so on) to the right handlers.

## What Students Will Achieve

By the end of this lesson, students will have:
- ✅ A working `server/discover` response
- ✅ Envelope validation with the three distinct rejection codes
- ✅ A subscription stream that is acknowledged and closed
- ✅ Polymorphic request-id handling
- ✅ Type-safe notification handling infrastructure
- ✅ Understanding of why a stateless protocol pushes state onto every message
- ✅ Experience using MCP Inspector for testing and debugging

This forms the foundation for implementing the actual Resources, Prompts, and Tools capabilities in later lessons, building on the established message routing and type-safe deserialization patterns.
