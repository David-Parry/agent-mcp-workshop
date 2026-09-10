# Agent MCP Workshop

> **You are on `complete`: the finished server**, every chapter implemented and every test
> passing. This is the reference — read it when you are stuck, and the instructor demonstrates
> from it. It is not where you start.
>
> To do the workshop, verify on `trunk` with `./verification.sh`, then
> `./clean-checkout.sh 01-chapter`. This branch is the reference, not the start.

Build a Java implementation of the Model Context Protocol from scratch, one chapter at a time, on revision `2026-07-28` — the stateless revision that removed the `initialize` handshake.

This is an **instructor-led** workshop. You can work through it alone, but a lot of the reasoning is discussed live.

## Before class

Follow [00-setup.md](00-setup.md) and run `./verification.sh` from **`trunk`**. Every step must pass before the first session.

## How the workshop is organised

The student path is `trunk` → `01-chapter` → … → `07-chapter`. Each chapter branch is a **slice of this tree**: chapters 1..N-1 already filled, chapter N hollowed, later chapters absent. `04t-chapter` is not part of that path.

After verifying on `trunk`:

```bash
./clean-checkout.sh 01-chapter
```

On `complete` you can read every lesson. On a chapter branch, `lessons/` holds **this chapter only**.

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

- `complete` — the finished server, all chapters done. It is what the instructor demonstrates from, where these chapter branches are generated from, and where to look when you are stuck.
- `trunk` — setup and environment verification only. No server code, no lessons.

## Building

```bash
./gradlew clean build
```

This produces the server JAR at `build/libs/agent-mcp-workshop-0.0.1.jar`, which is what `mcp.json` and `inspector/config.json` point at.

## Feedback

Issues and pull requests are welcome, and please tell your instructor directly during the session — clarity problems and missing prerequisites are the most useful things to hear about.
