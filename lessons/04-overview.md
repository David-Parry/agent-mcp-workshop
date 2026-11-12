# Overview of Workshop Lesson 4: Implementing Resources, Tools, and Prompts Capabilities

Based on the instruction file and building upon the foundation from lesson 3, here's what will take place in this lesson:

## Core Objective
This lesson completes the MCP server implementation by adding three main capabilities that were advertised in the `server/discover` response: Resources, Tools, and Prompts. These features transform the basic message router into a fully functional MCP server that can expose content, provide executable functionality, and offer intelligent user guidance.

## Key Implementation Tasks

### 1. **Resources Capability Implementation**
The Resources feature allows servers to expose various types of content that clients can discover and read:

- **JavadocResources Helper Class**: Students will use a pre-built utility class that:
  - Loads HTML Javadoc files from the JAR's classpath
  - Converts file paths into MCP Resource objects with meaningful names and descriptions
  - Uses JSoup to parse HTML and extract documentation summaries
  - Reads only `.html` pages, and nothing whose path climbs with `..`, because the path arrives from the client

- **RESOURCES_LIST Handler**: Implements the discovery mechanism:
  - Returns all available Javadoc HTML files from the MCP specification classes
  - Uses `ResourcesListResultBuilder` to construct the response
  - Carries the `ttlMs` / `cacheScope` hints every cacheable result must carry, and no `nextCursor`, because everything fits on one page — see **Caching and Pagination** below

- **RESOURCES_TEMPLATES_LIST Handler**: Answers with an empty list. Clients ask for templates whenever a server declares the resources capability, so the method has to be answered even though this server has none

- **RESOURCES_READ Handler**: Enables content retrieval:
  - Deserializes `ReadResourceParam` to get the requested resource URI
  - Reads HTML content from the classpath using `JavadocResources.readResourceContent()`
  - Returns the content with appropriate MIME type (`text/html`)
  - Answers an empty uri with `-32602`, and one it cannot read with `-32602` and the uri in `data`, never with empty `contents`

### 2. **Tools Capability Implementation**
Tools provide executable functionality that clients can discover and invoke:

- **KeyWordSearch Tool**: A practical example tool that:
  - Searches for the supplied keyword across every file under the directories it is given, skipping `logs/`, `build/`, `.git/` and `.gradle/`. The server's own log records every keyword it is asked for, so a search of its own project would otherwise find itself
  - Accepts two parameters defined by JSON Schema: `keyword`, which is required, and `directory`, which is optional. In this chapter a call without a directory is a tool-level error; Chapter 5 turns that gap into a round-trip question
  - Returns structured results with file paths and occurrence counts
  - Demonstrates proper tool implementation patterns, and why a tool holds no state now that there is no session to hold its context in: the directories are a parameter of `call`, not a field, so a directory resolved for one call is unreachable from the next

- **TOOLS_LIST Handler**: Enables tool discovery:
  - Creates a `KeyWordSearch` instance purely to read its name, description, and schema
  - Uses `ToolsListResultBuilder` to expose the tool's name, description, and schema
  - The schema enables client-side parameter validation and autocomplete

- **TOOLS_CALL Handler**: Executes tool invocations:
  - Deserializes `ToolCallParams` to identify which tool to execute
  - Matches the tool name exactly, because tool names are case-sensitive
  - Delegates to the tool's `call()` method for actual execution
  - Answers an unknown tool with `-32602`, a protocol error, and a missing directory with `isError: true`, a tool error the model can read and act on

### 3. **Prompts Capability Implementation**
Prompts create user-friendly templates that guide clients in using tools effectively:

- **PROMPTS_LIST Handler**: Defines available prompts:
  - Creates a "search_keyword" prompt linked to the KeyWordSearch tool
  - Specifies required arguments with descriptions
  - Uses `PromptsListResultBuilder` to construct the response

- **PROMPTS_GET Handler**: Expands the prompt:
  - Returns one `user` message asking the model to run `key_word_search` for the keyword
  - Answers an unknown prompt name, or a request without the required `keyword`, with `-32602`

- **COMPLETION_COMPLETE Handler**: Suggests values for the `keyword` argument while the user types it:
  - Returns the values with their `total` and `hasMore`, all three inside `completion`
  - Answers a request with no argument to complete with `-32602`

### 4. **Caching and Pagination**

The handlers in this chapter return two things students have not had to think about before: freshness hints on every result, and a cursor on the list results. They interact, and the interaction has rules.

#### Cache hints are mandatory, not optional

On revision `2026-07-28` a server **MUST** include `ttlMs` and `cacheScope` on any `resultType: "complete"` result from exactly six operations — `server/discover`, `tools/list`, `prompts/list`, `resources/list`, `resources/templates/list`, and `resources/read`. Five of those six land in this chapter; only `server/discover` was Chapter 3's. The server uses a uniform `LIST_TTL_MILLIS = 60_000` and `CacheScope.PUBLIC`:

```java
success(message.id(), new ResourcesListResult(result.resources(), result.nextCursor(),
                                              LIST_TTL_MILLIS, CacheScope.PUBLIC));
```

- **`ttlMs`** is a freshness hint, analogous to HTTP `Cache-Control: max-age`. It must be `>= 0`. It is *not* a polling interval — a client checks freshness when it needs the data, not on a timer.
- **`cacheScope`** is `"public"` or `"private"`. `"public"` says the result holds nothing user-specific, so a shared gateway may serve it to **any** caller — including across authorization contexts. These javadoc resources are identical for everyone, so `"public"` is right here. A per-user filtered list would not be, and `cacheScope` is a visibility *hint*, never an access control.

From Chapter 5 a server can also answer `input_required`, asking the client a question before it finishes. Those interim results carry no hints at all, and results produced by the retried call, a Multi Round-Trip Request (MRTR) — anything arriving with `inputResponses` or `requestState` — **MUST NOT** be cached, because the inputs that shaped them are not part of the cache key.

#### Each page caches independently

A paginated list is not one cached object. **Each page is a separately cacheable response**, and the cache key is the method plus the parameters that affect the result — which means **the cursor is part of the key**. Page two is a different cache entry from page one.

Three rules follow:

1. **Each page carries its own `ttlMs`**, and its freshness clock starts when *that page* arrives. A server MAY hand out different TTLs per page — a long one for early pages of a stable list, a short one for the tail.
2. **`cacheScope` MUST be uniform across all pages** of a given list request. If page one is `"private"`, every later page must be `"private"` too. This is the one place where the two fields behave differently, and it is the detail most worth remembering.
3. **An invalid cursor invalidates everything.** If a previously good cursor starts erroring, the client SHOULD throw away *all* cached pages and re-fetch from the beginning. There is no cross-page consistency guarantee otherwise: if the underlying data shifts between fetches, a client can legitimately observe duplicates or gaps. Anyone needing a true snapshot re-fetches from the start, without a cursor.

#### What this server does

Both lists fit on one page, so neither carries a `nextCursor`. That absence is meaningful: `nextCursor` is present **only when more results exist**, and its absence is precisely how a client learns it has reached the end.

An earlier version of this server sent a fixed placeholder cursor on every call and never read an incoming `cursor` back. A client that dutifully followed it was handed page one again, complete with the same cursor, forever. The Inspector hid that, because it shows one page and stops. Two rules come out of it:

- **Emit `nextCursor` only when there is genuinely a next page.** Returning `null` — omitting it — is how you say "that was all."
- **An empty string is a valid cursor.** Clients MUST NOT read `""` as the end of results; only an absent or null `nextCursor` means that. So "is the cursor falsy?" is the wrong test, in any language where the empty string is falsy.

A server that paginates for real reads `params.cursor`, returns the matching slice, and answers an unrecognised cursor with `-32602` (Invalid params) rather than silently restarting. The chapter's first stretch builds exactly that.

## Architecture Integration

This lesson demonstrates how the three capabilities work together:

1. **Resources** expose static documentation and content
2. **Tools** provide dynamic functionality and operations
3. **Prompts** make tools accessible through guided templates

The implementation shows important patterns:
- **Builder Pattern**: All responses use builders for type-safe construction
- **Errors Versus Results**: A request naming something the server does not have gets `-32602`; a tool that ran and failed returns a result with `isError: true`
- **Stateless Design**: Each request is independent with no session state
- **Schema-Driven Interface**: Tools use JSON Schema for parameter definition
- **Content Negotiation**: Resources include MIME types for proper handling

## Important Learning Points

1. **Classpath Resource Loading**: The JavadocResources class demonstrates how to bundle and serve static content from within a JAR file, making the server self-contained.

2. **Tool Parameter Validation**: The JSON Schema integration shows how to create self-documenting APIs where clients can validate parameters before sending requests.

3. **Prompt-Tool Relationship**: Students learn how prompts act as user-friendly wrappers around tools, enabling sophisticated autocomplete and guided experiences in MCP clients.

4. **Complete Feature Implementation**: Unlike lesson 3 where capabilities were only advertised, this lesson implements all advertised features, demonstrating the importance of fulfilling protocol contracts.

## Testing with MCP Inspector

Students will validate their implementation by:
- **Resources**: Listing and reading Javadoc HTML files
- **Tools**: Discovering and executing the keyword search tool
- **Prompts**: Viewing available prompts and their argument requirements, with completion suggestions while typing the keyword

The Inspector's Resources, Tools and Prompts tabs no longer show `Method not found`: all three features now answer.

## What Students Will Achieve

By the end of this lesson, students will have:
- ✅ A complete MCP server with all advertised capabilities implemented
- ✅ Understanding of how to expose static content through Resources
- ✅ Knowledge of creating executable functionality with Tools
- ✅ Experience building user-friendly interfaces with Prompts
- ✅ Insight into how these features enable intelligent client experiences

This completes the core MCP server implementation, transforming the basic message router from lesson 3 into a server that can serve documentation, execute searches, and provide intelligent user guidance through the Model Context Protocol. In lesson 5 you will extend this foundation with the MCP Extensions system.