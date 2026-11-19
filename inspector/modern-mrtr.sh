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
# Step 6: a call with no keyword either, which is asked for both in one round.
# Step 7: a client that can only open a URL, which is sent to a page instead.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

JAR=../build/libs/agent-mcp-workshop-0.0.1.jar
TARGET=$(cd .. && pwd)/src/main/java/com/workshop/mcp/tools

# The roots and sampling declarations are deliberate. SEP-2577 tells clients to
# keep declaring deprecated features for the whole transition period, so a real
# client still sends both, and the server must simply ignore what it no longer
# models.
META='"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{"roots":{"listChanged":true},"sampling":{},"elicitation":{"form":{},"url":{}}}}'
URL_ONLY_META='"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{"elicitation":{"url":{}}}}'

# The server seals requestState with HMAC-SHA256. Step 1 and the retries go to
# two different server processes here, which is what having no session means:
# any process that holds the key can carry on the exchange. Without a shared
# key each process makes its own, and the retry would be a first attempt.
WORKSHOP_STATE_KEY=$(openssl rand -base64 32)
export WORKSHOP_STATE_KEY

# Step 1: nothing to search yet, so the server embeds an elicitation/create.
FIRST=$(echo "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"tools/call\",\"params\":{$META,\"name\":\"key_word_search\",\"arguments\":{\"keyword\":\"record\"}}}" \
  | java -jar "$JAR")
echo "$FIRST"

# The retry echoes the state byte-exact. It holds only the stage: each retry
# repeats the keyword argument, so the state does not carry it.
DIR_STATE=$(printf '%s' "$FIRST" | sed -n 's/.*"requestState":"\([^"]*\)".*/\1/p')

{
  # Step 2: retry carrying the elicited directory.
  echo "{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"tools/call\",\"params\":{$META,\"name\":\"key_word_search\",\"arguments\":{\"keyword\":\"record\"},\"requestState\":\"$DIR_STATE\",\"inputResponses\":{\"search_directory\":{\"action\":\"accept\",\"content\":{\"directory\":\"$TARGET\"}}}}}"
  # Step 3: the user declined the form.
  echo "{\"jsonrpc\":\"2.0\",\"id\":12,\"method\":\"tools/call\",\"params\":{$META,\"name\":\"key_word_search\",\"arguments\":{\"keyword\":\"record\"},\"requestState\":\"$DIR_STATE\",\"inputResponses\":{\"search_directory\":{\"action\":\"decline\"}}}}"
  # Step 4: a client that cannot render a form.
  echo '{"jsonrpc":"2.0","id":13,"method":"tools/call","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}},"name":"key_word_search","arguments":{"keyword":"record"}}}'
  # Step 5: a client offering only the deprecated capability the server dropped.
  echo '{"jsonrpc":"2.0","id":14,"method":"tools/call","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{"roots":{"listChanged":true}}},"name":"key_word_search","arguments":{"keyword":"record"}}}'
  # Step 6: no keyword and no directory, so search_keyword and search_directory together.
  echo "{\"jsonrpc\":\"2.0\",\"id\":15,\"method\":\"tools/call\",\"params\":{$META,\"name\":\"key_word_search\",\"arguments\":{}}}"
  # Step 7: a URL-only client, sent to a page on 127.0.0.1 under search_page.
  echo "{\"jsonrpc\":\"2.0\",\"id\":16,\"method\":\"tools/call\",\"params\":{$URL_ONLY_META,\"name\":\"key_word_search\",\"arguments\":{\"keyword\":\"record\"}}}"
  sleep 1
} | java -jar "$JAR"
