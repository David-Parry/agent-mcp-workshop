# Chapter 06: MCP Apps — Building a Simple UI

## What You Are Building

In this lesson you will extend the `key_word_search` tool with a **UI declaration** so that MCP-compatible hosts like Claude render an interactive results dashboard alongside the tool's text output.

You will not be changing any existing behavior. The tool continues to work exactly as before. You are adding the protocol metadata that tells the host "there is also a UI available for this tool."

The new types (`UiMeta`, `AppMeta`, `AppTool`, `AppToolBuilder`) are already on this branch. Fill the three empty methods:
- `AppMeta.of` — **line 35** of `src/main/java/com/workshop/mcp/spec/AppMeta.java`
- `AppToolBuilder.withResourceUri` — **line 121** of `src/main/java/com/workshop/mcp/spec/builders/AppToolBuilder.java`
- `AppToolBuilder.build` — **line 138** of the same builder

The `IORouter` wiring that calls them is already written.

---

## Step 1: Understand the Three New Spec Records

Open these three new files in `src/main/java/com/workshop/mcp/spec/`. They form a simple three-layer object graph that maps directly to the `_meta` field the MCP Apps protocol requires on a tool.

### `UiMeta.java` — the innermost layer
```java
public record UiMeta(String resourceUri, List<String> visibility) {}
```
`resourceUri` is the `ui://` address where the host can fetch the HTML for this tool's app, and it is the only thing that changes from one MCP App to the next — everything else in the protocol is boilerplate around it.

`visibility` is an optional hint about where the host may show the app; leave it null and the host decides. `AppMeta.of` passes null for it, so you will not set it directly in this chapter.

Why a record? Because this is pure immutable data with no behavior. Gson serializes the field name `resourceUri` exactly as written, which is exactly what the spec requires.

### `AppMeta.java` — the middle layer

Paste the `of` factory at **line 35** of `src/main/java/com/workshop/mcp/spec/AppMeta.java`:
```java
public record AppMeta(
        UiMeta ui,
        @SerializedName("ui/resourceUri") String resourceUri
) {
    public static AppMeta of(String resourceUri) {
        return new AppMeta(new UiMeta(resourceUri, null), resourceUri);
    }
}
```

This is the one place in the app wiring with a surprise in it: **the URI goes on the wire twice.**

The reference MCP Apps extension writes it both nested, as `ui.resourceUri`, and flat, as `ui/resourceUri` — and mirrors whichever one the author left out. Hosts read one or the other depending on their vintage, so a server that emits only one of them works with some hosts and mystifies others. Carrying both costs a few bytes and removes an entire category of "it works in the Inspector but not in Claude" bug reports.

The flat key contains a slash, which is not legal in a Java identifier, so it is mapped with Gson's `@SerializedName`. This is the same problem the `_meta` envelope keys had in chapter 2, solved a different way: there the keys were read out of the JSON tree by hand, here the record component is simply renamed on the way out.

Use the `of` factory rather than the constructor. It is what guarantees the two keys agree.

### `AppTool.java` — the outer layer
```java
public record AppTool(
    String name,
    String description,
    InputSchema inputSchema,
    AppMeta _meta
) {}
```
Identical to `Tool` in every way except it carries the `_meta` field. The leading underscore in `_meta` is intentional and required by the protocol — Gson serializes the field name as-is, producing `"_meta"` in JSON.

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
    },
    "ui/resourceUri": "ui://keyword-search/mcp-app.html"
  }
}
```

Both spellings of the URI are there, as described above. When the host sees either one it knows two things: call the tool normally AND fetch that resource to render an app UI. Hosts that do not support MCP Apps ignore `_meta` entirely — the tool keeps working as text.

---

## Step 2: Understand the Builder — `AppToolBuilder.java`

Open `src/main/java/com/workshop/mcp/spec/builders/AppToolBuilder.java`.

It follows exactly the same fluent-builder pattern used throughout this project (`ToolsListResultBuilder`, `ResourcesListResultBuilder`, etc.), but it produces an `AppTool` instead of a `Tool`. The only addition over the standard tool builder is one method. Paste it at **line 121** of `src/main/java/com/workshop/mcp/spec/builders/AppToolBuilder.java`:

```java
public AppToolBuilder withResourceUri(String resourceUri) {
    this.resourceUri = resourceUri;
    return this;
}
```

This stores the `ui://` URI, which `build()` then hands to `AppMeta.of` before constructing the final `AppTool` record.

The `build()` method enforces that all three required values are present. Paste it at **line 138** of `AppToolBuilder.java`:
```java
public AppTool build() {
    if (name == null || name.isBlank()) {
        throw new IllegalStateException("name is required for AppTool");
    }
    if (description == null || description.isBlank()) {
        throw new IllegalStateException("description is required for AppTool");
    }
    if (resourceUri == null || resourceUri.isBlank()) {
        throw new IllegalStateException("resourceUri is required for AppTool — use withResourceUri(\"ui://...\")");
    }
    return new AppTool(name, description, inputSchema, AppMeta.of(resourceUri));
}
```

Note it calls `AppMeta.of(resourceUri)` rather than the constructor. Building the record by hand here would mean remembering to fill both the nested and the flat key at every call site, and the factory exists precisely so nobody has to.

Failing loudly on a missing `resourceUri` is deliberate too. An `AppTool` without one is a tool that claims to have a UI and does not — the host fetches nothing, shows nothing, and reports no error, which is far harder to debug than an exception at startup.

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
public record AppToolsListResult(
        String resultType,
        List<AppTool> tools,
        Long ttlMs,
        String cacheScope
) {}
```

This is a companion to `ToolsListResult` (which holds `List<Tool>`). The two records are separate because Java's type system does not allow `AppTool` to be passed where `Tool` is expected — they are independent records, not a subtype relationship.

It carries the same three protocol fields every other list result does: `resultType`, and the `ttlMs` / `cacheScope` cache hints from chapter 4.

When Gson serializes `AppToolsListResult`, it produces:
```json
{
  "resultType": "complete",
  "tools": [ { "name": "...", "description": "...", "inputSchema": {...}, "_meta": {...} } ],
  "ttlMs": 60000,
  "cacheScope": "public"
}
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

The file `lessons/mcp-app.html` is the HTML your server returns when the host requests `ui://keyword-search/mcp-app.html`.

**Action Required:** Open it directly in your browser:
```
open lessons/mcp-app.html
```

You will see an interactive keyword search results dashboard. This is exactly what Claude would render in the conversation iframe when the tool is called.

Spend a moment reading the comments in the HTML — they explain:
- Where real tool results would arrive (via `app.ontoolresult`)
- How the app calls back to the server (`app.callServerTool`)
- What the standalone demo substitutes in their place

---

## Step 6: Copy the HTML into the Classpath

`JavadocResources.readResourceContent(path)` reads from the classpath (inside the JAR), not from the filesystem. You need to put a copy of the HTML where the build can find it:

**Action Required:** Copy the file onto the classpath (replace `src/main/resources/lesson/mcp-app.html` from **line 1**):
```
cp lessons/mcp-app.html src/main/resources/lesson/mcp-app.html
```

Watch the two spellings: the handout lives in **`lessons/`** (plural, alongside the other lesson materials), and the destination on the classpath is **`lesson/`** (singular). They are not the same directory, and the copy fails silently in the wrong direction if you assume they are.

After this, `./gradlew build` will bundle it into the JAR and `readResourceContent("lesson/mcp-app.html")` will find it at runtime — note that the argument matches the *classpath* path, so it is the singular one.

---

## Step 7: The IORouter wiring is already on this branch

`discoverResult()` already declares `io.modelcontextprotocol/ui`. `TOOLS_LIST` already returns an `AppTool` via `AppToolBuilder.withResourceUri`. `RESOURCES_LIST` / `RESOURCES_READ` already serve `ui://keyword-search/mcp-app.html`.

Those calls go through `AppMeta.of` (**line 35**), `AppToolBuilder.withResourceUri` (**line 121**), and `AppToolBuilder.build` (**line 138**), which are **empty**. Fill them. Until `build()` returns a real `AppTool`, `tools/list` will fail the chapter tests.

Copy `lessons/mcp-app.html` onto `src/main/resources/lesson/mcp-app.html` if that file is still the lesson handout (the classpath copy is already in `src/main/resources/lesson/` on this branch).

There is no `withExecution` / `taskSupport` field, and nothing is declared under `experimental` or `io.modelcontextprotocol/apps`. The UI extension identifier is `io.modelcontextprotocol/ui`.

Read the existing cases so the three methods you fill have somewhere to land:

```java
.withExtension(MetaKeys.UI_EXTENSION,
               Map.of("mimeTypes", List.of(Resource.MIME_TYPE_UI_APP)))
```

```java
AppTool appTool = AppToolBuilder.builder()
        .withName(keyWordSearch.name())
        .withDescription(keyWordSearch.description())
        .withInputSchema(keyWordSearch.schema())
        .withResourceUri(KEYWORD_APP_URI)
        .build();
success(message.id(), new AppToolsListResult(List.of(appTool), LIST_TTL_MILLIS, CacheScope.PUBLIC));
```

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
| `"io.modelcontextprotocol/ui"` in `extensions` | Tells the host, in the `server/discover` result, that this server supports MCP Apps and which MIME types it renders |
| `Resource.MIME_TYPE_UI_APP` | `"text/html;profile=mcp-app"` — the MIME type the host's renderer requires |
| `lessons/mcp-app.html` → `src/main/resources/lesson/mcp-app.html` | The UI bundled into the JAR so `readResourceContent` can serve it |

## Key Takeaways

- **MCP Apps are just tools + resources.** You already know both primitives. Adding `_meta.ui.resourceUri` to a tool and serving the HTML resource is all that is new.
- **The tool keeps working.** Hosts that do not support MCP Apps call the tool normally and get text results. The UI is progressive enhancement.
- **The MIME type matters.** The Inspector and host both use `"text/html;profile=mcp-app"` to distinguish interactive app content from plain HTML. Any other value causes the host to reject the resource.
- **Type safety requires `AppToolsListResult`.** Because `AppTool` and `Tool` are independent record types, a separate response wrapper is needed — you cannot mix them in `List<Tool>`.
- **The host handles the hard parts.** Sandboxing, postMessage security, iframe lifecycle — all managed by the host. You write HTML and a server.

## Congratulations

You have extended a plain MCP tool into an MCP App. The pattern — declare a `ui://` resource on the tool, serve HTML at that URI — is the complete picture. Everything else is detail.
