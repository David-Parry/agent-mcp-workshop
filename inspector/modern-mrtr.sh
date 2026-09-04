#!/usr/bin/env bash
# Walk the keyword-search tool through both Multi Round-Trip Request paths.
#
# Path A: the client answers the embedded roots/list with a real root, so the
#         search runs on the first retry.
# Path B: the client answers roots/list with an empty list, the server falls
#         back to asking the user, and the search runs on the second retry.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

JAR=../build/libs/agent-mcp-workshop-0.0.1.jar
TARGET=$(cd .. && pwd)/src/main/java/com/workshop/mcp/tools
META='"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{"roots":{"listChanged":true},"elicitation":{"form":{},"url":{}}}}'

# requestState values as minted by SearchContinuation for keyword "record".
ROOTS_STATE=$(printf '{"keyword":"record","stage":"roots"}' | base64 | tr -d '=' | tr '/+' '_-')
DIR_STATE=$(printf '{"keyword":"record","stage":"directory"}' | base64 | tr -d '=' | tr '/+' '_-')

{
  # Path A: retry carrying a roots/list answer.
  echo "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"tools/call\",\"params\":{$META,\"name\":\"key_word_search\",\"arguments\":{\"keyword\":\"record\"},\"requestState\":\"$ROOTS_STATE\",\"inputResponses\":{\"search_roots\":{\"roots\":[{\"uri\":\"file://$TARGET\",\"name\":\"tools\"}]}}}}"
  # Path B step 1: retry carrying an empty roots/list answer.
  echo "{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"tools/call\",\"params\":{$META,\"name\":\"key_word_search\",\"arguments\":{\"keyword\":\"record\"},\"requestState\":\"$ROOTS_STATE\",\"inputResponses\":{\"search_roots\":{\"roots\":[]}}}}"
  # Path B step 2: retry carrying the elicited directory.
  echo "{\"jsonrpc\":\"2.0\",\"id\":12,\"method\":\"tools/call\",\"params\":{$META,\"name\":\"key_word_search\",\"arguments\":{\"keyword\":\"record\"},\"requestState\":\"$DIR_STATE\",\"inputResponses\":{\"search_directory\":{\"action\":\"accept\",\"content\":{\"directory\":\"$TARGET\"}}}}}"
  # Path B step 2 variant: the user declined the form.
  echo "{\"jsonrpc\":\"2.0\",\"id\":13,\"method\":\"tools/call\",\"params\":{$META,\"name\":\"key_word_search\",\"arguments\":{\"keyword\":\"record\"},\"requestState\":\"$DIR_STATE\",\"inputResponses\":{\"search_directory\":{\"action\":\"decline\"}}}}"
  # A client that can neither answer roots nor render a form.
  echo '{"jsonrpc":"2.0","id":14,"method":"tools/call","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}},"name":"key_word_search","arguments":{"keyword":"record"}}}'
  sleep 1
} | java -jar "$JAR"
