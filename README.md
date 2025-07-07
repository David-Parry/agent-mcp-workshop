# Agent MCP Workshop

A Java-based Model Context Protocol (MCP) server implementation that provides keyword search capabilities for AI agents. This project demonstrates how to build custom MCP tools and integrate them with AI agents for file analysis and content discovery.

## Overview

This workshop project implements:
- **MCP Server**: A Java-based server that implements the Model Context Protocol specification
- **Keyword Search Tool**: A custom tool that searches for keywords across project files
- **Agent Integration**: Pre-configured agents that use the keyword search tool for file analysis
- **JSON-RPC Communication**: Full implementation of JSON-RPC for MCP communication

## Features

### MCP Server
- Complete MCP specification implementation in Java
- JSON-RPC message handling with proper serialization/deserialization
- Tool registration and execution framework
- Asynchronous I/O handling for real-time communication
- Comprehensive logging and error handling

### Keyword Search Tool
- Recursive file system traversal
- Text file detection and binary file filtering
- Case-sensitive keyword matching with occurrence counting
- Support for multiple root directories
- Detailed results with file paths and match counts

### Agent Configuration
- **Sum Agent**: Analyzes keyword distribution across files and identifies the most relevant file
- Configurable execution strategies (plan vs. act)
- Structured output schemas with JSON validation
- Automatic result persistence to files

## Project Structure

```
agent-mcp-workshop/
├── src/main/java/com/workshop/mcp/
│   ├── Server.java                 # Main MCP server entry point
│   ├── Router.java                 # JSON-RPC message routing
│   ├── io/                         # I/O handling and logging
│   ├── spec/                       # MCP specification implementation
│   │   ├── *.java                  # MCP data models and types
│   │   └── builders/               # Builder patterns for MCP objects
│   └── tools/                      # Custom MCP tools
│       ├── Tool.java               # Tool interface
│       └── KeyWordSearch.java      # Keyword search implementation
├── agents/
│   └── sum.toml                    # Sum agent configuration
├── build.gradle                    # Gradle build configuration
├── mcp.json                        # MCP server configuration
├── agent.toml                      # Agent runtime configuration
└── run_agent.sh                    # Agent execution script
```

## Getting Started

### Prerequisites
- Java 21 or higher
- Gradle 8.0 or higher
- Qodo Command CLI (for agent execution)

### Building the Project

1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd agent-mcp-workshop
   ```

2. **Build the project**:
   ```bash
   ./gradlew clean build
   ```

### Running the MCP Server

The MCP server can be started directly or through the MCP configuration:

**Direct execution**:
```bash
java -jar build/libs/agent-mcp-workshop-0.0.1.jar
```

**Through MCP configuration** (mcp.json):
```json
{
  "mcpServers": {
    "workshop": {
      "command": "java",
      "args": ["-jar", "build/libs/agent-mcp-workshop-0.0.1.jar"],
      "env": {}
    }
  }
}
```

### Using the Agents

**Run the Sum Agent**:
```bash
./run_agent.sh
```

Or manually to debug:
```bash
qodo sum --set keyword="mcp" -y --debug
```

## MCP Tools

### key_word_search

Searches for keywords across project files and returns detailed results.

**Parameters**:
- `keyword` (string, required): The keyword to search for
- `root_directory` (string, optional): Root directory path (if not configured in server)

**Returns**:
- List of files containing the keyword
- Occurrence count for each file
- Absolute file paths

**Example Usage**:
```json
{
  "method": "tools/call",
  "params": {
    "name": "key_word_search",
    "arguments": {
      "keyword": "mcp",
      "root_directory": "/path/to/project"
    }
  }
}
```

## Agent Configurations

### Sum Agent (agents/sum.toml)

The Sum Agent performs comprehensive keyword analysis:

1. **Keyword Search**: Uses `key_word_search` to find all files containing the keyword
2. **Analysis**: Identifies the file with the highest keyword occurrence
3. **Content Analysis**: Reads and summarizes the most relevant file
4. **Output**: Generates structured results with explanations
5. **Persistence**: Saves results to `sum_response.json`

**Output Schema**:
```json
{
  "success": true,
  "file_path": "string",
  "keyword_count": 200,
  "file_summary": "string",
  "explanation": "string"
}
```

## Development

### Adding New Tools

1. **Implement the Tool interface**:
   ```java
   public class MyTool implements Tool {
       @Override
       public String name() { return "my_tool"; }
       
       @Override
       public String description() { return "Tool description"; }
       
       @Override
       public InputSchema schema() { /* Define input schema */ }
       
       @Override
       public ToolCallResult call(ToolCallParams params) { /* Implementation */ }
   }
   ```

2. **Register the tool** in the Router class

3. **Update the build** and redeploy

### Extending MCP Capabilities

The project includes a complete MCP specification implementation in the `spec` package:
- Message types (requests, responses, notifications)
- Capability definitions
- Resource and prompt management
- Error handling

### Testing

Run the test suite:
```bash
./gradlew test
```

## Configuration Files

### agent.toml
```toml
version = "1.0"
imports = ["agents/sum.toml"]
model = "claude-4-sonnet"
tools = ["workshop.key_word_search", "filesystem"]
```

### mcp.json
```json
{
  "mcpServers": {
    "workshop": {
      "command": "java",
      "args": ["-jar", "build/libs/agent-mcp-workshop-0.0.1.jar"],
      "env": {}
    }
  }
}
```

## Dependencies

- **Gson**: JSON serialization/deserialization
- **JSoup**: HTML parsing (for resource handling)
- **SLF4J**: Logging framework
- **JUnit 5**: Testing framework
- **Mockito**: Mocking for tests

## License

This project is licensed under the terms specified in the LICENSE file.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## Troubleshooting

### Common Issues

**Server won't start**:
- Check Java version (requires Java 21+)
- Verify JAR file exists in `build/libs/`
- Check for port conflicts

**Tool not found**:
- Ensure tool is registered in Router
- Verify MCP server configuration
- Check agent.toml tool references

**Agent execution fails**:
- Verify Qodo Command CLI installation
- Check agent.toml syntax
- Review agent logs for errors

### Logging

Server logs are written to the `logs/` directory. Check these files for detailed error information and debugging output.

## Examples

### Basic Keyword Search
```bash
# Search for "mcp" keyword in current project
qodo sum --set keyword="mcp" --silent -y
```


## Architecture

The project follows a modular architecture:

1. **Server Layer**: Handles MCP protocol communication
2. **Router Layer**: Routes JSON-RPC messages to appropriate handlers
3. **Tool Layer**: Implements specific functionality (keyword search)
4. **Specification Layer**: Provides MCP protocol data models
5. **I/O Layer**: Manages input/output and logging

This design allows for easy extension with new tools and capabilities while maintaining protocol compliance.

## MCP Message Flow Documentation

For a detailed visual representation of how MCP messages flow between clients and servers, see the [MCP Message Flow Diagram](mcp-message-flow.md).

This documentation provides:
- **Sequence diagrams** showing the exact order of MCP protocol messages
- **Initialization flow** including capability negotiation
- **Operation groups** (tools, prompts, resources, completion, sampling)
- **Message dependencies** and requirements
- **Error handling** patterns

The message flow diagram is particularly useful for:
- Understanding the MCP handshake process
- Implementing new MCP clients or servers
- Debugging communication issues
- Learning the protocol's request/response patterns