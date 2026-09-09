# MCP Message Flow - Lesson 4: Resources, Tools, and Prompts

This diagram shows the message flows for the three main capabilities implemented in lesson 4.

```mermaid
sequenceDiagram
    participant Client
    participant Server
    
    Note over Client,Server: Prerequisites: server/discover answered, and every request below carries its own params._meta envelope (from Lesson 3)
    
    rect rgb(100, 200, 100)
        Note over Client,Server: Resource Operations
        Client->>Server: resources/list (request)
        Note right of Server: Loads all Javadoc HTML files<br/>from classpath
        Server-->>Client: resources/list (response)
        Note right of Client: Receives list of available<br/>documentation resources
        
        Client->>Server: resources/read (request)
        Note right of Client: Sends resource URI<br/>(e.g., "javadoc/com/.../Resource.html")
        Note right of Server: Reads HTML content<br/>from classpath
        Server-->>Client: resources/read (response)
        Note right of Client: Receives HTML content<br/>with MIME type "text/html"
    end
    
    rect rgb(100, 150, 255)
        Note over Client,Server: Tool Operations
        Client->>Server: tools/list (request)
        Note right of Server: Creates KeyWordSearch instance<br/>Returns tool metadata
        Server-->>Client: tools/list (response)
        Note right of Client: Gets tool info:<br/>- name: "key_word_search"<br/>- description<br/>- JSON schema
        
        Client->>Server: tools/call (request)
        Note right of Client: Sends tool name and params:<br/>- keyword: "class"<br/>- directory: "/path/to/project" (optional)
        Note right of Server: Executes KeyWordSearch<br/>Searches files for keyword
        Server-->>Client: tools/call (response)
        Note right of Client: Receives search results:<br/>- file paths<br/>- occurrence counts
    end
    
    rect rgb(255, 150, 100)
        Note over Client,Server: Prompt Operations
        Client->>Server: prompts/list (request)
        Note right of Server: Returns "search_keyword" prompt<br/>with required arguments
        Server-->>Client: prompts/list (response)
        Note right of Client: Gets prompt definition:<br/>- name: "search_keyword"<br/>- argument: "keyword" (required)
        
        Client->>Server: prompts/get (request)
        Note right of Client: Sends prompt name<br/>and arguments
        Note right of Server: Generates contextual messages<br/>based on provided keyword
        Server-->>Client: prompts/get (response)
        Note right of Client: Receives guided messages:<br/>- User message<br/>- Assistant messages<br/>showing tool usage
    end
    
    Note over Client,Server: Error Handling Examples
    
    Client->>Server: resources/read (request)
    Note right of Client: Invalid or missing URI
    Server-->>Client: resources/read (error response)
    Note right of Server: Returns error message
    
    Client->>Server: tools/call (request)
    Note right of Client: Unknown tool name
    Server-->>Client: tools/call (error response)
    Note right of Server: "Tool not found" error
```

## Key Message Flows in Lesson 4

### Resources Flow
1. **List Resources**: Client discovers available Javadoc HTML documentation
2. **Read Resource**: Client retrieves specific HTML content by URI

### Tools Flow
1. **List Tools**: Client discovers the KeyWordSearch tool and its schema
2. **Call Tool**: Client executes keyword search with parameters

### Prompts Flow
1. **List Prompts**: Client discovers the search_keyword prompt template
2. **Get Prompt**: Client receives guided messages for tool usage

### Error Handling
- Resources: Handles missing or invalid resource URIs
- Tools: Handles unknown tool names gracefully
- All responses include proper error messages for debugging