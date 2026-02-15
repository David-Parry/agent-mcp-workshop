# MCP Server Workshop

## Build Your Own Model Context Protocol Server in Java

Welcome to the MCP Server Workshop! In this hands-on, instructor-led workshop, you'll build a fully functional Model Context Protocol (MCP) server from scratch using Java. By the end, you'll have a working MCP server that integrates with AI coding assistants like Claude Code, Cursor, and Windsurf.

**This is an instructor-led workshop. It is NOT intended for self-paced learning.**

## What is MCP?

The **Model Context Protocol (MCP)** is an open standard that enables AI assistants to connect with external tools, data sources, and services. Think of it as a universal adapter that lets LLMs interact with the world beyond their training data.

```
┌─────────────────┐     MCP Protocol     ┌─────────────────┐
│   AI Assistant  │◄───────────────────►│   Your Server   │
│  (Claude, etc.) │   JSON-RPC over      │   (Java)        │
└─────────────────┘     STDIO            └─────────────────┘
                                                │
                                                ▼
                                         ┌─────────────────┐
                                         │  Your Tools &   │
                                         │  Resources      │
                                         └─────────────────┘
```

## What You'll Build

A Java-based MCP server with:
- **Transport Layer**: Bidirectional STDIO communication
- **Protocol Handling**: JSON-RPC message routing
- **Tools**: Custom `key_word_search` tool for code analysis
- **Resources**: Expose files and data to AI assistants
- **Prompts**: Reusable prompt templates
- **Live Integration**: Connection to real AI coding assistants

## Workshop Syllabus

For the complete course outline, chapter descriptions, and learning objectives, see:

**[syllabus.md](sylabus.md)**

### Quick Overview

| Chapter | Topic | What You'll Build |
|---------|-------|-------------------|
| 1 | Transport Layer | Bidirectional I/O infrastructure |
| 2 | Protocol Review | Understanding JSON-RPC and MCP |
| 3 | Handshake & Routing | Protocol initialization and ping handling |
| 4 | Capabilities | Resources, Tools, Prompts, and Elicitation |
| 5 | Live Integration | Connect to Claude Code, Cursor, or Windsurf |

## Prerequisites

- Java fundamentals (classes, interfaces, records)
- Familiarity with JSON
- Basic command-line experience

### Required Tools

- JDK 21 or higher
- Gradle (wrapper included)
- Node.js (for MCP Inspector)
- Git
- An MCP-compatible AI assistant (Claude Code, Cursor, Windsurf, etc.)

## Pre-Workshop Setup

**Before attending class**, complete the environment setup:

1. Read and follow all instructions in **[00-setup.md](00-setup.md)**
2. Run the verification script to confirm your environment is ready
3. Resolve any issues before the workshop begins

This ensures you can start learning immediately when class begins.

## Branch Organization

Each lesson is organized as a separate branch:

| Branch | Content |
|--------|---------|
| `trunk` | Workshop introduction and setup |
| `01-chapter` | Transport Layer implementation |
| `02-chapter` | JSON-RPC Protocol review |
| `03-chapter` | Handshake and message routing |
| `04-chapter` | Resources, Tools, Prompts, Elicitation |
| `05-chapter` | Live LLM integration |
| `complete` | Final solution with all chapters |

## Getting Started (During Class)

1. Ensure pre-workshop setup from [00-setup.md](00-setup.md) is complete

2. When instructed, checkout the first lesson branch:
   ```bash
   git checkout 01-chapter
   ```

3. Read the intro and overview files in the `lesson/` directory before starting exercises

4. Follow along with the instructor

## Workshop Flow

Each chapter follows this pattern:

1. **Read the Overview** - Understand concepts and architecture
2. **Follow Instructions** - Step-by-step implementation guide
3. **Build & Test** - Verify your implementation with MCP Inspector
4. **Discuss** - Q&A with the instructor

## What You'll Take Away

By completing this workshop, you'll have:

- A production-ready MCP server written in Java
- Deep understanding of the Model Context Protocol specification
- Experience testing MCP servers with the Inspector
- A custom tool that works with any MCP-compatible AI assistant
- Knowledge to build your own MCP servers for any use case

## Feedback and Support

We value your feedback to improve these workshop lessons:

- **Issues**: Report problems via [GitHub Issues](../../issues)
- **Discussions**: Join conversations in [GitHub Discussions](../../discussions)
- **Pull Requests**: Suggest improvements to lesson content
- **Workshop Feedback**: Provide direct feedback to your instructor during sessions

### Helpful Feedback Topics

- Clarity of instructions
- Difficulty level of exercises
- Missing prerequisites or setup steps
- Technical issues with code examples
- Suggestions for additional topics

## Resources

- [MCP Specification](https://spec.modelcontextprotocol.io/)
- [MCP Documentation](https://modelcontextprotocol.io/)
- [JSON-RPC 2.0 Specification](https://www.jsonrpc.org/specification)

---

**Ready to begin?** Start with [00-setup.md](00-setup.md) to prepare your environment!
