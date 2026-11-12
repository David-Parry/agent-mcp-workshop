# MCP Json Communication Flow Diagram

The JSON below is what the server sends by the end of chapter 04. The builders it names, in `com.workshop.mcp.spec.builders`, arrive in chapters 03 and 04, so they are not on this branch yet. Chapters 05 to 07 add a round trip, an MCP App UI, and task handles.

```mermaid
sequenceDiagram
    participant Client as MCP Inspector<br/>(Client)
    participant Server as Agent MCP Workshop<br/>(Server)
    participant Builders as com.workshop.mcp.spec.builders

    Note over Client,Server: Discovery Phase — no handshake, no session

    Client->>Server: server/discover<br/>{"jsonrpc": "2.0", "id": "server-discover-probe-1", "method": "server/discover", "params": {<br/>"_meta": {"io.modelcontextprotocol/protocolVersion": "2026-07-28",<br/>"io.modelcontextprotocol/clientInfo": {"name": "inspector-cli", "version": "2.5.0"},<br/>"io.modelcontextprotocol/clientCapabilities": {"roots": {"listChanged": true},<br/>"extensions": {"io.modelcontextprotocol/tasks": {}}}}}}

    Note right of Server: Note the STRING id — a server MUST echo<br/>it back in the form it arrived.<br/>Server uses DiscoverResultBuilder.
    Note right of Client: The client still declares roots. SEP-2577 deprecated it<br/>rather than removing it, and asks clients to keep declaring<br/>it for the whole deprecation period. This server models no<br/>field for it, so the unrecognised key is simply ignored.
    Server-->>Builders: DiscoverResultBuilder.build()

    Server->>Client: discover result<br/>{"jsonrpc": "2.0", "id": "server-discover-probe-1", "result": {<br/>"resultType": "complete", "supportedVersions": ["2026-07-28"],<br/>"capabilities": {"completions": {}, "prompts": {"listChanged": false},<br/>"resources": {"listChanged": false, "subscribe": false}, "tools": {"listChanged": false}},<br/>"instructions": "Searches a project for a keyword.",<br/>"ttlMs": 60000, "cacheScope": "public",<br/>"_meta": {"io.modelcontextprotocol/serverInfo": {"name": "agent-mcp-workshop", "version": "0.0.1"}}}}

    Note over Client,Server: Every message below carries "jsonrpc": "2.0" and an id,<br/>every request repeats params._meta, and every result carries<br/>serverInfo in its _meta as discover's does. They are elided from here on,<br/>and each result is shown without its response wrapper.

    Note over Client,Server: Resource Discovery Phase

    Client->>Server: resources/list

    Note right of Server: Server uses ResourcesListResultBuilder<br/>to build list of Javadoc resources
    Server-->>Builders: ResourcesListResultBuilder<br/>.addResource(ResourceBuilder)

    Server->>Client: resources/list result<br/>{"resultType": "complete", "resources": [Javadoc pages],<br/>"ttlMs": 60000, "cacheScope": "public"}

    Client->>Server: resources/read<br/>{"uri": "javadoc/com/workshop/mcp/spec/Capability.html"}

    Note right of Server: Server uses ReadResourceResultBuilder
    Server-->>Builders: ReadResourceResultBuilder.build()

    Server->>Client: Capability.html content<br/>(cacheable: ttlMs + cacheScope)

    Note over Client,Server: Prompts Discovery Phase

    Client->>Server: prompts/list

    Note right of Server: Server uses PromptsListResultBuilder
    Server-->>Builders: PromptsListResultBuilder<br/>.withPrompt(name, description)<br/>.withPromptArgument(name, description, required)

    Server->>Client: prompts/list result<br/>{"resultType": "complete", "prompts": [{"name": "search_keyword",<br/>"arguments": [{"name": "keyword", "required": true}]}],<br/>"ttlMs": 60000, "cacheScope": "public"}

    Note over Client,Server: Tools Discovery Phase

    Client->>Server: tools/list

    Note right of Server: Server uses ToolsListResultBuilder
    Server-->>Builders: ToolsListResultBuilder.build()

    Server->>Client: tools/list result<br/>{"resultType": "complete", "tools": [{"name": "key_word_search", ...}],<br/>"ttlMs": 60000, "cacheScope": "public"}

    Note over Client,Server: Server-to-client notifications

    Client->>Server: subscriptions/listen<br/>{"id": "listen:0", "params": {"notifications":<br/>{"toolsListChanged": true, "resourcesListChanged": true}}}
    Server--)Client: notifications/subscriptions/acknowledged<br/>{"notifications": {},<br/>"_meta": {"io.modelcontextprotocol/subscriptionId": "listen:0"}}
    Note right of Server: The acknowledgement comes first and echoes only what<br/>the server agreed to send. Nothing this server lists ever<br/>changes, so it agrees to nothing and closes the stream.
    Server->>Client: closing result for id "listen:0"<br/>{"resultType": "complete",<br/>"_meta": {"io.modelcontextprotocol/subscriptionId": "listen:0"}}

    Note over Client,Server: Shutdown — the client closes stdin
    Server-->>Server: Stopping and closing resources
```
