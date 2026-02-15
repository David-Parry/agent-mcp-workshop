# Agent MCP Workshop

**This is an instructor-led workshop. It is NOT intended for self-paced learning.**

This repository contains hands-on lessons for building Java-based Model Context Protocol (MCP) server implementations. The workshop is designed to be delivered by an instructor, and attempting to work through these materials independently will likely result in confusion as key concepts and instructions are provided during live sessions.

## Workshop Structure

### Branch Organization

Each lesson is organized as a separate branch:
- `01-chapter`, `02-chapter`, `03-chapter`, etc. - Starting points for each lesson
- `complete` - Final solution with all chapters implemented

### How to Navigate Lessons

**Important Pattern**: Each chapter branch contains an intro file (e.g., `01-intro.md`, `02-intro.md`) that you should read before starting that chapter's exercises. These intro files contain the context and instructions specific to that lesson.

For the `trunk` branch (this branch), refer to:
- **[00-introduction.md](00-introduction.md)** - Workshop introduction and learning objectives
- **[00-setup.md](00-setup.md)** - Environment setup instructions (complete before class)

### Pre-Workshop Setup

**Before attending class**, please complete the environment setup:

1. Read and follow all instructions in [00-setup.md](00-setup.md)
2. Run the verification script to confirm your environment is ready
3. Resolve any issues before the workshop begins

This ensures you can start learning immediately when class begins.

### Getting Started (During Class)

1. Ensure pre-workshop setup from [00-setup.md](00-setup.md) is complete
2. When instructed, checkout the first lesson branch:

```bash
git checkout 01-chapter
```

3. Read the intro file in that branch before starting exercises

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
