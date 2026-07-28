# Model Context Protocol (MCP) - Key Points Review

## Objectives

By the end of this chapter you can:

- Tell the four JSON-RPC 2.0 message shapes apart by their fields, and explain why an explicit `"id": null` still makes a notification
- Turn away what is not a message in the right way: text that is not JSON throws, while well-formed JSON that is not a JSON-RPC 2.0 message comes back as `Unknown`
- Read a method's `params` into its own record, and explain why absent params stay `null`
- Lift the stateless envelope (`protocolVersion`, `clientCapabilities`, `clientInfo`, `logLevel`) out of `params._meta`, and say which `_meta` keys the specification reserves
- Explain why a request id is echoed back exactly as it arrived, as a string or a number

## Hands-on
- [Chapter 2 instructions: reading JSON-RPC messages](02-instructions.md) — implement `JsonRpcMessageDeserializer`

## Message Diagram Walkthrough
- [Class diagram of this chapter's classes](02-message-class-diagram.md)
- [Message flow diagram](02-mcp-message-flow.md)
- [JSON Message flow diagram](02-mcp-json-communication-flow-diagram.md)

Each diagram is a Markdown file with the Mermaid source embedded in it, so it renders in the editor and on GitHub without a separate viewer.

## JSON-RPC 2.0 Foundation

### Core Concepts
- MCP builds on the **JSON-RPC 2.0** specification for message exchange. Every message is a JSON object carrying `"jsonrpc": "2.0"`, and a batch (an array of messages) is not accepted
- Most traffic is a request and its response, but not all of it: a notification gets no reply, and a `subscriptions/listen` request stays open while notifications flow on it, until a result closes it
- **Requests flow one way.** The client sends them, and a server MUST NOT initiate one. When a server needs something from the user, it returns an `input_required` result and the client retries with the answer
- **Stateless protocol** - there is no `initialize` handshake and no session, so each request carries its own context in `params._meta`

## Key Protocol Features

### 1. **Extensibility**
- New methods can be added without breaking compatibility
- Optional parameters for backward compatibility
- Capability-based feature detection: the client declares its capabilities on every request, and the server advertises its own from `server/discover`
- Extensions such as `io.modelcontextprotocol/tasks` and `io.modelcontextprotocol/ui` are named by identifier and declared the same way
- `_meta` keys are namespaced, and prefixes whose second label is `modelcontextprotocol` or `mcp` are reserved for the specification

### 2. **Transport Agnostic**
- Two standard transports: **stdio**, which this workshop uses, and **Streamable HTTP**
- The older HTTP+SSE transport is deprecated; Streamable HTTP replaced it
- The transport handles framing and connections, and the protocol defines only the messages

### 3. **Server-to-Client Traffic Without Server Requests**
- Both sides send notifications, but every notification a server sends belongs to a request that is still in flight: progress on a call, or list changes on an open `subscriptions/listen` stream
- A question for the user travels inside a result, and its answer comes back on a new request, so the server never has to call the client

### 4. **Structured Data Exchange**
- Resources have URIs and MIME types
- Tools have JSON Schema definitions
- Prompts include argument specifications
