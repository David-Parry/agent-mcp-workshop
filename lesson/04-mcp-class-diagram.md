# MCP Class Diagram - Lesson 4: Tools Capability

This diagram shows only the classes and relationships used in lesson 4's Tools implementation.

```mermaid
classDiagram
    direction TB
    
    %% Core JSON-RPC Classes (from lesson 3, used for all operations)
    class JsonRpcRequest {
        +String method
        +Object params
        +String id
    }
    
    class JsonRpcResponse {
        +Object result
        +String id
    }
    
    %% Tool-related Classes
    class ToolsListResult {
        +List~Tool~ tools
        +String nextCursor
    }
    
    class Tool {
        +String name
        +String description
        +InputSchema inputSchema
    }
    
    class InputSchema {
        +String type
        +Map~String,PropertySchema~ properties
        +List~String~ required
    }
    
    class PropertySchema {
        +String type
        +String description
    }
    
    class ToolCallParams {
        +String name
        +Map~String,Object~ arguments
    }
    
    class ToolCallResult {
        +List~ContentItem~ content
        +boolean isError
    }
    
    class ContentItem {
        +String type
        +String text
    }
    
    %% Builder Classes (used in implementation)
    class ToolsListResultBuilder {
        +addTool(String, String, InputSchema)
        +build() ToolsListResult
    }
    
    class ToolCallResultBuilder {
        +addTextContent(String)
        +asError()
        +build() ToolCallResult
    }
    
    %% Implementation Classes
    class KeyWordSearch {
        +String name()
        +String description()
        +InputSchema schema()
        +ToolCallResult call(ToolCallParams)
    }
    
    %% Relationships
    JsonRpcRequest ..> ToolCallParams : params
    
    JsonRpcResponse ..> ToolsListResult : result
    JsonRpcResponse ..> ToolCallResult : result
    
    ToolsListResult "1" --> "*" Tool : contains
    Tool "1" --> "1" InputSchema : has
    InputSchema "1" --> "*" PropertySchema : properties
    ToolCallResult "1" --> "*" ContentItem : contains
    
    ToolsListResultBuilder ..> ToolsListResult : builds
    ToolCallResultBuilder ..> ToolCallResult : builds
    
    KeyWordSearch ..> ToolCallResult : returns
```

## Key Class Groups in Lesson 4

### Tool Classes
- **Tool**: Defines a tool with name, description, and input schema
- **ToolsListResult**: Contains the list of available tools
- **InputSchema**: JSON Schema definition for tool parameters
- **ToolCallParams**: Parameters for executing a tool
- **ToolCallResult**: Result of tool execution with content items

### Builder Classes
Each major result type has a corresponding builder for type-safe construction:
- ToolsListResultBuilder, ToolCallResultBuilder

### Implementation Classes
- **KeyWordSearch**: The example tool implementation for searching keywords