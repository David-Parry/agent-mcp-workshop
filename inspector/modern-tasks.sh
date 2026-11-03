#!/usr/bin/env bash
# Walk the keyword search through the tasks extension by hand.
#
# Step 1: the call, from a client that declares the extension and names no
#         directory. It is answered with a handle rather than a result.
# Step 2: poll tasks/get until the task asks for its directory, as its status.
# Step 3: answer with tasks/update, which is acknowledged with an empty result.
# Step 4: poll tasks/get until the task has completed with the search result.
# Step 5: cancel the finished task. It is acknowledged and stays completed.
# Step 6: the same tasks/get from a client that left the extension off.
# Step 7: tasks/get on a task id that was never issued.
#
# The server mints each task id, so unlike modern-mrtr.sh this script reads
# every answer before it sends the next request. The server pushes no task
# status, so each line it writes answers the request just sent.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

JAR=../build/libs/agent-mcp-workshop-0.0.1.jar
TARGET=$(cd .. && pwd)/src/main/java/com/workshop/mcp/tools

TASKS='"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{"elicitation":{"form":{}},"extensions":{"io.modelcontextprotocol/tasks":{}}}}'
NO_TASKS='"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{"elicitation":{"form":{}}}}'

# Named pipes rather than coproc, which the bash 3.2 that macOS ships lacks.
PIPES=$(mktemp -d)
trap 'rm -rf "$PIPES"' EXIT
mkfifo "$PIPES/requests" "$PIPES/answers"
java -jar "$JAR" <"$PIPES/requests" >"$PIPES/answers" &
exec 3>"$PIPES/requests" 4<"$PIPES/answers"

# Sends one request and prints the line that answers it, keeping it in ANSWER.
send() {
  echo "$1" >&3
  IFS= read -r ANSWER <&4
  echo "$ANSWER"
}

# The first string value of a field in ANSWER.
field() {
  grep -o "\"$1\":\"[^\"]*\"" <<<"$ANSWER" | head -1 | cut -d'"' -f4
}

# Polls tasks/get until the task reaches a status, printing only that answer.
POLLS=0
poll_until() {
  for _ in $(seq 1 50); do
    POLLS=$((POLLS + 1))
    echo "{\"jsonrpc\":\"2.0\",\"id\":\"poll-$POLLS\",\"method\":\"tasks/get\",\"params\":{$TASKS,\"taskId\":\"$TASK_ID\"}}" >&3
    IFS= read -r ANSWER <&4
    if [ "$(field status)" = "$1" ]; then
      echo "$ANSWER"
      return
    fi
    sleep 0.3
  done
  echo "task $TASK_ID never reached $1; the last answer was $ANSWER" >&2
  exit 1
}

send "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{$TASKS,\"name\":\"key_word_search\",\"arguments\":{\"keyword\":\"record\"}}}"
TASK_ID=$(field taskId)
poll_until input_required
send "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tasks/update\",\"params\":{$TASKS,\"taskId\":\"$TASK_ID\",\"inputResponses\":{\"search_directory\":{\"action\":\"accept\",\"content\":{\"directory\":\"$TARGET\"}}}}}"
poll_until completed
send "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tasks/cancel\",\"params\":{$TASKS,\"taskId\":\"$TASK_ID\"}}"
poll_until completed
send "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tasks/get\",\"params\":{$NO_TASKS,\"taskId\":\"$TASK_ID\"}}"
send "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tasks/get\",\"params\":{$TASKS,\"taskId\":\"no-such-task\"}}"
exec 3>&-
wait
