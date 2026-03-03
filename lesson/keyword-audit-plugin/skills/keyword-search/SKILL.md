---
name: keyword-search
description: Search the codebase for a keyword using the workshop MCP server
---

# keyword-search

Search the codebase for a specific keyword using the `key_word_search` MCP tool from the workshop server.

## Usage

```
/keyword-audit-plugin:keyword-search <keyword>
```

## What This Skill Does

1. Calls `key_word_search` with the provided keyword via the workshop MCP server
2. Returns a sorted list of files and their occurrence counts (highest first)
3. Highlights the top 3 files for quick reference

## Example

```
/keyword-audit-plugin:keyword-search TODO
```

**Expected output:**
```
Keyword: TODO
─────────────────────────────────────────
  12  src/main/java/com/workshop/mcp/Server.java
   5  src/main/java/com/workshop/mcp/tools/Tool.java
   3  src/main/java/com/workshop/mcp/Router.java
─────────────────────────────────────────
Total: 20 occurrences across 3 files
```

## Instructions for Claude

When this skill is invoked:

1. Extract the keyword argument from the invocation
2. Use the `key_word_search` MCP tool: `key_word_search(keyword: "<keyword>")`
3. Sort results descending by count
4. Display in the formatted table above
5. Include a total count summary at the bottom
6. If no results are found, say: "No occurrences of '<keyword>' found in the project."
