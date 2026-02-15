# MCP Server Workshop Syllabus

## Workshop Overview

This instructor-led workshop teaches you to build a Java-based Model Context Protocol (MCP) server from scratch. By the end, you'll have a fully functional MCP server that integrates with AI coding assistants like Claude Code, Cursor, and Windsurf.

**Duration**: 5 chapters (instructor-paced)
**Prerequisites**: Java fundamentals, familiarity with JSON
**Tools Required**: JDK 21+, Gradle, Node.js (for MCP Inspector)

---

## Chapter 1: MCP Transport Layer - The Foundation

**Branch**: `01-chapter`

### Objective
Build the bidirectional I/O infrastructure that enables communication between MCP clients and servers.

### What You'll Build
- `IOHandlerImpl` - Manages input/output streams
- `LogFileWriter` - Thread-safe logging to files
- Event-driven listener architecture

### Key Concepts
- STDIO-based communication
- Thread-safe input reading with `Scanner`
- Observer pattern for message distribution
- JSON serialization with Gson

### Tasks
1. Implement `startInputReader()` - Read lines from System.in
2. Implement `publishLine()` - Distribute input to listeners
3. Implement `emit()` - Send JSON responses to System.out

### Outcome
A working transport layer that can read input, publish to listeners, and emit JSON responses.

---

## Chapter 2: JSON-RPC Protocol Review

**Branch**: `02-chapter`

### Objective
Understand the JSON-RPC 2.0 protocol that MCP uses for all communication.

### What You'll Learn
- JSON-RPC request/response structure
- Message types: Request, Response, Notification, Error
- MCP's extension of JSON-RPC
- Capability negotiation concepts

### Key Concepts
- Stateless request-response pattern
- Transport-agnostic protocol design
- Bidirectional communication
- Structured data exchange with schemas

### Supporting Materials
- `02-message-class-diagram.md` - Class relationships
- `02-mcp-message-flow.md` - Message sequence diagrams
- `02-mcp-json-communication-flow-diagram.md` - JSON examples

### Outcome
Understanding of how MCP messages are structured and exchanged.

---

## Chapter 3: Protocol Handshake & Core Message Routing

**Branch**: `03-chapter`

### Objective
Implement the MCP protocol handshake and basic request handling.

### What You'll Build
- Initialize handler (client-server handshake)
- Ping handler (heartbeat mechanism)
- Notification deserialization infrastructure
- `NotificationCancelledParams` record

### Key Concepts
- Protocol version negotiation
- Capability advertisement
- Type-safe parameter deserialization
- STDIO discipline (never write debug output to stdout)

### Tasks
1. Implement INITIALIZE case in `IORouter`
2. Implement PING case in `IORouter`
3. Build and test with MCP Inspector
4. Add `deserializeParams()` to `JsonRpcMessageDeserializer`
5. Create `NotificationCancelledParams` record
6. Update notification handler for type-safe handling

### Verification
- MCP Inspector connects successfully
- Initialization completes (green status)
- Ping returns a response
- Cancellation notifications show reason text in logs

### Outcome
A server that completes the MCP handshake and handles basic requests.

---

## Chapter 4: Resources, Tools, Prompts & Elicitation

**Branch**: `04-chapter`

### Objective
Implement all MCP capabilities to create a fully functional server.

### What You'll Build

#### Resources Capability
- `JavadocResources` helper class
- RESOURCES_LIST handler (discovery)
- RESOURCES_READ handler (content retrieval)

#### Tools Capability
- `KeyWordSearch` tool implementation
- TOOLS_LIST handler (tool discovery)
- TOOLS_CALL handler (tool execution)

#### Prompts Capability
- PROMPTS_LIST handler
- PROMPTS_GET handler (already implemented)

#### Elicitation Capability
- `sendElicitationMessage()` method
- Client capability detection
- ELICITATION_CREATE_MESSAGE handler

### Key Concepts
- Builder pattern for response construction
- JSON Schema for tool parameters
- Classpath resource loading
- Bidirectional elicitation flow

### Verification
- "List Resources" shows Javadoc files
- "List Tools" shows `key_word_search`
- "List Prompts" shows `search_keyword`
- Tool execution returns search results

### Outcome
A complete MCP server with all advertised capabilities implemented.

---

## Chapter 5: Live LLM Integration

**Branch**: `05-chapter`

### Objective
Connect your MCP server to a real AI coding assistant and use it in conversation.

### What You'll Do
1. Create `mcp.json` configuration file
2. Register server with your chosen client
3. Verify tool discovery
4. Have real conversations using your tool

### Supported Clients
| Client | Configuration Location |
|--------|------------------------|
| Claude Code | Project `mcp.json` (auto-discovered) |
| Cursor | Settings > MCP Servers |
| Windsurf | `~/.codeium/windsurf/mcp_config.json` |
| Cline | VS Code extension settings |

### Example Conversations
- "Search for 'TODO' and tell me which file has the most occurrences"
- "Find all files containing 'test' and summarize the testing approach"
- "Compare occurrences of 'error' vs 'exception' in the codebase"

### Outcome
Your MCP server running with a live LLM, demonstrating the complete development lifecycle.

---

## Workshop Summary

| Chapter | Topic | Key Deliverable |
|---------|-------|-----------------|
| 1 | Transport Layer | Bidirectional I/O with event publishing |
| 2 | Protocol Review | Understanding of JSON-RPC and MCP |
| 3 | Handshake & Routing | Working protocol handshake and ping |
| 4 | Capabilities | Complete server with Resources, Tools, Prompts, Elicitation |
| 5 | Live Integration | MCP server running with AI assistant |

## What You'll Have Built

By completing this workshop, you'll have:

- A production-ready MCP server in Java
- Understanding of the Model Context Protocol specification
- Experience with MCP Inspector for testing
- A custom `key_word_search` tool
- Integration with your preferred AI coding assistant

## Next Steps After Workshop

- Add additional tools to your server
- Implement custom resources for your domain
- Create specialized prompts for your workflows
- Share your MCP server with your team
