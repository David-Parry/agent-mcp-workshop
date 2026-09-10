# Chapter 05: MCP Extensions — Elicitation as a Multi Round-Trip Request

## Overview

In this lesson we implement two related things:

1. **Extension declaration** — the server announces what it supports by adding to the `extensions` capability map in the `server/discover` response
2. **Elicitation** — the server asks for structured user input, and receives it, **without ever sending the client a request**

That second point is the whole chapter. In earlier revisions of MCP a server asked for input by sending the client a JSON-RPC request — `elicitation/create`, with an id, which the client answered like a server. Revision `2026-07-28` removed that direction of travel entirely. **Modern clients silently discard inbound requests.** A server that tries to ask the old way is not rejected; it is ignored, and it waits forever.

What replaced it is **Multi Round-Trip Requests (MRTR)**: the server answers with `resultType: "input_required"`, *embedding* the request it would have sent. The client resolves it and re-sends the **original** request — brand new id — carrying the answers. The question travels inside an answer.

> Official documentation: [https://modelcontextprotocol.info/docs/extensions/](https://modelcontextprotocol.info/docs/extensions/)

---

## Part 1: The `extensions` Map in `ServerCapabilities`

### Understanding `extensions` vs `experimental`

Revision `2026-07-28` fixes `ServerCapabilities` at exactly seven optional slots, and gives extensions a slot of their own:

```java
public record ServerCapabilities(
    Map<String, Object> experimental,
    Capability logging,
    Capability completions,
    Capability prompts,
    Capability resources,
    Capability tools,
    Map<String, Object> extensions
) {}
```

Both maps still exist, and they are not interchangeable:

- **`extensions`** — declares support for a *specified* extension, keyed by identifier. Official ones use the `io.modelcontextprotocol` prefix, e.g. `io.modelcontextprotocol/tasks`. This is where anything outside the core protocol now lives.
- **`experimental`** — for genuinely non-standard, in-house capabilities that no specification describes.

Note what is *absent* from that record: there is no top-level `tasks` slot any more. It became `extensions["io.modelcontextprotocol/tasks"]`, which you will use in Chapter 7.

An empty configuration object — `{}` — is the conventional way to say "supported, no settings".

### Step 1: Look at discovery — elicitation is not a server extension

`server/discover` already advertises **core capabilities only** on this branch — tools, resources, prompts, completions — plus instructions that mention the round trip you are about to add. You do not add `withExtension(...)` here.

**Do not** declare `io.modelcontextprotocol/ui` or `io.modelcontextprotocol/tasks`. Those arrive in Chapters 6 and 7. Elicitation is not a server extension at all: it is a thing the *client* can do, read per request from `envelope.supportsElicitationForm()`.

Confirm `discoverResult()` looks like this (it is already filled):

```java
private DiscoverResult discoverResult() {
    return DiscoverResultBuilder
            .builder()
            .withDefaultCapabilities()
            .withInstructions("Searches a project for a keyword. If no directory is supplied, the tool asks "
                              + "for one over a Multi Round-Trip Request, and searches its own working "
                              + "directory if the client offers nothing.")
            .withCacheHints(LIST_TTL_MILLIS, CacheScope.PUBLIC)
            .withDefaultServerInfo()
            .build();
}
```

#### What to notice

1. **Declaration order does not matter.** `build()` folds the accumulated `extensions` and `experimental` maps into the final `ServerCapabilities`, so `withExtension()` may come before or after `withDefaultCapabilities()`. That is a deliberate improvement on the old builder, where getting the order wrong silently dropped your declaration.

2. **There is no `withProtocolVersion()`.** A single negotiated version was a property of a session, and there are no sessions. Discovery advertises `supportedVersions` — a list — and every subsequent request states which one it is using in its own `_meta`.

3. **Elicitation is not declared here.** This is worth dwelling on, because it is the opposite of the old model. Elicitation is no longer a thing the *server* offers; it is a thing the *client* can do. The server reads `elicitation` off the client's capabilities on each request and decides whether it may ask.

---

## Part 2: There Is No `hasElicitation` Field

### Why the flags are gone

In the old handshake model, a server learned the client's capabilities once, at `initialize`, and stored them:

```java
// The old way — do not write this any more.
private boolean hasElicitation = false;
private boolean hasSampling = false;
private Set<String> roots = new HashSet<>();
```

Those fields *were* the session. With sessions removed, they are not just unnecessary — they are wrong. Any server that keeps them is asserting that the client which sent request #2 is the same one, with the same capabilities, that sent request #1. Nothing in the protocol guarantees that any more.

Instead, every request re-states the client's capabilities in its `_meta` envelope, and Chapter 3's `envelopeFor()` validated it. The envelope answers capability questions per request:

```java
RequestEnvelope envelope = envelopeFor(message);
if (envelope == null) {
    return;
}
```

**Action Required**: Make sure your `IORouter` has no capability fields left. The predicates you need instead are on `RequestEnvelope`:

| Question | Old | New |
|----------|-----|-----|
| Can the client show a form? | `hasElicitation` | `envelope.supportsElicitationForm()` |
| Can the client poll a task? | `hasTasks` | `envelope.supportsTasks()` |

Two of the old flags become no predicate at all. There is no `supportsRoots()` and no `supportsSampling()`: roots and sampling are both deprecated (see below), this server asks for neither, and a predicate nothing calls is a predicate that rots — so those flags are deleted rather than ported. Clients that still declare `"roots":{"listChanged":true}` or `"sampling":{}` are unaffected. `ClientCapabilities` is now just the two fields the server reads, and Gson quietly drops anything it does not name:

```java
public record ClientCapabilities(
    Elicitation elicitation,
    Map<String, Object> extensions
) {}
```

And there are no negative request-id constants — no `ELICITATION_REQUEST_ID = -4000L`, no `ROOTS_REQUEST_ID`. Those existed to correlate a response to a request *the server had sent*. The server sends no requests, so there is nothing to correlate.

---

## Part 3: Carrying State Across a Round Trip

The server asks its question in one request and reads the answer off a *different* one, and it has nowhere to remember, in between, what it was searching for or that it had already asked. So the state travels to the client and back, as an opaque `requestState` string.

### Step 1: Copy the `SearchContinuation` record

**Action Required**:
1. Copy the file `SearchContinuation.java` from the `lessons` folder
2. Paste it into `src/main/java/com/workshop/mcp/tools/SearchContinuation.java` (the file already exists). You can replace from **line 1**, or fill the hollowed methods: `awaitingDirectory` at **line 49**, `encode` at **line 60**, `decode` at **line 77**.

The handout is the complete record with its Javadoc. Abbreviated, it is this:

```java
public record SearchContinuation(String keyword, String stage) {

    public static final String STAGE_DIRECTORY = "directory";

    public static final String KEY_DIRECTORY = "search_directory";

    public static SearchContinuation awaitingDirectory(String keyword) {
        return new SearchContinuation(keyword, STAGE_DIRECTORY);
    }

    public String encode() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                GSON.toJson(this).getBytes(StandardCharsets.UTF_8));
    }

    public static SearchContinuation decode(String requestState) { /* … */ }
}
```

**Key components:**

- **`keyword`** — the tool argument from the original call. The client re-sends the whole call, but the server should not depend on the argument surviving intact, so on the retry it reads the keyword from here.
- **`stage`** — how far the exchange has got. This is what stops the server asking the same question forever. There is only one stage today, and it is tempting to reduce it to a boolean; leaving it as a named stage is what makes a second question cheap to add later.
- **`KEY_DIRECTORY`** — the identifier the server files its question under. The answer comes back under the same key.
- **`encode()` / `decode()`** — base64-encoded JSON. The protocol treats `requestState` as opaque bytes and guarantees only that the client returns them **unchanged**; base64 JSON is the convention the specification's own examples use.

### A note on trust

`decode()` returns `null` for anything malformed, and the caller treats that as a first attempt rather than an error:

```java
public static SearchContinuation decode(String requestState) {
    if (requestState == null || requestState.isBlank()) {
        return null;
    }
    try {
        String json = new String(Base64.getUrlDecoder().decode(requestState), StandardCharsets.UTF_8);
        SearchContinuation decoded = GSON.fromJson(json, SearchContinuation.class);
        return (decoded == null || decoded.stage == null) ? null : decoded;
    } catch (IllegalArgumentException | JsonSyntaxException e) {
        return null;
    }
}
```

The state originated on this server, but it made a round trip through a client. It is input, and it gets validated like input.

---

## Part 4: Implementing the Round Trip

### Step 1: Route `tools/call` through a resolver

**Action Required**: In the `TOOLS_CALL` case at **line 169** of `src/main/java/com/workshop/mcp/IORouter.java`, hand the keyword search off to a method that owns the round trip:

```java
case TOOLS_CALL -> {
    ToolCallParams toolCallParams = deserializer.deserializeParams(message, ToolCallParams.class);
    KeyWordSearch keyWordSearch = new KeyWordSearch();
    if (!keyWordSearch.name().equalsIgnoreCase(toolCallParams.name())) {
        success(message.id(), ToolCallResultBuilder
                .builder()
                .addTextContent("Tool not found: " + toolCallParams.name())
                .asError()
                .build());
    } else {
        handleKeywordSearch(message.id(), toolCallParams, envelope);
    }
}
```

Note that `envelope` is passed down. Capabilities are a property of *this request*, so they travel with it rather than being read off a field.

### Step 2: Resolve the directory, asking if necessary

**Action Required**: Fill `handleKeywordSearch` at **line 284** of `IORouter.java`:

```java
private void handleKeywordSearch(RequestId requestId, ToolCallParams params, RequestEnvelope envelope) {
    SearchContinuation continuation = SearchContinuation.decode(params.requestState());
    String keyword = continuation != null ? continuation.keyword() : keywordArgument(params);
    String stage = continuation != null ? continuation.stage() : null;

    Set<String> directories = new LinkedHashSet<>();

    // A directory argument settles the question outright.
    String argument = directoryArgument(params);
    if (argument != null) {
        directories.add(argument);
    }

    // Otherwise, read whatever the round trip answered.
    if (SearchContinuation.STAGE_DIRECTORY.equals(stage)) {
        String directory = elicitedDirectory(params.inputResponse(SearchContinuation.KEY_DIRECTORY));
        if (directory != null) {
            directories.add(directory);
        }
    }

    if (!directories.isEmpty()) {
        executeKeyWordSearchCall(requestId, params, keyword, directories, envelope);
        return;
    }

    // Nothing to search yet, so ask the user — once.
    if (!SearchContinuation.STAGE_DIRECTORY.equals(stage) && envelope.supportsElicitationForm()) {
        success(requestId, InputRequiredResult.of(
                SearchContinuation.KEY_DIRECTORY,
                InputRequest.elicitation(ElicitationBuilder.buildSearchDirectoryElicitation()),
                SearchContinuation.awaitingDirectory(keyword).encode()));
        return;
    }

    // Nothing left to ask, or nothing that may be asked. Rather than fail,
    // search from wherever the server was started.
    executeKeyWordSearchCall(requestId, params, keyword, Set.of(workingDirectory()), envelope);
}
```

#### Reading the shape of this method

Everything about it follows from having no session:

1. **It is one pass, not a callback.** There is no "send the request, wait, resume when the response arrives". Each invocation reads what it was given, decides, and answers. The exchange advances because the *client* calls again.

2. **`success(...)` sends an `InputRequiredResult`.** Look carefully at what that is: a perfectly ordinary JSON-RPC **result**, to the id the client sent. The server has now finished with this request completely and remembers nothing about it.

3. **`stage` is the only thing preventing a loop.** A declined form and a first attempt arrive looking almost identical — both are a `tools/call` with no directory anywhere in them. The stage is what tells them apart. Without it, declining once would land the user right back in the same form, forever.

4. **There is always a way out.** The last line is a fallback, not an error. A tool that can only answer after a successful round trip is unusable by any client that does not implement MRTR — and MRTR is optional for clients.

### Step 3: Read the answer back

**Action Required**: Fill `elicitedDirectory` at **line 334** of `IORouter.java`:

```java
private String elicitedDirectory(Object inputResponse) {
    ElicitationCreateResult result = deserializer.convert(inputResponse, ElicitationCreateResult.class);
    if (result == null || !"accept".equalsIgnoreCase(result.action())) {
        return null;
    }
    if (result.content() instanceof Map<?, ?> content && content.get("directory") instanceof String directory
        && !directory.isBlank()) {
        return directory;
    }
    return null;
}
```

**Key components:**

- **`deserializer.convert(...)`, not `deserializeResult(...)`.** An input response is the **bare result the method would have returned** — `{"action":"accept","content":{…}}` — not a JSON-RPC response object. There is no `jsonrpc`, no `id`, no `result` wrapper to unwrap. The method that used to unwrap one has been deleted from the deserializer, because a server that sends no requests receives no responses.
- **`"accept"`** — a form can also come back `decline` or `cancel`. Neither is an error; they just mean no directory, which sends the resolver on to its fallback.

### What the wire looks like

The interim result — the server asking:

```json
{"jsonrpc":"2.0","id":7,
 "result":{
   "resultType":"input_required",
   "inputRequests":{
     "search_directory":{
       "method":"elicitation/create",
       "params":{"mode":"form","message":"…","requestedSchema":{ … }}
     }
   },
   "requestState":"eyJrZXl3b3JkIjoicmVjb3JkIiwic3RhZ2UiOiJkaXJlY3RvcnkifQ"}}
```

Note what is missing from that embedded request: **no `jsonrpc`, and no `id`.** It has been de-JSON-RPC'd. It is data describing a question, identified by its `method` and correlated by the key it sits under.

The client's retry — a brand new request:

```json
{"jsonrpc":"2.0","id":8,"method":"tools/call",
 "params":{
   "_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28", "…":"…"},
   "name":"key_word_search",
   "arguments":{"keyword":"record"},
   "inputResponses":{
     "search_directory":{"action":"accept","content":{"directory":"/Users/you/code/repo"}}},
   "requestState":"eyJrZXl3b3JkIjoicmVjb3JkIiwic3RhZ2UiOiJkaXJlY3RvcnkifQ"}}
```

New id. Full envelope again. The `requestState` echoed back byte-exact.

### Only three methods may be embedded — and we build one

The revision permits exactly three: `roots/list`, `sampling/createMessage`, and `elicitation/create`. All three are also **absent from the inbound registry**, so a client that sends one *to* the server gets `-32601`:

```java
private static final Set<UniqueKeys> INBOUND_METHODS = EnumSet.of(
        UniqueKeys.SERVER_DISCOVER,
        UniqueKeys.PROMPTS_LIST,
        // … no ELICITATION_CREATE; the other two have no constant at all
        UniqueKeys.TASKS_CANCEL);
```

They exist in exactly one place now: inside an `input_required` result.

We build one of the three. `InputRequest` has a single factory, `elicitation(...)`:

```java
public record InputRequest(String method, Object params) {

    public static InputRequest elicitation(ElicitationCreateParams params) {
        return new InputRequest(UniqueKeys.ELICITATION_CREATE.getValue(), params);
    }
}
```

There is no `rootsList()` and no sampling factory, because both of those features are deprecated (see below) and the guidance is to take a tool parameter instead of Roots and to call a provider API directly instead of Sampling. Both still answer `-32601` inbound — that rule comes from the revision, not from whether any given server chooses to embed one.

This is worth pausing on, because it is the difference between modelling a protocol and modelling *your* use of it. The spec's three-method rule is a constraint on what is legal to embed. What you actually embed is a design decision, and carrying a factory, a capability record, and a predicate for a method you never send is how a codebase accumulates the kind of dead surface this chapter is about deleting.

### Removed and deprecated are different things

`2026-07-28` does both, and it is worth keeping them apart because the codebase treats them differently.

**Removed** means physically absent, answering `-32601`: `initialize`, `notifications/initialized`, `ping`, `logging/setLevel`, `resources/subscribe`, `resources/unsubscribe`, `tasks/result`, `tasks/list`, and `notifications/roots/list_changed` (SEP-2575).

**Deprecated** means annotated but fully functional. Roots, Sampling, and Logging are deprecated under the feature lifecycle policy (SEP-2577): new servers should not adopt them, but they keep working in this revision and in every revision published within a year of it, and actually removing them needs a separate proposal. The suggested migrations are to take directories as tool parameters instead of Roots, integrate an LLM provider API directly instead of Sampling, and write to `stderr` or OpenTelemetry instead of Logging.

That is why `key_word_search` takes a `directory` argument at all — it is the migration the specification names in place of Roots, and it is the reason the one-hop path is the one to reach for first.

Roots is also why this chapter reads the way it does. An embedded `roots/list` used to be the worked example above: the server asked the client where to look, the client answered from its own configuration without troubling anybody, and only an empty answer escalated to a form. Two questions and an escalation — a genuinely nice demonstration of a round trip, built on a feature that the same specification tells you, a few pages later, not to build on. Reading a spec's lifecycle policy before choosing an example is cheaper than reading it afterwards. Nothing about MRTR was lost in the move: the exchange you just wrote is the same exchange, with the user answering instead of the client.

Watch the two policies land differently on the same feature. `notifications/roots/list_changed` was *removed*, so it is gone from the method registry entirely. The `roots` capability was only *deprecated*, so clients keep declaring it — SEP-2577 says they should, for the whole transition — and the Inspector will still put `"roots":{"listChanged":true}` in every envelope it sends you. That is correct client behaviour, not a bug to chase. `ClientCapabilities` no longer names the field, so Gson drops it and the server carries on. It has nothing to do with `"protocolEra": "modern"` either: the era selects the sessionless behaviour family, it does not suppress a capability that is merely deprecated.

---

## Testing Your Implementation

### 1. Build the project

```bash
./gradlew clean build
```

### 2. Start the MCP Inspector

```bash
cd inspector
./run.sh
```

Make sure `inspector/config.json` has `"protocolEra": "modern"` as a **direct sibling key** — nested anywhere else and the CLI quietly sends a legacy `initialize` instead of `server/discover`.

### 3. Verify the discovery response

- Click **Connect**
- Find the `server/discover` result — note there is no `initialize` anywhere in the log
- Expand `capabilities` and confirm there is **no** `extensions` map for UI or tasks yet — those are later chapters
- Confirm `supportedVersions` is a **list**, and that `serverInfo` is under `_meta` rather than in the body
- Look at what the *client* sent, in `_meta.io.modelcontextprotocol/clientCapabilities`. The Inspector declares `"roots":{"listChanged":true}` there, and will keep doing so — deprecated is not removed. The server does not model the key, so it never arrives anywhere

### 4. Observe the round trip

- Open **List Tools** and call `key_word_search` with a keyword and **no** `directory`
- The first answer is `resultType: "input_required"` carrying `inputRequests.search_directory`. **Point at the missing `id` inside the embedded request** — this is a result, not a request
- The Inspector renders the form. Paste an absolute path and submit; it re-sends `tools/call` under a **new id**, with `inputResponses` and the echoed `requestState`
- **Decline the form once.** The search still returns — now rooted at the server's working directory
- Call it once more **with** a `directory` argument, and watch the round trip disappear entirely

### 5. Prove the old direction is gone

Send an `elicitation/create` *to* the server and watch it come back `-32601`. That method is not in the inbound registry; it only ever appears embedded in a result.

## What You Should Observe

### In the discovery response
- `extensions` appears in `capabilities`, keyed by namespaced identifier
- `supportedVersions` is a list — no single negotiated version, because there is no session to negotiate for
- `serverInfo` sits in `_meta`, not in the result body

### In the round trip
- **The server never sends a message that is not a reply.** Every question it asks, it asks inside an answer
- Each retry carries the **full** `_meta` envelope again — the server learns the client's capabilities afresh every time
- `requestState` comes back byte-identical
- Declining the form is not a failure: `isError` is `false` and the search falls back to the working directory
- Supplying `directory` as an argument skips the round trip entirely

---

## How This Implements the Extension Pattern

| Direction | Mechanism | Where |
|-----------|-----------|-------|
| Client → Server | `clientCapabilities.elicitation.form` in **every** request's `_meta` | `RequestEnvelope.supportsElicitationForm()` |
| Server → Client | `capabilities.extensions["…"]` in `server/discover` | `withExtension()` — **this lesson** |
| Server asks | `resultType: "input_required"` with an embedded `elicitation/create` | `InputRequiredResult` — **this lesson** |
| Client answers | A **new** `tools/call` carrying `inputResponses` + `requestState` | `handleKeywordSearch` — **this lesson** |

The pattern generalises to every extension:
1. Declare the identifier under `capabilities.extensions` in `server/discover`
2. Read the client's side off each request's `_meta` envelope — never off a field
3. Ask by embedding a request in a result, never by sending one
4. Carry any continuation state through `requestState`

---

## Congratulations!

Your MCP server now implements the modern extension and input model:

- ✅ **`ServerCapabilities.extensions`** — declares specified extensions by identifier
- ✅ **`withExtension()`** — order-independent, folded in at `build()`
- ✅ **Per-request capabilities** — read from the `_meta` envelope, with no session flags
- ✅ **`InputRequiredResult`** — the server asks by answering
- ✅ **`SearchContinuation`** — continuation state that survives a stateless round trip
- ✅ **A one-hop path and a fallback** — the tool is usable by clients that never implement MRTR at all

In the next lesson you will wrap this tool in an **MCP App**, giving it an HTML interface the client renders directly.
