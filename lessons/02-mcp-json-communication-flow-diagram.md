# MCP Json Communication Flow Diagram

```mermaid
sequenceDiagram
    participant Client as MCP Inspector<br/>(Client)
    participant Server as Agent MCP Workshop<br/>(Server)
    participant Builders as com.workshop.mcp.spec.builders

    Note over Client,Server: Discovery Phase — no handshake, no session

    Client->>Server: server/discover<br/>{"id": "server-discover-probe-1", "method": "server/discover", "params": {<br/>"_meta": {"io.modelcontextprotocol/protocolVersion": "2026-07-28",<br/>"io.modelcontextprotocol/clientInfo": {"name": "inspector-cli", "version": "2.5.0"},<br/>"io.modelcontextprotocol/clientCapabilities": {"roots": {"listChanged": true},<br/>"extensions": {"io.modelcontextprotocol/tasks": {}}}}}}

    Note right of Server: Note the STRING id — a server MUST echo<br/>it back in the form it arrived.<br/>Server uses DiscoverResultBuilder.
    Note right of Client: The client still declares roots. SEP-2577 deprecated it<br/>rather than removing it, and asks clients to keep declaring<br/>it for the whole deprecation period. This server models no<br/>field for it, so the unrecognised key is simply ignored.
    Server-->>Builders: DiscoverResultBuilder.build()

    Server->>Client: discover result<br/>{"resultType": "complete", "supportedVersions": ["2026-07-28"],<br/>"capabilities": {"tools": {"listChanged": false}, "prompts": {"listChanged": false},<br/>"resources": {"listChanged": false, "subscribe": false}, "completions": {},<br/>},<br/>"ttlMs": 60000, "cacheScope": "public",<br/>"_meta": {"io.modelcontextprotocol/serverInfo": {"name": "agent-mcp-workshop", "version": "0.0.1"}}}

    Note over Client,Server: Every request below repeats params._meta.<br/>It is elided as {...envelope...} from here on.

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
    Server-->>Builders: PromptsListResultBuilder<br/>.addPrompt(PromptBuilder)

    Server->>Client: prompts/list result<br/>{"resultType": "complete", "prompts": [{"name": "search_keyword",<br/>"arguments": [{"name": "keyword", "required": true}]}], "ttlMs": 60000}

    Note over Client,Server: Tools Discovery Phase

    Client->>Server: tools/list

    Note right of Server: Server uses ToolsListResultBuilder
    Server-->>Builders: ToolsListResultBuilder.build()

    Server->>Client: tools/list result<br/>{"resultType": "complete", "tools": [{"name": "key_word_search"}]}

    Note over Client,Server: Later chapters add a round trip, an MCP App UI, and task handles.

    Note over Client,Server: Server-to-client notifications

    Client->>Server: subscriptions/listen (id: "listen:0")
    Server->>Client: acknowledgment<br/>_meta["io.modelcontextprotocol/subscriptionId"]
    Server--)Client: notifications/*, tagged with the same subscriptionId

    Note over Client,Server: Shutdown
    Server-->>Server: Stopping and closing resources
```
