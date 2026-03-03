---
name: audit
description: Full codebase keyword audit — orchestrates keyword-search, java-script-runner, and keyword-report to identify hotspots and synthesize a prioritized audit report
---

# audit

A supervised agent that orchestrates the other three plugin skills to audit keyword debt across the codebase. It searches, identifies the hotspot file, runs structural analysis via the JVM, reads the source, reasons about why that file has the most occurrences, and synthesizes a prioritized report — pausing for your approval at the checkpoint.

## Usage

```
/keyword-audit-plugin:audit
```

With custom keywords:
```
/keyword-audit-plugin:audit keywords=[TODO,FIXME,HACK,deprecated]
```

## Supervised Agent Workflow

```
STEP 1: Search        — keyword-search for each keyword via MCP
STEP 2: Identify      — file with highest single-keyword count (hotspot)
STEP 3: Analyze       — java-script-runner --analyze <hotspot-path>
STEP 4: Read + Reason — read hotspot source, explain WHY it has the most occurrences
STEP 5: Report        — keyword-report logic for each keyword
[CHECKPOINT]          — show results + reasoning, wait for yes/no
STEP 6: Synthesis     — prioritized audit report
```

## Instructions for Claude

When this skill is invoked:

1. **Parse arguments**: check for `keywords=[...]`; default to `[TODO, FIXME, deprecated]`

2. **Step 1 — Search**: apply the `/keyword-audit-plugin:keyword-search` skill logic for each keyword sequentially — call `key_word_search(keyword: "<keyword>")` via the workshop MCP tool; record every file path and count per keyword

3. **Step 2 — Identify Hotspot**: find the single file with the highest individual keyword count across all searches; record its absolute path

4. **Step 3 — Analyze Hotspot**: apply the `/keyword-audit-plugin:java-script-runner` skill logic with `--analyze <hotspot-absolute-path>`:
   - Write the embedded FileAnalyzer Java source to `/tmp/FileAnalyzer_$$.java`
   - Run: `java --enable-preview --source 21 /tmp/FileAnalyzer_$$.java <hotspot-path>`
   - Display the full analysis output
   - Delete the temp file

5. **Step 4 — Read and Reason**: use the `Read` tool to read the hotspot file's full source. Cross-reference with the FileAnalyzer output and reason about WHY this file has the most occurrences:
   - What is the file's role (router, dispatcher, model, utility)?
   - Are occurrences clustered in one section or spread across the file?
   - Do they represent technical debt, planned stubs, or intentional markers?
   - Is the density high for the file's size?
   Present as a short "Hotspot Analysis" paragraph (3–6 sentences)

6. **Step 5 — Report**: apply the `/keyword-audit-plugin:keyword-report` skill logic for each keyword inline — group by package, calculate concentration, generate the markdown report sections; embed the hotspot reasoning for the keyword that produced the hotspot

7. **Checkpoint**: display the summary table + hotspot reasoning and ask: "Proceed to synthesis? (yes/no)"
   - "no" → stop, offer to re-run with different keywords
   - "yes" → continue

8. **Step 6 — Synthesis**: generate a final markdown audit report with:
   - Overall health score
   - Hotspot Finding section (with the Step 4 reasoning)
   - Prioritized action items (Priority 1/2/3)
   - Metrics table including hotspot file and reason
   - Ask: "Save report as `audit-report.md`? (yes/no)" and write the file if confirmed
