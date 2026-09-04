#!/usr/bin/env bash
# Drive the workshop server over stdio the way a 2026-07-28 client does.
#
# Sends the discovery probe, then a handful of stateless requests, each
# carrying the io.modelcontextprotocol/* envelope in params._meta. Useful for
# checking wire shapes without launching the Inspector UI.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

JAR=../build/libs/agent-mcp-workshop-0.0.1.jar
META='"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientInfo":{"name":"modern-probe","version":"0.0.0"},"io.modelcontextprotocol/clientCapabilities":{"sampling":{},"elicitation":{"form":{},"url":{}},"roots":{"listChanged":true},"extensions":{"io.modelcontextprotocol/tasks":{},"io.modelcontextprotocol/ui":{"mimeTypes":["text/html;profile=mcp-app"]}}}}'

{
  echo "{\"jsonrpc\":\"2.0\",\"id\":\"server-discover-probe-1\",\"method\":\"server/discover\",\"params\":{$META}}"
  echo "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"tools/list\",\"params\":{$META}}"
  echo "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/templates/list\",\"params\":{$META}}"
  echo "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"prompts/list\",\"params\":{$META}}"
  # First attempt: no directory, so the server should answer input_required.
  echo "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{$META,\"name\":\"key_word_search\",\"arguments\":{\"keyword\":\"record\"}}}"
  # Legacy handshake must now be refused.
  echo "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"initialize\",\"params\":{$META}}"
  # Envelope naming a revision this server does not speak.
  echo '{"jsonrpc":"2.0","id":5,"method":"tools/list","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2025-11-25","io.modelcontextprotocol/clientCapabilities":{}}}}'
  # Envelope missing entirely.
  echo '{"jsonrpc":"2.0","id":6,"method":"tools/list","params":{}}'
  sleep 1
} | java -jar "$JAR"
