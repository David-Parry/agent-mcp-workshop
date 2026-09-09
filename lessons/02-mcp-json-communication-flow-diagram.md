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

    Server->>Client: discover result<br/>{"resultType": "complete", "supportedVersions": ["2026-07-28"],<br/>"capabilities": {"tools": {"listChanged": false}, "prompts": {"listChanged": false},<br/>"resources": {"listChanged": false, "subscribe": false}, "completions": {},<br/>"extensions": {"io.modelcontextprotocol/ui": {...}, "io.modelcontextprotocol/tasks": {}}},<br/>"ttlMs": 60000, "cacheScope": "public",<br/>"_meta": {"io.modelcontextprotocol/serverInfo": {"name": "agent-mcp-workshop", "version": "0.0.1"}}}

    Note over Client,Server: Every request below repeats params._meta.<br/>It is elided as {...envelope...} from here on.

    Note over Client,Server: Resource Discovery Phase

    Client->>Server: resources/list

    Note right of Server: Server uses ResourcesListResultBuilder<br/>to build list of Javadoc resources
    Server-->>Builders: ResourcesListResultBuilder<br/>.addResource(ResourceBuilder)

    Server->>Client: resources/list result<br/>{"resultType": "complete", "resources": [76 Javadoc resources + 1 app UI],<br/>"ttlMs": 60000, "cacheScope": "public"}

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

    Note right of Server: Server uses AppToolBuilder —<br/>the resourceUri is mirrored under<br/>_meta.ui.resourceUri AND _meta["ui/resourceUri"]
    Server-->>Builders: AppToolBuilder.build()

    Server->>Client: tools/list result<br/>{"resultType": "complete", "tools": [{"name": "key_word_search",<br/>"inputSchema": {"properties": {"keyword": {...}, "directory": {...}},<br/>"required": ["keyword"]},<br/>"_meta": {"ui/resourceUri": "ui://keyword-search/mcp-app.html"}}]}

    Note over Client,Server: Tool Execution — the one-hop path

    Client->>Server: tools/call<br/>{"name": "key_word_search",<br/>"arguments": {"keyword": "java", "directory": "/Users/.../mcp/tools"}}
    Server-->>Builders: ToolCallResultBuilder.addContent(...)
    Server->>Client: {"resultType": "complete", "content": [file paths with counts], "isError": false}

    Note over Client,Server: Tool Execution — Multi Round-Trip Requests

    Client->>Server: tools/call<br/>{"name": "key_word_search", "arguments": {"keyword": "java"}}

    Note right of Server: No directory. The server cannot send<br/>a request, so it asks inside a result — and asking<br/>the user is the only question it has.
    Server->>Client: {"resultType": "input_required",<br/>"inputRequests": {"search_directory": {"method": "elicitation/create",<br/>"params": {"mode": "form", "requestedSchema": {"required": ["directory"]}}}},<br/>"requestState": "eyJrZXl3b3JkIjoiamF2YSIsInN0YWdlIjoiZGlyZWN0b3J5In0"}

    Client->>Server: tools/call (NEW id)<br/>{"name": "key_word_search", "arguments": {"keyword": "java"},<br/>"requestState": "eyJrZXl3b3JkIjoiamF2YSIsInN0YWdlIjoiZGlyZWN0b3J5In0",<br/>"inputResponses": {"search_directory": {"action": "accept",<br/>"content": {"directory": "/Users/.../mcp/tools"}}}}

    Note right of Server: SearchContinuation.decode(requestState)<br/>recovers the keyword and stage.<br/>Nothing was stored between requests.
    Server->>Client: {"resultType": "complete", "content": [file paths with counts]}

    Note right of Server: Declined, cancelled, or a client that cannot show<br/>a form at all? The asking has run out, so the server<br/>searches its own working directory. The directory<br/>argument above is the one hop that avoids all of this.

    Note over Client,Server: Completion Phase

    Client->>Server: completion/complete<br/>{"argument": {"name": "keyword", "value": "jav"},<br/>"ref": {"type": "ref/prompt", "name": "search_keyword"}}
    Server-->>Builders: CompletionCompleteBuilder.build()
    Server->>Client: {"resultType": "complete",<br/>"completion": {"values": ["java", "the", "and"]}, "total": 3, "hasMore": true}

    Note over Client,Server: Prompt Execution Phase

    Client->>Server: prompts/get<br/>{"name": "search_keyword", "arguments": {"keyword": "java"}}
    Server-->>Builders: PromptsGetResultBuilder.addMessage(...)
    Server->>Client: {"resultType": "complete", "messages": [{"role": "user", ...}]}

    Note over Client,Server: Tasks extension — only for a client that declared it

    Client->>Server: tools/call<br/>(_meta declares io.modelcontextprotocol/tasks)
    Server->>Client: {"resultType": "task", "taskId": "...", "status": "working",<br/>"pollIntervalMs": 1000}
    Client->>Server: tasks/get {"taskId": "..."}<br/>(id: "inspector-ext-1" — another string id)
    Server->>Client: {"resultType": "complete", "status": "completed", "result": {...}}

    Note over Client,Server: A task that has no directory asks the same question<br/>somewhere else. resultType holds one value, and this call<br/>already spent it on the handle, so the form arrives as the<br/>task's status instead.

    Client->>Server: tasks/get {"taskId": "..."}
    Server->>Client: {"resultType": "complete", "status": "input_required",<br/>"inputRequests": {"search_directory": {"method": "elicitation/create",<br/>"params": {"mode": "form", "requestedSchema": {"required": ["directory"]}}}}}
    Client->>Server: tasks/update<br/>{"taskId": "...", "inputResponses": {"search_directory": {"action": "accept",<br/>"content": {"directory": "/Users/.../mcp/tools"}}}}
    Server->>Client: {"resultType": "complete"}

    Note right of Server: No answer within 60s, or a client that cannot show<br/>a form? The task falls back to the working directory,<br/>exactly as the inline path does.

    Note over Client,Server: Server-to-client notifications

    Client->>Server: subscriptions/listen (id: "listen:0")
    Server->>Client: acknowledgment<br/>_meta["io.modelcontextprotocol/subscriptionId"]
    Server--)Client: notifications/*, tagged with the same subscriptionId

    Note over Client,Server: Shutdown
    Server-->>Server: Stopping and closing resources
```
