# Chapter 04: Implementing Resources, Tools, and Prompts Capabilities

## Overview

In this lesson you implement the three core MCP capabilities that Chapter 3 advertised but did not answer:

1. **Resources**: expose Javadoc HTML files that clients can list and read
2. **Tools**: a keyword-search tool that clients can list and call
3. **Prompts**: a `search_keyword` template, plus completion suggestions

Each matching `case` in `IORouter`'s request switch is empty. Copy the helper classes from `lessons/`, then fill every case. There is no MCP App URI yet, and a missing `directory` is a **tool-level error** — Chapter 5 is where the server learns to ask.

## Part 1: Implementing the Resources Capability

### Step 1: Copy the JavadocResources helper class

**Action Required**:
1. Copy `JavadocResources.java` from the `lessons` folder
2. Paste it into `src/main/java/com/workshop/mcp/resources/JavadocResources.java` (the file already exists)

The Javadoc HTML files are already at `src/main/resources/javadoc/`. They are bundled into the JAR, which is why the helper reads them with `getResourceAsStream()`.

### Step 2: Fill the resource handlers

**Action Required**: paste the following into the empty `RESOURCES_LIST`, `RESOURCES_TEMPLATES_LIST`, and `RESOURCES_READ` cases in `IORouter.java`:

```java
case RESOURCES_LIST -> {
    ResourcesListResultBuilder builder = ResourcesListResultBuilder
            .builder()
            .withResources(JavadocResources.loadAllHtmlResourcesFromFolder("javadoc/com/workshop/mcp/spec"))
            .withNextCursor("pageNext");
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
    ReadResourceResultBuilder builder = ReadResourceResultBuilder.builder();
    if (resourceUri == null || resourceUri.isEmpty()) {
        builder
                .addTextContent("", DEFAULT_MIME_TYPE, "Resource URI is null or empty, returning error.")
                .asError();
    } else {
        try {
            String content = JavadocResources.readResourceContent(resourceUri);
            builder.addTextContent(resourceUri, DEFAULT_MIME_TYPE, content);
        } catch (Exception e) {
            logger.log("[API][SENT] resources/read — error reading resource: " + resourceUri);
            builder.addTextContent(resourceUri, DEFAULT_MIME_TYPE, e.getMessage()).asError();
        }
    }
    ReadResourceResult result = builder.build();
    success(message.id(), new ReadResourceResult(result.contents(), result.isError(),
                                                 LIST_TTL_MILLIS, CacheScope.PUBLIC));
}
```

The list is Javadoc only. The `ui://` app resource is Chapter 6.

Notice each `success(...)` restates the built result with `LIST_TTL_MILLIS` and `CacheScope.PUBLIC`. The builders do not know about caching, so `builder.build()` alone would tell the client "never reuse this."

## Part 2: Implementing the Tools Capability

### Step 1: Copy the KeyWordSearch tool

**Action Required**:
1. Copy `KeyWordSearch.java` from the `lessons` folder
2. Paste it into `src/main/java/com/workshop/mcp/tools/KeyWordSearch.java`

The schema has a required `keyword` and a required `directory`. Supplying both is the whole call in this chapter.

### Step 2: Fill the tools handlers

**Action Required**: paste into the empty `TOOLS_LIST` and `TOOLS_CALL` cases:

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
    if (toolCallParams.name() == null
        || !keyWordSearch.name().equalsIgnoreCase(toolCallParams.name())) {
        success(message.id(), ToolCallResultBuilder
                .builder()
                .addTextContent("Tool not found: " + toolCallParams.name())
                .asError()
                .build());
    } else {
        String directory = directoryArgument(toolCallParams);
        if (directory == null) {
            success(message.id(), ToolCallResultBuilder
                    .builder()
                    .addTextContent("The directory argument is required.")
                    .asError()
                    .build());
        } else {
            success(message.id(), keyWordSearch.call(toolCallParams, Set.of(directory)));
        }
    }
}
```

`TOOLS_LIST` uses `ToolsListResultBuilder`, not `AppToolBuilder`. There is no `_meta` URI on the tool yet.

A missing directory is `isError: true` inside a successful JSON-RPC result. The protocol worked; the tool did not. Chapter 5 turns that gap into a question.

`directoryArgument` is already on the class — it reads `arguments.directory` and treats blank as absent.

## Part 3: Implementing the Prompts Capability

**Action Required**: paste into the empty `PROMPTS_LIST`, `PROMPTS_GET`, and `COMPLETION_COMPLETE` cases:

```java
case PROMPTS_LIST -> {
    PromptsListResultBuilder builder = PromptsListResultBuilder
            .builder()
            .withPrompt("search_keyword",
                        "Creates a prompt, to search for a word using the key_word_search tool.")
            .withPromptArgument("keyword", "The word to search for", true)
            .withNextCursor("nextPage");
    PromptsListResult result = builder.build();
    success(message.id(), new PromptsListResult(result.prompts(), result.nextCursor(),
                                                LIST_TTL_MILLIS, CacheScope.PUBLIC));
}
case PROMPTS_GET -> {
    // For the sake of the lesson we are dealing with a single prompt if we had more than one we would
    // need to look it up
    PromptsGetParams params = deserializer.deserializeParams(message, PromptsGetParams.class);
    PromptsGetResultBuilder builder = PromptsGetResultBuilder
            .builder()
            .withDescription("keyword")
            .addTextMessage("user", KEY_WORD_MESSAGE, params.arguments());
    success(message.id(), builder.build());
}
case COMPLETION_COMPLETE -> {
    CompletionCompleteParams params = deserializer.deserializeParams(message,
                                                                     CompletionCompleteParams.class);
    CompletionArgument argument = params.argument();
    if (argument == null) {
        logger.log("[API][SENT] completion/complete — no argument to complete (returning -32602)");
        error(message.id(), ErrorCodes.INVALID_PARAMS, "completion/complete requires an argument");
    } else if ("keyword".equalsIgnoreCase(argument.name())) {
        CompletionCompleteBuilder response = CompletionCompleteBuilder.withValue("java");
        response.value("the").value("and").total(3).hasMore(true);
        success(message.id(), response.build());
    } else {
        success(message.id(), CompletionCompleteBuilder.withValue("java").total(1).hasMore(false).build());
    }
}
```

Both extra `COMPLETION_COMPLETE` branches matter:

- **`argument == null`**: calling `.name()` on it throws out of the router and the client waits forever. A `-32602` is something the client can act on.
- **The `else`**: without it, a completion for any other argument name falls out of the switch with no reply.

## Testing Your Implementation

```bash
./gradlew chapterTest -Pchapter=04
./gradlew clean build
```

Then:

```bash
cd inspector
./run.sh
```

`inspector/config.json` already has `"protocolEra": "modern"` as a sibling of `command`. Do not nest it anywhere else.

### Resources
- **List Resources** shows Javadoc pages. There is no `ui://` entry.
- Opening a page returns its HTML.

### Tools
- **List Tools** shows `key_word_search` with a required `keyword` and a required `directory`.
- Calling **with** a directory returns occurrence counts.
- Calling **without** a directory is a tool error (`isError: true`) whose text mentions `directory`. The Inspector does **not** show a form — that is Chapter 5.

### Prompts
- **List Prompts** shows `search_keyword`.
- Completing the `keyword` argument returns `java` / `the` / `and`.

## Congratulations

You now implement everything Chapter 3 advertised:

- ✅ Resources: list and serve Javadoc
- ✅ Tools: `key_word_search` with a required directory
- ✅ Prompts and completion
- ✅ A missing directory is a tool-level error, not a protocol error

Next chapter adds the Multi Round-Trip Request: the same call without a directory becomes a question instead of an error.
