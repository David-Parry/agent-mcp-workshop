# Overview of Workshop Lesson 5: MCP Extensions and Multi Round-Trip Requests

Based on the foundation built in lesson 4, here's what will take place in this lesson:

## Core Objective

This lesson introduces two mechanisms that look separate but are not: the **extensions** system, which is how MCP evolves beyond its core specification, and **Multi Round-Trip Requests**, which is how a server asks for information now that it is forbidden from sending the client a request.

Students will declare an extension under `capabilities.extensions` in the `server/discover` response, then rewrite the elicitation flow so the server asks for a search directory *by answering* rather than by asking.

> Official documentation: [https://modelcontextprotocol.io/extensions/overview](https://modelcontextprotocol.io/extensions/overview)

## The Change That Drives This Lesson

In revision `2026-07-28`, **servers may not send requests to clients.** A modern client silently discards an inbound request; it does not reject it. Three things a server used to ask for were affected:

- `roots/list` — "where should I look?"
- `elicitation/create` — "user, please fill this in"
- `sampling/createMessage` — "run this through your model"

All three still exist, but only in one place: **embedded inside a result**. The server answers `resultType: "input_required"`, listing what it needs; the client resolves it and re-sends the original request, under a brand new id, carrying the answers.

This server embeds only `elicitation/create`. Roots and sampling are both deprecated under the same revision's feature lifecycle policy — the named migrations are to take directories as tool parameters instead of roots, and to integrate an LLM provider API directly instead of sampling — so we model neither capability and build neither request. Both still answer `-32601` if a client sends them inbound, because that rule is about the method being embedded-only, not about our choice to skip them. And clients will go on declaring `"roots"` in their capabilities for the whole deprecation period, exactly as they should; the server simply does not name the field, so it is ignored.

The question travels inside an answer. That single constraint explains every unusual thing about the code in this lesson.

## What Are MCP Extensions?

MCP extensions are **optional additions** to the specification. They define capabilities beyond the core protocol that are:

- **Modular** — independently versioned and maintained in separate repositories
- **Opt-in** — disabled by default in SDKs; both sides must explicitly declare support
- **Specified** — declared in the `extensions` map, keyed by identifier

## `extensions` vs `experimental`

Revision `2026-07-28` fixes `ServerCapabilities` at exactly seven optional slots, and gives extensions their own:

```java
public record ServerCapabilities(
    Map<String, Object> experimental,
    Capability logging,
    Capability completions,
    Capability prompts,
    Capability resources,
    Capability tools,
    Map<String, Object> extensions
) {}
```

Both maps survive, and they mean different things:

- **`extensions`** — support for a *specified* extension, keyed by identifier. This is where anything outside the core protocol now belongs.
- **`experimental`** — genuinely non-standard, in-house capabilities that no specification describes.

Note what is **absent**: the pre-`2026-07-28` top-level `tasks` slot is gone. Tasks became `extensions["io.modelcontextprotocol/tasks"]`, which is Chapter 7's subject.

## The Extension Lifecycle

Before the list of extensions, the thing to know about any one of them: **every extension sits at one of three stages**, and the stage tells you how much you can lean on it (SEP-3392).

| Stage | What can change | What you can expect |
|-------|-----------------|---------------------|
| **Experimental** | Anything, at any time, without notice | Nothing. The design may change or vanish. Pin to a commit. |
| **Beta** | Anything, but breaking changes should be rare, batched, and announced | Real usage is welcome. Breaking changes are documented in a changelog. |
| **Stable** | Only with a new core protocol revision | Behavior is fixed for the revision you negotiated. A published revision is immutable. |

An extension starts Experimental and **must** pass through Beta. From Beta it either settles at Stable or a Standards Track SEP promotes it into the core specification. Each Beta extension declares its intended destination — Stable or Core — and that intent can change while it is in Beta.

Two consequences worth holding on to:

- **The identifier never changes with the stage.** `io.modelcontextprotocol/tasks` is the same string whether the extension is Beta or has landed in core. Breaking changes keep the identifier too; a `-v2` suffix appears only when old and new behavior must coexist within one protocol revision.
- **Promotion to core makes the feature unconditional.** Once a Standards Track SEP folds an extension into revision R, it is core behavior for any peer that negotiates R or later — advertised or not. The extension spec still governs earlier revisions, so a client that speaks both keeps advertising it.

## Official Extensions

Official extensions live in the MCP GitHub organization in repositories prefixed `ext-`, and the directory holding the spec (`specification/beta/`, `specification/stable/`) is what records the stage.

| Extension | Identifier | Stage | What it does |
|-----------|-----------|-------|--------------|
| OAuth Client Credentials | `io.modelcontextprotocol/oauth-client-credentials` | Stable | Machine-to-machine auth for servers calling protected APIs on their own behalf |
| Enterprise-Managed Authorization | *(see ext-auth)* | Stable | Centralized, auditable access control through corporate identity providers |
| MCP Apps | `io.modelcontextprotocol/ui` | **Stable** | Interactive UI inline in conversations — charts, forms, media players. **Chapter 6's subject.** |
| MCP Tasks | `io.modelcontextprotocol/tasks` | **Beta, to Core** | Async execution for long-running work: polling, mid-flight input, durable handles. **Chapter 7's subject.** |
| Skills over MCP | `io.modelcontextprotocol/skills` | Beta | Serve Agent Skills — workflow instructions and supporting files — through Resources |
| Server Cards | *(see ext-server-card)* | Beta | Lets a client discover a server's metadata *before* connecting to it |

The two this workshop implements sit at different stages, and that difference is not cosmetic:

- **MCP Apps is Stable.** The UI contract you write in Chapter 6 is fixed for `2026-07-28`. It will not shift under you until a new protocol revision ships.
- **MCP Tasks is Beta, headed for Core.** Chapter 7's task handles work today, but the shape can still move, and when promotion lands, task support stops being something you declare and becomes something every server at that revision is simply expected to do.

**Server Cards** is the one on the list that operates outside a live session. Everything else here is negotiated *after* you connect; a server card is fetched over plain HTTP *before* a connection exists, so a client can decide whether connecting is worthwhile — a directory listing rather than a conversation.

## Extension Identifiers

Every extension uses a namespaced identifier:

```
{vendor-prefix}/{extension-name}
```

Official MCP extensions use the prefix `io.modelcontextprotocol`:

```
io.modelcontextprotocol/tasks
io.modelcontextprotocol/ui
io.modelcontextprotocol/oauth-client-credentials
```

Your own organization would use its own prefix:

```
com.mycompany/analytics-dashboard
```

## How the Discovery Response Carries Them

`server/discover` replaced the `initialize` handshake. Its result declares the extensions:

```json
{
  "resultType": "complete",
  "supportedVersions": ["2026-07-28"],
  "capabilities": {
    "completions": {},
    "prompts": { "listChanged": false },
    "resources": { "listChanged": false, "subscribe": false },
    "tools": { "listChanged": false },
    "extensions": {
      "com.mycompany/analytics-dashboard": {}
    }
  },
  "instructions": "…",
  "ttlMs": 60000,
  "cacheScope": "public",
  "_meta": { "io.modelcontextprotocol/serverInfo": { "name": "agent-mcp-workshop", "version": "0.0.1" } }
}
```

By the end of the workshop that `extensions` map holds two real entries — `io.modelcontextprotocol/ui` from Chapter 6 and `io.modelcontextprotocol/tasks` from Chapter 7. **This chapter does not declare either of them.** The placeholder above just shows the shape.

Three details worth pausing on:

1. **`supportedVersions` is a list.** A single negotiated version was a property of a session. There are no sessions, so the server advertises everything it speaks and each request states which one it is using.
2. **`serverInfo` is in `_meta`**, not in the result body.
3. **Elicitation is not declared here at all.** It is a *client* capability now, read off each request's `_meta` envelope.

## Key Implementation Tasks

### 1. Declare an extension in `server/discover`

Add a `withExtension()` call to the `DiscoverResultBuilder` chain. Unlike the old builder, ordering does not matter — `build()` folds the accumulated maps in at the end:

```java
builder.withDefaultCapabilities()
       .withExtension(MetaKeys.UI_EXTENSION,
                      Map.of("mimeTypes", List.of(Resource.MIME_TYPE_UI_APP)))
```

### 2. Delete the capability flags

`hasElicitation`, `hasSampling`, and the `roots` set were the session. They must go, along with the negative request-id constants (`ELICITATION_REQUEST_ID = -4000L`) that existed to correlate responses to requests the server had sent.

Capability questions are answered per request instead. Note that only one of the flags becomes a predicate: the server never asks for roots or sampling, so there is no question left for those two to answer, and they become nothing at all.

| Question | Old | New |
|----------|-----|-----|
| Can the client show a form? | `hasElicitation` | `envelope.supportsElicitationForm()` |
| Can the client poll a task? | `hasTasks` | `envelope.supportsTasks()` |

### 3. Add `SearchContinuation`

The server asks its question in one request and reads the answer off another, with nowhere to remember in between what it was searching for or that it had already asked. `SearchContinuation` holds the keyword and the stage, base64-encoded into the opaque `requestState` string that travels to the client and back.

### 4. Rewrite the tool call as a resolver

`handleKeywordSearch` runs in a single pass: read what arrived, decide, answer. There is no "send, wait, resume" — the exchange advances because the *client* calls again.

Its ordering matters:
1. An explicit `directory` argument settles it outright — the **one-hop path**
2. Otherwise read whatever the round trip answered
3. Otherwise ask the user with a form — once, which is what the stage is for
4. Otherwise search the server's own working directory

## Important Learning Points

1. **The server never sends a message that is not a reply.** This is the load-bearing fact of the whole chapter. Every question the server asks, it asks inside an answer.

2. **An embedded request has no `id` and no `jsonrpc`.** It has been de-JSON-RPC'd into data describing a question, identified by its `method` and correlated by the key it sits under in `inputRequests`.

3. **An input response is a bare result, not a JSON-RPC response.** There is no envelope to unwrap — which is why the deserializer no longer has a method for unwrapping one.

4. **`requestState` is opaque bytes.** The protocol guarantees only that the client returns them unchanged. It came from your server, but it travelled through a client, so validate it like any other input.

5. **Always leave a one-hop path.** MRTR is **optional** for clients, and not every client implements it — the Inspector's own task-augmented `tools/call` path rejects `input_required` outright. A tool whose only route to an answer is a round trip is unusable by those callers. Take the input as an optional argument and treat asking as the fallback.

6. **A decline is not an error.** `decline` and `cancel` just mean no directory, which sends the resolver to its fallback. Note the difference between `isError: true` inside a *successful* result and a JSON-RPC error object: the first says the tool could not do its job, the second says the protocol failed.

7. **Same pattern, every extension** — declare the identifier under `capabilities.extensions`, read the client's side off each request's envelope, and exchange extension-specific messages.

## What Students Will Achieve

By the end of this lesson, students will have:

- ✅ Understanding of the extensions system, and how `extensions` differs from `experimental`
- ✅ Knowledge of the namespaced extension identifier format
- ✅ An extension declared in every `server/discover` response
- ✅ No session state left in `IORouter` — capabilities read per request from `_meta`
- ✅ Elicitation implemented as a Multi Round-Trip Request, with no server-initiated messages
- ✅ `SearchContinuation` carrying continuation state through an opaque `requestState`
- ✅ A tool that stays usable by clients that never implement MRTR at all
- ✅ The mental model to implement any future MCP extension using the same pattern
