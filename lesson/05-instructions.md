# Chapter 04: Configuring MCP Server and Agent Setup

## Setting Up MCP Server Configuration and Agent Integration

In this lesson, we'll configure the MCP (Model Context Protocol) server to work with Qodo agents. This involves creating two essential configuration files: `mcp.json` for server configuration and `agent.toml` for agent setup. These files enable AI agents to interact with your MCP server and utilize its capabilities.

### Prerequisites: Ensure You Have a Qodo Session

Before we begin configuring the MCP server and agent setup, you need to be logged in to Qodo and verify you have the latest version.

#### Step 1: Check Qodo Version

**Action Required**: Run the following command to check your Qodo version:

```bash
qodo --version
```

This command will display your current Qodo version. Make sure you have the latest version installed to ensure compatibility with all features. If you need to update, the command output will show you the npm install command to run, which will look something like:

**You do not need to update usually** 
```bash
npm install -g @qodo/qodo-cli@latest
```

**only Run the suggested command to update to the latest version, if it says you need too**

#### Step 2: Log in to Qodo

**Action Required**: Run the following command to log in to Qodo:

```bash
qodo login
```

This command will:
- Open a browser window for authentication
- Create a session token for your Qodo account
- Enable access to Qodo's AI models and services

If you're already logged in, the command will confirm your active session. Make sure you have a valid session before proceeding with the configuration steps.

### First Configuration Task: Create the MCP Server Configuration File

The MCP server configuration tells AI tools how to launch and communicate with your server. This is essential for integrating your MCP server with various AI agents and tools.

#### Step 1: Create mcp.json in the Root Directory

**Action Required**: Create a new file called `mcp.json` in the root directory of your project and add the following configuration:
```json
{
  "mcpServers": {
    "workshop": {
      "command": "java",
      "args": [
        "-jar",
        "build/libs/agent-mcp-workshop-0.0.1.jar"
      ],
      "env": {
      }
    }
  }
}
```

## Understanding the MCP Configuration Structure:

Let's break down each field in this configuration:

1. **`mcpServers`**: The top-level object that contains all MCP server configurations. You can define multiple servers here if needed.

2. **`workshop`**: This is the unique identifier for your MCP server. This name will be referenced in your agent configuration to specify which server to use.

3. **`command`**: The executable command to run your server. In our case, it's `"java"` since we're running a Java application.

4. **`args`**: An array of command-line arguments passed to the command. Here we specify:
   - `"-jar"`: Tells Java to run a JAR file
   - `"build/libs/agent-mcp-workshop-0.0.1.jar"`: The path to your compiled MCP server JAR file

5. **`env`**: Environment variables to set when launching the server. Currently empty, but you could add variables like:
   ```json
   "env": {
     "LOG_LEVEL": "DEBUG",
     "API_KEY": "${MY_API_KEY}"
   }
   ```

### Second Configuration Task: Create the Agent Configuration File

Now we'll create an agent configuration that tells Qodo Command how to interact with your MCP server.

#### Step 2: Create agent.toml in the Root Directory

**Action Required**: Create a new file called `agent.toml` in the root directory and add the following configuration:

```toml
version = "1.0"
model = "claude-4-sonnet"

[commands.sum]
description = "This Agent is designed to work with the Tool key_word_search that comes from the workshop."

tools = ["workshop.key_word_search", "filesystem"]

# Optional: Define execution strategy: "plan" for multi-step, "act" for direct execution
execution_strategy = "act"

arguments = [
    {name = "keyword", type = "string", required = true, description = "The keyword to search for using the key_word_search tool."}
]

output_schema = """
{
    "properties": {
        "success": {
            "description": "Whether the task completed successfully",
            "type": "boolean"
        },
        "file_path": {
            "description": "The absolute file path of the file with the highest keyword_count",
            "type": "string"
        },
        "keyword_count": {
            "description": "The keyword_count found in the file",
            "type": "integer"
        },
        "file_summary": {
            "description": "A summary of the file contents",
             "type": "string"
        },
        "explanation": {
            "description": "Explanation of why this file has the highest keyword_count and its relevance to the project",
            "type": "string"
        }
    }
}
"""

exit_expression = "success"


instructions = """
This Agent performs keyword analysis across project files using the key_word_search tool to identify the most relevant file for a given keyword.

OBJECTIVE: Find and analyze the file with the highest occurrence of a specific keyword across all project files.

PROCESS:
Step 1: Execute keyword search
- Use the key_word_search tool to search for {keyword} in all project files
- The tool returns: absolute_file_path, keyword_count for each file
- Store all results for comparison

Step 2: Identify top file
- Compare all keyword_count values from Step 1
- Identify the file with the highest keyword_count
- If multiple files have the same highest count, select the first one found

Step 3: Analyze file contents
- Read the entire contents of the identified file
- Generate a comprehensive summary of the file's purpose and contents
- Analyze the context in which the keyword appears
- Assess the file's relevance to the overall project structure

Step 4: Prepare final output
- Return the required data structure with:
  * Absolute file path of the top file
  * The keyword_count value
  * A detailed summary of the file contents
  * An explanation of why this file contains the most occurrences and its significance

Step 5: Write results to file
- Write the complete output JSON to a file named 'sum_response.json'
- Ensure the file is created in the current working directory
- Format the JSON with proper indentation for readability

ERROR HANDLING:
- If no files contain the keyword, return success=false with appropriate message
- If file reading fails, include error details in the explanation
- If writing to sum_response.json fails, log the error but still return the output
"""
```

## Understanding the Agent Configuration:

Let's examine each field in the agent configuration:

1. **`version`**: Specifies the configuration format version. Currently `"1.0"` is the standard version for agent configurations.

3. **`model`**: Defines which AI model the agent should use. Here we're using `"claude-4-sonnet"`, which is:
   - A powerful language model from Anthropic
   - Optimized for complex reasoning and code understanding
   - Well-suited for development tasks

4. **`tools`**: An array of tools the agent can access. In our configuration:
   - **`"workshop.key_word_search"`**: References a specific tool from our "workshop" MCP server
     - The format is `<server_name>.<tool_name>`
     - This tool will allow the agent to perform keyword searches (we'll implement this in later lessons)
   - **`"filesystem"`**: A built-in tool that gives the agent file system access
     - Allows reading, writing, and navigating files
     - Essential for code generation and modification tasks

## Understanding the Commands.[COMMAND_NAME] :

This example agent demonstrates how to:
- Define a custom command (`sum`)
- Specify agent behavior through instructions
- Configure which tools the agent can access
- Set up argument handling

## How These Configurations Work Together:

The relationship between `mcp.json` and `agent.toml` creates a powerful integration:

1. **Server Definition**: `mcp.json` defines how to launch your MCP server ("workshop")
2. **Tool Access**: `agent.toml` specifies that agents can use tools from the "workshop" server
3. **Runtime Integration**: When Qodo Command runs, it:
   - Reads `mcp.json` to understand available MCP servers
   - Launches the "workshop" server using the specified command
   - Connects the agent to the server
   - Makes the `key_word_search` tool available to the AI model

### Important Configuration Notes:

#### File Locations Matter

Both configuration files must be in the **root directory** of your project:
```
/Users/davidparry/code/scratch/agent-mcp-workshop/
├── mcp.json          # MCP server configuration
├── agent.toml        # Agent configuration
├── build/
│   └── libs/
│       └── agent-mcp-workshop-0.0.1.jar
└── ... other project files
```

#### Tool Naming Convention

When referencing MCP server tools in `agent.toml`, always use the format:
```
<server_name>.<tool_name>
```

For example:
- `workshop.key_word_search` - Correct ✓
- `key_word_search` - Incorrect ✗ (missing server prefix)

#### Environment Variables

If your MCP server needs environment variables (like API keys), you can:

1. **Direct values** in `mcp.json`:
   ```json
   "env": {
     "API_KEY": "your-actual-key"
   }
   ```

2. **Reference system variables**:
   ```json
   "env": {
     "API_KEY": "${SYSTEM_API_KEY}"
   }
   ```

### Testing Your Configuration

After creating all configuration files, you can verify your setup:

1. **Build your MCP server** (if not already done):
   ```bash
   ./gradlew clean build
   ```

2. **Check file locations**:
   ```bash
   ls -la mcp.json agent.toml 
   ```
   You should see both configuration files.

3. **Test your agent configuration**:
   
   **Action Required**: Run the command from the `run_agent.sh` file to test your agent setup:
   ```bash
    ./run_agent.sh
   ```   
    This script looks like this and will only return the agent's output:
   ```bash
   qodo sum --set keyword="mcp" --silent -y
   ```
   
   This command will:
   - Launch the `sum` agent you just configured
   - Set the keyword parameter to "mcp"
   - Run in silent mode (`--silent`) to minimize output
   - Auto-confirm any prompts (`-y`)
   
   If everything is configured correctly, the agent will:
   - Start your MCP server using the configuration in `mcp.json`
   - Execute the agent's instructions with access to the configured tools
   
   **Note**: If you encounter any errors, check that:
   - You're logged in to Qodo (`qodo login`)
   - The JAR file exists at `build/libs/agent-mcp-workshop-0.0.1.jar`
   - All configuration files are in the correct locations

## Congratulations!

You've successfully configured:
- ✅ MCP server configuration (`mcp.json`)
- ✅ Agent configuration (`agent.toml`)
- ✅ Tool access setup
- ✅ Model selection

Your MCP server is now ready to be integrated with AI agents! 