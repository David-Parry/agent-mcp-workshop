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
Add the following code to your `IORouter.java` file in the switch statement, **after** the INITIALIZE case line 71:

```java
case RESOURCES_LIST -> {
    ResourcesListResultBuilder builder = ResourcesListResultBuilder
            .builder()
            .withResources(JavadocResources.loadAllHtmlResourcesFromFolder("javadoc/com/workshop/mcp/spec"))
            .withNextCursor("pageNext");
    success(message.id(), builder.build());
}
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
    KeyWordSearch keyWordSearch = new KeyWordSearch(this.roots);
    ToolsListResultBuilder builder = ToolsListResultBuilder
            .builder()
            .addTool(keyWordSearch.name(), keyWordSearch.description(), keyWordSearch.schema());
    success(message.id(), builder.build());
}
case TOOLS_CALL -> {
    KeyWordSearch keyWordSearch = new KeyWordSearch(this.roots);
    ToolCallParams toolCallParams = deserializer.deserializeParams(message, ToolCallParams.class);
    if (keyWordSearch.name().equalsIgnoreCase(toolCallParams.name())) {
        success(message.id(), keyWordSearch.call(toolCallParams));
    } else {
        success(message.id(), ToolCallResultBuilder
                .builder()
                .addTextContent("Tool not found: " + toolCallParams.name())
                .asError()
                .build());
    }
}
```

#### What the TOOLS_LIST handler does:

1. **Creates a KeyWordSearch tool instance**: This is our example tool that searches for keywords in files within specified directories

2. **Builds the tools list response**:
   - Uses `ToolsListResultBuilder` to construct the response
   - Adds the tool with its name, description, and JSON schema
   - The schema defines the parameters the tool accepts (keyword and optional root_directory)

3. **Enables client autocomplete**:
   - When the client receives this list, it knows what tools are available
   - The schema tells the client what parameters each tool expects
   - This enables intelligent autocomplete and parameter hints in the client UI

#### What the TOOLS_CALL handler does:

1. **Deserializes the tool call parameters**: Extracts which tool to call and its arguments from the client request

2. **Validates the tool name**:
   - Checks if the requested tool name matches our KeyWordSearch tool
   - This is case-insensitive to be more forgiving

3. **Executes the tool**:
   - If the tool is found, calls the tool's `call()` method with the parameters
   - The KeyWordSearch tool will search for the keyword in the specified paths

4. **Handles unknown tools**:
   - If the client requests a tool we don't have, returns a clear error message
   - This helps with debugging and provides good user experience

## Part 3: Implementing the Prompts Capability

### Understanding Prompts and Their Relationship to Tools

Prompts provide a user-friendly way for clients to interact with tools. They create pre-configured templates that guide users in calling tools with the right parameters. This is what enables the autocomplete and intelligent suggestions in MCP clients.

### Step 1: Add Prompts List Handler to IORouter

Add the following code to your `IORouter.java` file in the switch statement, **above** the PROMPTS_GET case:

```java
case PROMPTS_LIST -> {
    KeyWordSearch keyWordSearch = new KeyWordSearch(this.roots);
    PromptsListResultBuilder builder = PromptsListResultBuilder
            .builder()
            .withPrompt("search_keyword", "Creates a prompt, to search for a word using the " + keyWordSearch.name() + " tool.")
            .withPromptArgument("keyword", "The word to search for", true)
            .withNextCursor("nextPage");
    success(message.id(), builder.build());
}
```

#### What the PROMPTS_LIST handler does:

1. **Creates a KeyWordSearch instance**: This ensures the prompt is linked to our actual tool

2. **Builds a prompt definition**:
   - `withPrompt()`: Defines a prompt with ID "search_keyword" and a description
   - The description mentions the tool name to make the connection clear

3. **Defines prompt arguments**:
   - `withPromptArgument()`: Specifies that this prompt needs a "keyword" argument
   - The `true` parameter indicates this argument is required
   - The description helps users understand what to provide

4. **Sets a cursor**: Similar to resources, indicates potential pagination (not implemented)

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
                if ("keyword" .equalsIgnoreCase(params.argument().name())) {
                    // Simulating a keyword search completion
                    CompletionCompleteBuilder response = CompletionCompleteBuilder.withValue("java");
                    response.value("the").value("and").total(3).hasMore(true);
                    success(message.id(), response.build());
                }
            }

```

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

## Part 4: Implementing the Elicitation Capability

The elicitation capability allows the server to request additional information from the client through interactive prompts. This is useful when you need to gather user input before proceeding with an operation.

### Step 1: Add the `sendElicitationMessage()` method (IORouter line ~236)

This private method creates and sends an elicitation request to the client:

```java
private void sendElicitationMessage() {
    ElicitationCreateParams params = ElicitationBuilder.buildJiraProjectElicitation();
    JsonRpcRequest elicitationRequest = new JsonRpcRequest(JSON_RPC_VERSION, ELICITATION_REQUEST_ID,
                                                           UniqueKeys.ELICITATION_CREATE_MESSAGE.getValue(),
                                                           params);
    io.emit(elicitationRequest);
}
```

**Key components:**
- `ElicitationBuilder.buildJiraProjectElicitation()` - Creates the elicitation parameters with questions
- `ELICITATION_REQUEST_ID` - A unique identifier for tracking this request (defined as `-4000L`)
- `UniqueKeys.ELICITATION_CREATE_MESSAGE` - The method name for elicitation creation
- `io.emit()` - Sends the request to the client

### Step 2: Detect elicitation capability during initialization (IORouter line ~75)

In the `INITIALIZE` case of the `process(JsonRpcRequest message)` method, check if the client supports elicitation:

```java
if(clientCapabilities.elicitation() != null){
    hasElicitation = true;
}
```

This sets the `hasElicitation` flag when the client advertises elicitation support in its capabilities.

### Step 3: Send elicitation after initialization (IORouter line ~195)

In the `NOTIFICATIONS_INITIALIZED` case of the `process(JsonRpcNotification message)` method, trigger the elicitation:

```java
if(hasElicitation) {
    sendElicitationMessage();
}
```

This sends the elicitation request immediately after the client confirms initialization, ensuring the client is ready to receive it.

### Step 4: Handle elicitation responses (IORouter line ~175)

Add a case to handle responses from the client in the `process(JsonRpcRequest message)` method:

```java
case ELICITATION_CREATE_MESSAGE -> {
    // Handle elicitation method calls from client
    logger.log("Received elicitation/create method call from client: " + message);
    // Parse the elicitation response and handle it appropriately
    // For now, just acknowledge the elicitation request
    success(message.id(), new Object());
}
```

**Note:** The elicitation flow is bidirectional - the server can send elicitation requests to the client, and the client can also send elicitation requests to the server. This handler processes incoming elicitation requests from the client.


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
- The tool's schema shows it accepts a "keyword" parameter and optional "root_directory"
- Calling the tool returns a list of files containing the keyword with occurrence counts

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

Your MCP server is now fully functional and ready for real-world use!