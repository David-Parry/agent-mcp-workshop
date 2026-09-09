#!/usr/bin/env bash
# Walk the keyword-search tool through the Multi Round-Trip Request exchange.
#
# There is one question the tool can ask. It used to ask two: an embedded
# roots/list first, escalating to the form only when the roots answer came back
# empty. Roots is deprecated under SEP-2577, and the migration the spec names
# in its place is exactly the tool's own directory argument, so the roots stage
# is gone and the form is the whole exchange.
#
# Step 1: the bare call, which has nothing to search and so asks.
# Step 2: the retry carrying the elicited directory — the search runs.
# Step 3: the same retry with the form declined, which falls back rather than failing.
# Step 4: a client that cannot render a form, which is never asked at all.
# Step 5: a client whose only answerable capability is the deprecated one.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

JAR=../build/libs/agent-mcp-workshop-0.0.1.jar
TARGET=$(cd .. && pwd)/src/main/java/com/workshop/mcp/tools

# The roots and sampling declarations are deliberate. SEP-2577 tells clients to
# keep declaring deprecated features for the whole transition period, so a real
# client still sends both, and the server must simply ignore what it no longer
# models.
META='"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{"roots":{"listChanged":true},"sampling":{},"elicitation":{"form":{},"url":{}}}}'

# requestState as minted by SearchContinuation for keyword "record".
DIR_STATE=$(printf '{"keyword":"record","stage":"directory"}' | base64 | tr -d '=' | tr '/+' '_-')

{
  # Step 1: nothing to search yet, so the server embeds an elicitation/create.
  echo "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"tools/call\",\"params\":{$META,\"name\":\"key_word_search\",\"arguments\":{\"keyword\":\"record\"}}}"
  # Step 2: retry carrying the elicited directory.
  echo "{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"tools/call\",\"params\":{$META,\"name\":\"key_word_search\",\"arguments\":{\"keyword\":\"record\"},\"requestState\":\"$DIR_STATE\",\"inputResponses\":{\"search_directory\":{\"action\":\"accept\",\"content\":{\"directory\":\"$TARGET\"}}}}}"
  # Step 3: the user declined the form.
  echo "{\"jsonrpc\":\"2.0\",\"id\":12,\"method\":\"tools/call\",\"params\":{$META,\"name\":\"key_word_search\",\"arguments\":{\"keyword\":\"record\"},\"requestState\":\"$DIR_STATE\",\"inputResponses\":{\"search_directory\":{\"action\":\"decline\"}}}}"
  # Step 4: a client that cannot render a form.
  echo '{"jsonrpc":"2.0","id":13,"method":"tools/call","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}},"name":"key_word_search","arguments":{"keyword":"record"}}}'
  # Step 5: a client offering only the deprecated capability the server dropped.
  echo '{"jsonrpc":"2.0","id":14,"method":"tools/call","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{"roots":{"listChanged":true}}},"name":"key_word_search","arguments":{"keyword":"record"}}}'
  sleep 1
} | java -jar "$JAR"
