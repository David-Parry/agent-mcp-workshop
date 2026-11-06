# Chapter 03: Discovery and Routing

In chapter 02 every inbound line became a typed message, but nothing answers them yet. This chapter builds the front of the router: the part every request passes through before any tool, prompt or resource sees it.

Revision `2026-07-28` deleted the `initialize` handshake, and the session went with it. Two things replace it, and both live here. `server/discover` tells a client what the server is. A per-request envelope in `params._meta` tells the server, on every call, which revision the client speaks and what it can do.

Open `src/main/java/com/workshop/mcp/IORouter.java`. Five pieces of it are yours to write. Each holds a `// Chapter 03:` comment, and the two that return a value also hold a `throw new UnsupportedOperationException(...)`; replace the comment, and the `throw` where there is one, with the code.

The line numbers below are where each piece sits in the file as you checked it out. The tasks run from the bottom of the file up, so a paste never moves a piece you have not reached yet.

## Read First: What the Router Already Does

Read the top of `IORouter.java`, down to `process(JsonRpcRequest message)`. It is given, and it decides what happens to every line before your code runs:

1. **`route(String message)` replies to bad input instead of falling silent**:
   - It hands the line to your chapter 02 deserializer and switches on what comes back.
   - Text that is not JSON throws, and gets `-32700` Parse error. An `Unknown` gets `-32600` Invalid Request. That difference is why chapter 02 made the two arrive differently.
   - Both errors go out with **no `id` member at all**. There is no id to quote back, and the schema makes an error's `id` optional rather than allowing `null`.
   - A request goes to `answer()`, and a notification to `process(JsonRpcNotification message)`, which you write. An error from the client is logged. So is a result: a server on this revision never asks the client anything, so a result cannot be an answer to anything it sent.

2. **`answer(JsonRpcRequest request)` makes sure every request gets a reply**:
   - Params that do not fit the method's params record make `deserializeParams` throw `JsonParseException`, which becomes `-32602` Invalid params.
   - Anything else that throws becomes `-32603` Internal error.
   - Both carry the request's id. Without this, an exception would reach the transport, which can only log it, and the client would wait for a reply that never comes.

3. **`success()` names the server on every result**:
   - It runs each result through `withServerInfo()`, which adds `io.modelcontextprotocol/serverInfo` to the result's `_meta` and keeps anything already there.
   - With no handshake, a result is the only place a client can learn who answered it.

4. **`rejectUnknownMethod()` answers `-32601` Method not found**:
   - For `initialize` it also names the versions this server does support, in the message and in `data.supported`. A client still on the handshake has no other way to find out what would work.

5. **`INBOUND_METHODS` is the list of methods this chapter answers**: `server/discover` and `subscriptions/listen`. Everything else is `-32601` until a later chapter adds it. `UniqueKeys.fromValue` matches method names exactly, because they are case-sensitive: `SERVER/DISCOVER` is not a method.

`RequestId`, `McpGson` and `RequestEnvelope` are from chapter 02. New on this branch is `ProgressToken`: `MetaInfo`, the `_meta` record that params carry, reads `progressToken` as one, and like a `RequestId` it may be a string or an integer.

## First Implementation Task: Subscriptions

On this revision a client that wants to hear about changes opens a stream by sending `subscriptions/listen`. List-changed notifications and resource updates are delivered only on such a stream, never unprompted.

Paste at **line 270** of `IORouter.java`, inside the `acknowledgeSubscription(JsonRpcRequest message)` body:

```java
SubscriptionsListenParams params = deserializer.deserializeParams(message,
                                                                 SubscriptionsListenParams.class);
SubscriptionFilter requested = params.notifications() == null
        ? new SubscriptionFilter(null, null, null, null)
        : params.notifications();
SubscriptionFilter honored = requested.honoredUnder(discoverResult().capabilities());

logger.log("[API][SENT] subscriptions/listen — acknowledging id=" + message.id()
           + " honored=" + honored);
io.emit(new JsonRpcNotification(
        JSON_RPC_VERSION,
        UniqueKeys.NOTIFICATIONS_SUBSCRIPTIONS_ACKNOWLEDGED.getValue(),
        SubscriptionsAcknowledgedParams.of(message.id(), honored)));

logger.log("[API][SENT] subscriptions/listen — nothing honored, closing immediately");
success(message.id(), SubscriptionsListenResult.closing(message.id()));
```

### Understanding the Code

1. **The acknowledgement comes first**:
   - The server MUST send `notifications/subscriptions/acknowledged` before any other notification on the stream, and it must carry `io.modelcontextprotocol/subscriptionId` in its `_meta`.
   - Its `notifications` field is what the server will actually honour: what the client asked for, narrowed by what discovery advertised. That is `honoredUnder`. This server advertises `listChanged: false` everywhere and `subscribe: false`, so the answer is always `{}`.

2. **The subscription id is the id of the listen request itself**:
   - On stdio every stream shares one channel, so the id is the only way a client can tell which stream a notification belongs to.
   - An acknowledgement without it leaves the client waiting forever, with no error and no timeout.

3. **Then the close**:
   - A stream lasts as long as its listen request is unanswered. Holding one open to deliver nothing would be pointless, so the server answers the listen request straight away.
   - The answer is an ordinary `complete` result that also carries the subscription id: `SubscriptionsListenResult.closing()`. It is what tells the client the stream closed cleanly, rather than the transport dropping.

4. **Absent and wrong are different**:
   - A listen request that names no notifications is still valid, and is acknowledged with an empty filter.
   - `"notifications": "everything"` does not fit `SubscriptionsListenParams`, so `deserializeParams` throws, and `answer()` turns that into `-32602`.

`discoverResult()` is still empty, so this task's tests stay red until the third task is done.

## Second Implementation Task: Notifications

Notifications are never answered, not even with an error: they have no id to answer. The one a client sends on this revision is `notifications/cancelled`.

Paste at **line 232**, inside the `process(JsonRpcNotification message)` body:

```java
UniqueKeys uniqueKey = UniqueKeys.fromValue(message.method());
switch (uniqueKey) {
    case NOTIFICATION_CANCELLED -> {
        NotificationCancelledParams params = deserializer.deserializeParams(message,
                                                                            NotificationCancelledParams.class);
        // There is never an in-flight request to abandon: this server
        // answers every request before returning, and its one
        // long-lived request, subscriptions/listen, is closed as it is
        // acknowledged. So the cancellation is recorded and nothing else.
        logger.log("[API][RECEIVED] notifications/cancelled requestId=" + params.requestId()
                   + " reason=" + params.reason());
    }
    default -> logger.log("[API][RECEIVED] unhandled notification method: " + message.method()
                          + " for message: " + message);
}
```

### Understanding the Code

- **Only a client cancels.** A client ends a listen stream by sending `notifications/cancelled` naming the listen request's id, and that is the normal way a stream ends from the client's side.
- **The specification disagrees with itself on the server's side.** Its Cancellation section says a server MUST NOT send `notifications/cancelled` except to end a listen stream, which implies that is how a server ends one. Its Subscriptions section says a server SHOULD end one by answering the listen request with a `complete` result. This server does the latter, because it leaves no request unanswered.
- **The `[API][RECEIVED]` prefix** matches the one the transport writes for each raw line, so one search over the log shows everything that arrived, in order.
- **What is not in the switch**: `notifications/initialized`. It went the same way as `initialize`, so a client that still sends it lands in `default` and is logged, not answered.

## Third Implementation Task: The `server/discover` Answer

Paste at **line 226**, inside the `discoverResult()` body:

```java
return DiscoverResultBuilder
        .builder()
        .withDefaultCapabilities()
        .withInstructions("Searches a project for a keyword.")
        .withCacheHints(LIST_TTL_MILLIS, CacheScope.PUBLIC)
        .withDefaultServerInfo()
        .build();
```

### Understanding the Code

1. **No session, just a result**: discovery is a plain request with a plain answer. There is nothing to open and nothing to remember.

2. **`supportedVersions`** is how a client learns what to put in its envelope on every later request. This server lists one, `2026-07-28`.

3. **Capabilities**: prompts, resources, tools and completions, none of them with `listChanged`. **This server advertises them before it can serve them.** The handlers arrive in chapter 04, and until then each of those methods is `-32601`.

4. **Cache hints are required**: `ttlMs` and `cacheScope` let a client reuse the answer instead of asking again, and `cacheScope: "public"` lets a shared cache hand the same answer to other clients. That is why this answer is built from fixed configuration and nothing a long-running server would have accumulated. `LIST_TTL_MILLIS` is one minute: long enough that a client is not re-asking on every keystroke, short enough that a restart is noticed.

5. **`resultType` is required on every result**: the builder sets it to `complete`.

6. **Identity**: `serverInfo` is not a field of the result. It goes in `_meta` under `io.modelcontextprotocol/serverInfo`; `withDefaultServerInfo()` puts it there, and `success()` adds it to every other result too.

## Fourth Implementation Task: The Envelope

This is what replaced the handshake. Instead of agreeing on a version once, the server re-reads it from `params._meta` on every request.

Paste at **line 211**, inside the `envelopeFor(JsonRpcRequest message, boolean versionMatters)` body:

```java
RequestEnvelope envelope = deserializer.deserializeEnvelope(message);
if (!envelope.isComplete()) {
    logger.log("[API][SENT] " + message.method() + " — incomplete _meta envelope (returning -32602)");
    error(message.id(), ErrorCodes.INVALID_PARAMS,
          "Requests must carry " + MetaKeys.PROTOCOL_VERSION + " and "
          + MetaKeys.CLIENT_CAPABILITIES + " in params._meta");
    return null;
}
if (versionMatters && !envelope.isSupportedVersion()) {
    logger.log("[API][SENT] " + message.method() + " — unsupported protocol version '"
               + envelope.protocolVersion() + "' (returning -32022)");
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("supported", RequestEnvelope.SUPPORTED_VERSIONS);
    data.put("requested", envelope.protocolVersion());
    error(message.id(), ErrorCodes.UNSUPPORTED_PROTOCOL_VERSION, "Unsupported protocol version", data);
    return null;
}
return envelope;
```

### Understanding the Code

- **Both required members, or nothing**: a request missing `protocolVersion` or `clientCapabilities` is malformed, and the server MUST reject it with `-32602`. `clientInfo` is only recommended, so its absence is fine.
- **`-32022` must carry its `data`**: `supported` and `requested` are how a client renegotiates from the error alone. There is no handshake left in which to find out.
- **A `LinkedHashMap`, not `Map.of`**: JSON does not care about member order, but people reading the log do. `Map.of` promises no order, and its order changes from one run of the JVM to the next; a `LinkedHashMap` writes the members in the order they were put.
- **`versionMatters`** is false only for `server/discover`, for the reason the next task explains.
- **Returning null means "already answered"**: `envelopeFor` sends the error itself, so the caller only has to stop.

## Fifth Implementation Task: The Request Prelude

Everything so far is a part. This task wires the parts together, in the order the checks must run.

Paste at **line 162** of `IORouter.java`, inside `process(JsonRpcRequest message)`. The method starts at **line 159**. Its first line, which works out `uniqueKey`, and the `switch` at the end are already there. Replace the `// Chapter 03:` comment between them with these lines:

```java
// Method existence is settled before the envelope is looked at.
// Deletions in this revision are physical: a method absent from the
// registry is -32601 by absence, and answering a removed method like
// initialize with a complaint about its _meta would tell a legacy
// client the wrong thing about why it failed.
if (uniqueKey == UniqueKeys.NOT_FOUND || !INBOUND_METHODS.contains(uniqueKey)) {
    rejectUnknownMethod(message);
    return;
}

// server/discover is answered whatever version it names: a client uses
// it precisely to find out which versions this server speaks, so
// rejecting it for asking with the wrong one would be circular. It is
// still a request, though, so it must carry the required envelope.
RequestEnvelope envelope = envelopeFor(message, uniqueKey != UniqueKeys.SERVER_DISCOVER);
if (envelope == null) {
    return;
}
if (uniqueKey == UniqueKeys.SERVER_DISCOVER) {
    success(message.id(), discoverResult());
    logger.log("[API][SENT] server/discover — advertised " + RequestEnvelope.SUPPORTED_VERSIONS);
    return;
}
```

### Understanding the Code

The prelude produces three distinct rejections, and the order between them is the interesting part:

| Situation | Code | Why |
|---|---|---|
| `initialize`, `ping`, `tasks/result`, `SERVER/DISCOVER`, … | `-32601` | The method does not exist here. Removal in this revision is physical: absence from the registry. |
| No `_meta`, or one missing a required member | `-32602` | `protocolVersion` and `clientCapabilities` are both required, on discovery too. |
| `_meta` names `2025-11-25` | `-32022` | The client speaks a revision this server does not implement. |

- **The method comes first.** A legacy client sending `initialize` has also left out the envelope, so both checks would fire, but only `-32601` tells it the truth. Checking the envelope first would send it off to fix the wrong thing.
- **Discovery skips only the version check.** The specification allows a server to answer a discovery request naming an unsupported version with `-32022`; this one answers it, because that is the more useful reply to a client that is asking what to use.
- **The envelope is a per-request fact.** Chapter 05 reads capability flags like "can this client show a form" from `envelope` on each call. A server that stored them in a field would be bringing back the session the revision deleted.

## You are done when the tests pass

```bash
./gradlew chapterTest -Pchapter=03
```

The run reports 49 tests, and 21 of them fail before you start. The other 28 test the given code: `route()`'s handling of bad input and of messages a client never sends, the constant classes such as `ErrorCodes`, `UniqueKeys`, `ProgressToken`, the subscription filter and the discovery builder. If one of those fails, given code has been changed. Failures worth recognising:

- `methodsRemovedByThisRevisionAreNotFoundRatherThanInvalid` — the envelope is checked before the method, so `initialize` gets `-32602`.
- `discoverIsAnsweredEvenWhenTheEnvelopeNamesAnUnsupportedRevision` — `envelopeFor` is called with `versionMatters` true for discovery.
- `discoverWithoutTheRequiredEnvelopeIsInvalidParams` — discovery is answered before `envelopeFor` runs.
- `aRequestDeclaringAnUnsupportedRevisionIsRejectedWithRenegotiationData` — the `-32022` is missing `supported` or `requested`.
- `aSubscriptionIsAcknowledgedAndClosedWithItsSubscriptionId` — the acknowledgement or the closing result is missing, or one of them lacks the subscription id.
- `everyMethodTheServerAcceptsIsActuallyAnswered` — a method in `INBOUND_METHODS` gets no reply.
- `aCancelledNotificationForSomeOtherRequestIsNoted` or `anUnrecognisedNotificationIsIgnoredRatherThanRejected` — the notification `switch` is missing, or one of its arms logs nothing.
- A test that expects one response and finds a `-32603` — something you wrote threw. The log file says what.

Then drive the built server the way a client would. The harness starts the JAR and checks everything up to this chapter over stdio:

```bash
./gradlew shadowJar
python3 lessons/presentation-3hr/verify/harness.py --chapter 03
```

## Try It in the MCP Inspector

### Step 1: Build

```bash
./gradlew clean build
```

This compiles, runs every test on the branch, and packages the server at `build/libs/agent-mcp-workshop-0.0.1.jar`, which is what `inspector/config.json` points at.

### Step 2: Check the Inspector configuration

Open `inspector/config.json` and confirm `"protocolEra": "modern"` is a **direct sibling of `command` and `args`**, inside the `workshop` entry. Nested anywhere else the key is ignored, and the Inspector falls back to its default era, `legacy`: it sends `initialize`, and you spend twenty minutes debugging a `-32601` you asked for.

The era decides what the first message on the wire is:

| Era | What the Inspector does at connect |
|-----|------------------------------------|
| `legacy` | **The default.** Plain `initialize`, no probing. |
| `auto` | Probe `server/discover` first, fall back to `initialize` on any non-modern outcome. |
| `modern` | Pin exactly `2026-07-28`. No fallback, so a non-modern server fails loudly. |

The workshop ships `modern`. That pins the Inspector to `2026-07-28` and sends `server/discover` with no fallback. A server that cannot answer discovery fails at once, instead of being retried with the `initialize` handshake the way `auto` would.

`inspector/run.sh` pins the Inspector version:

```bash
npx @modelcontextprotocol/inspector@2.5.0 --web --catalog config.json
```

### Step 3: Launch and connect

```bash
cd inspector
./run.sh
```

Open the URL it prints (typically `http://127.0.0.1:6274`). The `workshop` card is read from `config.json`; turn on its switch to connect. Then:

1. **Discovery completes.** The probe's id is the string `"server-discover-probe-1"`, and it comes back as a string; `RequestId` exists for exactly that. The capabilities list prompts, resources, tools and completions.
2. **Open the Tools, Resources or Prompts tab.** The Inspector asked for every list as soon as discovery completed, and each request has already failed with `-32601`: the Tools tab says *Couldn't load tools*, with `Method not found: tools/list` and a **Retry** button. Discovery advertised them, and this branch cannot serve them yet. Chapter 04 adds the handlers; until then the server says so, rather than leaving the request unanswered.

### Step 4: Read the log

Nothing but MCP messages may go to stdout: a stray `System.out.println` corrupts the stream the client is parsing. So the server logs to a file instead, under `logs/` in whatever working directory the client starts it in. `run.sh` starts it in `inspector/`, so look in `inspector/logs/`. There is one file per server process, and the Inspector starts a new process each time you connect.

### Step 5: Send the mistakes on purpose

`inspector/modern-probe.sh` drives the server without the UI. It sends a discovery request, a subscription, a cancellation, the methods that are `-32601` until chapter 04, and then the mistakes. `run.sh` is still holding the first terminal, so run the probe from a second one, at the top of the repository:

```bash
./inspector/modern-probe.sh
```

The last five replies are the interesting ones:

```text
{"jsonrpc":"2.0","id":4,"error":{"code":-32601,"message":"Method not found: initialize. This server only supports protocol versions without the initialize handshake: 2026-07-28","data":{"supported":["2026-07-28"]}}}
{"jsonrpc":"2.0","id":5,"error":{"code":-32022,"message":"Unsupported protocol version","data":{"supported":["2026-07-28"],"requested":"2025-11-25"}}}
{"jsonrpc":"2.0","id":6,"error":{"code":-32602,"message":"Requests must carry io.modelcontextprotocol/protocolVersion and io.modelcontextprotocol/clientCapabilities in params._meta"}}
{"jsonrpc":"2.0","error":{"code":-32700,"message":"Parse error: the message is not valid JSON"}}
{"jsonrpc":"2.0","error":{"code":-32600,"message":"Invalid Request: not a JSON-RPC 2.0 message"}}
```

Each failure can be diagnosed from the error alone, and the two with no usable id carry none. In the newest file in `inspector/logs/`, the cancellation shows up as:

```text
[API][RECEIVED] notifications/cancelled requestId=listen:0 reason=probe finished
```

## Errors Versus Results

Protocol errors are narrow and deliberate. Each code this chapter sends means one thing, and without a handshake to renegotiate in, these errors are the negotiation:

| Code | Sent when |
|---|---|
| `-32700` | the line is not JSON |
| `-32600` | it is JSON, but not a JSON-RPC 2.0 message |
| `-32601` | the method is not one this server answers |
| `-32602` | the envelope or the params are malformed |
| `-32603` | handling the request failed unexpectedly |
| `-32022` | the envelope names an unsupported revision |

A tool that runs and fails is different: from chapter 04 that is a successful result with `isError` set, which a model can read and recover from. A search that found nothing is not a protocol fault.

## Stretch: Keep a Stream Open

This is optional, and it changes behaviour that two chapter tests and the harness's `capability_shape` and `subscriptions` scenarios assert, so do it on a scratch branch.

This server closes every stream at once because it has nothing to say. To see a stream that stays open:

1. Advertise `tools.listChanged: true`. In `discoverResult()`, replace `withDefaultCapabilities()` with `withCapabilities(...)`, passing a `ServerCapabilities` whose `tools` is `new Capability(true)`.
2. In `acknowledgeSubscription`, when `honored.isEmpty()` is false, remember `message.id()` in a set of open streams instead of answering it.
3. When the tool list changes (for the exercise, on a timer), emit `notifications/tools/list_changed` to each open stream, with the stream's id in `_meta` under `MetaKeys.SUBSCRIPTION_ID`.
4. When a `notifications/cancelled` names an open stream, forget it. When the server ends one itself, answer the listen request with `SubscriptionsListenResult.closing(id)`.

## Stretch: Require a Client Capability

`-32021`, `MISSING_REQUIRED_CLIENT_CAPABILITY`, is for a request the server cannot serve without something the client did not declare. Its `data` carries `requiredCapabilities`, shaped like `ClientCapabilities`. To see one, add this to `IORouter`:

```java
private boolean requireFormElicitation(JsonRpcRequest message, RequestEnvelope envelope) {
    if (envelope.supportsElicitationForm()) {
        return true;
    }
    error(message.id(), ErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY,
          "This request needs a client that can show a form",
          Map.of("requiredCapabilities", Map.of("elicitation", Map.of("form", Map.of()))));
    return false;
}
```

Then change the listen arm of the `switch` to:

```java
case SUBSCRIPTIONS_LISTEN -> {
    if (requireFormElicitation(message, envelope)) {
        acknowledgeSubscription(message);
    }
}
```

A listen request whose `clientCapabilities` is `{}` now gets `-32021`. Undo it afterwards. Nothing in this server truly needs a form: in chapter 05 the search tool asks for a directory with one, and a client that cannot show forms gets the server's working directory searched instead. Degrading like that is better than refusing, and `-32021` is for when there is nothing to degrade to.

## Where this goes next

Discovery works and every request is checked, but most of what the server advertised still answers `-32601`. Chapter 04 adds prompts, resources, tools and completion to `INBOUND_METHODS` and to the `switch`, and the Inspector's lists fill in.

Start it with `./clean-checkout.sh 04-chapter`, or keep your own code: commit it and run `git merge origin/04-chapter`. The README explains both.
