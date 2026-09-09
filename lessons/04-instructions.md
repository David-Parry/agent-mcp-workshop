# Chapter 04: Implementing Resources, Tools, and Prompts Capabilities

## Overview

In this lesson, we'll implement three important MCP capabilities:
1. **Resources**: Expose Javadoc HTML documentation files that clients can discover and read
2. **Tools**: Provide a keyword search tool that clients can discover and execute
3. **Prompts**: Create user-friendly templates that guide clients in using tools effectively

These capabilities work together to provide a complete MCP server implementation.

## Part 1: Implementing the Resources Capability

### Understanding the Resources Feature

The MCP Resources feature allows servers to expose various types of content (files, documents, data) that clients can discover and read. In our implementation, we'll expose the Javadoc documentation for the MCP specification classes as HTML resources.

### Step 1: Copy the JavadocResources Helper Class

The JavadocResources class provides utilities to load and read HTML files from the JAR's classpath. This approach ensures that resources are accessible even when the application is packaged as a JAR file.

**Action Required**: 
1. Copy the file `JavadocResources.java` from the `lessons` folder
2. Paste it into `src/main/java/com/workshop/mcp/resources/JavadocResources.java` (the file already exists, so you'll be confirming it's the same)

#### What the JavadocResources class does:

This utility class provides several key functions for handling Javadoc HTML resources:

1. **`createResourcesFromClasspath()`**: 
   - Converts HTML file paths into MCP Resource objects
   - Extracts meaningful names and descriptions from the HTML files
   - Sets appropriate MIME types for the resources

2. **`readResourceContent()`**: 
   - Reads HTML files from the JAR's classpath using `getResourceAsStream()`
   - Returns the complete HTML content as a string
   - Handles missing resources with appropriate exceptions

3. **`loadAllHtmlResourcesFromFolder()`**: 
   - Loads all HTML files from a specific folder in the classpath
   - Uses a listing file (`html-files.txt`) that contains all available HTML files
   - This listing file is generated at build time and included in the JAR

4. **`extractJavadocDescription()`**: 
   - Uses JSoup to parse HTML and extract meaningful descriptions
   - Looks for the main description block in Javadoc HTML structure
   - Provides fallback descriptions if parsing fails

**Important Note**: The Javadoc HTML files are already included in the project's resources folder at `src/main/resources/javadoc/`. These were generated from the MCP specification classes and are bundled with the application.

### Step 2: Add Resource Handlers to IORouter

Now we need to add the handlers for RESOURCES_LIST and RESOURCES_READ to our message router.

**Action Required**:
Add the following code to your `IORouter.java` file inside the `switch (uniqueKey)` block — the one that runs *after* the `server/discover` shortcut, the method-registry check, and the `envelopeFor` validation you wrote in Chapter 3:

```java
case RESOURCES_LIST -> {
    ResourcesListResultBuilder builder = ResourcesListResultBuilder
            .builder()
            .withResources(JavadocResources.loadAllHtmlResourcesFromFolder("javadoc/com/workshop/mcp/spec"))
            .addResource(ResourceBuilder.builder()
                    .withUri(KEYWORD_APP_URI)
                    .withName("Keyword Search App")
                    .withDescription("Interactive keyword search results dashboard")
                    .withMimeType(Resource.MIME_TYPE_UI_APP)
                    .build())
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
        // The app is the one resource whose uri is not also its
        // classpath path, but it is read and reported like any
        // other, so the two share a single failure path.
        boolean isApp = KEYWORD_APP_URI.equals(resourceUri);
        String mimeType = isApp ? Resource.MIME_TYPE_UI_APP : DEFAULT_MIME_TYPE;
        try {
            String content = JavadocResources.readResourceContent(
                    isApp ? "lesson/mcp-app.html" : resourceUri);
            builder.addTextContent(resourceUri, mimeType, content);
        } catch (Exception e) {
            logger.log("[API][SENT] resources/read — error reading resource: " + resourceUri);
            builder.addTextContent(resourceUri, mimeType, e.getMessage()).asError();
        }
    }
    ReadResourceResult result = builder.build();
    success(message.id(), new ReadResourceResult(result.contents(), result.isError(),
                                                 LIST_TTL_MILLIS, CacheScope.PUBLIC));
}
```

This refers to one more constant next to `LIST_TTL_MILLIS` from chapter 3:

```java
/** The uri the keyword-search app is published under. */
private static final String KEYWORD_APP_URI = "ui://keyword-search/mcp-app.html";
```

> **A forward reference.** The app resource and its `MIME_TYPE_UI_APP` belong to MCP Apps, which is chapter 6. It is listed here because a resource list that gains entries later would invalidate the cache hint you are about to set, and because leaving it out now means rewriting this handler twice. Treat it as plumbing for now; chapter 6 is where you build the app itself and it starts to mean something.

#### Why every list result is rebuilt before it is sent

Notice the shape of each `success(...)` call: the builder produces a result, and then that result is immediately copied into a new record with two extra values.

```java
ResourcesListResult result = builder.build();
success(message.id(), new ResourcesListResult(result.resources(), result.nextCursor(),
                                              LIST_TTL_MILLIS, CacheScope.PUBLIC));
```

The builders do not know about caching, so `builder.build()` alone yields `ttlMs = 0` and `cacheScope = private` — telling the client "never reuse this, ask me again every time." For a list of 70-odd Javadoc resources that is a lot of pointless traffic.

Wrapping the built result restates it with the cache hints attached:

- **`ttlMs = LIST_TTL_MILLIS`** — the client may reuse this answer for a minute
- **`cacheScope = PUBLIC`** — the answer does not depend on who is asking, so a shared cache may hold it

`PUBLIC` is only safe because these results are derived from static configuration. The moment a result depends on the caller, it has to be `PRIVATE`. Get this wrong and one client sees another client's answer.

#### What the RESOURCES_LIST handler does:

1. **Creates a ResourcesListResultBuilder**: This builder helps construct the response with the list of available resources

2. **Loads Javadoc resources**: Calls `JavadocResources.loadAllHtmlResourcesFromFolder()` with the path to the MCP spec Javadoc folder

3. **Sets a cursor**: The `withNextCursor("pageNext")` indicates there could be more resources available (though in our simple implementation, we return all resources at once - pagination is not implemented to keep complexity down)

4. **Sends the response**: Returns the list of available resources to the client

#### What the RESOURCES_READ handler does:

1. **Deserializes parameters**: Extracts the `ReadResourceParam` which contains the URI of the resource to read

2. **Validates the URI**: Checks if the resource URI is provided and not empty

3. **Reads the resource content**: 
   - If valid, uses `JavadocResources.readResourceContent()` to fetch the HTML content
   - Adds the content to the response with the appropriate MIME type (`text/html`)

4. **Handles errors gracefully**:
   - If reading fails, logs the error and returns an error response with the exception message
   - If URI is invalid, returns an error response indicating the issue

5. **Sends the response**: Returns either the resource content or an error to the client

### Understanding the Resource Flow

Here's how the complete resource flow works:

1. **Client requests resource list** → Server responds with all available Javadoc HTML files
2. **Client selects a resource** → Sends a read request with the resource URI
3. **Server reads the resource** → Returns the HTML content
4. **Client displays the content** → Can render or process the HTML as needed

### Important Design Decisions

1. **No Real Pagination**: While we include a `nextCursor` in the response, we don't actually implement pagination. This keeps the implementation simple while still conforming to the protocol specification.

2. **Classpath Resources**: All resources are loaded from the classpath, making them work seamlessly whether running from IDE or JAR file.

3. **Error Handling**: Both handlers include proper error handling to ensure the client receives meaningful error messages if something goes wrong.

4. **Static Resources Only**: This implementation only serves static HTML files that are bundled with the application. It doesn't access the file system or external resources.

## Part 2: Implementing the Tools Capability

### Step 1: Copy the KeyWordSearch Tool

**Action Required**:
1. Copy the file `KeyWordSearch.java` from the `lessons` folder
2. Paste it into the tools package at `src/main/java/com/workshop/mcp/tools/KeyWordSearch.java`

This KeyWordSearch tool provides functionality to search for keywords across files in specified directories. It demonstrates how to implement a tool that:
- Accepts parameters (keyword and optional root directories)
- Performs file system operations
- Returns structured results
- Handles errors gracefully

### Step 2: Add Tools Handlers to IORouter

After copying the KeyWordSearch.java file, add the following code to your `IORouter.java` file in the switch statement, **after** the RESOURCE_READ case:

```java
case TOOLS_LIST -> {
    KeyWordSearch keyWordSearch = new KeyWordSearch();
    AppTool appTool = AppToolBuilder.builder()
            .withName(keyWordSearch.name())
            .withDescription(keyWordSearch.description())
            .withInputSchema(keyWordSearch.schema())
            .withResourceUri(KEYWORD_APP_URI)
            .build();
    success(message.id(), new AppToolsListResult(List.of(appTool), LIST_TTL_MILLIS, CacheScope.PUBLIC));
}
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

#### What the TOOLS_LIST handler does:

1. **Creates a KeyWordSearch tool instance**: This is our example tool that searches for keywords in files within specified directories

2. **Builds the tools list response**:
   - Uses `AppToolBuilder` to construct the response
   - Adds the tool with its name, description, and JSON schema
   - `withResourceUri` attaches the app `_meta` block that chapter 6 explains. A plain `ToolsListResultBuilder` would produce a valid tool too, just without the UI association — this builder is the same thing with that one extra field
   - The result is wrapped in `AppToolsListResult` with the same `LIST_TTL_MILLIS` / `PUBLIC` hints as the resource list, for the same reason
   - The schema defines two parameters: `keyword`, which is required, and `directory`, which is optional. Supplying `directory` is the **one-hop path** — everything the tool needs arrives with the call. In Ch 5 you will add what happens when it is omitted: the server asks for a directory over a Multi Round-Trip Request, and falls back to its own working directory if the client offers nothing.

3. **Enables client autocomplete**:
   - When the client receives this list, it knows what tools are available
   - The schema tells the client what parameters each tool expects
   - This enables intelligent autocomplete and parameter hints in the client UI

#### What the TOOLS_CALL handler does:

1. **Deserializes the tool call parameters**: Extracts which tool to call and its arguments from the client request

2. **Validates the tool name**:
   - Checks if the requested tool name matches our KeyWordSearch tool
   - This is case-insensitive to be more forgiving

3. **Handles unknown tools first**:
   - If the client requests a tool we don't have, returns a clear error message
   - Note this is `isError: true` inside a *successful* result, not a JSON-RPC error. The protocol worked; the request was just for something that does not exist

4. **Delegates the real work**:
   - `handleKeywordSearch` is where the call actually happens, and it needs the `envelope` because what it is allowed to do depends on this client's capabilities
   - A client that can show a form may be asked for a missing directory; one that cannot has to be answered some other way
   - That branching is chapter 5's subject. For now the method is already present on your branch, so the call compiles and runs — a call **with** a `directory` argument goes straight through it and searches

## Part 3: Implementing the Prompts Capability

### Understanding Prompts and Their Relationship to Tools

Prompts provide a user-friendly way for clients to interact with tools. They create pre-configured templates that guide users in calling tools with the right parameters. This is what enables the autocomplete and intelligent suggestions in MCP clients.

### Step 1: Add Prompts List Handler to IORouter

Add the following code to your `IORouter.java` file in the switch statement, **above** the PROMPTS_GET case:

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
```

#### What the PROMPTS_LIST handler does:

1. **Builds a prompt definition**:
   - `withPrompt()`: Defines a prompt with ID "search_keyword" and a description
   - The description mentions the tool name to make the connection clear

2. **Defines prompt arguments**:
   - `withPromptArgument()`: Specifies that this prompt needs a "keyword" argument
   - The `true` parameter indicates this argument is required
   - The description helps users understand what to provide

3. **Sets a cursor**: Similar to resources, indicates potential pagination (not implemented)

### Step 2: Understanding the PROMPTS_GET Handler (Already Implemented)

The PROMPTS_GET handler is already present in your IORouter.java file. This handler is responsible for returning the actual autocomplete messages that help users construct tool calls. Let's examine what it does:

```java
case PROMPTS_GET -> {
// For the sake of the lesson we are dealing with a single prompt if we had more than one we would
// need to look it up
PromptsGetParams params = deserializer.deserializeParams(message, PromptsGetParams.class);
// this would be the key to look up our prompt
Object name = params.name();

PromptsGetResultBuilder builder = PromptsGetResultBuilder
        .builder()
        .withDescription("keyword")
        .addTextMessage("user", KEY_WORD_MESSAGE, params.arguments());
success(message.id(), builder.build());
        }
```

#### What the PROMPTS_GET handler does:

1. **Deserializes the prompt request**: Extracts which prompt is being requested and its arguments

2. **Validates the prompt name**: Checks if it's the "search_keyword" prompt we defined

3. **Generates autocomplete messages**:
   - If a keyword is provided, creates messages showing how the search will be performed
   - If no keyword is provided, prompts the user to provide one
   - These messages guide the user and show what will happen

4. **Provides contextual responses**:
   - The messages adapt based on whether arguments are provided
   - This creates an interactive experience for the user

### How Prompts Enable Client Autocomplete
The COMPLETION_COMPLETE handler is already present in your IORouter.java file. This handler is responsible for providing autocomplete suggestions based on user input. Let's examine how it works in the context of our keyword search prompt:

```java
case COMPLETION_COMPLETE -> {
    CompletionCompleteParams params = deserializer.deserializeParams(message,
                                                                     CompletionCompleteParams.class);
    CompletionArgument argument = params.argument();
    if (argument == null) {
        logger.log("[API][SENT] completion/complete — no argument to complete (returning -32602)");
        error(message.id(), ErrorCodes.INVALID_PARAMS, "completion/complete requires an argument");
    } else if ("keyword".equalsIgnoreCase(argument.name())) {
        // Simulating a keyword search completion
        CompletionCompleteBuilder response = CompletionCompleteBuilder.withValue("java");
        response.value("the").value("and").total(3).hasMore(true);
        success(message.id(), response.build());
    } else {
        success(message.id(), CompletionCompleteBuilder.withValue("java").total(1).hasMore(false).build());
    }
}
```

Both of the extra branches matter, and both were bugs before they were features:

- **`argument == null`**: `params.argument()` is not guaranteed to be there. Calling `.name()` on it straight away throws a `NullPointerException` out of the router, and because the throw happens before any reply is written, the client sits waiting for a response that never comes until it times out. A `-32602` says the same thing in a way the client can act on.
- **The `else`**: without it, a completion request for any argument other than `keyword` matches no branch and falls out of the `switch` silently — again, no reply. Every path through a request handler has to end in exactly one `success` or one `error`.

This is where the real autocomplete magic happens through the interaction of PROMPTS_LIST and PROMPTS_GET:

1. **Prompt Discovery**: 
   - PROMPTS_LIST tells the client what prompts are available
   - The client knows what arguments each prompt needs

2. **Interactive Autocomplete**:
   - When a user selects a prompt, the client calls PROMPTS_GET
   - PROMPTS_GET returns messages that guide the user
   - These messages can be shown as autocomplete suggestions or chat-like interactions

3. **Guided Tool Usage**:
   - The prompt messages show users exactly what will happen
   - Users see how their input will be used to call tools
   - This bridges the gap between user intent and tool execution

4. **Enhanced User Experience**:
   - Instead of manually typing tool names and parameters
   - Users get interactive guidance through prompts
   - The autocomplete messages make tool usage intuitive

## Testing Your Implementation

Now let's test all three capabilities - Resources, Tools, and Prompts:

### 1. Build the project:
```bash
./gradlew clean build
```

### 2. Start the MCP Inspector:
```bash
cd inspector
./run.sh
```

### 3. Test the Resources feature:
- Click **Connect** to establish connection
- Click **List Resources** - you should now see a list of Javadoc HTML files
- Click on any resource from the list to read its content
- The Inspector will display the HTML content of the selected Javadoc file

### 4. Test the Tools feature:
- Click **List Tools** - you should see the "key_word_search" tool listed
- Click on the tool to see its parameters
- Try calling the tool with a keyword like "class" or "method"
- The tool will search for that keyword in the project files and return the results

### 5. Test the Prompts feature:
- Click **List Prompts** - you should see the "search_keyword" prompt
- Notice how the prompt describes its purpose and required arguments
- The prompt provides a user-friendly way to interact with the keyword search tool

## What you should observe:

### For Resources:
- The "List Resources" button now works without errors
- You see a list of HTML documentation files for various MCP specification classes
- Clicking on a resource shows its HTML content
- The HTML contains the actual Javadoc for classes like `Resource.java`, `Tool.java`, etc.

### For Tools:
- The "List Tools" button shows the key_word_search tool
- The tool's schema shows a required "keyword" parameter and an optional "directory" one
- Calling the tool **with** a directory returns a list of files containing the keyword with occurrence counts
- Calling the tool **without** one does *not* fail. What happens depends on the client, and there are two outcomes worth seeing:
  - The Inspector can display a form, so it is **asked** for a directory — the response comes back as `input_required` rather than a result, and the Inspector shows you a prompt. That is the Multi Round-Trip Request, and chapter 5 is where you implement it.
  - A client that cannot display a form gets searched results anyway, because the server falls back to its own working directory. Since `run.sh` `cd`s into `inspector/` first, that is the `inspector` folder — so a search for `class` finds far less than you might expect. That is the fallback working correctly, not a bug.
- Either way, note what you do **not** get: a failure. Missing an optional argument is not an error condition when the server has two reasonable ways to carry on

### For Prompts:
- The "List Prompts" button shows the search_keyword prompt
- The prompt clearly indicates it needs a "keyword" argument
- The prompt description explains it will use the key_word_search tool

## How This Implementation Demonstrates MCP Concepts:

1. **Dynamic Discovery**: Clients can discover resources, tools, and prompts without hardcoding
2. **Schema-driven Interface**: Tools use JSON Schema to define their parameters
3. **User-friendly Interaction**: Prompts provide guided templates for tool usage
4. **Content Negotiation**: Resources include MIME types so clients know how to handle the content
5. **Stateless Design**: Each request is independent - no session state is maintained
6. **Error Resilience**: All features include proper error handling

## The Relationship Between Tools and Prompts:

- **Tools** define the actual functionality and parameters
- **Prompts** provide user-friendly templates that map to tool calls
- Together, they enable intelligent autocomplete and guided user experiences
- Clients can use prompts to help users construct valid tool calls without knowing the exact syntax

## Congratulations!

You've successfully implemented all three MCP capabilities! Your MCP server now has:
- ✅ **Resources**: List and serve Javadoc documentation
- ✅ **Tools**: Provide executable functionality with schema validation
- ✅ **Prompts**: Offer user-friendly templates for tool interaction
- ✅ **Error Handling**: Graceful error responses for all features
- ✅ **Complete MCP Implementation**: All advertised capabilities are functional

These features work together to create a powerful MCP server:
- **Resources** expose static content and documentation
- **Tools** provide dynamic functionality and operations
- **Prompts** make tools accessible through guided templates

In the next lesson you will extend this server with MCP Extensions — adding the `extensions` capability map and implementing the elicitation flow as a Multi Round-Trip Request.