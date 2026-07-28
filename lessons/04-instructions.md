# Chapter 04: Implementing Resources, Tools, and Prompts Capabilities

## Overview

In this lesson you implement the capabilities that Chapter 3 advertised but did not answer:

1. **Resources**: expose Javadoc HTML pages that clients can list and read, and answer the template list, empty
2. **Tools**: a keyword-search tool that clients can list and call
3. **Prompts**: a `search_keyword` template, plus completion suggestions for its argument

Each matching `case` in `IORouter`'s request switch is empty. The two helper classes, `JavadocResources` and `KeyWordSearch`, are already written: read them first, then fill every case. There is no MCP App URI yet, and a call without a `directory` is a **tool-level error**. Chapter 5 is where the server learns to ask.

The line numbers below are where each `case` sits in the file as you checked it out. Every paste pushes the cases below it further down, so after your first paste, find each case by its label rather than its number.

## Part 1: Implementing the Resources Capability

### Step 1: Read the JavadocResources helper

**Action Required**: open `src/main/java/com/workshop/mcp/resources/JavadocResources.java` and read it. Three things in it shape the handlers you write next:

- The Javadoc pages are in `src/main/resources/javadoc/`, which is bundled into the JAR, so the helper reads them with `getResourceAsStream()` rather than from the filesystem.
- `loadAllHtmlResourcesFromFolder` builds the list from a listing generated at build time, because a JAR has no directory to list.
- `readResourceContent` throws an `IOException` for a page that is not there. It also refuses anything that is not an `.html` page, or that contains `..`: the uri comes straight from the client, and the classpath holds the server's own classes too.

### Step 2: Fill the resource handlers

**Action Required**: paste the following over the empty cases in `src/main/java/com/workshop/mcp/IORouter.java`:
- `RESOURCES_LIST` — **line 214**
- `RESOURCES_TEMPLATES_LIST` — **line 217**
- `RESOURCES_READ` — **line 220**

```java
case RESOURCES_LIST -> {
    ResourcesListResultBuilder builder = ResourcesListResultBuilder
            .builder()
            .withResources(JavadocResources.loadAllHtmlResourcesFromFolder("javadoc/com/workshop/mcp/spec"));
    ResourcesListResult result = builder.build();
    success(message.id(), new ResourcesListResult(result.resources(), result.nextCursor(),
                                                  LIST_TTL_MILLIS, CacheScope.PUBLIC));
}
case RESOURCES_TEMPLATES_LIST -> {
    // Clients fetch this whenever a server declares any resource
    // capability, so it has to be answered even though this server
    // exposes no templates.
    success(message.id(), ResourceTemplatesListResult.empty(LIST_TTL_MILLIS));
}
case RESOURCES_READ -> {
    ReadResourceParam param = deserializer.deserializeParams(message, ReadResourceParam.class);
    String resourceUri = param.uri();
    if (resourceUri == null || resourceUri.isEmpty()) {
        logger.log("[API][SENT] resources/read — no uri (returning -32602)");
        error(message.id(), ErrorCodes.INVALID_PARAMS, "resources/read requires a uri");
    } else {
        try {
            String content = JavadocResources.readResourceContent(resourceUri);
            ReadResourceResult result = ReadResourceResultBuilder.builder()
                    .addTextContent(resourceUri, DEFAULT_MIME_TYPE, content)
                    .build();
            success(message.id(), new ReadResourceResult(result.contents(),
                                                         LIST_TTL_MILLIS, CacheScope.PUBLIC));
        } catch (IOException e) {
            logger.log("[API][SENT] resources/read — " + e.getMessage() + " (returning -32602)");
            error(message.id(), ErrorCodes.INVALID_PARAMS, "Resource not found",
                  Map.of("uri", resourceUri));
        }
    }
}
```

The list is Javadoc only. The `ui://` app resource is Chapter 6.

Notice each `success(...)` restates the built result with `LIST_TTL_MILLIS` and `CacheScope.PUBLIC`. The builders do not know about caching, so `builder.build()` alone would tell the client "never reuse this."

The list has no `nextCursor`. A client keeps asking for the next page until `nextCursor` is absent, and this server has one page. Offering a cursor it never reads back would hand the client page one again, with the same cursor, forever.

A uri the server cannot read is answered with `-32602` and the uri in `data`. It is not a result with empty `contents`, and `ReadResourceResult` has no `isError`: only a tool result does. Earlier revisions used `-32002` for a missing resource. This one retired that code, and a server on it must never send it.

## Part 2: Implementing the Tools Capability

### Step 1: Read the KeyWordSearch tool

**Action Required**: open `src/main/java/com/workshop/mcp/tools/KeyWordSearch.java` and read it.

The schema has a required `keyword` and an optional `directory`. In this chapter a call has to supply both, because the server has no way yet to ask for a missing directory. The directories to search are a parameter of `call`, not a field, so nothing from one call can leak into the next. The search skips `logs/`, `build/`, `.git/` and `.gradle/` below the directory it is given. The server's log records every request it receives, keyword included, so without that a search of this project would find itself.

### Step 2: Fill the tools handlers

**Action Required**: paste into the empty cases in `IORouter.java`:
- `TOOLS_LIST` — **line 208**
- `TOOLS_CALL` — **line 211**

```java
case TOOLS_LIST -> {
    KeyWordSearch keyWordSearch = new KeyWordSearch();
    ToolsListResult result = ToolsListResultBuilder.builder()
            .addTool(keyWordSearch.name(), keyWordSearch.description(), keyWordSearch.schema())
            .build();
    success(message.id(), new ToolsListResult(result.tools(), LIST_TTL_MILLIS, CacheScope.PUBLIC));
}
case TOOLS_CALL -> {
    ToolCallParams toolCallParams = deserializer.deserializeParams(message, ToolCallParams.class);
    KeyWordSearch keyWordSearch = new KeyWordSearch();
    if (!keyWordSearch.name().equals(toolCallParams.name())) {
        logger.log("[API][SENT] tools/call — unknown tool " + toolCallParams.name()
                   + " (returning -32602)");
        error(message.id(), ErrorCodes.INVALID_PARAMS, "Unknown tool: " + toolCallParams.name());
    } else {
        String directory = directoryArgument(toolCallParams);
        if (directory == null) {
            success(message.id(), ToolCallResultBuilder
                    .builder()
                    .addTextContent("No directory was given. Call key_word_search again with "
                                    + "the directory to search.")
                    .asError()
                    .build());
        } else {
            success(message.id(), keyWordSearch.call(toolCallParams, Set.of(directory)));
        }
    }
}
```

`TOOLS_LIST` uses `ToolsListResultBuilder`, not `AppToolBuilder`. There is no `_meta` URI on the tool yet.

`directoryArgument` is already on the class. It reads `arguments.directory` and treats blank as absent.

### Errors and results

The two failures in `TOOLS_CALL` look alike and are answered differently:

- **An unknown tool name is a protocol error.** The request itself is wrong, so it gets `-32602`, `"Unknown tool: ..."`. Names are matched exactly: `Key_Word_Search` is a different tool, and is unknown.
- **A missing directory is a tool error.** The request is fine and the tool ran, but it could not do its job. That is `isError: true` inside a successful result, with text the model can read and act on: here, by calling again with a directory. Chapter 5 turns that gap into a question.

`prompts/get` and `resources/read` follow the first rule: an unknown prompt, a missing required argument and an unknown resource are all `-32602`.

### A note on the tool's `inputSchema`

`KeyWordSearch` declares a plain object schema with two string properties, which is about as simple as a tool schema gets. A tool with no arguments at all still has one: the schema MUST be a JSON Schema object, never `null`, and for such a tool the specification recommends `{"type":"object","additionalProperties":false}`. The given builders refuse to build a tool without a schema.

Two rules sit behind that simplicity and are worth knowing before you write a more ambitious one.

**The dialect is JSON Schema 2020-12 by default.** A schema with no `$schema` field is interpreted as 2020-12, and every conforming client and server must support that dialect. You may name a different one explicitly — `"$schema": "http://json-schema.org/draft-07/schema#"` — but then both sides have to support it, and an implementation that does not is required to say so rather than guess. This revision deliberately loosened `inputSchema` and `outputSchema` to accept *any* 2020-12 keyword, where earlier revisions permitted only a small subset. `structuredContent` was loosened the same way, to any JSON value.

**That loosening came with two guardrails**, both aimed at the fact that a schema is attacker-controlled input:

- A `$ref` may point at an absolute URI, but an implementation **MUST NOT** automatically dereference one that resolves over the network. Fetching remote refs is allowed only as an explicit opt-in, off by default, and even then wants a host allowlist, timeouts, and size limits. A schema whose external `$ref` cannot be resolved should be rejected, not quietly treated as permissive.
- Composition keywords — `anyOf`, `oneOf`, `allOf`, `if`/`then`/`else`, and `$defs` — are expressive and expensive. Implementations should bound them: a maximum depth, a cap on subschema count, or a per-validation time budget. Without a bound, a deeply nested schema is a denial-of-service vector aimed straight at your validator.

Neither rule bites this workshop's two-string schema. Both bite the moment someone pastes in a generated schema from elsewhere.

### Two more things about `tools/list`

**Return tools in a deterministic order.** With one tool it makes no difference, which is exactly why it is easy to get wrong later. A list that reshuffles between calls defeats the `ttlMs` cache you just attached to it, and because the tool list is rendered into the model's prompt, a reshuffle costs prompt-cache hits at the LLM too. Sort by name, or preserve registration order — just pick one and keep it.

**`icons` is available and unused here.** Tools, prompts, resources, and the server's own identity may all carry an optional `icons` array, each entry an `src` URI (HTTPS or `data:`) plus optional `mimeType`, `sizes`, and `theme`. Hosts render them beside the name. This server advertises none. If you add them, treat icon URIs as untrusted input: HTTPS or `data:` only, no credentials on the fetch, same-origin as the server, and validate the content type by magic bytes rather than trusting the declared `mimeType`.

## Part 3: Implementing the Prompts Capability

### Step 1: Fill the prompt handlers

**Action Required**: paste into the empty cases in `IORouter.java`:
- `PROMPTS_LIST` — **line 202**
- `PROMPTS_GET` — **line 205**

```java
case PROMPTS_LIST -> {
    PromptsListResultBuilder builder = PromptsListResultBuilder
            .builder()
            .withPrompt("search_keyword",
                        "Creates a prompt, to search for a word using the key_word_search tool.")
            .withPromptArgument("keyword", "The word to search for", true);
    PromptsListResult result = builder.build();
    success(message.id(), new PromptsListResult(result.prompts(), result.nextCursor(),
                                                LIST_TTL_MILLIS, CacheScope.PUBLIC));
}
case PROMPTS_GET -> {
    // There is a single prompt, so any other name is unknown. With
    // more than one, the name would be looked up instead.
    PromptsGetParams params = deserializer.deserializeParams(message, PromptsGetParams.class);
    String keyword = params.arguments() == null ? null : params.arguments().get("keyword");
    if (!"search_keyword".equals(params.name())) {
        logger.log("[API][SENT] prompts/get — unknown prompt " + params.name() + " (returning -32602)");
        error(message.id(), ErrorCodes.INVALID_PARAMS, "Unknown prompt: " + params.name());
    } else if (keyword == null || keyword.isBlank()) {
        logger.log("[API][SENT] prompts/get — no keyword argument (returning -32602)");
        error(message.id(), ErrorCodes.INVALID_PARAMS, "Missing required argument: keyword");
    } else {
        PromptsGetResultBuilder builder = PromptsGetResultBuilder
                .builder()
                .withDescription("keyword")
                .addTextMessage("user", KEY_WORD_MESSAGE, params.arguments());
        success(message.id(), builder.build());
    }
}
```

`prompts/get` returns a single `user` message that asks the model to run `key_word_search` for the keyword. The prompt declares `keyword` as required, so a request without one is turned away rather than expanded into a message that searches for nothing.

### Step 2: Fill the completion handler

Completion suggests values for a prompt argument while the user types it.

**Action Required**: paste into the empty `COMPLETION_COMPLETE` case in `IORouter.java` — **line 223**:

```java
case COMPLETION_COMPLETE -> {
    CompletionCompleteParams params = deserializer.deserializeParams(message,
                                                                     CompletionCompleteParams.class);
    CompletionArgument argument = params.argument();
    if (argument == null) {
        logger.log("[API][SENT] completion/complete — no argument to complete (returning -32602)");
        error(message.id(), ErrorCodes.INVALID_PARAMS, "completion/complete requires an argument");
    } else if ("keyword".equals(argument.name())) {
        // Simulating a keyword search completion
        CompletionCompleteBuilder response = CompletionCompleteBuilder.withValue("java");
        response.value("the").value("and").total(3).hasMore(false);
        success(message.id(), response.build());
    } else {
        success(message.id(), new CompletionCompleteBuilder().total(0).hasMore(false).build());
    }
}
```

Both extra branches matter:

- **`argument == null`**: calling `.name()` on it throws out of the router and the client waits forever. A `-32602` is something the client can act on.
- **The `else`**: without it, a completion for any other argument name falls out of the switch with no reply. The prompt has no other argument, so the answer is an empty list.

`total` and `hasMore` travel inside `completion`, beside the values they describe. `total` may be larger than the number of values sent, and `hasMore` says whether more exist beyond them. All three are sent here, so `hasMore` is false.

## You are done when the tests pass

```bash
./gradlew chapterTest -Pchapter=04
```

The run reports 141 tests, and 22 of them fail before you start, all of them in `IORouterTest`. The other 119 test the given code: the builders, the spec records, `KeyWordSearch` and `JavadocResources`. If one of those fails, given code has been changed. Failures worth recognising:

- `everyMethodTheServerAcceptsIsActuallyAnswered` — a case is still empty, so a method the server accepts gets no reply. This test ran in Chapter 3 too, and it runs in every chapter that adds to the switch.
- `aListThatFitsOnOnePageOffersNoCursorToANextOne` — a list offers a `nextCursor` although it has one page.
- `readingAResourceThatIsNotThereIsInvalidParamsNamingTheUri` — a missing page is answered as a result, or the `-32602` lacks `data.uri`.
- `callingAToolThisServerDoesNotHaveIsInvalidParams` or `toolNamesAreMatchedCaseSensitively` — an unknown tool gets a result instead of `-32602`, or the name is compared with `equalsIgnoreCase`.
- `aToolCallWithoutADirectoryIsAToolLevelError` — a missing directory gets `-32602` instead of `isError: true`, or the text never mentions the directory.
- `aPromptWithoutItsRequiredKeywordIsInvalidParams` — `prompts/get` expands the prompt without a keyword.
- `completingTheKeywordArgumentOffersSeveralWords` — `total` or `hasMore` sits beside `completion` instead of inside it.
- `cacheableResultsCarryFreshnessHintsAndUncacheableOnesDoNot` — `tools/list` was sent as `builder.build()`, without `LIST_TTL_MILLIS` and `CacheScope.PUBLIC`.
- A test that expects one response and finds a `-32603` — something you wrote threw. The log file says what.

Then build everything and drive the built server the way a client would. The harness starts the JAR and checks everything up to this chapter over stdio:

```bash
./gradlew clean build
python3 lessons/presentation-3hr/verify/harness.py --chapter 04
```

## Try It in the MCP Inspector

```bash
cd inspector
./run.sh
```

`inspector/config.json` already has `"protocolEra": "auto"` as a sibling of `command`. Do not nest it anywhere else, or the Inspector drops back to its `legacy` default and opens with `initialize`.

Turn on the switch on the `workshop` card to connect. The Inspector fetches every list as soon as discovery completes, so each tab is filled when you open it.

### Resources
- The **Resources** tab lists the Javadoc pages. There is no `ui://` entry.
- Opening a page returns its HTML.

### Tools
- The **Tools** tab shows `key_word_search` with a required `keyword` and an optional `directory`.
- Calling **with** a directory returns occurrence counts.
- Calling **without** a directory is a tool error (`isError: true`) whose text asks for a directory. The Inspector does **not** show a form — that is Chapter 5.

### Prompts
- The **Prompts** tab shows `search_keyword`.
- Typing in its `keyword` field offers `java`, `the` and `and`. Getting the prompt with a keyword returns one `user` message.

## Stretch: Page for real

`resources/list` returns all the Javadoc pages at once. Split it into pages of 20. Read `params.cursor`, return the matching slice, and send a `nextCursor` only while pages remain. Make the cursor opaque, for example an offset encoded in Base64, and answer one you did not issue with `-32602` rather than starting again from the top. Two rules to keep: `cacheScope` must be the same on every page of a list, and an empty string is a valid cursor, so only an absent `nextCursor` means the end.

## Stretch: Complete what was typed

The completion handler offers the same three words whatever the user has typed so far. `argument.value()` holds that partial input: return only the values that start with it, and set `total` to how many matched. A completion may send at most 100 values, so a real one would also cut the list there and set `hasMore`.

## Stretch: Return structured content

A tool may declare an `outputSchema`, and then its results carry `structuredContent` matching it, beside the text `content`. Describe the search result as an object, with the total count and one entry per file, and return it as `structuredContent` alongside the text a model reads today.

## Where this goes next

Every capability the server advertises now answers. A call without a directory still ends in a tool error, though, and leaves the model to guess one. Chapter 05 turns that gap into a question: the server answers `input_required`, the client asks its user, and the call is retried with the answer.

Start it with `./clean-checkout.sh 05-chapter`, or keep your own code: commit it and run `git merge origin/05-chapter`. The README explains both.
