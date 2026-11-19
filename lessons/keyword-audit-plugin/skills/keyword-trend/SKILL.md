---
name: keyword-trend
description: Compare how often a keyword occurs in production code and in its tests
---

# keyword-trend

Search for the same keyword in the project's `src/main/java` and `src/test/java` using the `key_word_search` MCP tool from the workshop server, and compare the two.

## Usage

```
/keyword-audit-plugin:keyword-trend <keyword>
```

## What This Skill Does

1. Calls `key_word_search` twice for the keyword via the workshop MCP server: once in `src/main/java` and once in `src/test/java`
2. Totals the occurrences and the files on each side
3. Pairs each production file with its test, `<Name>.java` with `<Name>Test.java`
4. Says whether the keyword lives mostly in production code, mostly in the tests, or in both

## Example

```
/keyword-audit-plugin:keyword-trend deprecated
```

**Output format:**
```
Keyword: <keyword>
─────────────────────────────────────────
src/main/java  (<occurrences> in <n> files)
   <count>  <path>
src/test/java  (<occurrences> in <n> files)
   <count>  <path>
─────────────────────────────────────────
Split: main <occurrences> : test <occurrences>
```

## Instructions for Claude

When this skill is invoked:

1. Extract the keyword argument
2. Call `key_word_search(keyword: "<keyword>", directory: "<absolute path>")` twice, always passing `directory`: first the absolute path of the project's `src/main/java`, then that of its `src/test/java`. Without it the server stops to ask the user for one. If the project has no `src/test/java`, say so and run only the first search.
3. Each result reads `<absolute path>, keyword_count=<n>`. Sort each side descending by count, then by path
4. Display both sides in the format above, with paths relative to the project root
5. Pair the files by name: `<Name>.java` under `src/main/java` with `<Name>Test.java` in the same package under `src/test/java`. List the pairs in which both files contain the keyword, then the production files whose test does not contain it
6. Show the split, and say what it suggests: a keyword found mostly in the tests is checked more than it is implemented, and one found only in production code may be untested
7. If the keyword occurs on neither side, say: "No occurrences of '<keyword>' found in src/main/java or src/test/java."
