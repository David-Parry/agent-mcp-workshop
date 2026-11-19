#!/usr/bin/env bash
# Walk the keyword search through the tasks extension by hand.
#
# Step 1: the call, from a client that declares the extension and names no
#         directory. It is answered with a handle rather than a result.
# Step 2: poll tasks/get until the task asks for its directory, as its status.
# Step 3: open a stream that names the task, instead of polling any further.
#         The server agrees to it and pushes the task as it stands.
# Step 4: answer with tasks/update, which is acknowledged with an empty result.
#         Every change is pushed as notifications/tasks: the search's progress
#         in the status message, then the completed task with its result. The
#         stream closes once the task has finished.
# Step 5: cancel the finished task. It is acknowledged and stays completed.
# Step 6: the same tasks/get from a client that left the extension off.
# Step 7: tasks/get on a task id that was never issued.
#
# The server mints each task id, so unlike modern-mrtr.sh this script reads
# every answer before it sends the next request. Task status is pushed only on
# a stream that names the task, so outside steps 3 and 4 each line the server
# writes answers the request just sent.
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

# Prints lines until the one that answers request $1, keeping it in ANSWER.
# What the server pushes on an open stream arrives in between, and is printed too.
until_answer() {
  while IFS= read -r ANSWER <&4; do
    echo "$ANSWER"
    case "$ANSWER" in
      *"\"id\":$1,"* | *"\"id\":\"$1\","*) return ;;
    esac
  done
  echo "the server stopped before it answered $1" >&2
  exit 1
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
echo "{\"jsonrpc\":\"2.0\",\"id\":\"listen:tasks\",\"method\":\"subscriptions/listen\",\"params\":{$TASKS,\"notifications\":{\"taskIds\":[\"$TASK_ID\"]}}}" >&3
# The acknowledgement, then the task as it stands.
for _ in 1 2; do
  IFS= read -r ANSWER <&4
  echo "$ANSWER"
done
echo "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tasks/update\",\"params\":{$TASKS,\"taskId\":\"$TASK_ID\",\"inputResponses\":{\"search_directory\":{\"action\":\"accept\",\"content\":{\"directory\":\"$TARGET\"}}}}}" >&3
until_answer 2
until_answer listen:tasks
send "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tasks/cancel\",\"params\":{$TASKS,\"taskId\":\"$TASK_ID\"}}"
poll_until completed
send "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tasks/get\",\"params\":{$NO_TASKS,\"taskId\":\"$TASK_ID\"}}"
send "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tasks/get\",\"params\":{$TASKS,\"taskId\":\"no-such-task\"}}"
exec 3>&-
wait
