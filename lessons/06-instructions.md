# Chapter 06: MCP Apps — A Page for Your Tool

## Overview

In this lesson `key_word_search` gets a page of its own. A host that renders **MCP Apps** shows the page beside the tool's result. Every other client sees the same tool, calls it the same way and reads the same text, so nothing the tool already does changes.

MCP Apps is an official extension, `io.modelcontextprotocol/ui`, and it is **Stable**: its specification is revision `2026-01-26`. It adds no methods between client and server. A tool points at a page with `_meta.ui.resourceUri`, the page is an ordinary resource at a `ui://` address, and the host reads it with `resources/read`.

The records, the router wiring and the page are already on this branch. You write three methods:

1. `AppMeta.of`, which writes the page's address under both of the keys hosts read
2. `AppToolBuilder.withResourceUri`, which stores that address
3. `AppToolBuilder.build`, which refuses an incomplete tool and builds the rest

The line numbers below are where each method sits in the file as you checked it out. Every paste moves what is below it, so after your first paste in a file, find each one by its name rather than its number.

> Official documentation: [https://modelcontextprotocol.io/extensions/apps/overview](https://modelcontextprotocol.io/extensions/apps/overview), and the specification itself at [https://github.com/modelcontextprotocol/ext-apps/blob/main/specification/2026-01-26/apps.mdx](https://github.com/modelcontextprotocol/ext-apps/blob/main/specification/2026-01-26/apps.mdx)

---

## Part 1: The Records That Link a Tool to Its Page

### Step 1: Read `UiMeta`

**Action Required**: open `src/main/java/com/workshop/mcp/spec/UiMeta.java`. It is the innermost layer:

```java
public record UiMeta(String resourceUri, List<String> visibility) {}
```

- **`resourceUri`** is the `ui://` address of the page. It is the one thing that changes from one app to the next.
- **`visibility`** says **who may see and call the tool**, not where the page is shown. `"model"` puts the tool in the agent's tool list. `"app"` lets a page call it, and only a page from this same server. Left out, it means both, which is what this tool wants, so `AppMeta.of` passes null and Gson leaves the field out of the JSON.

A tool marked `["app"]` alone is one the model never sees, such as the tool behind a page's refresh button. A host must keep it out of the agent's tool list, and must refuse a page's call to any tool that does not include `"app"`.

### Step 2: Read `AppMeta`, the `_meta` block

**Action Required**: open `src/main/java/com/workshop/mcp/spec/AppMeta.java`:

```java
public record AppMeta(
        UiMeta ui,
        @SerializedName("ui/resourceUri") String resourceUri
) {
```

**The address goes on the wire twice**: nested, as `ui.resourceUri`, and flat, as `ui/resourceUri`. The shape suggests the wrong answer about which one is current, because every other `_meta` key you have met is flat, and most are slash-namespaced. The nested key is the canonical one. The MCP Apps specification marks the flat key **deprecated**, says it "will be removed before GA", and names `_meta.ui.resourceUri` as its replacement.

This server sends both, for the same reason clients still declare `roots`: hosts built against the older spelling still read it, and a deprecated field keeps working until it is removed. A host should read the nested key first. The MCP Inspector does, and falls back to the flat key only when the nested one is missing.

The flat key contains a slash, which no Java identifier can, so `@SerializedName` renames the component on the way out. Chapter 2 met the same problem with the `_meta` envelope keys, and read them out of the JSON tree by hand.

### Step 3: Read `AppTool` and `AppToolsListResult`

`AppTool` is `Tool` with one more component, `_meta`. Gson writes a field name as it is, so the key in the JSON is `_meta`, which is what the protocol requires.

`AppToolsListResult` is `ToolsListResult` for a list of `AppTool`. The two tool records are unrelated types, so a `List<Tool>` cannot hold an `AppTool`. It carries the same `resultType`, `ttlMs` and `cacheScope` as every other list result.

This is the tool the server sends in `tools/list` once you are done:

```json
{
  "name": "key_word_search",
  "description": "Searches for a specified keyword across all files in a project. Returns the total count of matches and the absolute file paths of the files containing the keyword. Pass a directory to search it directly; omit it and the server asks for one over a Multi Round-Trip Request, falling back to its own working directory if nothing is offered.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "keyword": { "type": "string", "description": "the keyword to search for in a file" },
      "directory": { "type": "string", "description": "the absolute directory to search; omit it to have the server ask for one over a Multi Round-Trip Request and fall back to its working directory" }
    },
    "required": ["keyword"]
  },
  "_meta": {
    "ui": { "resourceUri": "ui://keyword-search/mcp-app.html" },
    "ui/resourceUri": "ui://keyword-search/mcp-app.html"
  }
}
```

Everything but `_meta` is the tool from Chapters 4 and 5, `directory` and all. A client that does not support MCP Apps ignores `_meta`, and the tool works as text.

---

## Part 2: Write the Three Methods

### Step 1: Fill `AppMeta.of`

**Action Required**: fill `of` at **line 36** of `src/main/java/com/workshop/mcp/spec/AppMeta.java`:

```java
public static AppMeta of(String resourceUri) {
    return new AppMeta(new UiMeta(resourceUri, null), resourceUri);
}
```

Use `of` rather than the constructor everywhere: it is what keeps the two keys in step.

### Step 2: Fill `withResourceUri`

**Action Required**: fill `withResourceUri` at **line 121** of `src/main/java/com/workshop/mcp/spec/builders/AppToolBuilder.java`:

```java
public AppToolBuilder withResourceUri(String resourceUri) {
    this.resourceUri = resourceUri;
    return this;
}
```

### Step 3: Fill `build`

**Action Required**: fill `build` at **line 139** of `AppToolBuilder.java`, which is line 138 once `withResourceUri` is filled:

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
    if (inputSchema == null) {
        throw new IllegalStateException("inputSchema is required for AppTool, even one that takes no arguments");
    }
    return new AppTool(name, description, inputSchema, AppMeta.of(resourceUri));
}
```

#### What to notice

1. **The protocol requires two of these four, and the builder requires all four.** A tool must have a `name` and an `inputSchema`. The schema MUST be a JSON Schema object, never `null`, even for a tool that takes no arguments: such a tool sends `{"type":"object","additionalProperties":false}`, or at least an object with no properties. The protocol leaves `description` optional, but a model picks tools by their descriptions, so this builder insists on one. The `resourceUri` is what makes this an `AppTool` at all.
2. **The checks run in this order on purpose.** The tests leave the schema out when they test a missing or blank name, description or page, and expect to hear about what they left out first.
3. **Failing loudly is the kind failure.** An `AppTool` with no page claims a UI it does not have: the host fetches nothing, shows nothing and reports nothing. An exception the first time `tools/list` is answered is far easier to find.
4. **`build` calls `AppMeta.of`**, so no call site can fill one key and forget the other.

---

## Part 3: Read the Router Wiring

Nothing in `IORouter.java` needs writing in this chapter. Read the four places that wire the page in, in `src/main/java/com/workshop/mcp/IORouter.java`. `TOOLS_LIST` is the one that calls what you just wrote.

### `discoverResult` declares the extension

```java
.withExtension(MetaKeys.UI_EXTENSION, Map.of())
```

This is the first entry in the `extensions` map Chapter 5 described. A host only uses an extension that both sides declare, so a server that left this out would never have its page shown.

The settings object is empty on purpose. MCP Apps defines one setting, `mimeTypes`, and it belongs to the **host**: the list of page types it can render, which it sends in the `clientCapabilities` of every request as `{"mimeTypes":["text/html;profile=mcp-app"]}`. The extension defines no settings for a server, and the extensions overview's own example of a server declaring it is `"io.modelcontextprotocol/ui": {}`.

### `TOOLS_LIST` sends an `AppTool`

The case builds the tool with your builder:

```java
KeyWordSearch keyWordSearch = new KeyWordSearch();
AppTool appTool = AppToolBuilder.builder()
        .withName(keyWordSearch.name())
        .withDescription(keyWordSearch.description())
        .withInputSchema(keyWordSearch.schema())
        .withResourceUri(KEYWORD_APP_URI)
        .build();
success(message.id(), new AppToolsListResult(List.of(appTool), LIST_TTL_MILLIS, CacheScope.PUBLIC));
```

`KEYWORD_APP_URI` is a constant at the top of the router, `"ui://keyword-search/mcp-app.html"`, because three cases have to agree on it.

The tool carries its `_meta` whatever the client declared. MCP Apps says a server SHOULD check the client's capabilities before offering a tool with a UI, so that a host without the extension can be offered a plain tool instead. This server offers every caller the same tool, for three reasons:

- The list is cached as `public`, which says it is the same for every caller.
- A host that does not support the extension ignores `_meta`, so it costs that host nothing.
- The search answers with text either way, which is the fallback the extension asks for: a tool with a UI MUST still return meaningful `content`.

The second stretch below does the check instead.

### `RESOURCES_LIST` lists the page, and `RESOURCES_READ` serves it

`RESOURCES_LIST` adds the page beside the Javadoc pages, named `Keyword Search App`, with the MIME type `Resource.MIME_TYPE_UI_APP`, `"text/html;profile=mcp-app"`. MCP Apps allows a server to leave a page out of `resources/list`, because hosts find it through the tool. Listing it lets you see it in the Inspector.

`RESOURCES_READ` answers the page's address with `lesson/mcp-app.html` from the classpath, under the same MIME type. The specification requires that type for a page, and a host may read the page before the tool is ever called.

---

## Part 4: Look at the Page

The page is `src/main/resources/lesson/mcp-app.html`. It is already on the classpath, which is where `RESOURCES_READ` reads it from, so `./gradlew build` puts it in the JAR.

**Action Required**: open it in a browser. On macOS:

```bash
open src/main/resources/lesson/mcp-app.html
```

On Linux use `xdg-open`, and on Windows `start`, with the same path.

What you see is a **demo**. The banner says so: the page never talks to the host, the rows are sample data, and **Search** only filters them by file path. A real MCP App talks to its host over `postMessage`. The host hands the page the tool's arguments and result as `ui/notifications/tool-input` and `ui/notifications/tool-result`, and the page can ask the host to call a tool for it with `tools/call`. Making this page do that is the first stretch below.

The page also loads nothing from outside itself: its script and styles are inline, and it uses system fonts. That matters because a host gives a page a strict Content Security Policy unless the resource declares otherwise. Inline scripts and styles are allowed, and every network request and outside script is blocked.

---

## You are done when the tests pass

```bash
./gradlew chapterTest -Pchapter=06
```

The run reports 17 tests, and 13 of them fail before you start: 9 in `AppToolBuilderTest`, 3 in `IORouterTest` and 1 in `SpecTypesCoverageTest`. Two of the `IORouterTest` failures are Chapter 4 tests that read `tools/list`, which your builder now builds, so they run again here. The other 4 tests pass already, because the router's wiring is given: the extension is declared, the page is listed and read, and every method the router accepts is still answered. Failures worth recognising:

- `anAppToolWritesItsResourceUriUnderBothMetaKeys` — `of` fills one key and not the other, or `build` makes its `AppMeta` some other way. `appToolsCarryTheResourceUriUnderBothMetaKeys` fails with it, without a `-32603`, because the reply is missing the same key.
- `anAppToolWithoutANameIsRejected` or `anAppToolWithoutADescriptionIsRejected` — the check is missing, or its message differs from the snippet's.
- `aBlankNameIsRefusedJustLikeAMissingOne`, `aBlankDescriptionIsRefusedJustLikeAMissingOne` or `aBlankResourceUriIsRefusedJustLikeAMissingOne` — the check tests for `null` and misses `isBlank()`.
- `anAppToolWithoutAResourceUriIsRejected` — `build` lets a tool with no page through, or checks the schema first. This test leaves the schema out too, and expects to hear about the page.
- `anAppToolWithoutAnInputSchemaIsRejected` — `build` lets a tool with no schema through.
- `anAppToolAcceptsAPreBuiltInputSchema` — `build` copies or rebuilds the schema instead of passing on the one it was given.
- `anAppToolsListIsCompleteAndUnshareableUnlessToldOtherwise` — `AppMeta.of` still throws, or leaves the flat key empty.
- `appToolsCarryTheResourceUriUnderBothMetaKeys`, `cacheableResultsCarryFreshnessHintsAndUncacheableOnesDoNot` or `anInputSchemaWritesOnlyJsonSchemaKeywords` — `tools/list` answered `-32603`, because something you wrote threw. The log file says what.

Then build everything and drive the built server the way a client would. The harness starts the JAR and checks everything up to this chapter over stdio:

```bash
./gradlew clean build
python3 lessons/presentation-3hr/verify/harness.py --chapter 06
```

Last, look at the two answers a host reads before it shows anything. Ask the server what it supports, and then for its tools:

```bash
printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"server/discover","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}}}' '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}}}' | java -jar build/libs/agent-mcp-workshop-0.0.1.jar
```

```text
{"jsonrpc":"2.0","id":1,"result":{…"extensions":{"io.modelcontextprotocol/ui":{}}},…}}
{"jsonrpc":"2.0","id":2,"result":{"resultType":"complete","tools":[{"name":"key_word_search",…,"_meta":{"ui":{"resourceUri":"ui://keyword-search/mcp-app.html"},"ui/resourceUri":"ui://keyword-search/mcp-app.html"}}],"ttlMs":60000,"cacheScope":"public",…}}
```

This client declared nothing, and still got the tool with its page. That is the choice Part 3 describes.

## Try It in the MCP Inspector

```bash
cd inspector
./run.sh
```

Turn on the switch on the `workshop` card to connect. The Inspector speaks `2026-07-28` and supports MCP Apps, so every request it sends declares `io.modelcontextprotocol/ui` with its `mimeTypes`.

### The wiring
- The **Protocol** panel on the right lists every message. Open the `server/discover` reply: `extensions` now holds `"io.modelcontextprotocol/ui": {}`.
- Open the `tools/list` reply. `key_word_search` carries `_meta`, with the address under both keys. The **Tools** tab shows the tool's arguments but not its `_meta`.
- In the **Resources** tab, `Keyword Search App` is listed beside the Javadoc pages. Read it: the HTML comes back under `text/html;profile=mcp-app`.

### The app
- The **Apps** tab appears because a tool has a `_meta.ui.resourceUri`. It lists `key_word_search` under **MCP Apps (1)**.
- Select it, give it a `keyword` and a `directory`, and click **Open App**. The Inspector calls the tool and renders the page inside its own sandbox.
- The page shows its banner and its sample rows, whatever you searched for. The real result is in the **Protocol** panel, as the `tools/call` reply. The host sent it to the page as well, and the page did not listen.

A host such as Claude Desktop or VS Code GitHub Copilot renders the page the same way, beside the tool's result in the conversation, provided it speaks `2026-07-28`. The [client matrix](https://modelcontextprotocol.io/extensions/client-matrix) lists which clients support which extensions.

## Stretch: Make the page real

Make the page listen. On load, send `ui/initialize` to the host with `postMessage`, and send `ui/notifications/initialized` once the host answers. Render the rows from `ui/notifications/tool-result`, whose `content` holds one `<path>, keyword_count=N` line per file. Then make **Search** ask the host to `tools/call` `key_word_search` instead of filtering the sample rows.

The `App` class in `@modelcontextprotocol/ext-apps` does all of this for you, but the page may not load it from a CDN without saying so. Either inline it, or declare the CDN's origin in `_meta.ui.csp.resourceDomains` on the content `resources/read` returns. That `_meta.ui` also takes `prefersBorder`, if you want to choose whether the host draws a border.

## Stretch: Offer the page only to hosts that can show it

Read `io.modelcontextprotocol/ui` from the request's `clientCapabilities`. Offer the `AppTool` only when its `mimeTypes` includes `text/html;profile=mcp-app`, and the plain `Tool` otherwise. Then decide whether the list can still be cached as `public`, now that two callers can get different answers. The caching rules say a cached response is identified by its method and the request parameters that affect the result.

## Stretch: A tool only the page can call

Add a second tool for the page's own use, such as one that lists the most recent searches, and mark it `"visibility": ["app"]`. `tools/list` still sends it, but the host keeps it out of the agent's tool list and lets only the page call it. Give `AppMeta` a second factory that takes the visibility, so `of` keeps its one argument.

## Where this goes next

The tool now has a page, but every search still finishes before the caller hears anything. Chapter 07 lets a search run as a task: the server answers at once with a handle, and the client polls it for the result.

Start it with `./clean-checkout.sh 07-chapter`, or keep your own code: commit it and run `git merge origin/07-chapter`. The README explains both.
