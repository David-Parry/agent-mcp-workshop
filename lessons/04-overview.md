# Overview of Workshop Lesson 4: Implementing Resources, Tools, and Prompts Capabilities

Based on the instruction file and building upon the foundation from lesson 3, here's what will take place in this lesson:

## Core Objective
This lesson completes the MCP server implementation by adding the three main capabilities that were advertised during initialization: Resources, Tools, and Prompts. These features transform the basic message router into a fully functional MCP server that can expose content, provide executable functionality, and offer intelligent user guidance.

## Key Implementation Tasks

### 1. **Resources Capability Implementation**
The Resources feature allows servers to expose various types of content that clients can discover and read:

- **JavadocResources Helper Class**: Students will use a pre-built utility class that:
  - Loads HTML Javadoc files from the JAR's classpath
  - Converts file paths into MCP Resource objects with meaningful names and descriptions
  - Uses JSoup to parse HTML and extract documentation summaries
  - Handles resource reading with proper error management

- **RESOURCES_LIST Handler**: Implements the discovery mechanism:
  - Returns all available Javadoc HTML files from the MCP specification classes
  - Uses `ResourcesListResultBuilder` to construct the response
  - Includes a cursor for potential pagination (though not implemented for simplicity)

- **RESOURCES_READ Handler**: Enables content retrieval:
  - Deserializes `ReadResourceParam` to get the requested resource URI
  - Reads HTML content from the classpath using `JavadocResources.readResourceContent()`
  - Returns the content with appropriate MIME type (`text/html`)
  - Includes comprehensive error handling for missing or invalid resources

### 2. **Tools Capability Implementation**
Tools provide executable functionality that clients can discover and invoke:

- **KeyWordSearch Tool**: A practical example tool that:
  - Searches for keywords across files in specified directories
  - Accepts parameters defined by JSON Schema (keyword and optional root_directory)
  - Returns structured results with file paths and occurrence counts
  - Demonstrates proper tool implementation patterns

- **TOOLS_LIST Handler**: Enables tool discovery:
  - Creates a `KeyWordSearch` instance with configured root directories
  - Uses `ToolsListResultBuilder` to expose the tool's name, description, and schema
  - The schema enables client-side parameter validation and autocomplete

- **TOOLS_CALL Handler**: Executes tool invocations:
  - Deserializes `ToolCallParams` to identify which tool to execute
  - Validates the tool name (case-insensitive for better UX)
  - Delegates to the tool's `call()` method for actual execution
  - Returns clear error messages for unknown tools

### 3. **Prompts Capability Implementation**
Prompts create user-friendly templates that guide clients in using tools effectively:

- **PROMPTS_LIST Handler**: Defines available prompts:
  - Creates a "search_keyword" prompt linked to the KeyWordSearch tool
  - Specifies required arguments with descriptions
  - Uses `PromptsListResultBuilder` to construct the response
  - Enables intelligent client-side autocomplete and suggestions

- **PROMPTS_GET Handler** (already implemented): Generates interactive guidance:
  - Returns contextual messages based on provided arguments
  - Creates a conversation-like flow showing what will happen
  - Adapts responses based on whether arguments are provided
  - Bridges the gap between user intent and tool execution

## Architecture Integration

This lesson demonstrates how the three capabilities work together:

1. **Resources** expose static documentation and content
2. **Tools** provide dynamic functionality and operations  
3. **Prompts** make tools accessible through guided templates

The implementation shows important patterns:
- **Builder Pattern**: All responses use builders for type-safe construction
- **Error Handling**: Every handler includes proper error management
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
- **Prompts**: Viewing available prompts and their argument requirements

The Inspector will no longer show errors when clicking "List Resources", "List Tools", or "List Prompts" - all features are now fully functional.

## What Students Will Achieve

By the end of this lesson, students will have:
- ✅ A complete MCP server with all advertised capabilities implemented
- ✅ Understanding of how to expose static content through Resources
- ✅ Knowledge of creating executable functionality with Tools
- ✅ Experience building user-friendly interfaces with Prompts
- ✅ Insight into how these features enable intelligent client experiences
- ✅ A fully functional MCP server ready for real-world use

This completes the MCP server implementation, transforming the basic message router from lesson 3 into a production-ready server that can serve documentation, execute searches, and provide intelligent user guidance through the Model Context Protocol.