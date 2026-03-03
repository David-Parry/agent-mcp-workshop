# Chapter Agent: Running Your MCP Server with a Live LLM

## What You're Building

In this lesson you will:
1. Connect your MCP server to Claude Code and use it in a live conversation
2. Build the `keyword-audit-plugin` step by step — creating the manifest and copying each skill from the reference, reading and understanding each one as you go
3. Launch in development mode with a single script
4. Run each skill individually, then run the full supervised audit end-to-end
5. Write your own skill and add it to the plugin

The reference plugin lives at `lesson/keyword-audit-plugin/`. Your working copy will be at `keyword-audit-plugin/` in the project root — the same two-folder pattern used in earlier chapters.

```
agent-mcp-workshop/
├── lesson/keyword-audit-plugin/   ← reference — read from here
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

## Step 1: Create the MCP Server Configuration

The `mcp.json` file tells Claude Code how to launch your MCP server process.

**Action Required**: Create `mcp.json` in the project root:

```json
{
  "mcpServers": {
    "workshop": {
      "command": "java",
      "args": [
        "-jar",
        "build/libs/agent-mcp-workshop-0.0.1.jar"
      ],
      "env": {}
    }
  }
}
```

If the JAR doesn't exist yet, build it first:

```bash
./gradlew clean build
```

---

## Step 2: Use Your MCP Server in a Live Conversation

Before adding the plugin, verify the MCP tool works on its own. Start Claude Code:

```bash
claude
```

Ask:
```
What MCP tools are available?
```

You should see `key_word_search` listed. Try it directly:

```
Search for the keyword "import" in this project — which file uses it most?
```

```
Find all files containing "TODO" and summarize what work remains.
```

Claude calls `key_word_search`, gets back structured data, and interprets it. That's the MCP loop. Now you'll wrap it in a plugin.

---

## Step 3: Create the Plugin Scaffold

Create the plugin directory and manifest at the project root.

```bash
mkdir -p keyword-audit-plugin/.claude-plugin
mkdir -p keyword-audit-plugin/skills
```

Open `lesson/keyword-audit-plugin/.claude-plugin/plugin.json` and read it:

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
cp lesson/keyword-audit-plugin/.claude-plugin/plugin.json keyword-audit-plugin/.claude-plugin/plugin.json
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

Copy each skill from `lesson/keyword-audit-plugin/skills/` into `keyword-audit-plugin/skills/`. **Read the `SKILL.md` before running it.**

### Skill 1: `keyword-search` — The Data Layer

```bash
cp -r lesson/keyword-audit-plugin/skills/keyword-search keyword-audit-plugin/skills/
```

Open `keyword-audit-plugin/skills/keyword-search/SKILL.md`.

**What it does**: Calls your MCP server's `key_word_search` tool with a keyword and returns a sorted table of files ranked by occurrence count.

**Read `## Instructions for Claude`** — the skill:
1. Extracts the keyword argument from the invocation
2. Calls `key_word_search(keyword: "<keyword>")` via the workshop MCP tool
3. Sorts results descending by count
4. Formats and displays the result table, highlighting the top 3

This is the **data layer** — deterministic search results with no interpretation.

---

### Skill 2: `keyword-report` — The Analysis Layer

```bash
cp -r lesson/keyword-audit-plugin/skills/keyword-report keyword-audit-plugin/skills/
```

Open `keyword-audit-plugin/skills/keyword-report/SKILL.md`.

**What it does**: Runs `key_word_search` for a keyword, groups files by Java package, calculates concentration percentages, and produces a shareable markdown report with a concrete recommendation.

**Read `## Instructions for Claude`** — the skill:
1. Calls `key_word_search` for the keyword
2. Groups file paths by the package segment after `java/` in the path
3. Calculates what percentage of all hits are concentrated in the top file
4. Generates sections: Summary, Distribution by Package, Files Requiring Attention, Recommendation

This is the **analysis layer** — the same data as `keyword-search`, but structured, grouped, and interpreted.

---

### Skill 3: `java-script-runner` — The Execution Layer

```bash
cp -r lesson/keyword-audit-plugin/skills/java-script-runner keyword-audit-plugin/skills/
```

Open `keyword-audit-plugin/skills/java-script-runner/SKILL.md`.

**What it does**: Runs `.java` files directly through the JVM without a build step. Its main feature is a `FileAnalyzer` — the full Java 21 source is **embedded directly inside the `SKILL.md`**. When invoked with `--analyze`, Claude writes that source to a temp file, executes it, and cleans up.

**Read `## Built-in FileAnalyzer Source`** — the embedded Java:
- Reads a target file's lines
- Counts total / code / comment / blank lines
- Reports keyword frequencies for: `TODO`, `FIXME`, `deprecated`, `import`, `class`
- Lists the top 5 longest lines

**Two Java 21 features make this work without `javac`**:
- **JEP 330** (Java 11+): `java MyFile.java` compiles and runs a single source file in one step
- **JEP 463** (Java 21 preview): `void main(String[] args)` with no surrounding `class {}` — unnamed class

The first line `///usr/bin/env java ...` is simultaneously a Unix shebang (executable on Linux/macOS) and valid Java syntax (a comment). The file carries its own launcher.

**Read `## Instructions for Claude`** — when `--analyze <path>` is given, Claude:
1. Writes the embedded source to `/tmp/FileAnalyzer_$$.java` via a Bash heredoc
2. Runs: `java --enable-preview --source 21 /tmp/FileAnalyzer_$$.java <target-path>`
3. Displays the full output
4. Deletes the temp file

This is the **execution layer** — a JVM process that returns a structural profile of a source file. No external `.java` file exists anywhere on disk.

---

### Skill 4: `audit` — The Orchestrator

```bash
cp -r lesson/keyword-audit-plugin/skills/audit keyword-audit-plugin/skills/
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

---

## Step 5: Launch in Development Mode

The project includes `start_claude_mcp.sh`, which handles everything in one command:

```bash
./start_claude_mcp.sh
```

The script:
1. Checks that `keyword-audit-plugin/.claude-plugin/plugin.json` exists (Step 4 must be complete)
2. Resolves the project root to an absolute path
3. Builds the JAR if it's missing
4. Expands `lesson/mcp.json` into `.mcp-resolved.json` with your real path substituted
5. Starts Claude Code with both MCP server and plugin:

```bash
claude --mcp-config .mcp-resolved.json --strict-mcp-config \
       --plugin-dir ./keyword-audit-plugin
```

> **`--plugin-dir`** loads the plugin from its source directory without any global install. Changes to `SKILL.md` files take effect on the next session start. This is **development mode** — the same plugin directory you just built.

> **`.mcp-resolved.json`** is a generated file the script writes on startup and deletes on exit. It's the expanded form of `lesson/mcp.json` with the absolute JAR path filled in. You never edit it directly.

Verify the plugin loaded:
```
/plugin
```
Open the **Installed** tab — you should see `keyword-audit-plugin`.

---

## Step 6: Run Each Skill

With the plugin loaded, test each skill individually before running the full audit.

### Run keyword-search

```
/keyword-audit-plugin:keyword-search TODO
```

Expected:
```
Keyword: TODO
─────────────────────────────────────────
  12  src/main/java/com/workshop/mcp/IORouter.java     ★
   5  src/main/java/com/workshop/mcp/tools/KeyWordSearch.java
─────────────────────────────────────────
Total: 17 occurrences across 2 files
```

### Run keyword-report

```
/keyword-audit-plugin:keyword-report TODO
```

You'll get a markdown report grouping the same results by Java package, with a percentage concentration and a recommendation.

### Run java-script-runner

```
/keyword-audit-plugin:java-script-runner --analyze src/main/java/com/workshop/mcp/IORouter.java
```

Claude writes the embedded `FileAnalyzer` to a temp file, runs it, and displays:
```
════════════════════════════════════════════════════════════
  File Analysis: IORouter.java
  Path:          /abs/path/.../IORouter.java
════════════════════════════════════════════════════════════
  Total lines:         247
  Code lines:          189
  Comment lines:        23
  Blank lines:          35

  Keyword Frequency:
    TODO:          12 occurrences
    FIXME:          0 occurrences
    ...
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

**Steps 1–2** — searches run, hotspot identified:
```
Search complete.
  TODO        17 occurrences  2 files   Hotspot: IORouter.java (12 hits)
  FIXME        2 occurrences  1 file    Hotspot: Router.java   (2 hits)
  deprecated   0 occurrences

Overall hotspot: IORouter.java — analyzing now...
```

**Step 3** — FileAnalyzer runs via JVM (structural profile output)

**Step 4** — Claude reads the source and reasons:
```
Hotspot Analysis: IORouter.java
────────────────────────────────────────────────────────────
IORouter.java is the central JSON-RPC dispatcher for the MCP server.
Its 12 TODOs are concentrated in the ELICITATION and SAMPLING handlers —
protocol features that have been stubbed but not yet fully implemented.
This is planned work, not forgotten debt.
────────────────────────────────────────────────────────────
```

**Checkpoint** — the agent stops and waits:
```
Audit results and hotspot analysis ready for review.
Proceed to synthesis? (yes/no)
```

Type `yes`. The agent generates a final prioritized audit report and asks if you want to save it as `audit-report.md`.

---

## Troubleshooting

### Script exits with "Plugin not found"

Complete Step 4 first. The script checks for `keyword-audit-plugin/.claude-plugin/plugin.json` before launching.

### MCP server not found
```bash
ls build/libs/agent-mcp-workshop-0.0.1.jar   # must exist
./gradlew clean build                          # rebuild if missing
```

### Plugin commands not found

Plugin skills are namespaced. Use `/keyword-audit-plugin:keyword-search`, not `/keyword-search`. Run `/skills` to see the full list including namespaced commands.

### `/plugin` installed tab is empty

The plugin only loads when Claude Code is launched with `--plugin-dir`. Always use `./start_claude_mcp.sh` — not bare `claude` — for this project.

### `java-script-runner` fails

```bash
java -version   # must be 11+; 21 for --enable-preview
```

The `--analyze` flag requires `--enable-preview --source 21`. If you see a version error, upgrade your JDK.

### Audit stops before the checkpoint

Run `/keyword-audit-plugin:keyword-search TODO` manually. If that fails, the MCP server connection is the issue — check `mcp.json` and confirm the JAR path is correct.

---

## What You've Built

| Step | What You Did |
|------|-------------|
| Connected MCP server | Registered your JAR with Claude Code via `mcp.json` |
| Learned skills vs plugins | Skill = single command; Plugin = manifest + namespaced skill bundle |
| Built the plugin scaffold | Created `keyword-audit-plugin/.claude-plugin/plugin.json` |
| Copied + read keyword-search | Data layer: MCP tool → sorted file counts |
| Copied + read keyword-report | Analysis layer: grouped by package, adds recommendations |
| Copied + read java-script-runner | Execution layer: embedded Java 21 runs via JVM, no build step |
| Copied + read audit | Orchestrator: supervised 6-step agent with checkpoint |
| Launched with dev script | `start_claude_mcp.sh` → `--plugin-dir ./keyword-audit-plugin` |
| Ran full audit | Search → JVM analysis → read source → reason → checkpoint → synthesize |

The plugin demonstrates the full composition:

```
MCP tool (data)  +  java-script-runner (JVM)  +  Read (source)  +  Claude (reasoning)
     ↓                      ↓                         ↓                  ↓
  what files         structural profile         actual code         why it matters
```
