# Chapter 06: Running Your MCP Server with a Live LLM

> **Bonus appendix — running against a live LLM.**
> Follows on from [bonus-live-llm-overview.md](bonus-live-llm-overview.md).
> Use `lessons/mcp.json`, which already sets `"protocolEra": "modern"`.

## From Inspector to Real Conversations

In this lesson, you'll register your newly written MCP server with your favorite code generation client and see it run with a live LLM—not the Inspector anymore. This demonstrates the complete lifecycle of MCP development: build, test, deploy, and use.

## Prerequisites

Before starting, ensure you have:
- Your MCP server built and tested (from previous lessons)
- The JAR file at `build/libs/agent-mcp-workshop-0.0.1.jar`
- A code generation client that supports MCP (Claude Code, Cursor, Windsurf, Cline, etc.)

## Step 1: Create the MCP Server Configuration

The `mcp.json` file tells your code generation client how to launch your MCP server.

**Action Required**: Create a file called `mcp.json` in the root directory of your project:

```json
{
  "mcpServers": {
    "workshop": {
      "command": "java",
      "args": [
        "-jar",
        "build/libs/agent-mcp-workshop-0.0.1.jar"
      ],
      "env": {}
    }
  }
}
```

### Understanding the Configuration

| Field | Purpose |
|-------|---------|
| `mcpServers` | Container for all MCP server definitions |
| `workshop` | Unique name for your server (you choose this) |
| `command` | The executable to run (`java`) |
| `args` | Command-line arguments to pass |
| `env` | Environment variables (empty for now) |

## Step 2: Register with Your Code Generation Client

Choose your client and follow the appropriate setup:

### Option A: Claude Code

Claude Code automatically discovers `mcp.json` files in your project directory. Simply:

1. Ensure `mcp.json` is in your project root
2. Open Claude Code in your project directory
3. The server will be available automatically

To verify, you can ask Claude Code: "What MCP tools are available?"

### Option B: Cursor

1. Open Cursor Settings
2. Navigate to **MCP Servers** section
3. Add a new server with:
   - **Name**: `workshop`
   - **Command**: `java`
   - **Args**: `-jar /full/path/to/build/libs/agent-mcp-workshop-0.0.1.jar`

### Option C: Windsurf

1. Edit `~/.codeium/windsurf/mcp_config.json`
2. Add your server configuration:
```json
{
  "mcpServers": {
    "workshop": {
      "command": "java",
      "args": ["-jar", "/full/path/to/build/libs/agent-mcp-workshop-0.0.1.jar"]
    }
  }
}
```

### Option D: Other Clients

Most MCP-compatible clients use a similar `mcp.json` format. Consult your client's documentation for the specific configuration location.

## Step 3: Verify Your Server is Running

Once configured, verify that your code generation client can see your MCP server.

**Action Required**: Ask your LLM client:

> "What MCP tools do you have access to?"

Or:

> "Can you list the available tools from the workshop server?"

You should see `key_word_search` listed among the available tools.

## Step 4: Use Your MCP Server in Conversation

Now for the exciting part—using your tool in a real conversation!

**Action Required**: Try these prompts with your code generation client:

### Basic Usage
```
Search for the keyword "mcp" in this project and tell me which file has the most occurrences.
```

### Analysis Request
```
Find all files containing "TODO" and summarize what work remains to be done.
```

### Comparative Query
```
Compare the occurrence of "test" vs "spec" across the codebase. What does this tell us about the testing approach?
```

## Step 5: Observe the Complete Lifecycle

As you interact with the LLM, observe what happens:

1. **Tool Discovery**: The LLM knows about your `key_word_search` tool
2. **Decision Making**: The LLM decides when your tool is useful
3. **Tool Invocation**: Your MCP server receives the request
4. **Data Return**: Your server returns structured results
5. **Interpretation**: The LLM explains what the data means

This is the complete lifecycle you've built!

## Using the Prompt Template

In the `lessons/` directory, you'll find `agent.toml` with a prompt template:

```toml
# Keyword Search Agent Prompt
#
# This is a prompt template for use with your code generation client of choice
# (Claude, GPT, Copilot, etc.). Copy the instructions below and adapt as needed.
```

You can use this prompt to guide more structured interactions with your MCP server. Simply copy the instructions section and paste it into your conversation when you want the LLM to follow a specific workflow.

## Troubleshooting

### Server Not Found

If your client can't find the server:
- Verify the JAR file exists: `ls build/libs/agent-mcp-workshop-0.0.1.jar`
- Check the path in `mcp.json` is correct
- Rebuild if needed: `./gradlew clean build`

### Server Won't Start

If the server fails to launch:
- Test manually: `java -jar build/libs/agent-mcp-workshop-0.0.1.jar`
- Check for Java errors in your client's logs
- Ensure Java is in your PATH

### Tool Not Working

If the tool returns errors:
- Review the MCP Inspector tests from previous lessons
- Check that your tool handles edge cases
- Look at your server's stderr output for errors

## What You've Accomplished

By completing this lesson, you've experienced the full MCP development lifecycle:

| Phase | What You Did |
|-------|--------------|
| **Build** | Created an MCP server with a custom tool |
| **Test** | Verified functionality with MCP Inspector |
| **Configure** | Set up `mcp.json` for your client |
| **Deploy** | Registered with your code generation client |
| **Use** | Had real conversations powered by your tool |

## The Power of MCP

You've now seen how MCP enables:

- **Separation of Concerns**: Your tool does data collection; the LLM does interpretation
- **Reusability**: One MCP server works with any compatible client
- **Natural Interaction**: Users speak naturally; the LLM handles tool orchestration
- **Extensibility**: Add more tools to your server as needed

## Next Steps

Now that you understand the complete lifecycle, you can:

1. **Add more tools** to your MCP server
2. **Build specialized servers** for different domains
3. **Share your servers** with team members
4. **Create complex workflows** combining multiple tools

## Congratulations!

You've successfully:
- Built an MCP server from scratch
- Tested it with the MCP Inspector
- Registered it with a live LLM client
- Used it in real conversations

You now understand the complete MCP development lifecycle and can build your own tools to extend any MCP-compatible AI assistant!
