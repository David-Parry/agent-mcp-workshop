#!/usr/bin/env bash
# start_claude_mcp.sh — launch Claude Code with the workshop MCP server, plus the
# keyword-audit plugin once you have built it
# Usage: ./start_claude_mcp.sh

set -euo pipefail

# Resolve the project root regardless of where this script is called from
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$ROOT/build/libs/agent-mcp-workshop-0.0.1.jar"
TEMPLATE="$ROOT/lessons/mcp.json"
RESOLVED_CONFIG="$ROOT/.mcp-resolved.json"
PLUGIN_DIR="$ROOT/keyword-audit-plugin"

if [ ! -f "$TEMPLATE" ]; then
  echo "MCP config template not found at $TEMPLATE"
  exit 1
fi

# Build the JAR if it doesn't exist
if [ ! -f "$JAR" ]; then
  echo "JAR not found — building..."
  (cd "$ROOT" && ./gradlew build -q)
  echo "Build complete."
fi

# Clean up the temp config on exit (normal or error)
trap 'rm -f "$RESOLVED_CONFIG"' EXIT

# Substitute {YOUR_BASE} in the template with the real project root
sed "s|{YOUR_BASE}|$ROOT|g" "$TEMPLATE" > "$RESOLVED_CONFIG"

CLAUDE_ARGS=(--mcp-config "$RESOLVED_CONFIG" --strict-mcp-config)
# Students build the plugin in Steps 3 and 4 of lessons/agent-instructions.md
if [ -f "$PLUGIN_DIR/.claude-plugin/plugin.json" ]; then
  CLAUDE_ARGS+=(--plugin-dir "$PLUGIN_DIR")
  PLUGIN_STATUS="$PLUGIN_DIR"
else
  PLUGIN_STATUS="not built yet (Steps 3 and 4 of lessons/agent-instructions.md), starting without it"
fi

echo "Starting Claude Code with the workshop MCP server..."
echo "  MCP config : $TEMPLATE"
echo "  JAR        : $JAR"
echo "  Plugin     : $PLUGIN_STATUS"
echo ""

# Claude Code starts the server in its own working directory, so the server's
# logs/ and its fallback search directory are the project root.
cd "$ROOT"
claude "${CLAUDE_ARGS[@]}"
