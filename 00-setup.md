## Setup and Prerequisites

Lets start with the setup for this workshop.

1. Access to a free [Qodo Account](https://app.qodo.ai/signin).
2. Install [Qodo Command](https://docs.qodo.ai/qodo-documentation/qodo-command/getting-started/setup-and-quickstart) on your local machine.
   - For Qodo Command, you will need NPM and Node.js installed on your machine. You can download and install them from [Node.js official website](https://nodejs.org/).
```bash 
   npm install -g @qodo/command 
```
3. JDK 21.0.7 or later installed on your machine. You can download and install it from [OpenJDK](https://www.azul.com/downloads/?version=java-21-lts&package=jdk#zulu).
4. Install [MCP Inspector documentation](https://github.com/modelcontextprotocol/inspector)
 ```bash
   npm install -g @modelcontextprotocol/inspector
 ```

### Verification of development environment
- To verify that your development environment is set up correctly, you can run the following command in your terminal:
```bash 
    ./verification.sh
```
- Valid output should look something like this (Summary needs to be green):
```bash
➜  agent-mcp-workshop git:(trunk) ✗     ./verification.sh
MCP Workshop Verification Script
=========================

Step 1: Verifying JDK installation...
------------------------------------
Java version detected: openjdk version "21.0.3" 2024-04-16 LTS

✅ Step 1 PASSED: JDK 21 is installed (meets minimum requirement of 21)

Step 2: Verifying Gradle wrapper...
-----------------------------------

------------------------------------------------------------
Gradle 8.13
------------------------------------------------------------

Build time:    2025-02-25 09:22:14 UTC
Revision:      073314332697ba45c16c0a0ce1891fa6794179ff

Kotlin:        2.0.21
Groovy:        3.0.22
Ant:           Apache Ant(TM) version 1.10.15 compiled on August 25 2024
Launcher JVM:  21.0.3 (Azul Systems, Inc. 21.0.3+9-LTS)
Daemon JVM:    /Users/davidparry/app/java/zulu21.34.19-ca-jdk21.0.3-macosx_aarch64/zulu-21.jdk/Contents/Home (no JDK specified, using current Java home)
OS:            Mac OS X 14.6.1 aarch64


✅ Step 2 PASSED: ./gradlew --version completed successfully!

Step 3: Verifying Gradle project build...
----------------------------------------

[Incubating] Problems report is available at: file:///Users/davidparry/code/github/mcp-servers/agent-mcp-workshop/build/reports/problems/problems-report.html

Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/8.13/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD SUCCESSFUL in 345ms
1 actionable task: 1 executed

✅ Step 3 PASSED: ./gradlew clean completed successfully!

Step 4: Verifying Node.js, NPM, and npx installation...
-------------------------------------------------------
Node.js version detected: v22.14.0
✓ Node.js version meets minimum requirement (v22)
NPM version detected: 10.9.2
✓ NPM version meets minimum requirement (10.9)
npx version detected: 10.9.2
✓ npx version meets minimum requirement (10.9)

✅ Step 4 PASSED: Node.js (v22+), NPM (10.9+), and npx (10.9+) meet all requirements!

Step 5: Verifying qodo installation...
-------------------------------------
qodo version detected: 0.9.1
✓ qodo version meets minimum requirement (0.9)

✅ Step 5 PASSED: qodo command is installed with version 0.9.1!

Step 6: Verifying MCP Inspector...
----------------------------------
Starting MCP Inspector to verify npx functionality...
(This will start a server - it will be automatically stopped after verification)


✅ Step 6 PASSED: MCP Inspector started successfully!
Server started and listening - npx can run MCP tools correctly.

Summary
=======
✅ ALL TESTS PASSED: All development tools are properly configured!
  - JDK 21+ ✓
  - Gradle wrapper ✓
  - Gradle project build ✓
  - Node.js, NPM, and npx ✓
  - qodo 0.9+ ✓
  - MCP Inspector ✓

```

- To verify that you have a Qodo account and can login, run the following command in your terminal:
```bash
   qodo login  
```
- Valid output should look something like this:
```bash
➜  agent-mcp-workshop git:(trunk) ✗    qodo login  
Starting authentication process...

🔐  Please authenticate in your browser
🌐  If it doesn't open automatically, visit:
https://auth.qodo.ai/......

Waiting for authentication to complete...
✅ Authentication successful! API key saved.
📋 Your API key: sk-{KEY_DATA}
➜  agent-mcp-workshop git:(trunk) ✗
```

### Running 

Once you have verified and signed in to your Qodo account, you need an editor to run the workshop.
The instructor will be using JetBrains IntelliJ IDEA, but you can use any editor of your choice for Java development, such as Eclipse, VS Code, or even a simple text editor.


## Navigation

This workshop follows a modular [chapter structure](lessons) designed for flexible learning ➜ [chapters](lessons):

- **Progressive Build**: Each chapter builds upon concepts from the previous one when followed sequentially
- **Chapter Independence**: Every chapter checkout includes a complete, working implementation of all previous chapters' objectives
- **Skip-Friendly Design**: You can jump to any chapter without completing earlier ones - simply checkout the chapter branch, and you'll have all prerequisite code ready
- **Self-Contained Exercises**: While the learning narrative flows sequentially, each chapter's starting point contains all necessary code from prior chapters

This architecture ensures you can:
1. Follow the workshop from start to finish for the complete learning experience
2. Jump directly to topics of interest without setup overhead
3. Resume from any point if interrupted
4. Use any chapter as a standalone reference implementation

**Important Note**: If you choose to work through the chapters sequentially without checking out each chapter branch, you must carefully follow all instructions in each [chapter's README file](lessons). Each README contains the specific steps needed to transform your code from the previous chapter's state to the current chapter's objectives. Missing steps may result in incomplete implementations.

Each chapter branch represents a clean checkpoint with all previous functionality implemented and tested.


---

#### Troubleshooting

For different types of issues, please refer to the appropriate resources:

- **Qodo Account Issues**: If you have problems with your Qodo account (login, registration, access), please reach out to the Qodo support team.
- **Qodo Command Issues**: For issues with the `qodo` command installation or functionality, please contact Qodo support or refer to the [Qodo Documentation](https://docs.qodo.ai/).
- **MCP Inspector Issues**: For debugging MCP servers or issues with the inspector tool:
  - Check the [MCP Inspector documentation](https://github.com/modelcontextprotocol/inspector)
  - Use `npx @modelcontextprotocol/inspector --help` for command-line options
  - For connection issues, verify the server is running on the expected port (default: 6274)
  - Check the browser console for client-side errors when using the web UI
- **Other Tool Issues**: For problems with individual tools, please consult their respective troubleshooting guides:
  - **JDK/Java**: Refer to the [OpenJDK troubleshooting guide](https://www.azul.com/support/)
  - **Node.js/NPM/npx**: Check the [Node.js help documentation](https://nodejs.org/en/docs/guides/)
  - **Gradle**: See the [Gradle troubleshooting guide](https://docs.gradle.org/current/userguide/troubleshooting.html)
  - **IDE Issues**: Consult your specific IDE's documentation (IntelliJ IDEA, VS Code, Eclipse, etc.)