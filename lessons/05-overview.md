# Overview of Workshop Lesson 5: MCP Extensions and the Experimental Capability

Based on the foundation built in lesson 4, here's what will take place in this lesson:

## Core Objective

This lesson introduces the **MCP Extensions** system — the mechanism the protocol uses to evolve beyond its core specification without breaking existing implementations. Students will update the server's `ServerCapabilities` to include the `experimental` map, add a builder method to populate it, and wire the declaration into the `initialize` response so clients know what extensions the server supports.

> Official documentation: [https://modelcontextprotocol.info/docs/extensions/](https://modelcontextprotocol.info/docs/extensions/)

## What Are MCP Extensions?

MCP extensions are **optional additions** to the specification. They define capabilities beyond the core protocol that are:

- **Modular** — independently versioned and maintained in separate repositories
- **Opt-in** — disabled by default in SDKs; both sides must explicitly declare support
- **Experimental** — tracked in the `experimental` field of capabilities until they stabilize

## Official Extensions

The MCP organization currently maintains two extension repositories:

### ext-auth — Supplementary Authorization

Provides authorization mechanisms for scenarios the core protocol doesn't cover:

- **OAuth 2.0 Client Credentials** — machine-to-machine (M2M) authentication flow for servers that call protected APIs on behalf of themselves rather than a user
- **Enterprise-Managed Authorization** — a framework for enterprise environments requiring centralized, auditable access control through corporate identity providers

### ext-apps — Interactive UI Capabilities

Allows MCP servers to render **interactive UI elements inline within conversations**:
- Charts and data visualizations
- Forms for structured input collection
- Video and media players
- Custom interactive components

Both follow the same declaration pattern: the server advertises support in the `experimental` map of its `ServerCapabilities`, the client checks for that key, and only then does either side send extension-specific messages.

## Extension Identifiers

Every extension uses a namespaced identifier:

```
{vendor-prefix}/{extension-name}
```

Official MCP extensions use the prefix `io.modelcontextprotocol`:

```
io.modelcontextprotocol/oauth-client-credentials
io.modelcontextprotocol/enterprise-managed-authorization
io.modelcontextprotocol/mcp-apps
```

Your own organization would use its own prefix:

```
com.mycompany/analytics-dashboard
```

## How the Experimental Map Works

The `experimental` field in `ServerCapabilities` is a `Map<String, Object>`. Each entry is an extension identifier pointing to an extension-specific configuration object. During `initialize`, the server includes this map in its response, and the client reads it to know what extensions are available.

```json
{
  "protocolVersion": "2024-11-05",
  "capabilities": {
    "tools": {},
    "prompts": {},
    "resources": {},
    "completions": {},
    "experimental": {
      "io.modelcontextprotocol/elicitation": {}
    }
  }
}
```

## Key Implementation Tasks

### 1. Add `experimental` to `ServerCapabilities`

Add `Map<String, Object> experimental` as a new field alongside `completions`. This is the slot in the `initialize` response where the server lists each extension it supports:

```java
public record ServerCapabilities(
    Capability tools,
    Capability prompts,
    Capability resources,
    Capability completions,
    Map<String, Object> experimental
) {}
```

### 2. Add `withExperimentalCapability()` to `InitializeResultBuilder`

A new builder method that registers an extension by identifier and configuration, called before `withDefaultCapabilities()` so the map is populated when the `ServerCapabilities` record is constructed:

```java
builder.withExperimentalCapability("io.modelcontextprotocol/elicitation", new Object())
       .withDefaultCapabilities()
```

### 3. Implement the Elicitation Capability in `IORouter`

Four additions to `IORouter`:
- `hasElicitation` flag and `ELICITATION_REQUEST_ID` constant
- Check `clientCapabilities.elicitation()` during `INITIALIZE`
- Call `sendElicitationMessage()` in `NOTIFICATIONS_INITIALIZED`
- Handle `ELICITATION_CREATE_MESSAGE` in the request switch

### 4. Wire Extension Declaration in `IORouter.INITIALIZE`

Call `withExperimentalCapability()` in the builder chain so every `initialize` response tells the client this server supports elicitation.

## How This Fits the Full Extension Lifecycle

After this lesson, both directions of the elicitation handshake are complete:

| Direction | Mechanism | Where |
|-----------|-----------|-------|
| Client → Server | `capabilities.elicitation: {}` in `initialize` request | `ClientCapabilities.elicitation` (already in spec) |
| Server → Client | `capabilities.experimental["io.modelcontextprotocol/elicitation"]` in `initialize` response | **This lesson** |
| Server sends elicitation | `elicitation/create` in `NOTIFICATIONS_INITIALIZED` | **This lesson** |
| Client responds | `JsonRpcRequest` with `ELICITATION_CREATE_MESSAGE` | **This lesson** |

## Important Learning Points

1. **Extension Identifiers** — The `{vendor-prefix}/{extension-name}` format prevents naming collisions across the ecosystem.

2. **Opt-In by Default** — SDK support for extensions is disabled by default and requires explicit declaration in both `initialize` request and response before either side activates extension behavior.

3. **Backwards Compatibility** — Because extensions live in the `experimental` map, servers that don't support a given extension simply omit its key. Clients that don't recognize a key ignore it. Neither side breaks.

4. **Same Pattern, Every Extension** — ext-auth, ext-apps, and any future extension follow the same lifecycle: declare in `experimental`, check for the key in the other side's `experimental`, then exchange extension-specific messages.

5. **Completions Capability** — The `completions` field in `ServerCapabilities` is a core capability (not an extension) that enables the `completion/complete` handler already present in the server. It is included as part of the `ServerCapabilities` update in this lesson.

## What Students Will Achieve

By the end of this lesson, students will have:

- ✅ Understanding of the MCP extensions system and official extension repositories
- ✅ Knowledge of the `experimental` capability map and extension identifier format
- ✅ `ServerCapabilities` updated with `completions` and `experimental` fields
- ✅ `InitializeResultBuilder` updated with `withExperimentalCapability()` method
- ✅ Elicitation wired end-to-end in `IORouter` (detect, send, handle)
- ✅ Server declaring `io.modelcontextprotocol/elicitation` in every `initialize` response
- ✅ Both directions of the elicitation handshake fully implemented
- ✅ The mental model to implement any future MCP extension using the same pattern
