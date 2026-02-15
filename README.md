# Agent MCP Workshop - Completed Project

**Instructor Use Only**

This branch contains the **completed workshop project** with all chapters finished. It is intended for instructors to demonstrate what students will have built by the end of the workshop.

## Building the MCP Server

To build the completed MCP server:

```bash
./gradlew clean build
```

This creates the MCP server JAR at `build/libs/agent-mcp-workshop-0.0.1.jar`.

For instructions on how to run and test the server with MCP Inspector, refer to the lesson documentation in prior chapters (e.g., `03-chapter`).

## What This Project Includes

- **MCP Server**: Complete Java implementation of the Model Context Protocol specification
- **JSON-RPC Communication**: Full message handling with serialization/deserialization
- **Keyword Search Tool**: Custom tool that searches for keywords across project files
- **Agent Integration**: Pre-configured agents using the keyword search tool
- **Tool Registration Framework**: Extensible system for adding MCP tools

## For Students

This branch is **not for learning** - it shows only the final result.

To follow the workshop lessons, switch to the appropriate chapter branch:

```bash
git checkout 01-chapter  # Start here
```

Each chapter branch contains lesson documentation in the `lesson/` folder with step-by-step instructions.
