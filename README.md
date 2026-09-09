# Agent MCP Workshop

Build a Java implementation of the Model Context Protocol from scratch, one chapter at a time, on revision `2026-07-28` — the stateless revision that removed the `initialize` handshake.

This is an **instructor-led** workshop. You can work through it alone, but a lot of the reasoning is discussed live.

## Before class

Follow [00-setup.md](00-setup.md) and run `./verification.sh`. Every step must pass before the first session.

## How the workshop is organised

Each chapter is a git branch. The branch gives you the whole server with that chapter's methods emptied out, plus the tests that grade them. You fill in the bodies.

```bash
./clean-checkout.sh 01-chapter   # start here
```

Read the chapter's instructions in [lessons/](lessons/) — every branch carries the full set, so you can read ahead or look back at any time.

Chapter order is in [lessons/sylabus.md](lessons/sylabus.md):

1. Transport layer — reading stdio, publishing lines, emitting JSON
2. JSON-RPC — classifying requests, notifications, responses and errors
3. Discovery and routing — `server/discover`, the `_meta` envelope, `RequestId`
4. Tools and resources — the keyword-search tool and Javadoc resources
5. Extensions and Multi Round-Trip Requests — asking the client a question
6. MCP Apps — shipping a UI with your tool
7. Tasks — long-running work, polling, and task-level questions

Two optional appendices sit outside the timed chapters:

- [Running against a live LLM](lessons/bonus-live-llm-overview.md) — point a real assistant at your finished server
- [Building an agent plugin](lessons/agent-overview.md) — wrap the tool in skills an agent can drive

### Knowing when you are done

Each chapter has tests that fail until your code is right:

```bash
./gradlew chapterTest -Pchapter=01
```

Red means keep going; green means move on.

> **Careful:** `clean-checkout.sh` runs `git clean -fdx`, which deletes every untracked and ignored file — your work in progress, IDE settings, and build output included. Commit or copy anything you want to keep before switching chapters.

## Reference branches

- `complete` — the finished server, all chapters done. Useful when you are stuck, and what the instructor demonstrates from.
- `trunk` — the canonical source the chapter branches are generated from.

## Building

```bash
./gradlew clean build
```

This produces the server JAR at `build/libs/agent-mcp-workshop-0.0.1.jar`, which is what `mcp.json` and `inspector/config.json` point at.

## Feedback

Issues and pull requests are welcome, and please tell your instructor directly during the session — clarity problems and missing prerequisites are the most useful things to hear about.
