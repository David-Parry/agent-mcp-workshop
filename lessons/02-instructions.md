# Chapter 02: Reading JSON-RPC Messages

In chapter 1 you got lines of text in and out of the process. Those lines are just strings — nobody has decided what any of them *mean* yet.

This chapter is where bytes become messages. You will implement `JsonRpcMessageDeserializer`, the single place in the server where inbound JSON is turned into a typed object. Every request the server ever answers passes through the method you are about to write.

Open `src/main/java/com/workshop/mcp/spec/JsonRpcMessageDeserializer.java`. The class, its fields, and its Javadoc are there. Each method body holds a `// Chapter 02:` comment and a `throw new UnsupportedOperationException(...)`, so the class compiles but every call fails; replace both lines with the code.

The line numbers below are where each method starts in the file as you checked it out. The tasks run from the top of the file down, so every paste pushes the methods below it further down: after your first paste, find each method by its name rather than its number.

## The four shapes

JSON-RPC 2.0 puts four different kinds of message on the same wire. Every one of them is a JSON object whose `jsonrpc` member is exactly the string `"2.0"`, and they are told apart purely by which other fields are present:

| Shape | Has `method` | Has `id` | Meaning |
| --- | --- | --- | --- |
| Request | yes | yes | Answer me, and quote this id back |
| Notification | yes | no | For your information; do not reply |
| Response | no | — | The result of something you asked for |
| Error response | no | — | ...and it went wrong |

In this revision requests only flow one way. A server MUST NOT initiate requests: when it needs something from the user, it returns an `input_required` result and the client sends a new request carrying the answer (chapter 05). So a server receives requests and notifications, and a client receives the responses. The classifier still knows all four shapes, because they look the same in both directions and a misbehaving peer can send anything.

Anything that fits none of these is not something this server understands, and that includes well-formed JSON that is not an object, or that does not say `"jsonrpc": "2.0"`. It must be preserved rather than guessed at.

## First Implementation Task: Classification

Paste at **line 75** of `JsonRpcMessageDeserializer.java`, inside the `deserialize(String json)` body:

```java
JsonElement tree = JsonParser.parseString(json);
// An array (a batch, which this revision does not accept), a bare
// value, or an object that does not say it is JSON-RPC 2.0 is still
// well-formed JSON. Returning it rather than throwing is what keeps it
// an invalid request instead of a parse error.
if (!tree.isJsonObject() || !JSON_RPC_VERSION.equals(tree.getAsJsonObject().get("jsonrpc"))) {
    return new Unknown(json);
}
JsonObject jsonObject = tree.getAsJsonObject();
if (jsonObject.has("method")) {
    // An explicit "id": null is still no id. Testing only for the key
    // being present classified such a message as a request, and the
    // server then answered a notification with a null id.
    JsonElement id = jsonObject.get("id");
    if (id != null && !id.isJsonNull()) {
        return gson.fromJson(json, JsonRpcRequest.class);
    } else {
        return gson.fromJson(json, JsonRpcNotification.class);
    }
} else if (jsonObject.has("result")) {
    return gson.fromJson(json, JsonRpcResponse.class);
} else if (jsonObject.has("error")) {
    return gson.fromJson(json, JsonRpcErrorResponse.class);
} else {
    return new Unknown(json);
}
```

### Understanding the Code

1. **Parse once, then look**:
   - `JsonParser.parseString(json)` gives an untyped tree, and we inspect that tree to decide *which* type to ask Gson for
   - You cannot skip this step: Gson needs to know the target class up front, and here the class depends on the content
   - If the text is not JSON at all, `parseString` throws `JsonSyntaxException`. Let it escape: that exception is how chapter 03 knows to answer with a parse error

2. **Well-formed JSON is not yet a message**:
   - An array, a bare string, a number, or `null` parses without complaint, but none of them is a JSON-RPC message. JSON-RPC allows *batches*, which arrive as an array, and this revision does not accept them — so an array must not be mistaken for a single message
   - Nor is an object without `"jsonrpc": "2.0"`. `JSON_RPC_VERSION` is a `JsonPrimitive` holding the string `"2.0"`, and comparing it with `equals` rejects a missing member, `null`, another version, and the *number* `2.0`. Calling `getAsString()` instead would let the number through, because Gson turns it into the string `"2.0"`
   - All of these come back as `Unknown` rather than as an exception. Chapter 03 answers `Unknown` with `-32600` Invalid Request and the exception with `-32700` Parse error, and it can only tell the two apart because they arrive differently

3. **`method` decides the direction**:
   - A `method` means the other side is asking us to do something, or telling us something if there is no id
   - No `method` means it is an answer. A server on this revision never asks the client anything, so an answer arriving here is unexpected: chapter 03's router logs it and moves on

4. **The `id: null` trap**:
   - `has("id")` returns true for `"id": null`, because the key *is* present — it just has a null value
   - Classifying that as a request means the server dutifully sends back a response with `"id": null`, replying to a notification that wanted no reply
   - So test the value, not the key: `id != null && !id.isJsonNull()`

5. **Unknown preserves the evidence**:
   - Rather than throwing, an unrecognised shape is wrapped in `Unknown` with its original text intact
   - This keeps a malformed client's message visible in the logs instead of vanishing, and lets chapter 03 reply with an error the client can act on

### Key Concepts

- **Structural typing**: JSON-RPC has no type tag, so the shape of the object *is* the type
- **Absent vs. null**: two different things in JSON, and conflating them is the classic JSON-RPC bug
- **Two kinds of bad input**: text that is not JSON throws `JsonSyntaxException`, while JSON that is not a JSON-RPC 2.0 message comes back as `Unknown`. Neither is silently swallowed, and keeping them apart is what lets the server send the right error for each

## Second Implementation Task: Typed Params

Classification gives you a `JsonRpcRequest`, whose `params()` is a generic `Object`. Each method needs its own params record. Paste at **line 95**, inside the `deserializeParams(JsonRpcRequest request, Class<T> paramsClass)` body:

```java
return gson.fromJson(gson.toJson(request.params()), paramsClass);
```

And the same at **line 115**, inside the `JsonRpcNotification` overload:

```java
return gson.fromJson(gson.toJson(request.params()), paramsClass);
```

Then paste at **line 134**, inside `convert(Object value, Class<T> type)`:

```java
return value == null ? null : gson.fromJson(gson.toJsonTree(value), type);
```

### Understanding the Code

- **The round trip**: `toJson` then `fromJson` looks wasteful, and it is, but it is the simple way to re-read an already-parsed value as a different type. Clarity beats micro-optimisation in a message that is a few hundred bytes.
- **`convert` takes the tree, not the text**: it uses `toJsonTree` rather than `toJson`, because it is handed a value that has already been parsed out of a larger object. Later chapters reuse `convert` for the same reason.
- **Null in, null out**: an absent answer must stay absent. Converting null into an empty object of the target type would invent an answer the client never gave.

## Third Implementation Task: The Stateless Envelope

Revision `2026-07-28` removed the `initialize` handshake. There is no longer a moment at the start of the connection where the client announces who it is and what it can do — so **every single request carries that information itself**, in `params._meta`.

That block is the envelope, and reading it is how the server knows what it is allowed to do on this call.

Paste at **line 158**, inside the `deserializeEnvelope(JsonRpcRequest request)` body:

```java
JsonObject meta = metaObject(request.params());
if (meta == null) {
    return new RequestEnvelope(null, null, null, null);
}
return new RequestEnvelope(
        asString(meta.get(MetaKeys.PROTOCOL_VERSION)),
        gson.fromJson(meta.get(MetaKeys.CLIENT_CAPABILITIES), ClientCapabilities.class),
        gson.fromJson(meta.get(MetaKeys.CLIENT_INFO), ClientInfo.class),
        asString(meta.get(MetaKeys.LOG_LEVEL)));
```

And the two private helpers it leans on. Paste at **line 164**, inside `metaObject(Object params)`:

```java
if (params == null) {
    return null;
}
JsonElement tree = gson.toJsonTree(params);
if (!tree.isJsonObject()) {
    return null;
}
JsonElement meta = tree.getAsJsonObject().get("_meta");
return (meta != null && meta.isJsonObject()) ? meta.getAsJsonObject() : null;
```

...and at **line 170**, inside `asString(JsonElement element)`:

```java
return (element != null && element.isJsonPrimitive()) ? element.getAsString() : null;
```

### Understanding the Code

1. **Why the keys are read by hand**:
   - The envelope keys are namespaced identifiers like `io.modelcontextprotocol/protocolVersion`
   - Slashes and dots are not legal in Java identifiers, so a record component cannot be named after them the way ordinary params can
   - They are therefore pulled out of the tree by literal name, using the constants in `MetaKeys`

2. **What else is allowed to live in `_meta`**:
   - A key is an optional prefix — dot-separated labels ending in a slash — followed by a name. Any prefix whose *second* label is `modelcontextprotocol` or `mcp` is reserved for the specification, which is why every key this server reads begins `io.modelcontextprotocol/`. Note that `com.example.mcp/` is **not** reserved: the second label there is `example`.
   - The specification reserves one set of keys with **no prefix at all**: `traceparent`, `tracestate`, and `baggage`, carrying [W3C trace context](https://www.w3.org/TR/trace-context/) for OpenTelemetry. They are a deliberate exception to the prefix rule, kept unprefixed so existing tracing libraries work unchanged. This server does not participate in tracing, so it ignores them — but a production server that does would read `traceparent` here and continue the caller's trace instead of starting a new one. The stretch exercise at the end of this chapter does the reading.
   - Everything else is free for vendors under their own reversed-domain prefix. An unrecognised key is simply ignored, which is exactly what `deserializeEnvelope` does by only asking for the names it knows.

3. **Missing is not the same as broken**:
   - No params, positional (array) params, or no `_meta` all produce an envelope of four nulls rather than an exception
   - `RequestEnvelope.isComplete()` then reports it as incomplete, and the router turns that into a clean protocol error — which is far more useful to a client than a stack trace

4. **`asString` guards the type, not just the presence**:
   - A `protocolVersion` that arrives as `{"major":2026}` is not a version string
   - Checking `isJsonPrimitive()` before `getAsString()` means a wrongly-typed field reads as absent, instead of throwing and taking the whole request down

### Why This Matters

The envelope is the single biggest consequence of the protocol going stateless. In the old revision, capabilities were negotiated once and remembered; the server held a session. Now the server holds nothing between calls — every request re-states its context, and the server re-reads it every time.

That is what makes the server restartable, load-balanceable, and much easier to reason about: there is no "current connection state" to get out of sync.

## You are done when the tests pass

```bash
./gradlew chapterTest -Pchapter=02
```

The run reports 33 tests. The 21 in `JsonRpcMessageDeserializerTest` cover exactly the methods you just wrote, and they all fail until you start. The other 12 are the `SpecTypesCoverageTest` cases tagged for this chapter, in its `Envelopes`, `RequestIds` and `WireRecords` groups. They check the records and the id adapter you were given, so they pass from the start; if one of them fails, given code has been changed. Failures worth recognising:

- `aMessageWhoseIdIsExplicitlyNullIsStillANotification` — you tested `has("id")` instead of testing the value.
- `jsonThatIsNotAnObjectIsUnknownRatherThanRejected` — you called `getAsJsonObject()` before checking `isJsonObject()`, so an array or a bare value throws `IllegalStateException` instead of coming back as `Unknown`.
- `aMessageThatDoesNotSayItIsJsonRpc2IsUnknown` — the version check is missing, or it uses `getAsString()`, which also accepts the number `2.0`.
- `malformedJsonIsRejected` — something is catching the `JsonSyntaxException` that `parseString` throws. Let it escape.
- `anEnvelopeFieldThatIsNotAPrimitiveIsReadAsAbsent` — `asString` is calling `getAsString()` without checking `isJsonPrimitive()` first.
- `aRequestWithNoParamsHasAnIncompleteEnvelopeRatherThanNone` — `metaObject` is not returning null for absent params, so the null check in `deserializeEnvelope` never fires.

## Stretch: Read the Caller's Trace Context

This is optional, and no chapter test checks it.

A client that traces its own work with OpenTelemetry can say which trace a request belongs to, by sending `traceparent` (and optionally `tracestate` and `baggage`) in `params._meta`. A server that continues that trace, instead of starting a new one, lets someone follow a single user action from the client, through this server, and into anything it calls. The keys are already in `MetaKeys`.

Add this method to `JsonRpcMessageDeserializer`, just above `metaObject`:

```java
/**
 * Reads the W3C trace context a caller may send in {@code params._meta}.
 *
 * @param request the request to read
 * @return the {@code traceparent} value, or {@code null} when the caller sent none
 */
public String traceparent(JsonRpcRequest request) {
    JsonObject meta = metaObject(request.params());
    return meta == null ? null : asString(meta.get(MetaKeys.TRACEPARENT));
}
```

Then add a test for it to `JsonRpcMessageDeserializerTest`. The value is the example from the [W3C Trace Context](https://www.w3.org/TR/trace-context/) specification:

```java
@Test
void aTraceparentIsReadOutOfParamsMeta() {
    JsonRpcRequest traced = request("""
            {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{"_meta":{
              "traceparent":"00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"}}}""");
    JsonRpcRequest untraced = request("""
            {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}""");

    assertEquals("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", deserializer.traceparent(traced));
    assertNull(deserializer.traceparent(untraced));
}
```

Logging the value belongs to the router, which arrives in chapter 03. `03-chapter` starts from the reference solution, so you would add the method again there, and then log it where `IORouter.process(JsonRpcRequest message)` begins:

```java
String traceparent = deserializer.traceparent(message);
if (traceparent != null) {
    logger.log("[API][RECEIVED] " + message.method() + " traceparent=" + traceparent);
}
```

## Where this goes next

You now have typed messages, but nothing routes them. Chapter 3 takes the `RequestEnvelope` you just parsed and uses it: checking the protocol version, answering `server/discover`, and dispatching each method to a handler. It also answers the two kinds of bad input differently: `-32700` for the `JsonSyntaxException`, `-32600` for an `Unknown`.
