# Chapter 06: MCP Apps — Building a Simple UI

## What You Are Building

In this lesson you will extend the `key_word_search` tool with a **UI declaration** so that MCP-compatible hosts like Claude render an interactive results dashboard alongside the tool's text output.

You will not be changing any existing behavior. The tool continues to work exactly as before. You are adding the protocol metadata that tells the host "there is also a UI available for this tool."

---

## Step 1: Understand the Three New Spec Records

Open these three new files in `src/main/java/com/workshop/mcp/spec/`. They form a simple three-layer object graph that maps directly to the `_meta` field the MCP Apps protocol requires on a tool.

### `UiMeta.java` — the innermost layer
```java
public record UiMeta(String resourceUri) {}
```
Holds a single value: the `ui://` address where the host can fetch the HTML for this tool's app. The `resourceUri` is the only thing that changes from one MCP App to the next — everything else in the protocol is boilerplate around it.

Why a record? Because this is pure immutable data with no behavior. Jackson serializes the field name `resourceUri` exactly as written in JSON, which is exactly what the spec requires.

### `AppMeta.java` — the middle layer
```java
public record AppMeta(UiMeta ui) {}
```
Wraps `UiMeta` as a field named `ui`. This produces the intermediate JSON object `{ "ui": { "resourceUri": "..." } }`. The field name `ui` is mandatory — it is the key the host uses to find the resource URI when scanning the tool listing.

Why a separate record? Because the spec's `_meta` object is a map that can hold other future fields alongside `ui`. Separating `AppMeta` from `UiMeta` keeps the door open for those additions without restructuring anything.

### `AppTool.java` — the outer layer
```java
public record AppTool(
    String name,
    String description,
    InputSchema inputSchema,
    AppMeta _meta
) {}
```
Identical to `Tool` in every way except it carries the `_meta` field. The leading underscore in `_meta` is intentional and required by the protocol — Jackson serializes the field name as-is, producing `"_meta"` in JSON.

**The JSON the host receives in `tools/list`:**
```json
{
  "name": "key_word_search",
  "description": "Searches for a keyword across all project files.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "keyword": { "type": "string", "description": "The keyword to search for" }
    },
    "required": ["keyword"]
  },
  "_meta": {
    "ui": {
      "resourceUri": "ui://keyword-search/mcp-app.html"
    }
  }
}
```

When the host sees `_meta.ui.resourceUri` it knows two things: call the tool normally AND fetch that resource to render an app UI. Hosts that do not support MCP Apps ignore `_meta` entirely — the tool keeps working as text.

---

## Step 2: Understand the Builder — `AppToolBuilder.java`

Open `src/main/java/com/workshop/mcp/spec/builders/AppToolBuilder.java`.

It follows exactly the same fluent-builder pattern used throughout this project (`ToolsListResultBuilder`, `ResourcesListResultBuilder`, etc.), but it produces an `AppTool` instead of a `Tool`. The only addition over the standard tool builder is one method:

```java
public AppToolBuilder withResourceUri(String resourceUri) {
    this.resourceUri = resourceUri;
    return this;
}
```

This stores the `ui://` URI which the `build()` method then wraps into the two-layer `AppMeta(new UiMeta(resourceUri))` structure before constructing the final `AppTool` record.

The `build()` method enforces that all three required values are present:
```java
public AppTool build() {
    if (name == null || name.isBlank())        throw new IllegalStateException("name is required");
    if (description == null || description.isBlank()) throw new IllegalStateException("description is required");
    if (resourceUri == null || resourceUri.isBlank())  throw new IllegalStateException("resourceUri is required");
    AppMeta meta = new AppMeta(new UiMeta(resourceUri));
    return new AppTool(name, description, inputSchema, meta);
}
```

The complete builder call you will use in `IORouter.java`:
```java
AppTool appTool = AppToolBuilder.builder()
        .withName(keyWordSearch.name())
        .withDescription(keyWordSearch.description())
        .withInputSchema(keyWordSearch.schema())
        .withResourceUri("ui://keyword-search/mcp-app.html")
        .build();
```

---

## Step 3: Understand `AppToolsListResult.java` — the response wrapper

Open `src/main/java/com/workshop/mcp/spec/AppToolsListResult.java`.

```java
public record AppToolsListResult(List<AppTool> tools) {}
```

This is a companion to `ToolsListResult` (which holds `List<Tool>`). The two records are separate because Java's type system does not allow `AppTool` to be passed where `Tool` is expected — they are independent records, not a subtype relationship.

When Jackson serializes `AppToolsListResult`, it produces:
```json
{ "tools": [ { "name": "...", "description": "...", "inputSchema": {...}, "_meta": {...} } ] }
```

This is the same envelope the host expects from `tools/list` — the only difference from the plain `ToolsListResult` is that each entry now includes `_meta`.

---

## Step 4: Check the MIME Type Constant — `Resource.java`

Open `src/main/java/com/workshop/mcp/spec/Resource.java` and verify:
```java
public static final String MIME_TYPE_UI_APP = "text/html;profile=mcp-app";
```

This is the MIME type the MCP Apps specification requires and the one the Inspector's `@mcp-ui/client` library checks. If this constant reads `"application/vnd.mcp-ui.app+html"` instead, change it — the Inspector will reject the resource with **"Unsupported UI resource content format"** for any other value.

---

## Step 5: Open the Sample UI in Your Browser

The file `lesson/mcp-app.html` is the HTML your server returns when the host requests `ui://keyword-search/mcp-app.html`.

**Action Required:** Open it directly in your browser:
```
open lesson/mcp-app.html
```

You will see an interactive keyword search results dashboard. This is exactly what Claude would render in the conversation iframe when the tool is called.

Spend a moment reading the comments in the HTML — they explain:
- Where real tool results would arrive (via `app.ontoolresult`)
- How the app calls back to the server (`app.callServerTool`)
- What the standalone demo substitutes in their place

---

## Step 6: Copy the HTML into the Classpath

`JavadocResources.readResourceContent(path)` reads from the classpath (inside the JAR), not from the filesystem. You need to put a copy of the HTML where the build can find it:

**Action Required:** Copy the file:
```
cp lesson/mcp-app.html src/main/resources/lesson/mcp-app.html
```

After this, `./gradlew build` will bundle it into the JAR and `readResourceContent("lesson/mcp-app.html")` will find it at runtime.

---

## Step 7: Wire Everything Up in `IORouter.java`

Four targeted changes — each shown as a before/after pair so you can cut and paste directly.

### Change 1 of 4 — Declare the Apps Extension in `INITIALIZE`

The host checks the `experimental` field of the `initialize` response to know whether this server supports MCP Apps. Without this declaration the host ignores `_meta.ui.resourceUri` entirely.

**Find this block** (the `INITIALIZE` case, lines 73–80):
```java
InitializeResultBuilder builder = InitializeResultBuilder
        .builder()
        .withProtocolVersion(initializeParams.protocolVersion())
        .withExperimentalCapability("io.modelcontextprotocol/elicitation", new Object())
        .withDefaultCapabilities()
        .withDefaultServerInfo();
```

**Replace it with:**
```java
InitializeResultBuilder builder = InitializeResultBuilder
        .builder()
        .withProtocolVersion(initializeParams.protocolVersion())
        .withExperimentalCapability("io.modelcontextprotocol/elicitation", new Object())
        .withExperimentalCapability("io.modelcontextprotocol/apps", new Object())
        .withDefaultCapabilities()
        .withDefaultServerInfo();
```

The `new Object()` serializes to `{}` — an empty JSON object. The key's presence is the signal; no configuration is needed inside it.

**The `initialize` response now includes:**
```json
{
  "capabilities": {
    "experimental": {
      "io.modelcontextprotocol/elicitation": {},
      "io.modelcontextprotocol/apps": {}
    }
  }
}
```

---

### Change 2 of 4 — Return an `AppTool` from `TOOLS_LIST`

**Find this block** (the `TOOLS_LIST` case):
```java
case TOOLS_LIST -> {
    KeyWordSearch keyWordSearch = new KeyWordSearch(this.roots);
    ToolsListResultBuilder builder = ToolsListResultBuilder
            .builder()
            .addTool(keyWordSearch.name(), keyWordSearch.description(), keyWordSearch.schema());
    success(message.id(), builder.build());
}
```

**Replace it with:**
```java
case TOOLS_LIST -> {
    KeyWordSearch keyWordSearch = new KeyWordSearch(this.roots);
    AppTool appTool = AppToolBuilder.builder()
            .withName(keyWordSearch.name())
            .withDescription(keyWordSearch.description())
            .withInputSchema(keyWordSearch.schema())
            .withResourceUri("ui://keyword-search/mcp-app.html")
            .build();
    success(message.id(), new AppToolsListResult(List.of(appTool)));
}
```

`AppToolsListResult` is used instead of `ToolsListResult` because `AppTool` and `Tool` are separate record types — Java's type system does not allow one where the other is expected.

---

### Change 3 of 4 — Advertise the UI Resource in `RESOURCES_LIST`

The Inspector's Apps tab reads `resources/list` after `tools/list` to confirm the `ui://` resource exists. Without this entry it cannot render the app.

**Find this block** (the `RESOURCES_LIST` case):
```java
case RESOURCES_LIST -> {
    ResourcesListResultBuilder builder = ResourcesListResultBuilder
            .builder()
            .withResources(JavadocResources.loadAllHtmlResourcesFromFolder("javadoc/com/workshop/mcp/spec"))
            .withNextCursor("pageNext");
    success(message.id(), builder.build());
}
```

**Replace it with:**
```java
case RESOURCES_LIST -> {
    ResourcesListResultBuilder builder = ResourcesListResultBuilder
            .builder()
            .withResources(JavadocResources.loadAllHtmlResourcesFromFolder("javadoc/com/workshop/mcp/spec"))
            .addResource(ResourceBuilder.builder()
                    .withUri("ui://keyword-search/mcp-app.html")
                    .withName("Keyword Search App")
                    .withDescription("Interactive keyword search results dashboard")
                    .withMimeType(Resource.MIME_TYPE_UI_APP)
                    .build())
            .withNextCursor("pageNext");
    success(message.id(), builder.build());
}
```

---

### Change 4 of 4 — Serve the HTML in `RESOURCES_READ`

When the host requests `ui://keyword-search/mcp-app.html`, the server must return the HTML content with the correct MIME type. Add a branch at the top of the `RESOURCES_READ` case that handles `ui://` URIs before falling through to the existing Javadoc logic.

**Find this block** (the `RESOURCES_READ` case):
```java
case RESOURCES_READ -> {
    ReadResourceParam param = deserializer.deserializeParams(message, ReadResourceParam.class);
    String resourceUri = param.uri();
    ReadResourceResultBuilder builder = ReadResourceResultBuilder.builder();
    if (resourceUri != null && !resourceUri.isEmpty()) {
        try {
            String content = JavadocResources.readResourceContent(resourceUri);
            builder.addTextContent(resourceUri, DEFAULT_MIME_TYPE, content);
        } catch (Exception e) {
            logger.log("Error reading resource: " + resourceUri);
            builder.addTextContent(resourceUri, DEFAULT_MIME_TYPE, e.getMessage()).asError();
        }
    } else {
        builder
                .addTextContent("", DEFAULT_MIME_TYPE, "Resource URI is null or empty, returning error.")
                .asError();
    }
    success(message.id(), builder.build());
}
```

**Replace it with:**
```java
case RESOURCES_READ -> {
    ReadResourceParam param = deserializer.deserializeParams(message, ReadResourceParam.class);
    String resourceUri = param.uri();
    ReadResourceResultBuilder builder = ReadResourceResultBuilder.builder();
    if ("ui://keyword-search/mcp-app.html".equals(resourceUri)) {
        try {
            String html = JavadocResources.readResourceContent("lesson/mcp-app.html");
            builder.addTextContent(resourceUri, Resource.MIME_TYPE_UI_APP, html);
        } catch (Exception e) {
            logger.log("Error reading UI app resource: " + resourceUri);
            builder.addTextContent(resourceUri, Resource.MIME_TYPE_UI_APP, e.getMessage()).asError();
        }
    } else if (resourceUri != null && !resourceUri.isEmpty()) {
        try {
            String content = JavadocResources.readResourceContent(resourceUri);
            builder.addTextContent(resourceUri, DEFAULT_MIME_TYPE, content);
        } catch (Exception e) {
            logger.log("Error reading resource: " + resourceUri);
            builder.addTextContent(resourceUri, DEFAULT_MIME_TYPE, e.getMessage()).asError();
        }
    } else {
        builder
                .addTextContent("", DEFAULT_MIME_TYPE, "Resource URI is null or empty, returning error.")
                .asError();
    }
    success(message.id(), builder.build());
}
```

The `ui://` branch runs first so it never falls into the Javadoc path. The MIME type `Resource.MIME_TYPE_UI_APP` (`"text/html;profile=mcp-app"`) signals to the host's renderer that this is an interactive app, not a plain HTML document.

---

## Step 8: Rebuild and Test with the Inspector

```bash
./gradlew clean build
```

Restart your server and reconnect the Inspector. Verify each piece:

**Tools tab** — `key_word_search` now shows a `_meta` field:
```json
"_meta": { "ui": { "resourceUri": "ui://keyword-search/mcp-app.html" } }
```

**Resources tab** — `ui://keyword-search/mcp-app.html` appears in the list alongside the Javadoc resources.

**Apps tab** — Click **Refresh Apps**. The Inspector calls `tools/list`, finds the `_meta.ui.resourceUri` field, and shows **Keyword Search App** in the list.

**Action Required:** Read the `ui://keyword-search/mcp-app.html` resource directly from the Resources tab. You should receive the HTML back with MIME type `text/html;profile=mcp-app`. This is exactly what the host does before rendering the app.

---

## Step 9: Connect to Claude and See the UI

Once your server is registered with Claude, ask:

> "Search for the keyword 'mcp' in this project and show me the results."

In a Claude host that supports MCP Apps:
1. Claude calls `key_word_search("mcp")`
2. Claude fetches `ui://keyword-search/mcp-app.html`
3. An interactive results dashboard appears in the conversation
4. You can type a new keyword in the UI and search again without a new prompt

---

## What You Built

| Piece | What It Does |
|-------|-------------|
| `UiMeta` | Holds `resourceUri` — the `ui://` address of the HTML app |
| `AppMeta` | Wraps `UiMeta` as the `ui` field, producing the `_meta` object |
| `AppTool` | A tool record that carries `_meta`, serialised exactly as the MCP Apps spec requires |
| `AppToolBuilder` | Fluent builder that assembles `UiMeta → AppMeta → AppTool` with validation |
| `AppToolsListResult` | Response envelope for `tools/list` typed to `List<AppTool>` |
| `"io.modelcontextprotocol/apps"` in `experimental` | Tells the host during `initialize` that this server supports MCP Apps |
| `Resource.MIME_TYPE_UI_APP` | `"text/html;profile=mcp-app"` — the MIME type the host's renderer requires |
| `lesson/mcp-app.html` → `src/main/resources/lesson/mcp-app.html` | The UI bundled into the JAR so `readResourceContent` can serve it |

## Key Takeaways

- **MCP Apps are just tools + resources.** You already know both primitives. Adding `_meta.ui.resourceUri` to a tool and serving the HTML resource is all that is new.
- **The tool keeps working.** Hosts that do not support MCP Apps call the tool normally and get text results. The UI is progressive enhancement.
- **The MIME type matters.** The Inspector and host both use `"text/html;profile=mcp-app"` to distinguish interactive app content from plain HTML. Any other value causes the host to reject the resource.
- **Type safety requires `AppToolsListResult`.** Because `AppTool` and `Tool` are independent record types, a separate response wrapper is needed — you cannot mix them in `List<Tool>`.
- **The host handles the hard parts.** Sandboxing, postMessage security, iframe lifecycle — all managed by the host. You write HTML and a server.

## Congratulations

You have extended a plain MCP tool into an MCP App. The pattern — declare a `ui://` resource on the tool, serve HTML at that URI — is the complete picture. Everything else is detail.
