# MCP Message Flow - Lesson 4: Tools Capability

This diagram shows the message flow for the Tools capability implemented in lesson 4.

```mermaid
sequenceDiagram
    participant Client
    participant Server
    
    Note over Client,Server: Prerequisites: Initialization already completed (from Lesson 3)
    
    rect rgb(100, 150, 255)
        Note over Client,Server: Tool Operations
        Client->>Server: tools/list (request)
        Note right of Server: Creates KeyWordSearch instance<br/>Returns tool metadata
        Server-->>Client: tools/list (response)
        Note right of Client: Gets tool info:<br/>- name: "key_word_search"<br/>- description<br/>- JSON schema
        
        Client->>Server: tools/call (request)
        Note right of Client: Sends tool name and params:<br/>- keyword: "class"<br/>- root_directory: (optional)
        Note right of Server: Executes KeyWordSearch<br/>Searches files for keyword
        Server-->>Client: tools/call (response)
        Note right of Client: Receives search results:<br/>- file paths<br/>- occurrence counts
    end
    
    Note over Client,Server: Error Handling Example
    
    Client->>Server: tools/call (request)
    Note right of Client: Unknown tool name
    Server-->>Client: tools/call (error response)
    Note right of Server: "Tool not found" error
```

## Key Message Flows in Lesson 4

### Tools Flow
1. **List Tools**: Client discovers the KeyWordSearch tool and its schema
2. **Call Tool**: Client executes keyword search with parameters

### Error Handling
- Tools: Handles unknown tool names gracefully
- All responses include proper error messages for debugging