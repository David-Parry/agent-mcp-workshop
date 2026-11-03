# Server Class Diagram

These are the classes on the chapter 03 branch that the router works with. `IORouter` holds the five pieces you write; everything else is given, and the JSON-RPC types, `RequestId` and `McpGson` come from chapter 02.

```mermaid
classDiagram
    direction LR

    %% JsonRpc Classes
    class JsonRpcRequest
    class JsonRpcResponse
    class JsonRpcNotification
    class JsonRpcErrorResponse
    class Unknown

    %% Identity — an id or a progress token is a string or a number
    class RequestId
    class RequestIdTypeAdapter
    class ProgressToken
    class ProgressTokenTypeAdapter
    class McpGson

    %% Core Protocol Classes
    class DiscoverResult
    class RequestEnvelope
    class NotificationCancelledParams
    class MetaInfo
    class ResultType
    class CacheScope

    %% Subscriptions
    class SubscriptionsListenParams
    class SubscriptionFilter {
        +honoredUnder(ServerCapabilities capabilities) SubscriptionFilter
        +isEmpty() boolean
    }
    class SubscriptionsAcknowledgedParams
    class SubscriptionsListenResult

    %% Supporting Classes
    class ServerCapabilities
    class ClientCapabilities {
        +Elicitation elicitation
        +Map~String, Object~ extensions
    }
    class ServerInfo
    class ClientInfo
    class Capability
    class Elicitation
    class MetaKeys
    class UniqueKeys
    class ErrorCodes

    %% Server and related
    class Server
    class Router
    class IORouter
    class JsonRpcMessageDeserializer
    class DiscoverResultBuilder

    %% Relationships
    JsonRpcRequest ..> RequestId : id
    JsonRpcResponse ..> RequestId : id
    JsonRpcErrorResponse ..> RequestId : id
    RequestIdTypeAdapter ..> RequestId : reads and writes
    ProgressTokenTypeAdapter ..> ProgressToken : reads and writes
    McpGson ..> RequestIdTypeAdapter : registers
    McpGson ..> ProgressTokenTypeAdapter : registers
    MetaInfo ..> ProgressToken : progressToken

    JsonRpcRequest ..> RequestEnvelope : params._meta
    JsonRpcRequest ..> SubscriptionsListenParams : params of subscriptions/listen
    JsonRpcResponse ..> DiscoverResult : result
    JsonRpcResponse ..> SubscriptionsListenResult : result that closes a stream

    JsonRpcNotification ..> NotificationCancelledParams : params
    JsonRpcNotification ..> SubscriptionsAcknowledgedParams : params of the acknowledgement
    NotificationCancelledParams ..> RequestId : requestId

    SubscriptionsListenParams ..> SubscriptionFilter : notifications
    SubscriptionsAcknowledgedParams ..> SubscriptionFilter : what is honoured
    SubscriptionFilter ..> ServerCapabilities : honoredUnder

    DiscoverResult ..> ServerCapabilities : contains
    DiscoverResult ..> ServerInfo : in _meta
    DiscoverResult ..> ResultType : resultType
    SubscriptionsListenResult ..> ResultType : resultType
    DiscoverResultBuilder ..> CacheScope : cacheScope

    RequestEnvelope ..> ClientCapabilities : contains
    RequestEnvelope ..> ClientInfo : contains
    RequestEnvelope ..> MetaKeys : keyed by

    ServerCapabilities ..> Capability : contains
    ClientCapabilities ..> Elicitation : contains
    Elicitation ..> Capability : form and url

    Server ..> Router : hands every line to
    IORouter ..|> Router : implements
    IORouter ..> JsonRpcMessageDeserializer : uses
    IORouter ..> DiscoverResultBuilder : uses
    IORouter ..> UniqueKeys : INBOUND_METHODS
    IORouter ..> ErrorCodes : uses
    JsonRpcMessageDeserializer ..> McpGson : uses
    JsonRpcMessageDeserializer ..> Unknown : returns for what is not a message

    DiscoverResultBuilder ..> DiscoverResult : builds
    DiscoverResultBuilder ..> ServerCapabilities : creates
    DiscoverResultBuilder ..> ServerInfo : creates
```
