# Server Class Diagram

```mermaid
classDiagram
    direction LR

    %% JsonRpc Classes
    class JsonRpcRequest
    class JsonRpcResponse
    class JsonRpcNotification
    class JsonRpcErrorResponse

    %% Identity — an id is a string or a number
    class RequestId
    class RequestIdTypeAdapter
    class McpGson

    %% Core Protocol Classes
    class DiscoverResult
    class RequestEnvelope
    class NotificationCancelledParams

    %% Supporting Classes
    class ServerCapabilities
    class ClientCapabilities
    class ServerInfo
    class ClientInfo
    class Capability
    class RootsCapability
    class SamplingCapability
    class Elicitation
    class MetaKeys

    %% Server and related
    class Server
    class JsonRpcMessageDeserializer
    class DiscoverResultBuilder

    %% Relationships
    JsonRpcRequest ..> RequestId : id
    JsonRpcResponse ..> RequestId : id
    JsonRpcErrorResponse ..> RequestId : id
    RequestIdTypeAdapter ..> RequestId : reads and writes
    McpGson ..> RequestIdTypeAdapter : registers

    JsonRpcRequest ..> RequestEnvelope : params._meta
    JsonRpcResponse ..> DiscoverResult : result

    JsonRpcNotification ..> NotificationCancelledParams : params
    NotificationCancelledParams ..> RequestId : requestId

    DiscoverResult ..> ServerCapabilities : contains
    DiscoverResult ..> ServerInfo : in _meta

    RequestEnvelope ..> ClientCapabilities : contains
    RequestEnvelope ..> ClientInfo : contains
    RequestEnvelope ..> MetaKeys : keyed by

    ServerCapabilities ..> Capability : contains
    ClientCapabilities ..> RootsCapability : contains
    ClientCapabilities ..> SamplingCapability : contains
    ClientCapabilities ..> Elicitation : contains

    Server ..> JsonRpcMessageDeserializer : uses
    Server ..> DiscoverResultBuilder : uses
    JsonRpcMessageDeserializer ..> McpGson : uses

    DiscoverResultBuilder ..> DiscoverResult : builds
    DiscoverResultBuilder ..> ServerCapabilities : creates
    DiscoverResultBuilder ..> ServerInfo : creates
```
