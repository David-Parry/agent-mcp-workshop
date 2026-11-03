# Agent Chapter: Running Your MCP Server with a Live LLM

## What You're Building

In this lesson you will:
1. Connect your MCP server to Claude Code and use it in a live conversation
2. Build the `keyword-audit-plugin` step by step — creating the manifest and copying each skill from the reference, reading and understanding each one as you go
3. Launch in development mode with a single script
4. Run each skill individually, then run the full supervised audit end-to-end

The Stretch steps at the end have you write a skill of your own.

The reference plugin lives at `lessons/keyword-audit-plugin/`. Your working copy will be at `keyword-audit-plugin/` in the project root — the same two-folder pattern used in earlier chapters.

```
agent-mcp-workshop/
├── lessons/keyword-audit-plugin/   ← reference — read from here
│   ├── .claude-plugin/
│   │   └── plugin.json
│   └── skills/
│       ├── keyword-search/SKILL.md
│       ├── keyword-report/SKILL.md
│       ├── java-script-runner/SKILL.md
│       └── audit/SKILL.md
│
└── keyword-audit-plugin/          ← your working copy — build here
    └── (you populate this)
```

---

## Before You Start

This chapter runs on the finished server. Start from the `complete` branch and build it:

```bash
./clean-checkout.sh complete
./gradlew clean build
```

`complete` already contains the finished plugin at `keyword-audit-plugin/`. Remove it, so that you build it yourself in Steps 3 and 4:

```bash
rm -rf keyword-audit-plugin
```

You need:
- **Claude Code 2.1.292 or later.** This server speaks only MCP revision `2026-07-28`, and 2.1.292 is the release that made Claude Code negotiate that revision with local servers on every install. Check with `claude --version`. Leave `MCP_PROTOCOL_NEGOTIATION` unset: set to `legacy`, it makes Claude Code open with `initialize`, which this server refuses.
- **A JDK from 21 on**, for the server and for the `java-script-runner` skill.

---

## Step 1: Read the MCP Server Configuration

Claude Code needs to know how to launch your server. The project keeps that in one file, `lessons/mcp.json`:

```json
{
  "mcpServers": {
    "workshop": {
      "command": "java",
      "args": [
        "-jar",
        "{YOUR_BASE}/build/libs/agent-mcp-workshop-0.0.1.jar"
      ],
      "env": {
      }
    }
  }
}
```

`{YOUR_BASE}` stands for the absolute path of your checkout. You don't edit it: `start_claude_mcp.sh` fills it in each time it starts Claude Code.

Claude Code does not read `mcp.json` on its own. It reads `.mcp.json` in a project's root, the servers you add with `claude mcp add`, and any file passed with `--mcp-config`, which is what the script uses.

---

## Step 2: Use Your MCP Server in a Live Conversation

Before adding the plugin, verify the MCP tool works on its own. Start Claude Code through the project's script:

```bash
./start_claude_mcp.sh
```

The script prints `Plugin     : not built yet` and starts Claude Code with only the MCP server. Run `/mcp` and select `workshop`: it should be connected, with `2026-07-28` as its protocol.

Ask:
```
What MCP tools are available?
```

You should see `key_word_search` listed. Try it directly:

```
Search for the keyword "import" in this project — which file uses it most?
```

```
Which files mention "deprecated", and what is being deprecated?
```

Claude calls `key_word_search`, gets back structured data, and interprets it. The tool answers with one line per matching file:

```text
/path/to/agent-mcp-workshop/src/main/java/com/workshop/mcp/IORouter.java, keyword_count=2
```

The tool's `directory` argument is optional. If Claude leaves it out, the server asks for a directory instead of searching, and Claude Code shows you that request. Give it an absolute path, or decline, and the server searches its own working directory: the project root, where the script started Claude Code.

That's the MCP loop. Now you'll wrap it in a plugin. Leave Claude Code with `/exit`.

---

## Step 3: Create the Plugin Scaffold

Create the plugin directory and manifest at the project root.

```bash
mkdir -p keyword-audit-plugin/.claude-plugin
mkdir -p keyword-audit-plugin/skills
```

Open `lessons/keyword-audit-plugin/.claude-plugin/plugin.json` and read it:

```json
{
  "name": "keyword-audit-plugin",
  "description": "Full codebase keyword audit — orchestrates keyword-search, java-script-runner, and keyword-report to identify keyword debt hotspots and synthesize a prioritized audit report",
  "version": "1.0.0",
  "author": {
    "name": "agent-mcp-workshop"
  },
  "skills": "./skills"
}
```

The manifest requires a `name` field. `skills` points to the directory where Claude Code will look for skill subdirectories. Copy it:

```bash
cp lessons/keyword-audit-plugin/.claude-plugin/plugin.json keyword-audit-plugin/.claude-plugin/plugin.json
```

Your structure so far:

```
keyword-audit-plugin/
├── .claude-plugin/
│   └── plugin.json    ✓
└── skills/            ← empty, you'll populate this next
```

---

## Step 4: Copy and Learn Each Skill

Copy each skill from `lessons/keyword-audit-plugin/skills/` into `keyword-audit-plugin/skills/`. **Read the `SKILL.md` before running it.**

### Skill 1: `keyword-search` — The Data Layer

```bash
cp -r lessons/keyword-audit-plugin/skills/keyword-search keyword-audit-plugin/skills/
```

Open `keyword-audit-plugin/skills/keyword-search/SKILL.md`.

**What it does**: Calls your MCP server's `key_word_search` tool with a keyword and returns a sorted table of files ranked by occurrence count.

**Read `## Instructions for Claude`** — the skill:
1. Extracts the keyword argument from the invocation
2. Calls `key_word_search(keyword: "<keyword>", directory: "<absolute path>")` via the workshop MCP tool, passing the project's `src/main/java`
3. Sorts results descending by count
4. Formats and displays the result table, highlighting the top 3

Every skill in this plugin passes `directory`. Without it, the server would stop to ask you for a directory, once for every keyword the audit searches.

This is the **data layer** — deterministic search results with no interpretation.

---

### Skill 2: `keyword-report` — The Analysis Layer

```bash
cp -r lessons/keyword-audit-plugin/skills/keyword-report keyword-audit-plugin/skills/
```

Open `keyword-audit-plugin/skills/keyword-report/SKILL.md`.

**What it does**: Runs `key_word_search` for a keyword, groups files by Java package, calculates concentration percentages, and produces a shareable markdown report with a concrete recommendation.

**Read `## Instructions for Claude`** — the skill:
1. Calls `key_word_search` for the keyword, with the same `directory`
2. Groups file paths by the package segment after `java/` in the path
3. Calculates what percentage of all hits are concentrated in the top file
4. Generates sections: Summary, Distribution by Package, Files Requiring Attention, Recommendation

This is the **analysis layer** — the same data as `keyword-search`, but structured, grouped, and interpreted.

---

### Skill 3: `java-script-runner` — The Execution Layer

```bash
cp -r lessons/keyword-audit-plugin/skills/java-script-runner keyword-audit-plugin/skills/
```

Open `keyword-audit-plugin/skills/java-script-runner/SKILL.md`.

**What it does**: Runs `.java` files directly through the JVM without a build step. Its main feature is a `FileAnalyzer` whose full source is **embedded directly inside the `SKILL.md`**. When invoked with `--analyze`, Claude writes that source to a temp file, executes it, and cleans up.

**Read `## Built-in FileAnalyzer Source`** — the embedded Java:
- Reads a target file's lines
- Counts total / code / comment / blank lines
- Reports keyword frequencies for: `TODO`, `FIXME`, `deprecated`, `import`, `class`, counted the way `key_word_search` counts them
- Lists the top 5 longest lines

**One JDK feature makes this work without `javac`**: JEP 330 (Java 11+) lets `java FileAnalyzer.java` compile and run a single source file in one step. FileAnalyzer is an ordinary class with a `public static void main`, so that command runs it on every JDK from 21 on.

Newer JDKs can drop the class as well. A *compact source file* holds a bare `void main()`. It is final in JDK 25 (JEP 512), but before that it was a preview feature (JEP 445 in JDK 21, JEP 463 in JDK 22) that needs `--enable-preview --source <N>` and then runs only on JDK N. The skill knows how to launch both kinds when you hand it a script of your own.

**Read `## Instructions for Claude`** — when `--analyze <path>` is given, Claude runs one Bash command that:
1. Writes the embedded source to `/tmp/FileAnalyzer_$$.java` via a heredoc
2. Runs `java /tmp/FileAnalyzer_$$.java <target-path>`
3. Deletes the temp file

It has to be one command: each Bash call starts a new shell, where `$$` would name a different file. Claude then displays the full output.

This is the **execution layer** — a JVM process that returns a structural profile of a source file. No external `.java` file exists anywhere on disk.

---

### Skill 4: `audit` — The Orchestrator

```bash
cp -r lessons/keyword-audit-plugin/skills/audit keyword-audit-plugin/skills/
```

Open `keyword-audit-plugin/skills/audit/SKILL.md`.

**What it does**: Ties the other three skills together into a supervised, multi-step workflow with a human approval checkpoint before final synthesis.

**Read `## Supervised Agent Workflow`**:

```
STEP 1: Search        — apply keyword-search logic for each keyword via MCP
STEP 2: Identify      — find the file with the highest single-keyword count (hotspot)
STEP 3: Analyze       — apply java-script-runner --analyze on the hotspot
STEP 4: Read + Reason — Read the hotspot source; explain WHY it has the most hits
STEP 5: Report        — apply keyword-report logic for each keyword
[CHECKPOINT]          — show results + reasoning; wait for yes/no
STEP 6: Synthesis     — prioritized audit report
```

**How skill-to-skill referencing works**: skills cannot call each other like functions. When the `audit` SKILL.md references `/keyword-audit-plugin:keyword-search`, Claude recognizes it because all installed skills from the plugin are loaded into its context at session start. Claude reads that skill's definition and applies its logic inline. This is why all four skills must be present in the plugin before running the audit.

**The checkpoint** is what makes this a **supervised agent**: it automates five steps of real work but stops and asks for your approval before generating the final output. You see all evidence before committing.

Your completed plugin structure:

```
keyword-audit-plugin/
├── .claude-plugin/
│   └── plugin.json
└── skills/
    ├── keyword-search/
    │   └── SKILL.md
    ├── keyword-report/
    │   └── SKILL.md
    ├── java-script-runner/
    │   └── SKILL.md
    └── audit/
        └── SKILL.md
```

Check your copy against the one `complete` ships. This prints nothing when they match:

```bash
git status --short keyword-audit-plugin
```

---

## Step 5: Launch in Development Mode

Start Claude Code through the same script as in Step 2:

```bash
./start_claude_mcp.sh
```

The script:
1. Resolves the project root to an absolute path
2. Builds the JAR if it's missing
3. Expands `lessons/mcp.json` into `.mcp-resolved.json` with your real path substituted
4. Adds the plugin, now that `keyword-audit-plugin/.claude-plugin/plugin.json` exists, and prints its path after `Plugin     :`
5. Starts Claude Code from the project root with both MCP server and plugin:

```bash
claude --mcp-config .mcp-resolved.json --strict-mcp-config \
       --plugin-dir ./keyword-audit-plugin
```

> **`--plugin-dir`** loads the plugin from its source directory without any global install. Changes to `SKILL.md` files take effect on the next session start. This is **development mode** — the same plugin directory you just built.

> **`.mcp-resolved.json`** is a generated file the script writes on startup and deletes on exit. It's the expanded form of `lessons/mcp.json` with the absolute JAR path filled in. You never edit it directly.

Verify the plugin loaded:
```
/plugin
```
Open the **Installed** tab — you should see `keyword-audit-plugin`.

---

## Step 6: Run Each Skill

With the plugin loaded, test each skill individually before running the full audit. The finished server has no `TODO` or `FIXME` left in `src/main/java`, so these examples search for `deprecated`. Claude lays out the results in its own words; the files and counts come from the tool and should match.

### Run keyword-search

```
/keyword-audit-plugin:keyword-search deprecated
```

Expected:
```
Keyword: deprecated
─────────────────────────────────────────
   2  src/main/java/com/workshop/mcp/IORouter.java
   2  src/main/java/com/workshop/mcp/spec/AppMeta.java
   1  src/main/java/com/workshop/mcp/spec/ClientCapabilities.java
   1  src/main/java/com/workshop/mcp/spec/InputRequest.java
   1  src/main/java/com/workshop/mcp/spec/builders/AppToolBuilder.java
─────────────────────────────────────────
Total: 7 occurrences across 5 files
```

### Run keyword-report

```
/keyword-audit-plugin:keyword-report deprecated
```

You'll get a markdown report grouping the same seven hits by Java package: four in `com.workshop.mcp.spec`, two in `com.workshop.mcp` and one in `com.workshop.mcp.spec.builders`. The top file holds 2 of the 7, about 29%, and the report ends with a recommendation.

### Run java-script-runner

```
/keyword-audit-plugin:java-script-runner --analyze src/main/java/com/workshop/mcp/IORouter.java
```

Claude writes the embedded `FileAnalyzer` to a temp file, runs it, and displays:
```
════════════════════════════════════════════════════════════
  File Analysis: IORouter.java
  Path:          /path/to/agent-mcp-workshop/src/main/java/com/workshop/mcp/IORouter.java
════════════════════════════════════════════════════════════
  Total lines:         918
  Code lines:          533
  Comment lines:       329
  Blank lines:         56

  Keyword Frequency:
    TODO:          0 occurrences
    FIXME:         0 occurrences
    deprecated:    2 occurrences
    import:        23 occurrences
    class:         14 occurrences

  Top 5 Longest Lines:
    [121 chars]  logger.log("[API][SENT] " + message.method() + " — the tasks extensio...
    [120 chars]  .withResources(JavadocResources.loadAllHtmlResourcesFromFolder("javad...
    [119 chars]  logger.log("[API][SENT] tasks/update — unknown taskId=" + params.task...
    [119 chars]  logger.log("[API][SENT] tasks/cancel — unknown taskId=" + params.task...
    [119 chars]  NotificationCancelledParams.class);
════════════════════════════════════════════════════════════
```

### Run the full audit

```
/keyword-audit-plugin:audit
```

Or with custom keywords:
```
/keyword-audit-plugin:audit keywords=[TODO,FIXME,HACK,deprecated]
```

**Steps 1–2** — searches run for the default keywords, `TODO`, `FIXME` and `deprecated`, and the hotspot is identified:
```
TODO          0 occurrences
FIXME         0 occurrences
deprecated    7 occurrences across 5 files

Hotspot: IORouter.java (2 hits)
```

`IORouter.java` and `AppMeta.java` both have two hits. The skill breaks a tie by total hits across all keywords, then by path order, which puts `IORouter.java` first.

**Step 3** — FileAnalyzer runs via the JVM and prints the profile shown above.

**Step 4** — Claude reads the source and explains the hits. In `IORouter.java` both are comments recording what revision `2026-07-28` deprecates: `roots/list` and `sampling/createMessage` as input requests (SEP-2577), and the `roots` question the search tool used to ask before it took a `directory` argument. They mark migrations already made, not debt left behind.

**Checkpoint** — the agent stops and waits:
```
Proceed to synthesis? (yes/no)
```

Type `yes`. The agent generates a final prioritized audit report and asks if you want to save it as `audit-report.md`.

---

## Troubleshooting

### The script says `Plugin     : not built yet`

Complete Step 4. The script adds `--plugin-dir` only once `keyword-audit-plugin/.claude-plugin/plugin.json` exists.

### MCP server not found
```bash
ls build/libs/agent-mcp-workshop-0.0.1.jar   # must exist
./gradlew clean build                          # rebuild if missing
```

If `/mcp` lists `workshop` as failed, read the newest file in `logs/` at the project root; the server writes its log there, not to stderr. A log that ends with `unsupported method 'initialize' (returning -32601)` means Claude Code opened with the legacy handshake: check `claude --version`, and that `MCP_PROTOCOL_NEGOTIATION` is not set to `legacy`.

### Plugin commands not found

Plugin skills are namespaced. Use `/keyword-audit-plugin:keyword-search`, not `/keyword-search`. Run `/skills` to see the full list including namespaced commands.

### `/plugin` installed tab is empty

The plugin only loads when Claude Code is launched with `--plugin-dir`. Always use `./start_claude_mcp.sh` — not bare `claude` — for this project.

### `java-script-runner` fails

```bash
java -version   # must be 21 or later
```

FileAnalyzer runs with plain `java`, with no preview flags. If you see `invalid source release 21 with --enable-preview`, your plugin has an old copy of the skill: copy it again from `lessons/keyword-audit-plugin/`.

### Audit stops before the checkpoint

Run `/keyword-audit-plugin:keyword-search deprecated` manually. If that fails, the MCP server connection is the issue — run `/mcp` to check that `workshop` is connected.

---

## Stretch: Write Your Own Skill

Add a fifth skill to the plugin. Create `keyword-audit-plugin/skills/<name>/SKILL.md` with the same frontmatter as the others (`name` and `description`), a `## Usage` section and a `## Instructions for Claude` section. Restart with `./start_claude_mcp.sh` and run it as `/keyword-audit-plugin:<name>`.

Ideas:
- `keyword-compare`: search for two keywords and explain where they overlap
- `keyword-trend`: search the same keyword in `src/main/java` and `src/test/java` and compare the two

Whatever it does, have it pass `directory` to `key_word_search`, as the other skills do.

## Stretch: Serve the Skills over MCP

The plugin ships its skills on disk. The **Skills over MCP** extension (`io.modelcontextprotocol/skills`) ships the same `SKILL.md` files from a server instead: the host finds them with `skills/list` and `skills/get` and reads them with `resources/read` on `skill://` URIs. [agent-overview.md](agent-overview.md) compares the two. Try declaring the extension in your server's `server/discover` and serving `keyword-search` that way.

---

## What You've Built

| Step | What You Did |
|------|-------------|
| Connected MCP server | Started Claude Code with `lessons/mcp.json` through `start_claude_mcp.sh` |
| Learned skills vs plugins | Skill = single command; Plugin = manifest + namespaced skill bundle |
| Built the plugin scaffold | Created `keyword-audit-plugin/.claude-plugin/plugin.json` |
| Copied + read keyword-search | Data layer: MCP tool → sorted file counts |
| Copied + read keyword-report | Analysis layer: grouped by package, adds recommendations |
| Copied + read java-script-runner | Execution layer: embedded Java runs via the JVM, no build step |
| Copied + read audit | Orchestrator: supervised 6-step agent with checkpoint |
| Launched with dev script | `start_claude_mcp.sh` → `--plugin-dir ./keyword-audit-plugin` |
| Ran full audit | Search → JVM analysis → read source → reason → checkpoint → synthesize |

The plugin demonstrates the full composition:

```
MCP tool (data)  +  java-script-runner (JVM)  +  Read (source)  +  Claude (reasoning)
     ↓                      ↓                         ↓                  ↓
  what files         structural profile         actual code         why it matters
```
