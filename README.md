# Agent MCP Workshop

> **You are on `trunk`.** Verify your environment, then start at `01-chapter`.

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
- [sylabus.md](sylabus.md) — the seven chapters, what each one covers, and how each one is checked

### 3. Start chapter 1

There are two ways through the chapters, and you can switch between them at any point.

**Check out each chapter.** Every chapter branch starts from the reference solutions of the chapters before it:

```bash
./clean-checkout.sh 01-chapter
```

That branch is a **slice** of the finished server: Chapter 1's methods are empty, later chapters are not there yet, and `lessons/` holds this chapter only. When a chapter is green, move on with `./clean-checkout.sh 0(N+1)-chapter` — that discards uncommitted work on purpose, because the next branch already contains the solutions for 1..N.

> **Careful:** `clean-checkout.sh` runs `git clean -fdx`, which deletes every untracked and ignored file — your work in progress, IDE settings, and build output included. Commit or copy anything you want to keep before switching chapters.

**Keep your own code.** Work on a branch of your own, and merge each chapter into it:

```bash
git switch -c my-work
git fetch origin
git merge origin/01-chapter
```

When a chapter is green, commit it (`git add -A && git commit -m "Finish chapter 0N"`) and run `git merge origin/0(N+1)-chapter`. Each chapter branch is the one before it with that chapter solved, so the merge brings in the reference solution and the next chapter's empty methods. It is clean if you wrote the methods as the handouts give them. Where you wrote something differently, git stops with a conflict: keep your version, or take the reference with `git checkout --theirs -- <file>`.

## How the rest of the workshop is organised

The student path is `trunk` → `01-chapter` → … → `07-chapter`. Each chapter branch contains the server **as far as that lesson**, with that chapter's methods emptied out, plus the tests that grade them. Later chapters' code is not there.

You know a chapter is finished when its tests go green. Use **that** chapter's number (`01` on `01-chapter`, `02` on `02-chapter`, and so on):

```bash
./gradlew chapterTest -Pchapter=0N
```

Red is the normal starting state for a chapter — the methods you are about to write are empty.

Stuck? Look at `complete`. The full lesson set lives there too.

## The branches

| Branch | What it is |
| --- | --- |
| `trunk` | This one. Setup and verification only, no server code. |
| `01-chapter` … `07-chapter` | The workshop proper. Each is the one before it with that chapter solved: prior chapters filled, the current chapter hollowed, later chapters absent. |
| `complete` | The finished server, every chapter done. What the instructor demonstrates from, where to look when you are stuck, and where the agent chapter and the bonus start. |

## Feedback

Issues and pull requests are welcome, and please tell your instructor directly during the session — clarity problems and missing prerequisites are the most useful things to hear about.
