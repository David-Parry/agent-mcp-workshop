# Agent MCP Workshop

> **You are on `05-chapter`: Extensions and Multi Round-Trip Requests** — asking the client a question when a directory is missing.
>
> The work this chapter covers is waiting for you; earlier chapters are already
> written, and later chapters are not on this branch. Your instructions are in
> [lessons/05-instructions.md](lessons/05-instructions.md),
> and you are finished when `./gradlew chapterTest -Pchapter=05` is green.

Build a Java implementation of the Model Context Protocol from scratch, one chapter at a time, on revision `2026-07-28` — the stateless revision that removed the `initialize` handshake.

This is an **instructor-led** workshop. You can work through it alone, but a lot of the reasoning is discussed live.

## Before class

Follow [00-setup.md](00-setup.md) and run `./verification.sh` from **`trunk`**. Every step must pass before the first session.

## How the workshop is organised

Each chapter is a git branch. The branch contains the server **as far as this lesson**, with this chapter's methods emptied out, plus the tests that grade them. Later chapters' code is not here.

### This chapter (`05-chapter`)

Grade **this** branch with `05` — not the next chapter's number:

```bash
./gradlew chapterTest -Pchapter=05
```

Red means keep going; green means this chapter is done. `./gradlew test` on this branch runs only the chapters you have reached.

### Next chapter (`06-chapter`)

When **this** chapter's tests are green, check out `06-chapter` — that is the following lesson, not this one:

```bash
./clean-checkout.sh 06-chapter
```

That discards uncommitted work on purpose: `06-chapter` already contains the solutions for chapters 1–5.

> **Careful:** `clean-checkout.sh` runs `git clean -fdx`, which deletes every untracked and ignored file — your work in progress, IDE settings, and build output included. Commit or copy anything you want to keep before switching chapters.

## Reference branches

- `complete` — the finished server, all chapters done. It is what the instructor demonstrates from, where these chapter branches are generated from, and where to look when you are stuck.
- `trunk` — setup and environment verification only. No server code, no lessons.

## Building

```bash
./gradlew clean build
```

This produces the server JAR at `build/libs/agent-mcp-workshop-0.0.1.jar`, which is what `inspector/config.json` points at.

## Feedback

Issues and pull requests are welcome, and please tell your instructor directly during the session — clarity problems and missing prerequisites are the most useful things to hear about.
