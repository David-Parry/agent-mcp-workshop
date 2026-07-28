#!/usr/bin/env bash
# Drive the workshop server over stdio the way a 2026-07-28 client does.
#
# Sends the discovery probe, then a handful of stateless requests, each
# carrying the io.modelcontextprotocol/* envelope in params._meta, then the
# mistakes a server has to answer. Useful for checking wire shapes without
# launching the Inspector UI.
#
# Works from chapter 03 on. The envelope checks use subscriptions/listen
# because chapter 03 already answers it; tools/list, prompts/list and the rest
# are -32601 until chapter 04 adds them, and tools/call asks for a directory
# from chapter 05.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

JAR=../build/libs/agent-mcp-workshop-0.0.1.jar
META='"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientInfo":{"name":"modern-probe","version":"0.0.0"},"io.modelcontextprotocol/clientCapabilities":{"sampling":{},"elicitation":{"form":{},"url":{}},"roots":{"listChanged":true},"extensions":{"io.modelcontextprotocol/tasks":{},"io.modelcontextprotocol/ui":{"mimeTypes":["text/html;profile=mcp-app"]}}}}'

{
  echo "{\"jsonrpc\":\"2.0\",\"id\":\"server-discover-probe-1\",\"method\":\"server/discover\",\"params\":{$META}}"
  # Acknowledged, then closed at once: this server's lists never change.
  echo "{\"jsonrpc\":\"2.0\",\"id\":\"listen:0\",\"method\":\"subscriptions/listen\",\"params\":{$META,\"notifications\":{\"toolsListChanged\":true}}}"
  # A client ends a stream by cancelling its listen request; the server logs it.
  echo '{"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":"listen:0","reason":"probe finished"}}'
  echo "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"tools/list\",\"params\":{$META}}"
  echo "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/templates/list\",\"params\":{$META}}"
  echo "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"prompts/list\",\"params\":{$META}}"
  # First attempt: no directory, so the server should answer input_required.
  echo "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{$META,\"name\":\"key_word_search\",\"arguments\":{\"keyword\":\"record\"}}}"
  # Legacy handshake must now be refused, naming the revisions that would work.
  echo "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"initialize\",\"params\":{$META}}"
  # Envelope naming a revision this server does not speak: -32022.
  echo '{"jsonrpc":"2.0","id":5,"method":"subscriptions/listen","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2025-11-25","io.modelcontextprotocol/clientCapabilities":{}}}}'
  # Envelope missing entirely: -32602.
  echo '{"jsonrpc":"2.0","id":6,"method":"subscriptions/listen","params":{}}'
  # Not JSON at all: -32700, with no id to quote back.
  echo '{"jsonrpc":"2.0","id":7,"method":'
  # JSON, but not a JSON-RPC 2.0 message: -32600, also with no id.
  echo '{"id":8,"method":"server/discover"}'
  sleep 1
} | java -jar "$JAR"
