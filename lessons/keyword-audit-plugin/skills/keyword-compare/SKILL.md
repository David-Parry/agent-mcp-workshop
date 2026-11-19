---
name: keyword-compare
description: Compare where two keywords occur in the codebase and explain where they overlap
---

# keyword-compare

Search the codebase for two keywords using the `key_word_search` MCP tool from the workshop server, and show where they occur together and where only one of them does.

## Usage

```
/keyword-audit-plugin:keyword-compare <first-keyword> <second-keyword>
```

## What This Skill Does

1. Calls `key_word_search` once for each keyword via the workshop MCP server, searching the project's `src/main/java`
2. Splits the files into three groups: both keywords, only the first, and only the second
3. Ranks the shared files by their combined count
4. Explains what the overlap says about how the two keywords relate

## Example

```
/keyword-audit-plugin:keyword-compare deprecated roots
```

**Output format:**
```
Keywords: <first> vs <second>
─────────────────────────────────────────
Both (<n> files)
   <a> + <b> = <sum>  <path>
Only <first> (<n> files)
   <count>  <path>
Only <second> (<n> files)
   <count>  <path>
─────────────────────────────────────────
Overlap: <both> of <either> files (<percent>%)
```

## Instructions for Claude

When this skill is invoked:

1. Extract the two keyword arguments. If there are not exactly two, say: "Usage: /keyword-audit-plugin:keyword-compare <first-keyword> <second-keyword>" and stop.
2. Call `key_word_search(keyword: "<keyword>", directory: "<absolute path>")` once for each keyword, always passing `directory`: the absolute path of the project's `src/main/java`, or of the project root if it has none. Without it the server stops to ask the user for one, once for every keyword.
3. Each result reads `<absolute path>, keyword_count=<n>`. Match the files of the two searches by absolute path.
4. Display the three groups in the format above, with paths relative to the project root. Sort **Both** descending by the combined count, and each **Only** group descending by count, then each by path.
5. Show the overlap: the files that contain both keywords, out of the files that contain either, as a percentage rounded to a whole number.
6. Read the first file under **Both** and explain in two or three sentences why both keywords occur there. Base the explanation on the code you read, not on the file name.
7. If neither keyword occurs, say: "Neither '<first>' nor '<second>' occurs in the project." If only one does, show its group and say that the other does not occur.
