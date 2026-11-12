# Chapter 05: Elicitation as a Multi Round-Trip Request

## Overview

In Chapter 4 a call without a `directory` ended in a tool error. In this lesson the server asks the user for one instead, **without ever sending the client a request**.

In earlier revisions of MCP a server asked for input by sending the client a JSON-RPC request, `elicitation/create`, with an id, which the client answered like a server. Revision `2026-07-28` removed that direction of travel entirely. **Modern clients silently discard inbound requests.** A server that tries to ask the old way is not rejected; it is ignored, and it waits forever.

What replaced it is **Multi Round-Trip Requests (MRTR)**: the server answers with `resultType: "input_required"`, *embedding* the request it would have sent. The client resolves it and re-sends the **original** request, under a brand new id, carrying the answers. The question travels inside an answer.

You write four things:

1. `SearchContinuation`'s three methods, which carry the exchange's state through the client and back
2. A `TOOLS_CALL` case that hands the search to a method that owns the round trip
3. `handleKeywordSearch`, that method
4. `elicitedDirectory`, which reads the user's answer back

Each bold line number below is the line of the method's signature, or of the case label, in the file as you checked it out. A method's `// Chapter 05:` comment is on the line after it. Each paste changes the length of the code it replaces, so what is below it moves up or down: where a target has moved by the time you reach it, its new line follows in parentheses, and finding it by its name always works.

> Official documentation: [https://modelcontextprotocol.io/extensions/overview](https://modelcontextprotocol.io/extensions/overview)

---

## Part 1: What Discovery Says, and What It Does Not

### Step 1: Read `discoverResult`

**Action Required**: open `src/main/java/com/workshop/mcp/IORouter.java` and read `discoverResult()`. It is already written:

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

Only the instructions changed, to tell the model about the round trip. **This chapter declares no extension.** `ServerCapabilities` has an `extensions` map, keyed by identifier, and `withExtension()` adds to it, but the first entry arrives in Chapter 6 (`io.modelcontextprotocol/ui`) and the second in Chapter 7 (`io.modelcontextprotocol/tasks`).

Elicitation is not declared here at all, and this is the opposite of the old model. Elicitation is no longer something the *server* offers; it is something the *client* can do. The client says so in every request, and the server reads it off that request before it asks.

---

## Part 2: There Is No `hasElicitation` Field

### Step 1: Read how a request says what its client can do

In the old handshake model, a server learned the client's capabilities once, at `initialize`, and stored them:

```java
// The old way. Do not write this any more.
private boolean hasElicitation = false;
private boolean hasSampling = false;
private Set<String> roots = new HashSet<>();
```

Those fields *were* the session. With sessions removed they are not just unnecessary, they are wrong: a server that keeps them assumes the client that sent request #2 is the one, with the same capabilities, that sent request #1. Nothing in the protocol promises that any more.

Instead every request restates the client's capabilities in its `_meta` envelope, and `envelopeFor()` from Chapter 3 hands you that envelope. `IORouter` has no capability fields to remove. The questions are answered by `RequestEnvelope`:

| Question | Old | New |
|----------|-----|-----|
| Can the client show a form? | `hasElicitation` | `envelope.supportsElicitationForm()` |
| Can the client poll a task? | `hasTasks` | `envelope.supportsTasks()` (Chapter 7) |

**Action Required**: open `src/main/java/com/workshop/mcp/spec/Elicitation.java` and read `supportsForm()`, which `supportsElicitationForm()` asks:

```java
public boolean supportsForm() {
    return form != null || url == null;
}
```

A client declares `"elicitation":{"form":{}}` for forms, `"url":{}` for out-of-band pages, or both. An empty `"elicitation":{}` names neither, and means form: that is how a client declared elicitation before there were modes to name. Only a client that names `url` alone cannot be shown a form.

Two of the old flags have no replacement at all. There is no `supportsRoots()` and no `supportsSampling()`: both features are deprecated (see the end of Part 4), this server asks for neither, and a predicate nothing calls only goes stale. Clients that still declare `"roots":{"listChanged":true}` or `"sampling":{}` are unaffected. `ClientCapabilities` names only the two fields the server reads, and Gson drops anything it does not name:

```java
public record ClientCapabilities(
    Elicitation elicitation,
    Map<String, Object> extensions
) {}
```

There are no negative request-id constants either, no `ELICITATION_REQUEST_ID = -4000L`. They existed to match a response to a request *the server had sent*, and this server sends none.

---

## Part 3: Carrying State Across a Round Trip

The server asks its question in one request and reads the answer off a *different* one, and it has nowhere to remember, in between, that it already asked. So that fact travels to the client and back, as an opaque `requestState` string. `SearchContinuation` is what goes in it.

### Step 1: Fill `SearchContinuation`

Open `src/main/java/com/workshop/mcp/tools/SearchContinuation.java`. The record has one field, `stage`, and its two constants are already there:

- **`STAGE_DIRECTORY`**: the server has asked for a directory and is waiting for the answer.
- **`KEY_DIRECTORY`**: the key the question is filed under in `inputRequests`. The answer comes back under the same key in `inputResponses`.

**Action Required**: fill `awaitingDirectory` at **line 53** of `src/main/java/com/workshop/mcp/tools/SearchContinuation.java`:

```java
public static SearchContinuation awaitingDirectory() {
    return new SearchContinuation(STAGE_DIRECTORY);
}
```

Fill `encode` at **line 64** (line 62 once `awaitingDirectory` is in):

```java
public String encode() {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(
            GSON.toJson(this).getBytes(StandardCharsets.UTF_8));
}
```

Fill `decode` at **line 81** (line 78 once the two above are in):

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

#### What to notice

1. **The encoding is a convention, not a rule.** The protocol treats `requestState` as opaque bytes and promises only that the client returns them unchanged. Base64-encoded JSON is what the specification's own examples use. The URL-safe alphabet without padding keeps the value free of `+`, `/` and `=`, none of which it needs.

2. **`stage` is what stops the server asking forever.** A declined form and a first attempt look almost the same: both are a `tools/call` with no directory in them. The stage tells them apart. There is only one stage today, and it is tempting to make it a boolean; a named stage is what makes a second question cheap to add.

3. **The keyword is not in the state.** The client retries the *whole* original call, so the `keyword` argument comes back on its own. And `requestState` comes back from the client, which can send any value it likes. The specification says that when this state drives authorization, resource access or business logic, its integrity MUST be protected, for example by signing it. A keyword read from here could be swapped for one the arguments never asked for. A stage cannot do that kind of harm: a forged one only skips the question, which declining does anyway.

4. **`decode` returns null for anything it cannot read**, and the caller treats that as a first attempt rather than an error. The state came from this server, but it travelled through a client, so it is input and gets checked like input.

---

## Part 4: Implementing the Round Trip

### Step 1: Hand `tools/call` to a resolver

The `TOOLS_CALL` case is the one you wrote in Chapter 4: an unknown tool is `-32602`, and a call without a directory is a tool error. The error is what this chapter replaces.

**Action Required**: replace the `TOOLS_CALL` case at **line 242** of `src/main/java/com/workshop/mcp/IORouter.java`. Paste this over the whole case:

```java
case TOOLS_CALL -> {
    ToolCallParams toolCallParams = deserializer.deserializeParams(message, ToolCallParams.class);
    KeyWordSearch keyWordSearch = new KeyWordSearch();
    if (!keyWordSearch.name().equals(toolCallParams.name())) {
        logger.log("[API][SENT] tools/call — unknown tool " + toolCallParams.name()
                   + " (returning -32602)");
        error(message.id(), ErrorCodes.INVALID_PARAMS, "Unknown tool: " + toolCallParams.name());
    } else {
        handleKeywordSearch(message.id(), toolCallParams, envelope);
    }
}
```

`envelope` is passed down because capabilities belong to *this request*, so they travel with it rather than sitting in a field.

### Step 2: Resolve the directory, asking if necessary

**Action Required**: fill `handleKeywordSearch` at **line 416** of `IORouter.java` (line 406 once Step 1's case is in):

```java
private void handleKeywordSearch(RequestId requestId, ToolCallParams params, RequestEnvelope envelope) {
    SearchContinuation continuation = SearchContinuation.decode(params.requestState());
    String stage = continuation != null ? continuation.stage() : null;

    Set<String> directories = new LinkedHashSet<>();

    // A directory argument settles the question outright, so a client that
    // cannot answer an input_required can still call the tool in one hop.
    String argument = directoryArgument(params);
    if (argument != null) {
        directories.add(argument);
    }

    if (SearchContinuation.STAGE_DIRECTORY.equals(stage)) {
        String directory = elicitedDirectory(params.inputResponse(SearchContinuation.KEY_DIRECTORY));
        if (directory != null) {
            directories.add(directory);
        }
        logger.log("[API][RECEIVED] tools/call retry — elicited directory=" + directory);
    }

    if (!directories.isEmpty()) {
        executeKeyWordSearchCall(requestId, params, directories, envelope);
        return;
    }

    // Asking the user for a directory is the only question this tool has
    // left. It used to embed a roots/list first, since a client answers
    // that from configuration without troubling anyone, but roots is
    // deprecated under SEP-2577 and the directory argument above is the
    // migration the spec names in its place.
    if (!SearchContinuation.STAGE_DIRECTORY.equals(stage) && envelope.supportsElicitationForm()) {
        logger.log("[API][SENT] tools/call — input_required, embedding elicitation/create");
        success(requestId, InputRequiredResult.of(
                SearchContinuation.KEY_DIRECTORY,
                InputRequest.elicitation(ElicitationBuilder.buildSearchDirectoryElicitation()),
                SearchContinuation.awaitingDirectory().encode()));
        return;
    }

    // There is nothing left to ask, or nothing that may be asked. Rather
    // than fail, search from wherever the server was started — the same
    // place a shell command with no path argument would look.
    String workingDirectory = workingDirectory();
    logger.log("[API][SENT] tools/call — no directory to search, using the working directory "
               + workingDirectory);
    executeKeyWordSearchCall(requestId, params, Set.of(workingDirectory), envelope);
}
```

`executeKeyWordSearchCall` and `workingDirectory` are already in the file. The search reads its keyword from the call's own arguments, retry or not.

#### Reading the shape of this method

Everything about it follows from having no session:

1. **It is one pass, not a callback.** There is no "send the request, wait, resume when the response arrives". Each call reads what it was given, decides, and answers. The exchange moves on because the *client* calls again.

2. **`success(...)` sends an `InputRequiredResult`.** That is an ordinary JSON-RPC **result**, to the id the client sent. The server is finished with this request and remembers nothing about it.

3. **The stage is checked before asking.** Without that check, declining once would put the user straight back in the same form, forever.

4. **There is always a way out.** The last lines are a fallback, not an error. MRTR is optional for clients, and a tool that can only answer after a round trip is unusable by a client that does not implement it.

### Step 3: Read the answer back

**Action Required**: fill `elicitedDirectory` at **line 458** of `IORouter.java` (line 493 once Steps 1 and 2 are in):

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

- **`deserializer.convert(...)`, not `deserializeResult(...)`.** An input response is the **bare result the method would have returned**, `{"action":"accept","content":{…}}`, not a JSON-RPC response. There is no `jsonrpc`, no `id`, and no `result` wrapper to unwrap. A server that sends no requests receives no responses, so the deserializer has no method for one.
- **`"accept"`**: a form can also come back `decline` or `cancel`. Neither is an error. Both mean no directory, which sends the resolver on to its fallback.

### What the wire looks like

The interim result, with the server asking:

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
   "requestState":"eyJzdGFnZSI6ImRpcmVjdG9yeSJ9",
   "_meta":{"io.modelcontextprotocol/serverInfo":{ … }}}}
```

The embedded request has **no `jsonrpc` and no `id`**. It is data describing a question, named by its `method` and matched up by the key it sits under. The `requestState` decodes to `{"stage":"directory"}`, and that is all of it.

The client's retry is a brand new request:

```json
{"jsonrpc":"2.0","id":8,"method":"tools/call",
 "params":{
   "_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28", "…":"…"},
   "name":"key_word_search",
   "arguments":{"keyword":"record"},
   "inputResponses":{
     "search_directory":{"action":"accept","content":{"directory":"/Users/you/code/repo"}}},
   "requestState":"eyJzdGFnZSI6ImRpcmVjdG9yeSJ9"}}
```

It has a new id, the full envelope again, the original arguments, and the `requestState` echoed back exactly.

### Only three methods may be embedded, and we build one

The revision permits exactly three: `roots/list`, `sampling/createMessage` and `elicitation/create`. None of them is in the inbound list, so a client that sends one *to* the server gets `-32601`:

```java
private static final Set<UniqueKeys> INBOUND_METHODS = EnumSet.of(
        UniqueKeys.SERVER_DISCOVER,
        UniqueKeys.PROMPTS_LIST,
        // … the rest of Chapter 4's methods, and no ELICITATION_CREATE;
        // the other two have no constant at all
        UniqueKeys.SUBSCRIPTIONS_LISTEN);
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

There is no `rootsList()` and no sampling factory, because both features are deprecated. The specification's advice is to take a tool parameter instead of Roots, and to call a model provider's API directly instead of Sampling. The `-32601` for all three inbound comes from the revision, not from which of them a server chooses to embed.

That is the difference between modelling a protocol and modelling *your use* of it. The three-method rule limits what is legal to embed; what you embed is a design decision. A factory, a capability record and a predicate for a method you never send are dead code that someone has to keep in step with the spec.

### Removed and deprecated are different things

`2026-07-28` does both, and the code treats them differently. Each change below is named by its SEP (a Specification Enhancement Proposal, the numbered form every change to the specification takes).

**Removed** means gone, answering `-32601` (SEP-2575): `initialize`, `notifications/initialized`, `ping`, `logging/setLevel`, `resources/subscribe`, `resources/unsubscribe`, `notifications/roots/list_changed`, `notifications/elicitation/complete`, `tasks/result` and `tasks/list`. On HTTP the session headers, `Mcp-Session-Id` and `Last-Event-ID`, went with them. Tasks moved to the `io.modelcontextprotocol/tasks` extension, which is Chapter 7.

**Deprecated** means marked, but still working. New code should not adopt a deprecated feature. It keeps working until at least the first revision published on or after 2027-07-28, and removing it takes a proposal of its own:

- **Roots** (SEP-2577). Take directories as tool parameters instead, which is why `key_word_search` has a `directory` argument at all.
- **Sampling** (SEP-2577), with the `thisServer` and `allServers` values of `includeContext`, which go no later than Sampling does. Call a model provider's API directly instead.
- **Logging** (SEP-2577). The log level is now each request's `logLevel`, and a server must not send `notifications/message` to a request that did not set one. Write to `stderr` or OpenTelemetry instead. This server writes to a file.
- **Dynamic Client Registration**, replaced by Client ID Metadata Documents. It belongs to HTTP authorization, which a stdio server does not use.
- **The HTTP+SSE transport** (SEP-2596), which can be removed three months after SEP-2596 reaches Final. Streamable HTTP replaces it.

Roots is also why this chapter reads the way it does. An embedded `roots/list` used to be the worked example: the server asked the client where to look, the client answered from its own configuration without troubling anybody, and only an empty answer escalated to a form. That made a good round-trip demonstration, built on a feature that the same specification tells you, a few pages later, not to build on. Nothing about MRTR was lost in the move: the exchange you just wrote is the same exchange, with the user answering instead of the client.

The two policies land differently on the same feature. `notifications/roots/list_changed` was *removed*, so it is gone from the method registry. The `roots` capability was only *deprecated*, so clients keep declaring it, as SEP-2577 asks them to for the whole transition, and the Inspector puts `"roots":{"listChanged":true}` in every envelope it sends you. That is correct client behaviour, not a bug to chase. `ClientCapabilities` does not name the field, so Gson drops it and the server carries on.

---

## You are done when the tests pass

```bash
./gradlew chapterTest -Pchapter=05
```

The run reports 36 tests, and 27 of them fail before you start: 9 in `SearchContinuationTest` and 18 in `IORouterTest`. The other 9 pass already. 5 cover given code: `ElicitationBuilder` and the form it builds, how a retry's `ToolCallParams` finds an answer under the key the server chose, and that `KeyWordSearch` keeps no state between calls. The other 4 pass because Chapter 4's `TOOLS_CALL` case already answers them. Keep those 4 passing when you replace that case. Failures worth recognising:

- `awaitingDirectoryCarriesOnlyTheDirectoryStage` — the state carries more than the stage, such as the keyword.
- `anEncodedContinuationIsUnpaddedUrlSafeBase64` — `encode` uses `getEncoder()`, or keeps the padding.
- `aCorruptRequestStateIsTreatedAsAFirstAttemptRatherThanAnError` — `decode` throws on a value it cannot read instead of returning null.
- `aRetrySearchesForItsOwnKeywordArgumentAndNotOneInTheState` — the keyword was read out of `requestState` instead of the call's arguments.
- `theQuestionIsAskedOnceAndOnlyOnce` — a declined form is asked again, because the stage is not checked before asking.
- `aClientThatCannotShowAFormIsNotAsked` or `anElicitationDeclarationThatNamesNoModeIsShownTheForm` — the server asks without `envelope.supportsElicitationForm()`, or reads `elicitation` itself and misses that `{}` means form.
- `aDirectoryArgumentSkipsTheRoundTripEntirely` — this one passed before you started, so a directory argument is no longer searched straight away.
- `anAcceptedFormWithNoDirectoryInItFallsBackRatherThanSearchingNowhere` — `elicitedDirectory` returned something other than a usable directory.
- A test that expects one response and finds a `-32603` — something you wrote threw. The log file says what.

Then build everything and drive the built server the way a client would. The harness starts the JAR and checks everything up to this chapter over stdio:

```bash
./gradlew clean build
python3 lessons/presentation-3hr/verify/harness.py --chapter 05
```

The probe sends the round trip by hand: a bare call, the retry with a directory, the retry with the form declined, and two clients that cannot be asked. Its first reply is the question, and the second is the search:

```bash
./inspector/modern-mrtr.sh
```

```text
{"jsonrpc":"2.0","id":10,"result":{"resultType":"input_required","inputRequests":{"search_directory":{"method":"elicitation/create","params":{"mode":"form",…}}},"requestState":"eyJzdGFnZSI6ImRpcmVjdG9yeSJ9",…}}
{"jsonrpc":"2.0","id":11,"result":{"resultType":"complete","content":[…],"isError":false,…}}
```

The other three replies are searches of the probe's own directory, because none of those calls has a directory to search.

Last, prove the old direction is gone. Send `elicitation/create` *to* the server:

```bash
printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"elicitation/create","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}},"mode":"form","message":"Pick one","requestedSchema":{"type":"object","properties":{}}}}' | java -jar build/libs/agent-mcp-workshop-0.0.1.jar
```

```text
{"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"Method not found: elicitation/create"}}
```

The method is not in the inbound list. It only ever appears embedded in a result.

## Try It in the MCP Inspector

```bash
cd inspector
./run.sh
```

`inspector/config.json` already has `"protocolEra": "modern"` as a sibling of `command`, as in Chapter 3: the Inspector sends `server/discover` and never falls back to `initialize`. Do not nest it anywhere else, or the Inspector drops back to its `legacy` default and opens with `initialize`.

### Discovery
- Turn on the switch on the `workshop` card, and expand the `server/discover` entry in the message log. There is no `initialize` anywhere in the log.
- `capabilities` has no `extensions` map yet. The UI extension arrives in Chapter 6 and tasks in Chapter 7.
- Look at what the *client* sent in `_meta.io.modelcontextprotocol/clientCapabilities`. The Inspector declares `"roots":{"listChanged":true}` there, and will keep doing so: deprecated is not removed. The server does not model the key, so it is dropped.

### The round trip
- Open the **Tools** tab and call `key_word_search` with the keyword `java` and **no** `directory`.
- The first answer is `resultType: "input_required"` carrying `inputRequests.search_directory`. The embedded request has no `id`: this is a result, not a request.
- The Inspector shows the form as an **Elicitation Request**. Paste an absolute path and submit; it re-sends `tools/call` under a **new id**, with `inputResponses` and the echoed `requestState`. The log groups both calls under one **MRTR** entry, as Round 1 and Round 2.
- **Decline the form once.** The search still returns, now rooted at the server's working directory, and `isError` is `false`. That directory is `inspector/`, where `run.sh` started the server. The search skips its `logs/`, so `java` is found only in the few files there, such as `config.json`; a keyword that appears only under `src/` comes back as **Empty result**. Nothing else ran in between, so the Inspector adds these two calls to the same **MRTR** entry, as Round 3 and Round 4.
- Call it once more **with** a `directory` argument. The round trip disappears entirely.

## Stretch: Ask with a URL

A client that declares only `"url":{}` cannot show a form, so today it is never asked. Embed an `elicitation/create` with `mode: "url"` for it instead: the user opens a page, and the client retries once the page reports back. Decide what the page does, and how its answer reaches the retry.

## Stretch: Ask two questions in one round

`inputRequests` is a map, so one `input_required` can ask several things at once. Ask for the keyword as well as the directory when a call has neither, with one key for each, and read both answers off the same retry. The stage now has to say which questions were asked.

## Stretch: Sign the state

The stage is safe to trust, because a forged one can only skip the question. State that chose what to search would not be. Sign `requestState` with an HMAC and a time limit, and treat a value whose signature does not match, or that has expired, as a first attempt.

## Where this goes next

The search can now ask for what it lacks, but every answer is still a list of paths for the model to read. Chapter 06 gives the tool a page of its own: an MCP App, which a host that supports the extension shows beside the tool's result.

Start it with `./clean-checkout.sh 06-chapter`, or keep your own code: commit it and run `git merge origin/06-chapter`. The README explains both.
