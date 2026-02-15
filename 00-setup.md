# Environment Setup

This document contains the prerequisites and setup instructions for the workshop. **Complete these steps before attending class** to ensure your development environment is ready.

## Prerequisites

### 1. Git

Install the Git command line tool from the [Git official website](https://git-scm.com/downloads).

### 2. Clone the Repository

```bash
git clone git@github.com:David-Parry/agent-mcp-workshop.git
```

### 3. JDK 21

Install JDK 21.0.7 or later. You can download it from [Azul OpenJDK](https://www.azul.com/downloads/?version=java-21-lts&package=jdk#zulu).

### 4. Node.js and NPM

Install Node.js v22 or later with NPM 10.9+. Download from [Node.js official website](https://nodejs.org/).

### 5. MCP Inspector

Install the MCP Inspector tool globally:

```bash
npm install -g @modelcontextprotocol/inspector
```

## Verification

Run the verification script to confirm your environment is properly configured:

```bash
./verification.sh
```

A successful run will show output similar to:

```
MCP Workshop Verification Script
=========================

Step 1: Verifying JDK installation...
------------------------------------
Java version detected: openjdk version "21.0.3" 2024-04-16 LTS

✅ Step 1 PASSED: JDK 21 is installed (meets minimum requirement of 21)

Step 2: Verifying Gradle wrapper...
-----------------------------------
✅ Step 2 PASSED: ./gradlew --version completed successfully!

Step 3: Verifying Gradle project build...
----------------------------------------
✅ Step 3 PASSED: ./gradlew clean completed successfully!

Step 4: Verifying Node.js, NPM, and npx installation...
-------------------------------------------------------
✅ Step 4 PASSED: Node.js (v22+), NPM (10.9+), and npx (10.9+) meet all requirements!

Step 5: Verifying MCP Inspector...
----------------------------------
✅ Step 5 PASSED: MCP Inspector started successfully!

Summary
=======
✅ ALL TESTS PASSED: All development tools are properly configured!
```

**The Summary section should show all tests passed (green).** If any tests fail, resolve the issues before the workshop.

## IDE / Editor

The instructor will use JetBrains IntelliJ IDEA, but you can use any Java-capable editor:
- IntelliJ IDEA
- Eclipse
- VS Code with Java extensions
- Any text editor with Java support

## Troubleshooting

For issues with specific tools, consult their documentation:

- **JDK/Java**: [Azul Support](https://www.azul.com/support/)
- **Node.js/NPM**: [Node.js Documentation](https://nodejs.org/en/docs/guides/)
- **Gradle**: [Gradle Troubleshooting Guide](https://docs.gradle.org/current/userguide/troubleshooting.html)
- **MCP Inspector**: [MCP Inspector GitHub](https://github.com/modelcontextprotocol/inspector)
  - Use `npx @modelcontextprotocol/inspector --help` for command-line options
  - Default port: 6274
- **IDE Issues**: Consult your IDE's documentation
