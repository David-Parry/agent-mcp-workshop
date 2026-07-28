# Server Sequence Diagram

What a chapter 03 server does with each kind of line. Methods other than `server/discover` and `subscriptions/listen` are `-32601` until chapter 04, which is why the envelope rejections below are shown on a listen request.

```mermaid
sequenceDiagram
    participant Client
    participant Server

    Note over Client,Server: Discovery — no handshake, no session

    rect rgb(100, 150, 255)
        Client->>Server: server/discover (id: "server-discover-probe-1")
        Note right of Server: Answered whatever version it names:<br/>the client is asking what we speak
        Server-->>Client: result
        Note right of Server: Returns:<br/>- resultType: "complete"<br/>- supportedVersions<br/>- capabilities<br/>- instructions, ttlMs, cacheScope<br/>- _meta["io.modelcontextprotocol/serverInfo"]
    end

    Note over Client,Server: Every request carries its own envelope, discovery included

    rect rgb(120, 200, 150)
        Client->>Server: any method + params._meta
        Note right of Server: RequestEnvelope holds:<br/>- protocolVersion (required)<br/>- clientCapabilities (required)<br/>- clientInfo (optional)<br/>- logLevel (optional)
    end

    Note over Client,Server: A subscription stream

    rect rgb(120, 200, 180)
        Client->>Server: subscriptions/listen (id: "listen:0")
        Server--)Client: notifications/subscriptions/acknowledged<br/>(subscriptionId "listen:0", nothing honoured)
        Server-->>Client: result for "listen:0" (resultType: "complete")
        Note right of Server: Nothing this server lists ever changes,<br/>so it honours nothing and closes the<br/>stream straight after acknowledging it
    end

    Note over Client,Server: Three distinct rejections, checked in this order

    rect rgb(255, 150, 100)
        Client->>Server: initialize
        Server-->>Client: -32601 Method not found, with data.supported
        Note right of Server: Removal is physical — absence<br/>from the method registry.<br/>Checked before the envelope.
    end

    rect rgb(255, 150, 100)
        Client->>Server: subscriptions/listen (no _meta)
        Server-->>Client: -32602 Invalid params
        Note right of Server: protocolVersion and<br/>clientCapabilities are required
    end

    rect rgb(255, 150, 100)
        Client->>Server: subscriptions/listen (_meta says 2025-11-25)
        Server-->>Client: -32022 Unsupported protocol version
        Note right of Server: data carries requested + supported<br/>so the client knows what to renegotiate to
    end

    Note over Client,Server: Lines that are not messages

    rect rgb(255, 200, 120)
        Client->>Server: text that is not JSON
        Server-->>Client: -32700 Parse error, with no id
        Client->>Server: JSON that is not a JSON-RPC 2.0 message
        Server-->>Client: -32600 Invalid Request, with no id
    end

    Note over Client,Server: Notifications

    rect rgb(200, 150, 255)
        Client->>Server: notifications/cancelled
        Note right of Server: Logs "notifications/cancelled requestId=… reason=…"<br/>from NotificationCancelledParams:<br/>- requestId (string or number)<br/>- reason
    end

    Note over Client,Server: Logged, never answered

    alt Client sends an error response
        Client->>Server: JsonRpcErrorResponse
        Note right of Server: Logs: "error from client"
    end

    alt Client sends a result
        Client->>Server: JsonRpcResponse
        Note right of Server: Logs: "unexpected message, which a client never sends"
    end

    alt Unhandled notification
        Client->>Server: notifications/initialized, or any other
        Note right of Server: Logs: "unhandled notification method"
    end
```
