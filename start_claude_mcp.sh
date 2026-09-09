#!/usr/bin/env bash
# start_claude_mcp.sh — launch Claude Code with the workshop MCP server and plugin
# Usage: ./start_claude_mcp.sh

set -euo pipefail

# Resolve the project root regardless of where this script is called from
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$ROOT/build/libs/agent-mcp-workshop-0.0.1.jar"
RESOLVED_CONFIG="$ROOT/.mcp-resolved.json"
PLUGIN_DIR="$ROOT/keyword-audit-plugin"

# Chapter branches ship the lesson docs as either lesson/ or lessons/
TEMPLATE=""
for candidate in "$ROOT/lesson/mcp.json" "$ROOT/lessons/mcp.json" "$ROOT/mcp.json"; do
  if [ -f "$candidate" ]; then
    TEMPLATE="$candidate"
    break
  fi
done

if [ -z "$TEMPLATE" ]; then
  echo "MCP config template not found — looked for lesson/mcp.json, lessons/mcp.json, and mcp.json under $ROOT"
  exit 1
fi

# Verify the plugin directory exists (students build this in Step 4)
if [ ! -f "$PLUGIN_DIR/.claude-plugin/plugin.json" ]; then
  echo "Plugin not found at $PLUGIN_DIR"
  echo "Complete Step 4 in lesson/agent-instructions.md to build the plugin first."
  exit 1
fi

# Build the JAR if it doesn't exist
if [ ! -f "$JAR" ]; then
  echo "JAR not found — building..."
  cd "$ROOT" && ./gradlew build -q
  echo "Build complete."
fi

# Clean up the temp config on exit (normal or error)
trap 'rm -f "$RESOLVED_CONFIG"' EXIT

# Substitute {YOUR_BASE} in the template with the real project root
sed "s|{YOUR_BASE}|$ROOT|g" "$TEMPLATE" > "$RESOLVED_CONFIG"

echo "Starting Claude Code with workshop MCP server and keyword-audit plugin..."
echo "  MCP config : $TEMPLATE"
echo "  JAR        : $JAR"
echo "  Plugin     : $PLUGIN_DIR"
echo ""

claude --mcp-config "$RESOLVED_CONFIG" --strict-mcp-config --plugin-dir "$PLUGIN_DIR"
