# Agent MCP Workshop

> **You are on `01-chapter`: The Transport Layer** — reading stdio, publishing lines, and emitting JSON.
>
> The work this chapter covers is waiting for you; earlier chapters are already
> written, and later chapters are not on this branch. Your instructions are in
> [lessons/01-instructions.md](lessons/01-instructions.md),
> and you are finished when `./gradlew chapterTest -Pchapter=01` is green.

Build a Java implementation of the Model Context Protocol from scratch, one chapter at a time, on revision `2026-07-28` — the stateless revision that removed the `initialize` handshake.

This is an **instructor-led** workshop. You can work through it alone, but a lot of the reasoning is discussed live.

## Before class

Follow [00-setup.md](00-setup.md) and run `./verification.sh` from **`trunk`**. Every step must pass before the first session.

## How the workshop is organised

Each chapter is a git branch. The branch contains the server **as far as this lesson**, with this chapter's methods emptied out, plus the tests that grade them. Later chapters' code is not here. Every chapter branch is the one before it with that chapter solved, so you can check the next chapter out or merge it into your own work.

### This chapter (`01-chapter`)

Grade **this** branch with `01` — not the next chapter's number:

```bash
./gradlew chapterTest -Pchapter=01
```

Red means keep going; green means this chapter is done. `./gradlew test` on this branch runs only the chapters you have reached.

### Next chapter (`02-chapter`)

When **this** chapter's tests are green, move on to `02-chapter`, the following lesson. There are two ways.

**Start from the reference.** `02-chapter` already contains the solutions for chapters 1–1, so this discards your work on purpose:

```bash
./clean-checkout.sh 02-chapter
```

**Keep your own code.** Commit it, then merge the next chapter into it:

```bash
git add -A
git commit -m "Finish chapter 01"
git fetch origin
git merge origin/02-chapter
```

The merge brings in this chapter's reference solution and the next chapter's empty methods. If you wrote the methods as the handout gives them, it is clean. Where you wrote something differently, git stops with a conflict in that file: keep your version, or take the reference with `git checkout --theirs -- <file>`, then `git add` the file and run `git commit --no-edit`.

> **Careful:** `clean-checkout.sh` runs `git clean -fdx`, which deletes every untracked and ignored file — your work in progress, IDE settings, and build output included. Commit or copy anything you want to keep before switching chapters.

## Reference branches

- `complete` — the finished server, all chapters done. It is what the instructor demonstrates from, where these chapter branches are generated from, and where to look when you are stuck.
- `trunk` — setup and environment verification only. No server code, no lessons.

## Building

```bash
./gradlew clean build
```

This produces the server JAR at `build/libs/agent-mcp-workshop-0.0.1.jar`.

## Feedback

Issues and pull requests are welcome, and please tell your instructor directly during the session — clarity problems and missing prerequisites are the most useful things to hear about.
