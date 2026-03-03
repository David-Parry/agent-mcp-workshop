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
    ElicitationCreateParams params = ElicitationBuilder.buildJiraProjectElicitation();
    JsonRpcRequest elicitationRequest = new JsonRpcRequest(JSON_RPC_VERSION, ELICITATION_REQUEST_ID,
                                                           UniqueKeys.ELICITATION_CREATE_MESSAGE.getValue(),
                                                           params);
    io.emit(elicitationRequest);
}
```

**Key components:**
- `ElicitationBuilder.buildJiraProjectElicitation()` — creates the elicitation parameters with a prompt and a JSON schema defining a dropdown for Project Key and a dropdown for Time Range
- `ELICITATION_REQUEST_ID` — the unique ID we defined in Step 1; the response will arrive as a `JsonRpcResponse` with this same ID
- `io.emit()` — sends the request to the client

### Step 5: Send elicitation after initialization (~line 185)

**Action Required**: In the `NOTIFICATIONS_INITIALIZED` case of `process(JsonRpcNotification message)`, add the elicitation trigger after the existing roots request:

```java
case NOTIFICATIONS_INITIALIZED -> {
    // if server has roots, request the roots list
    if (hasRoots) {
        io.emit(rootsRequest);
    }
    if (hasElicitation) {
        sendElicitationMessage();
    }
}
```

This sends the elicitation request immediately after the client confirms initialization. `NOTIFICATIONS_INITIALIZED` is the correct moment — the client has confirmed the connection is ready and can accept server-initiated requests.

### Step 6: Handle elicitation responses (~line 160)

**Action Required**: Add a case to handle incoming `elicitation/create` method calls from the client in the `process(JsonRpcRequest message)` switch statement, before the `default` case:

```java
case ELICITATION_CREATE_MESSAGE -> {
    // Handle elicitation method calls from client
    logger.log("Received elicitation/create method call from client: " + message);
    // Parse the elicitation response and handle it appropriately
    // For now, just acknowledge the elicitation request
    success(message.id(), new Object());
}
```

**Note:** The elicitation flow is bidirectional — the server can send elicitation requests to the client (via `sendElicitationMessage()`), and the client can also send elicitation requests to the server. This handler processes the client-to-server direction and acknowledges it.

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

### 4. Observe the elicitation flow:

- After clicking **Connect**, the server sends an `elicitation/create` request in response to `notifications/initialized`
- The MCP Inspector displays the Jira project elicitation form with:
  - A **Project Key** dropdown: ENG (Engineering), HR (Human Resources), OPS (Operations)
  - A **Time Range** dropdown: Last 7 Days, Last 30 Days, Custom Range
- Select values and click **Submit** — the server log confirms receipt

## What You Should Observe

### In the initialize response:
- The `experimental` map appears in `capabilities` with the elicitation key
- The `completions` capability is present alongside tools, prompts, and resources

### In the elicitation flow:
- The server sends `elicitation/create` immediately after `notifications/initialized`
- The inspector renders the structured form from `ElicitationBuilder`
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
