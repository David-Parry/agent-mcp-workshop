# Model Context Protocol (MCP) - Key Points Review

## Message Diagram Walkthrough
- [High-level class diagram illustrates key MCP components](02-message-class-diagram.md)
- [Message flow diagram](02-mcp-message-flow.md)
- [JSON Message flow diagram](02-mcp-json-communication-flow-diagram.md)

## JSON-RPC 2.0 Foundation

### Core Concepts
- MCP builds on **JSON-RPC 2.0** specification for message exchange
- All communication follows request-response pattern with structured JSON messages
- **Stateless protocol** - each request contains all necessary information
- Supports both synchronous requests and asynchronous notifications

## Key Protocol Features

### 1. **Extensibility**
- New methods can be added without breaking compatibility
- Optional parameters for backward compatibility
- Capability-based feature detection

### 2. **Transport Agnostic**
- Works over **STDIO**, sse, streamable http, etc.
- Transport layer handles connection management
- Protocol focuses on message semantics

### 3. **Bidirectional Communication**
- Both client and server can send notifications
- Enables real-time updates and event streaming
- Supports collaborative workflows

### 4. **Structured Data Exchange**
- Resources have URIs and MIME types
- Tools have JSON Schema definitions
- Prompts include argument specifications
