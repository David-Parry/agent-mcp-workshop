# Bonus: Running Your MCP Server with a Live LLM

> **Bonus appendix — running against a live LLM.**
> Follows on from [bonus-live-llm-overview.md](bonus-live-llm-overview.md).
> It runs on the finished server, on the `complete` branch.

## From Inspector to Real Conversations

In this lesson, you'll register your newly written MCP server with your favorite code generation client and see it run with a live LLM—not the Inspector anymore. This demonstrates the complete lifecycle of MCP development: build, test, deploy, and use.

## Prerequisites

Before starting, ensure you have:
- The finished server, built. The JAR lands at `build/libs/agent-mcp-workshop-0.0.1.jar`:
  ```bash
  ./clean-checkout.sh complete
  ./gradlew clean build
  ```
- A code generation client that speaks MCP revision `2026-07-28`

This server speaks no other revision. A client that only knows an earlier one opens with `initialize`, and the server refuses it:

```text
{"jsonrpc":"2.0","id":0,"error":{"code":-32601,"message":"Method not found: initialize. This server only supports protocol versions without the initialize handshake: 2026-07-28","data":{"supported":["2026-07-28"]}}}
```

- **Claude Code** speaks `2026-07-28` from version 2.1.292.
- **Cursor** does not yet. The Cursor checked for this workshop bundles MCP SDK 1.25.1, whose newest revision is `2025-11-25`, so its `initialize` gets that `-32601`.
- For any other client, look for `2026-07-28` in its release notes.

## Step 1: The MCP Server Configuration

The project keeps the launch configuration in one file, `lessons/mcp.json`:

```json
{
  "mcpServers": {
    "workshop": {
      "command": "java",
      "args": [
        "-jar",
        "{YOUR_BASE}/build/libs/agent-mcp-workshop-0.0.1.jar"
      ],
      "env": {
      }
    }
  }
}
```

`{YOUR_BASE}` stands for the absolute path of your checkout. Replace it when you copy the entry into a client's own configuration.

### Understanding the Configuration

| Field | Purpose |
|-------|---------|
| `mcpServers` | Container for all MCP server definitions |
| `workshop` | Unique name for your server (you choose this) |
| `command` | The executable to run (`java`) |
| `args` | Command-line arguments to pass |
| `env` | Environment variables (empty for now) |

`inspector/config.json` also sets `protocolEra` and `modernLogLevel`. Those are MCP Inspector settings. Claude Code negotiates the revision itself and never reads them, so `lessons/mcp.json` leaves them out.

## Step 2: Register with Your Code Generation Client

Choose your client and follow the appropriate setup:

### Option A: Claude Code

Claude Code does not read `mcp.json`. It reads `.mcp.json` in the project root, the servers you add with `claude mcp add`, and files passed with `--mcp-config`. `claude_desktop_config.json` belongs to Claude Desktop, not Claude Code.

The project's script passes `lessons/mcp.json` with your path filled in:

```bash
./start_claude_mcp.sh
```

It writes the expanded file to `.mcp-resolved.json`, starts Claude Code from the project root with `--mcp-config .mcp-resolved.json`, and deletes the file when Claude Code exits. Run `/mcp` to check that `workshop` is connected.

### Option B: Cursor

Not yet: Cursor opens with `initialize`, which this server refuses, so the connection fails. The Stretch at the end shows what the server would need to accept such clients. Once Cursor speaks `2026-07-28`, add the server under **Settings → MCP** with:
- **Name**: `workshop`
- **Command**: `java`
- **Args**: `-jar /full/path/to/build/libs/agent-mcp-workshop-0.0.1.jar`

### Option C: Windsurf

If your Windsurf speaks `2026-07-28`:
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

Most MCP-compatible clients take the same `mcpServers` entry. Copy the one from `lessons/mcp.json`, replace `{YOUR_BASE}`, and put it where your client's documentation says.

## Step 3: Verify Your Server is Running

Once configured, verify that your code generation client can see your MCP server.

**Action Required**: Ask your LLM client:

> "What MCP tools do you have access to?"

Or:

> "Can you list the available tools from the workshop server?"

You should see `key_word_search` listed among the available tools.

Behind that answer, the client sent `server/discover` and then asked for the tool list: the methods you built in chapters 03 and 04. The server's log shows both. It writes one file per run to `logs/` in its working directory, which `start_claude_mcp.sh` makes the project root.

## Step 4: Use Your MCP Server in Conversation

Now for the exciting part—using your tool in a real conversation!

**Action Required**: Try these prompts with your code generation client:

### Basic Usage
```
Search for the keyword "mcp" in this project and tell me which file has the most occurrences.
```

### Analysis Request
```
Which files in src/main/java mention "deprecated", and what is being deprecated?
```

### Comparative Query
```
Compare the occurrence of "test" vs "spec" across the codebase. What does this tell us about the testing approach?
```

Each call returns one line per matching file: its absolute path and how often the keyword appears in it. The analysis request gets five such lines, among them:

```text
/path/to/agent-mcp-workshop/src/main/java/com/workshop/mcp/IORouter.java, keyword_count=2
/path/to/agent-mcp-workshop/src/main/java/com/workshop/mcp/spec/AppMeta.java, keyword_count=2
/path/to/agent-mcp-workshop/src/main/java/com/workshop/mcp/spec/InputRequest.java, keyword_count=1
```

The tool's `directory` argument is optional. If the model leaves it out, the server asks for a directory before it searches, and a client that renders forms, as Claude Code does, shows you that request. Give it an absolute path, or decline, and the server searches its own working directory.

## Step 5: Observe the Complete Lifecycle

As you interact with the LLM, observe what happens:

1. **Tool Discovery**: The client calls `server/discover` and lists your tools, so the LLM knows about `key_word_search`
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
- If you wrote the client's configuration by hand, check that the JAR path in it is absolute
- Rebuild if needed: `./gradlew clean build`

### Server Won't Start or Won't Connect

Test the server without a client. `inspector/modern-probe.sh` talks to it over stdio the way a `2026-07-28` client does and prints every answer:

```bash
./inspector/modern-probe.sh
```

The first line is the discovery result, and it includes `"supportedVersions":["2026-07-28"]`. If the probe works but your client does not, look in the server's log for `unsupported method 'initialize'`: the client speaks an earlier revision.

### Tool Not Working

If the tool returns errors:
- Review the MCP Inspector tests from previous lessons
- Check that your tool handles edge cases
- Read the server's log, `logs/agent-mcp-workshop-<PID>-<timestamp>.log` in its working directory. The server writes nothing to stderr.

## Stretch: Accept Older Clients

Cursor, and any client still on `2025-11-25`, opens with `initialize`. Teach the server to answer it the legacy way: reply with an `initialize` result, accept `notifications/initialized`, and serve that client's requests without the per-request `_meta` envelope. Keep `server/discover` for `2026-07-28` clients, so one server speaks both revisions. The `-32601` refusal of `initialize` in `IORouter` is where the legacy path would start.

## What You've Accomplished

By completing this lesson, you've experienced the full MCP development lifecycle:

| Phase | What You Did |
|-------|--------------|
| **Build** | Created an MCP server with a custom tool |
| **Test** | Verified functionality with MCP Inspector |
| **Configure** | Pointed your client at the server with `lessons/mcp.json` |
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
