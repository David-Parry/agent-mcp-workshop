# Chapter 04: Implementing Tools Capabilities

## Overview

In this lesson, we'll implement the Tools capability:
- **Tools**: Provide a keyword search tool that clients can discover and execute

This capability provides the core functionality for an MCP server through tool execution.

## Part 1: Implementing the Tools Capability

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

After copying the KeyWordSearch.java file, add the following code to your `IORouter.java` file in the switch statement, **after** the INITIALIZE case line 71:

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

## Testing Your Implementation

Now let's test the Tools capability:

### 1. Build the project:
```bash
./gradlew clean build
```

### 2. Start the MCP Inspector:
```bash
cd inspector
./run.sh
```

### 3. Test the Tools feature:
- Click **Connect** to establish connection
- Click **List Tools** - you should see the "key_word_search" tool listed
- Click on the tool to see its parameters
- Try calling the tool with a keyword like "class" or "method"
- The tool will search for that keyword in the project files and return the results

## What you should observe:

### For Tools:
- The "List Tools" button shows the key_word_search tool
- The tool's schema shows it accepts a "keyword" parameter and optional "root_directory"
- Calling the tool returns a list of files containing the keyword with occurrence counts

## How This Implementation Demonstrates MCP Concepts:

1. **Dynamic Discovery**: Clients can discover tools without hardcoding
2. **Schema-driven Interface**: Tools use JSON Schema to define their parameters
3. **Stateless Design**: Each request is independent - no session state is maintained
4. **Error Resilience**: Features include proper error handling

## Congratulations!

You've successfully implemented the Tools capability! Your MCP server now has:
- ✅ **Tools**: Provide executable functionality with schema validation
- ✅ **Error Handling**: Graceful error responses for features
- ✅ **Complete Tools Implementation**: Advertised tools are functional

This creates a powerful MCP server focused on tool capabilities:
- **Tools** provide dynamic functionality and operations

Your MCP server is now functional with tools support!