# Agent MCP Workshop

Build a Java implementation of the Model Context Protocol from scratch, one chapter at a time, on revision `2026-07-28` — the stateless revision that removed the `initialize` handshake.

This is an **instructor-led** workshop. You can work through it alone, but a lot of the reasoning is discussed live.

## You are on `trunk` — start here

This branch is deliberately almost empty. It exists for one job: to prove your machine is ready **before** the workshop starts, so nobody spends the first session installing a JDK.

There is no server code here yet. You write that, chapter by chapter.

### 1. Check your environment

Follow [00-setup.md](00-setup.md), then run:

```bash
./verification.sh
```

Every step must pass before the first session. If something fails, fix it now — the troubleshooting section at the bottom of `00-setup.md` covers the common cases.

### 2. Read what you are about to build

- [00-introduction.md](00-introduction.md) — why the protocol matters and what the worked example is
- [sylabus.md](sylabus.md) — the seven chapters, what each one covers, and roughly how long it takes

### 3. Start chapter 1

```bash
./clean-checkout.sh 01-chapter
```

That switches you to the first chapter, which has the real project: the full source tree with chapter 1's methods emptied out for you to fill in, the tests that grade them, and the complete `lessons/` directory.

> **Careful:** `clean-checkout.sh` runs `git clean -fdx`, which deletes every untracked and ignored file — your work in progress, IDE settings, and build output included. Commit or copy anything you want to keep before switching chapters.

## How the rest of the workshop is organised

Each chapter is its own branch, and each carries the full lesson material so you can read ahead or look back:

```bash
./clean-checkout.sh 03-chapter
```

You know a chapter is finished when its tests go green:

```bash
./gradlew chapterTest -Pchapter=03
```

Red is the normal starting state for a chapter — the methods you are about to write are empty.

## The branches

| Branch | What it is |
| --- | --- |
| `trunk` | This one. Setup and verification only, no server code. |
| `01-chapter` … `07-chapter` | The workshop proper. Each is the full project with that chapter's work removed. |
| `complete` | The finished server, every chapter done. What the instructor demonstrates from, and where to look when you are stuck. |
| `agent-chapter` | Optional bonus: wrapping the tool in agent skills. |

## Feedback

Issues and pull requests are welcome, and please tell your instructor directly during the session — clarity problems and missing prerequisites are the most useful things to hear about.
