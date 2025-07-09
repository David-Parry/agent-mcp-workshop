# Agent MCP Workshop

**⚠️ Important: This is NOT a completed project - it's a set of educational lessons!**

This repository contains a series of hands-on lessons for learning to build Java-based Model Context Protocol (MCP) server implementations. Each lesson is organized as a separate branch, with completed solutions available in corresponding branches.

## 📚 Workshop Structure

This workshop is designed as an **instructor-led class** and is **NOT intended for self-study**. While you may attempt to work through the lessons independently, there is no guarantee of success as many instructions and concepts will be discussed during the workshop sessions.

### Branch Organization
- **Lesson branches**: `01-chapter`, `02-chapter`, `03-chapter`, etc. - Starting points for each lesson

### Pre-Workshop Setup
**⚠️ IMPORTANT: After you have registered, please follow the [00-setup.md](00-setup.md) instructions before class to ensure your environment will be ready to go and learning can commence immediately.**

### Getting Started with Lessons
1. Complete the pre-workshop setup from `00-setup.md`
2. Start with the `01-chapter` branch

```bash
# Start first lesson
git checkout 01-chapter

# View completed solution (if needed)
git checkout complete
```

## 💬 Feedback and Support

We value your feedback to improve these workshop lessons!

### How to Provide Feedback
- **Issues**: Report problems or suggestions via [GitHub Issues](../../issues)
- **Discussions**: Join conversations in [GitHub Discussions](../../discussions)
- **Pull Requests**: Suggest improvements to lesson content
- **Workshop Feedback**: Provide direct feedback to your instructor during sessions

### What Feedback Helps
- Clarity of instructions
- Difficulty level of exercises
- Missing prerequisites or setup steps
- Technical issues with code examples
- Suggestions for additional topics

## 🎯 Learning Objectives
=======
  
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

