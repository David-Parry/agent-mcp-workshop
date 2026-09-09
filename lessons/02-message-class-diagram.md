# High level Class Diagram for MCP (Model Context Protocol)

```mermaid
classDiagram
    direction LR
    
    %% JsonRpc Classes - Top Row
    class JsonRpcRequest
    class JsonRpcResponse
    class JsonRpcNotification
    class JsonRpcError
    class JsonRpcErrorResponse
    
    %% Core Protocol Classes - Second Row
    class DiscoverResult
    class CompletionCompleteResponse
    class InputRequiredResult
    class InputRequest
    
    %% Parameter Classes
    class RequestEnvelope
    class PromptsGetParams
    class ToolCallParams
    class ReadResourceParam
    class CompletionCompleteParams
    class ToolsListParams
    class PromptsListParams
    class TasksGetParams
    
    %% Schema Classes - Left Side
    class InputSchema
    class PropertySchema
    
    %% Tool Classes - Center Left
    class ToolsListResult
    class ToolCallResult
    class Tool
    
    %% Prompt Classes - Center Right
    class Prompt
    class PromptsListResult
    class PromptsGetResult
    
    %% Resource Classes - Bottom Row
    class Resource
    class ResourcesListResult
    class ReadResourceResult
    class TextReadResource
    
    %% Additional Core Classes
    class Message
    class ContentItem
    class ServerCapabilities
    class ClientCapabilities {
        +Elicitation elicitation
        +Map~String, Object~ extensions
    }
    class ServerInfo
    class ClientInfo
    class Capability
    class Elicitation
    class ElicitationCreateParams
    class ElicitationCreateResult
    class MetaInfo
    class CompletionArgument
    class PromptRef
    
    %% Relationships to show grouping
    InputSchema ..> PropertySchema : contains
    PromptsListResult ..> Prompt : contains
    ResourcesListResult ..> Resource : contains
    JsonRpcErrorResponse ..> JsonRpcError : contains
    
    %% JsonRpcRequest can contain these in params field
    JsonRpcRequest ..> RequestEnvelope : params._meta
    JsonRpcRequest ..> PromptsGetParams : params
    JsonRpcRequest ..> ToolCallParams : params
    JsonRpcRequest ..> ReadResourceParam : params
    JsonRpcRequest ..> CompletionCompleteParams : params
    JsonRpcRequest ..> ToolsListParams : params
    JsonRpcRequest ..> PromptsListParams : params
    JsonRpcRequest ..> TasksGetParams : params
    
    %% JsonRpcResponse can contain these in result field
    JsonRpcResponse ..> CompletionCompleteResponse : result
    JsonRpcResponse ..> InputRequiredResult : result
    JsonRpcResponse ..> DiscoverResult : result
    JsonRpcResponse ..> ToolsListResult : result
    JsonRpcResponse ..> ToolCallResult : result
    JsonRpcResponse ..> PromptsListResult : result
    JsonRpcResponse ..> PromptsGetResult : result
    JsonRpcResponse ..> ResourcesListResult : result
    JsonRpcResponse ..> ReadResourceResult : result
    
    %% Multi Round-Trip Requests — the question travels out in a result,
    %% the answer comes back in the params of the retry
    InputRequiredResult ..> InputRequest : inputRequests
    InputRequest ..> ElicitationCreateParams : params
    ToolCallParams ..> ElicitationCreateResult : inputResponses
    
    %% Additional relationships discovered
    ToolsListResult ..> Tool : contains
    ReadResourceResult ..> TextReadResource : contains
    ToolCallResult ..> ContentItem : contains
    PromptsGetResult ..> Message : contains
    DiscoverResult ..> ServerCapabilities : contains
    DiscoverResult ..> ServerInfo : in _meta
    RequestEnvelope ..> ClientCapabilities : contains
    RequestEnvelope ..> ClientInfo : contains
    ServerCapabilities ..> Capability : contains
    ClientCapabilities ..> Elicitation : contains
    Elicitation ..> Capability : form and url
    Tool ..> InputSchema : contains
    CompletionCompleteParams ..> MetaInfo : contains
    CompletionCompleteParams ..> CompletionArgument : contains
    CompletionCompleteParams ..> PromptRef : contains
    PromptsGetParams ..> MetaInfo : contains
    ToolCallParams ..> MetaInfo : contains
    ReadResourceParam ..> MetaInfo : contains
    ToolsListParams ..> MetaInfo : contains
    PromptsListParams ..> MetaInfo : contains
```
