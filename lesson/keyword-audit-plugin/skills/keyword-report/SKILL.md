---
name: keyword-report
description: Generate a structured analysis report from keyword search results
---

# keyword-report

Generate a structured markdown report analyzing the distribution and significance of a keyword across the codebase.

## Usage

```
/keyword-audit-plugin:keyword-report <keyword>
```

## What This Skill Does

1. Runs `key_word_search` for the keyword
2. Groups results by package/directory
3. Identifies concentration hotspots
4. Produces a shareable markdown report with recommendations

## Instructions for Claude

When this skill is invoked:

1. Extract the keyword argument
2. Call `key_word_search(keyword: "<keyword>")`
3. Group files by their Java package (directory path segment after `java/`)
4. Calculate the percentage concentration in the top file
5. Generate a markdown report with: Summary, Distribution by Package, Files Requiring Attention, Recommendation
6. Add today's date in the "Generated" field
7. Include a concrete recommendation based on the distribution pattern
