# Chapter 02: Reading JSON-RPC Messages

In chapter 1 you got lines of text in and out of the process. Those lines are just strings — nobody has decided what any of them *mean* yet.

This chapter is where bytes become messages. You will implement `JsonRpcMessageDeserializer`, the single place in the server where inbound JSON is turned into a typed object. Every request the server ever answers passes through the method you are about to write.

Open `src/main/java/com/workshop/mcp/spec/JsonRpcMessageDeserializer.java`. The class, its fields, and its Javadoc are there; the method bodies are empty. Each paste below names the line of the hollowed method.

## The four shapes

JSON-RPC 2.0 puts four different kinds of message on the same wire, and they are told apart purely by which fields are present:

| Shape | Has `method` | Has `id` | Meaning |
| --- | --- | --- | --- |
| Request | yes | yes | Answer me, and quote this id back |
| Notification | yes | no | For your information; do not reply |
| Response | no | — | The result of something you asked for |
| Error response | no | — | ...and it went wrong |

Anything that fits none of these is not something this server understands, and it must be preserved rather than guessed at.

## First Implementation Task: Classification

Paste at **line 66** of `JsonRpcMessageDeserializer.java`, inside the empty `deserialize(String json)` body:

```java
JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
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

2. **`getAsJsonObject()` is doing real work**:
   - It throws `IllegalStateException` when the payload is an array, a bare string, a number, or `null`
   - That is deliberate. JSON-RPC allows *batches*, which arrive as an array, and this server does not accept them — so an array must be rejected rather than mistaken for a single message

3. **`method` decides the direction**:
   - A `method` means the other side is asking us to do something
   - No `method` means it is an answer to something we asked

4. **The `id: null` trap**:
   - `has("id")` returns true for `"id": null`, because the key *is* present — it just has a null value
   - Classifying that as a request means the server dutifully sends back a response with `"id": null`, replying to a notification that wanted no reply
   - So test the value, not the key: `id != null && !id.isJsonNull()`

5. **Unknown preserves the evidence**:
   - Rather than throwing, an unrecognised shape is wrapped in `Unknown` with its original text intact
   - This keeps a malformed client's message visible in the logs instead of vanishing

### Key Concepts

- **Structural typing**: JSON-RPC has no type tag, so the shape of the object *is* the type
- **Absent vs. null**: two different things in JSON, and conflating them is the classic JSON-RPC bug
- **Fail loud on nonsense**: malformed JSON throws `JsonSyntaxException`, non-objects throw `IllegalStateException`, and neither is silently swallowed

## Second Implementation Task: Typed Params

Classification gives you a `JsonRpcRequest`, whose `params()` is a generic `Object`. Each method needs its own params record. Paste at **line 86**, inside the empty `deserializeParams(JsonRpcRequest request, Class<T> paramsClass)` body:

```java
return gson.fromJson(gson.toJson(request.params()), paramsClass);
```

And the same at **line 106**, inside the `JsonRpcNotification` overload:

```java
return gson.fromJson(gson.toJson(request.params()), paramsClass);
```

Then paste at **line 125**, inside `convert(Object value, Class<T> type)`:

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

Paste at **line 149**, inside the empty `deserializeEnvelope(JsonRpcRequest request)` body:

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

And the two private helpers it leans on. Paste at **line 155**, inside `metaObject(Object params)`:

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

...and at **line 161**, inside `asString(JsonElement element)`:

```java
return (element != null && element.isJsonPrimitive()) ? element.getAsString() : null;
```

### Understanding the Code

1. **Why the keys are read by hand**:
   - The envelope keys are namespaced identifiers like `io.modelcontextprotocol/protocolVersion`
   - Slashes and dots are not legal in Java identifiers, so a record component cannot be named after them the way ordinary params can
   - They are therefore pulled out of the tree by literal name, using the constants in `MetaKeys`

2. **Missing is not the same as broken**:
   - No params, positional (array) params, or no `_meta` all produce an envelope of four nulls rather than an exception
   - `RequestEnvelope.isComplete()` then reports it as incomplete, and the router turns that into a clean protocol error — which is far more useful to a client than a stack trace

3. **`asString` guards the type, not just the presence**:
   - A `protocolVersion` that arrives as `{"major":2026}` is not a version string
   - Checking `isJsonPrimitive()` before `getAsString()` means a wrongly-typed field reads as absent, instead of throwing and taking the whole request down

### Why This Matters

The envelope is the single biggest consequence of the protocol going stateless. In the old revision, capabilities were negotiated once and remembered; the server held a session. Now the server holds nothing between calls — every request re-states its context, and the server re-reads it every time.

That is what makes the server restartable, load-balanceable, and much easier to reason about: there is no "current connection state" to get out of sync.

## You are done when the tests pass

```bash
./gradlew chapterTest -Pchapter=02
```

`JsonRpcMessageDeserializerTest` has 20 tests covering exactly the methods you just wrote. Failures worth recognising:

- `aMessageWhoseIdIsExplicitlyNullIsStillANotification` — you tested `has("id")` instead of testing the value.
- `jsonThatIsNotAnObjectIsRejected` — something is catching the `IllegalStateException` that `getAsJsonObject()` is supposed to throw.
- `anEnvelopeFieldThatIsNotAPrimitiveIsReadAsAbsent` — `asString` is calling `getAsString()` without checking `isJsonPrimitive()` first.
- `aRequestWithNoParamsHasAnIncompleteEnvelopeRatherThanNone` — `metaObject` is not returning null for absent params, so the null check in `deserializeEnvelope` never fires.

## Where this goes next

You now have typed messages, but nothing routes them. Chapter 3 takes the `RequestEnvelope` you just parsed and uses it: checking the protocol version, answering `server/discover`, and dispatching each method to a handler.
