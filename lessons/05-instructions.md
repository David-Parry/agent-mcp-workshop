# Chapter 05: MCP Extensions — Elicitation and the Experimental Capability

## Overview

In this lesson we implement two related things:

1. **Elicitation** — the server requests structured user input from the client through an interactive form
2. **Extension declaration** — the server announces it supports elicitation by adding it to the `experimental` capability map in the `initialize` response

Both follow the same MCP extension lifecycle: declare the capability field, add builder support, detect it at runtime, send the request, and handle the response.

> Official documentation: [https://modelcontextprotocol.info/docs/extensions/](https://modelcontextprotocol.info/docs/extensions/)

---

## Part 1: Add the `experimental` Field to `ServerCapabilities`

### Understanding the `experimental` Map

The MCP spec uses `experimental` in `ServerCapabilities` as the standard place for servers to declare extension support. It is a `Map<String, Object>` where each key is an extension identifier and the value is an extension-specific configuration object.

Extension identifiers follow the format `{vendor-prefix}/{extension-name}`. Official MCP extensions use the prefix `io.modelcontextprotocol`.

### Step 1: Update `ServerCapabilities.java`

**Action Required**: Replace the contents of `src/main/java/com/workshop/mcp/spec/ServerCapabilities.java` with the following:

```java
package com.workshop.mcp.spec;

import java.util.Map;

/**
 * Represents the capabilities provided by an MCP (Model Context Protocol) server.
 */
public record ServerCapabilities(
    Capability tools,
    Capability prompts,
    Capability resources,
    Capability completions,
    Map<String, Object> experimental
) {}
```

#### What changed:

1. **`experimental`** — a `Map<String, Object>` where the server declares which extensions it supports; each key is an extension identifier, each value is an extension-specific config object

When the client receives the `initialize` response, it reads this map to discover what extensions the server supports. Both sides must declare support before either activates extension behavior — this is the opt-in guarantee the MCP extension spec requires.

---

## Part 2: Add Builder Support in `InitializeResultBuilder`

### Step 1: Add the experimental map field and imports (~line 4 and ~line 36)

**Action Required**: Add two import statements at the top of `InitializeResultBuilder.java`:

```java
import java.util.HashMap;
import java.util.Map;
```

Then add a field inside the class body to accumulate experimental capability entries:

```java
private final Map<String, Object> experimental = new HashMap<>();
```

**Key components:**
- `HashMap` — collects extension declarations added by the caller before `build()` is called
- The map starts empty; entries are only added when `withExperimentalCapability()` is called
- Passing `null` to `ServerCapabilities` when empty keeps the JSON response clean

### Step 2: Update `withDefaultCapabilities()` (~line 126)

**Action Required**: Replace the existing `withDefaultCapabilities()` method:

```java
public InitializeResultBuilder withDefaultCapabilities() {
    Capability capabilityTrue = new Capability();
    this.capabilities = new ServerCapabilities(
        capabilityTrue,
        capabilityTrue,
        new Capability(false, false),
        new Capability(null, null),
        experimental.isEmpty() ? null : experimental
    );
    return this;
}
```

#### What changed:

1. **5th argument** — passes the accumulated `experimental` map if it has any entries, or `null` if empty so the field is omitted from the JSON response when no extensions are declared

2. **`withExperimentalCapability()` must be called before `withDefaultCapabilities()`** — calling it first populates the map so `withDefaultCapabilities()` picks it up correctly

### Step 3: Add `withExperimentalCapability()` method (~line 130)

**Action Required**: Add this method after `withDefaultServerInfo()`:

```java
/**
 * Declares support for an MCP extension in the server's experimental capabilities.
 * Extensions use the format {vendor-prefix}/{extension-name}.
 * Official MCP extensions use the prefix io.modelcontextprotocol.
 *
 * @param identifier the extension identifier, e.g. "io.modelcontextprotocol/elicitation"
 * @param config     the capability configuration object for this extension
 * @return this builder instance for method chaining
 */
public InitializeResultBuilder withExperimentalCapability(String identifier, Object config) {
    this.experimental.put(identifier, config);
    return this;
}
```

#### What this method does:

1. **Accepts an identifier** — the namespaced extension key, e.g. `"io.modelcontextprotocol/elicitation"`

2. **Accepts a config object** — extensions may require configuration; for simple opt-in extensions `new Object()` is sufficient

3. **Accumulates entries** — multiple `withExperimentalCapability()` calls can be chained to declare several extensions at once

4. **Returns `this`** — follows the fluent builder pattern already used by all other builder methods

---

## Part 3: Implementing the Elicitation Capability in `IORouter`

Elicitation is the capability that allows the server to request structured user input from the client through an interactive form. The server sends an `elicitation/create` request containing a prompt and a JSON schema; the client renders a form and returns the user's response.

### Step 1: Add the `ELICITATION_REQUEST_ID` constant and `hasElicitation` field (~line 19 and ~line 26)

**Action Required**: Add the request ID constant alongside the existing ID constants:

```java
private static final Long ELICITATION_REQUEST_ID = -4000L;
```

Then add the capability flag alongside the existing flags:

```java
private boolean hasElicitation = false;
```

**Key components:**
- `ELICITATION_REQUEST_ID` — unique ID (`-4000L`) used to match the elicitation response when it comes back as a `JsonRpcResponse`
- `hasElicitation` — set to `true` during initialization when the client declares elicitation support; gates all elicitation behavior

### Step 2: Detect elicitation capability during initialization (~line 65)

**Action Required**: In the `INITIALIZE` case of `process(JsonRpcRequest message)`, add the elicitation check after the existing `hasSampling` check:

```java
if (clientCapabilities.elicitation() != null) {
    hasElicitation = true;
}
```

This sets the `hasElicitation` flag when the client advertises elicitation support in its `ClientCapabilities`. The server will only send elicitation requests when this flag is `true`.

### Step 3: Declare the extension in the initialize response (~line 68)

**Action Required**: Add a call to `withExperimentalCapability()` in the existing `InitializeResultBuilder` chain. The builder chain currently reads:

```java
InitializeResultBuilder builder = InitializeResultBuilder
        .builder()
        .withProtocolVersion(initializeParams.protocolVersion())
        .withDefaultCapabilities()
        .withDefaultServerInfo();
```

Replace it with:

```java
InitializeResultBuilder builder = InitializeResultBuilder
        .builder()
        .withProtocolVersion(initializeParams.protocolVersion())
        .withExperimentalCapability("io.modelcontextprotocol/elicitation", new Object())
        .withDefaultCapabilities()
        .withDefaultServerInfo();
```

#### Why `withExperimentalCapability()` comes before `withDefaultCapabilities()`:

`withDefaultCapabilities()` reads the `experimental` map when it constructs `ServerCapabilities`. The call to `withExperimentalCapability()` must populate that map first so the entry is included in the response.

### Step 4: Add the `sendElicitationMessage()` method (~line 210)

**Action Required**: Add this private method after `sendSamplingMessage()`:

```java
/**
 * Creates and sends an elicitation request to the client.
 * Uses ElicitationBuilder to construct a structured question with a JSON schema.
 */
private void sendElicitationMessage() {
    ElicitationCreateParams params = ElicitationBuilder.buildSearchDirectoryElicitation();
    JsonRpcRequest elicitationRequest = new JsonRpcRequest(JSON_RPC_VERSION, ELICITATION_REQUEST_ID,
                                                           UniqueKeys.ELICITATION_CREATE_MESSAGE.getValue(),
                                                           params);
    io.emit(elicitationRequest);
}
```

**Key components:**
- `ElicitationBuilder.buildSearchDirectoryElicitation()` — creates the elicitation parameters with a prompt and a JSON schema defining a single required `directory` string field (an absolute path) that the `key_word_search` tool will use as its search root
- `ELICITATION_REQUEST_ID` — the unique ID we defined in Step 1; the response will arrive as a `JsonRpcResponse` with this same ID
- `io.emit()` — sends the request to the client

### Step 5: Trigger elicitation lazily inside `TOOLS_CALL`

**Action Required**: The elicitation is **not** sent at startup. It's only sent when the tool actually needs a search directory and the client did not provide one via `roots/list`. Defer the response to the original `tools/call` until the user submits the form, then resume.

Add two fields next to the capability flags so the router remembers which `tools/call` it owes a response to:

```java
private Long pendingToolsCallRequestId = null;
private ToolCallParams pendingToolCallParams = null;
```

In the `TOOLS_CALL` case, branch on whether roots are available before dispatching:

```java
case TOOLS_CALL -> {
    KeyWordSearch keyWordSearch = new KeyWordSearch(this.roots);
    ToolCallParams toolCallParams = deserializer.deserializeParams(message, ToolCallParams.class);
    if (!keyWordSearch.name().equalsIgnoreCase(toolCallParams.name())) {
        success(message.id(), ToolCallResultBuilder.builder()
                .addTextContent("Tool not found: " + toolCallParams.name())
                .asError()
                .build());
    } else if (roots.isEmpty() && hasElicitation && pendingToolsCallRequestId == null) {
        // No directory yet — remember the call, ask the user, resume on response
        pendingToolsCallRequestId = message.id();
        pendingToolCallParams = toolCallParams;
        sendElicitationMessage();
    } else {
        executeKeyWordSearchCall(message.id(), toolCallParams);
    }
}
```

Where `executeKeyWordSearchCall(...)` contains the actual tool dispatch (synchronous or task-augmented), shared between the immediate and resumed paths.

### Step 6: Consume the elicitation response and resume the deferred call

**Action Required**: The form submission arrives as a `JsonRpcResponse` with `id == ELICITATION_REQUEST_ID`. Add a branch in `process(JsonRpcResponse)` that adds the chosen directory to `roots` and then resumes the pending `tools/call`:

```java
else if (ELICITATION_REQUEST_ID.equals(message.id())) {
    ElicitationCreateResult result =
        deserializer.deserializeResult(message, ElicitationCreateResult.class);
    if ("accept".equalsIgnoreCase(result.action())
            && result.content() instanceof Map<?, ?> contentMap
            && contentMap.get("directory") instanceof String dir
            && !dir.isBlank()) {
        roots.add(dir);
    }
    if (pendingToolsCallRequestId != null) {
        Long resumeId = pendingToolsCallRequestId;
        ToolCallParams resumeParams = pendingToolCallParams;
        pendingToolsCallRequestId = null;
        pendingToolCallParams = null;
        executeKeyWordSearchCall(resumeId, resumeParams);
    }
}
```

If the user declines or cancels, `roots` stays empty and the resumed call returns a clean "no search directory available" error from the tool itself — no extra error-handling code needed.

**Why lazy instead of eager?** Asking for a directory at server startup is annoying when the user just wants to look at prompts or resources. By deferring until the tool actually needs the directory, the elicitation form only appears when it's relevant.

---

## Testing Your Implementation

### 1. Build the project:

```bash
./gradlew clean build
```

### 2. Start the MCP Inspector:

```bash
cd inspector
./run.sh
```

### 3. Verify the initialize response:

- Click **Connect** to establish the connection
- In the MCP Inspector message log, find the `initialize` response from the server
- Expand the `capabilities` object — you should now see an `experimental` field containing `"io.modelcontextprotocol/elicitation": {}`

### 4. Observe the lazy elicitation flow:

- After clicking **Connect**, the server does **not** immediately ask anything — `notifications/initialized` only triggers the `roots/list` request.
- Open **List Tools** and call `key_word_search` with a keyword.
  - If the client supplied roots via `roots/list`, the call returns synchronously.
  - If the client supplied no roots, the server now sends `elicitation/create` and **defers** the `tools/call` response.
- The MCP Inspector displays the search-directory elicitation form with a single **Search Directory** text field — paste an absolute path (e.g. `/Users/you/code/some-repo`).
- Click **Submit** — the server log shows the directory being added to the roots set, then the originally-deferred `tools/call` response arrives carrying the search results.

## What You Should Observe

### In the initialize response:
- The `experimental` map appears in `capabilities` with the elicitation key
- The `completions` capability is present alongside tools, prompts, and resources

### In the elicitation flow:
- Nothing is asked at startup — the inspector only sees the `roots/list` request after `notifications/initialized`
- The first `tools/call key_word_search` that finds an empty roots set causes the server to emit `elicitation/create`
- The original `tools/call` response arrives only after the user accepts the form (or returns an error if they decline)
- Submitting, declining, or cancelling produces different log entries on the server

---

## How This Implements the Full Extension Pattern

The work you just did covers both sides of the elicitation extension handshake:

| Direction | Mechanism | Where |
|-----------|-----------|-------|
| Client → Server | `capabilities.elicitation: {}` in `initialize` request | `ClientCapabilities.elicitation` (already in spec) |
| Server → Client | `capabilities.experimental["io.modelcontextprotocol/elicitation"]` in `initialize` response | `withExperimentalCapability()` — **this lesson** |
| Server sends request | `elicitation/create` in `NOTIFICATIONS_INITIALIZED` | `sendElicitationMessage()` — **this lesson** |
| Client responds | `JsonRpcResponse` or `JsonRpcRequest` with elicitation data | `ELICITATION_CREATE_MESSAGE` case — **this lesson** |

The same four-step pattern applies to every MCP extension (ext-auth, ext-apps, or custom):
1. Add the field to `ServerCapabilities`
2. Add builder support in `InitializeResultBuilder`
3. Detect client support and declare server support in `INITIALIZE`
4. Send and handle extension-specific messages

---

## Congratulations!

Your MCP server now supports the full MCP extension lifecycle:

- ✅ **`ServerCapabilities.experimental`** — declares extension support to clients
- ✅ **`withExperimentalCapability()`** — fluent builder method for extension declaration
- ✅ **Capability detection** — `hasElicitation` flag set from client capabilities
- ✅ **Extension declaration** — `io.modelcontextprotocol/elicitation` in every initialize response
- ✅ **Elicitation request** — `elicitation/create` sent after initialization
- ✅ **Elicitation handler** — `ELICITATION_CREATE_MESSAGE` case handles client requests
- ✅ **Complete handshake** — both directions of the extension lifecycle implemented
