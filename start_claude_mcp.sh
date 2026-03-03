#!/usr/bin/env bash
# start_claude_mcp.sh — launch Claude Code with the workshop MCP server and plugin
# Usage: ./start_claude_mcp.sh

set -euo pipefail

# Resolve the project root regardless of where this script is called from
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$ROOT/build/libs/agent-mcp-workshop-0.0.1.jar"
TEMPLATE="$ROOT/lesson/mcp.json"
RESOLVED_CONFIG="$ROOT/.mcp-resolved.json"
PLUGIN_DIR="$ROOT/keyword-audit-plugin"

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

# Substitute {YOUR_BASE} in lesson/mcp.json with the real project root
sed "s|{YOUR_BASE}|$ROOT|g" "$TEMPLATE" > "$RESOLVED_CONFIG"

# Clean up the temp config on exit (normal or error)
trap 'rm -f "$RESOLVED_CONFIG"' EXIT

echo "Starting Claude Code with workshop MCP server and keyword-audit plugin..."
echo "  MCP config : $TEMPLATE"
echo "  JAR        : $JAR"
echo "  Plugin     : $PLUGIN_DIR"
echo ""

claude --mcp-config "$RESOLVED_CONFIG" --strict-mcp-config --plugin-dir "$PLUGIN_DIR"
