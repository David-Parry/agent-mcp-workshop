# Chapter 02 Class Diagram: Reading JSON-RPC Messages

These are the classes on the chapter 02 branch, all in `com.workshop.mcp.spec`. `JsonRpcMessageDeserializer` is the one you implement; everything else is given. The result types, params records and builders arrive with the chapters that use them, starting with `DiscoverResult` in chapter 03.

```mermaid
classDiagram
    direction LR

    class JsonRpcMessageDeserializer {
        -Gson gson
        +deserialize(String json) Object
        +deserializeParams(JsonRpcRequest request, Class~T~ paramsClass) T
        +deserializeParams(JsonRpcNotification request, Class~T~ paramsClass) T
        +convert(Object value, Class~T~ type) T
        +deserializeEnvelope(JsonRpcRequest request) RequestEnvelope
    }
    class McpGson {
        +create()$ Gson
    }
    class RequestIdTypeAdapter {
        +write(JsonWriter out, RequestId value)
        +read(JsonReader in) RequestId
    }
    class RequestId {
        +String stringValue
        +Long numberValue
        +of(String value)$ RequestId
        +of(long value)$ RequestId
        +isString() boolean
    }

    class JsonRpcRequest {
        +String jsonrpc
        +RequestId id
        +String method
        +Object params
    }
    class JsonRpcNotification {
        +String jsonrpc
        +String method
        +Object params
    }
    class JsonRpcResponse {
        +String jsonrpc
        +RequestId id
        +Object result
    }
    class JsonRpcErrorResponse {
        +String jsonrpc
        +RequestId id
        +JsonRpcError error
    }
    class JsonRpcError {
        +int code
        +String message
        +Object data
    }
    class Unknown {
        +String json
    }

    class RequestEnvelope {
        +String protocolVersion
        +ClientCapabilities clientCapabilities
        +ClientInfo clientInfo
        +String logLevel
        +isComplete() boolean
        +isSupportedVersion() boolean
        +supportsElicitationForm() boolean
        +supportsTasks() boolean
    }
    class ClientCapabilities {
        +Elicitation elicitation
        +Map~String, Object~ extensions
    }
    class ClientInfo {
        +String name
        +String version
    }
    class Elicitation {
        +Capability form
        +Capability url
    }
    class Capability {
        +Boolean listChanged
        +Boolean subscribe
    }
    class NotificationCancelledParams {
        +RequestId requestId
        +String reason
    }

    class MetaKeys {
        <<constants>>
        +PROTOCOL_VERSION$
        +CLIENT_CAPABILITIES$
        +CLIENT_INFO$
        +LOG_LEVEL$
        +TRACEPARENT$
        +TRACESTATE$
        +BAGGAGE$
    }
    class ErrorCodes {
        <<constants>>
        +PARSE_ERROR = -32700$
        +INVALID_REQUEST = -32600$
        +METHOD_NOT_FOUND = -32601$
        +INVALID_PARAMS = -32602$
        +INTERNAL_ERROR = -32603$
        +HEADER_MISMATCH = -32020$
        +MISSING_REQUIRED_CLIENT_CAPABILITY = -32021$
        +UNSUPPORTED_PROTOCOL_VERSION = -32022$
    }

    JsonRpcMessageDeserializer ..> McpGson : gets its Gson from
    McpGson ..> RequestIdTypeAdapter : registers
    RequestIdTypeAdapter ..> RequestId : reads and writes

    JsonRpcMessageDeserializer ..> JsonRpcRequest : method and an id
    JsonRpcMessageDeserializer ..> JsonRpcNotification : method, no id
    JsonRpcMessageDeserializer ..> JsonRpcResponse : result
    JsonRpcMessageDeserializer ..> JsonRpcErrorResponse : error
    JsonRpcMessageDeserializer ..> Unknown : valid JSON, not a message
    JsonRpcMessageDeserializer ..> RequestEnvelope : reads params._meta
    JsonRpcMessageDeserializer ..> MetaKeys : looks up keys by

    JsonRpcRequest --> RequestId
    JsonRpcResponse --> RequestId
    JsonRpcErrorResponse --> RequestId
    JsonRpcErrorResponse --> JsonRpcError
    JsonRpcError ..> ErrorCodes : code
    JsonRpcNotification ..> NotificationCancelledParams : params of notifications/cancelled
    NotificationCancelledParams --> RequestId

    RequestEnvelope --> ClientCapabilities
    RequestEnvelope --> ClientInfo
    ClientCapabilities --> Elicitation
    Elicitation --> Capability : form and url
```

## Reading the diagram

- **Five possible answers from `deserialize`.** The four JSON-RPC shapes, plus `Unknown` for well-formed JSON that is not a JSON-RPC 2.0 message: an array, a bare value, an object without `"jsonrpc": "2.0"`, an object with none of `method`, `result` and `error`, or one whose members have the wrong types, such as an id that is an object. Text that is not JSON at all throws instead, so chapter 03 can answer the two cases with different errors.
- **An id is a string or a number, never both.** `RequestId` keeps whichever form arrived, and `RequestIdTypeAdapter` writes it back the same way, because a server must quote an id exactly as the client sent it. `McpGson.create()` is the one place the adapter is registered, so every component that reads or writes JSON shares it.
- **The envelope is not a params record.** Its keys, such as `io.modelcontextprotocol/protocolVersion`, are not legal Java identifiers, so `deserializeEnvelope` reads them by the names in `MetaKeys` and builds a `RequestEnvelope` by hand.
- **`traceparent`, `tracestate` and `baggage`** are in `MetaKeys` because the specification reserves them unprefixed for W3C trace context. This server does not read them yet; see the stretch exercise in the [instructions](02-instructions.md).
- **`ErrorCodes` is defined here and used from chapter 03 on.** The -32000 to -32019 block is legacy, so the server uses only the JSON-RPC codes and the specification's own reserved ones.
