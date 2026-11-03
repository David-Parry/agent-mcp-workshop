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

1. Calls `key_word_search` with the provided keyword via the workshop MCP server, searching the project's `src/main/java`
2. Returns a sorted list of files and their occurrence counts (highest first)
3. Highlights the top 3 files for quick reference

## Example

```
/keyword-audit-plugin:keyword-search deprecated
```

**Expected output** on the `complete` branch:
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

## Instructions for Claude

When this skill is invoked:

1. Extract the keyword argument from the invocation
2. Use the `key_word_search` MCP tool, always passing `directory`: the absolute path of the project's `src/main/java`, or of the project root if it has none. `key_word_search(keyword: "<keyword>", directory: "<absolute path>")`. Without a `directory` the server stops to ask the user for one.
3. Each result reads `<absolute path>, keyword_count=<n>`. Sort descending by count, then by path
4. Display in the formatted table above, with paths relative to the project root
5. Include a total count summary at the bottom
6. If no results are found, say: "No occurrences of '<keyword>' found in the project."
