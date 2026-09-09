#!/usr/bin/env python3
"""Generate a chapter branch's starting state from the finished server.

Each chapter branch is the complete code with that chapter's work removed:
method bodies emptied, Javadoc left intact, everything still compiling. A
student fills the bodies back in and `./gradlew chapterTest -Pchapter=NN`
tells them when they are right.

The branches are independent projections of complete rather than a chain, so
chapter 5 is not "chapter 4 plus more" -- it is complete with chapter 5's work
removed and everything else present. That keeps every branch buildable and
lets a student start anywhere.

Usage:  python3 tools/hollow_chapter.py 03
"""

import re
import sys
from pathlib import Path

SRC = Path("src/main/java/com/workshop/mcp")

IO_HANDLER = SRC / "io/IOHandlerImpl.java"
DESERIALIZER = SRC / "spec/JsonRpcMessageDeserializer.java"
ID_ADAPTER = SRC / "spec/RequestIdTypeAdapter.java"
ROUTER = SRC / "IORouter.java"
CONTINUATION = SRC / "tools/SearchContinuation.java"
APP_META = SRC / "spec/AppMeta.java"
APP_TOOL_BUILDER = SRC / "spec/builders/AppToolBuilder.java"
TASK_STORE = SRC / "tasks/TaskStore.java"

# What each chapter asks the student to write.
#
#   methods: (file, method name) -- the body is emptied
#   cases:   (file, switch label) -- the arm's block is emptied
#   prelude: (file, method name) -- only the statements before the `switch`
CHAPTERS = {
    "01": {
        "methods": [
            (IO_HANDLER, "startInputReader"),
            (IO_HANDLER, "publishLine"),
            (IO_HANDLER, "emit"),
        ],
    },
    "02": {
        "methods": [
            (DESERIALIZER, "deserialize"),
            (DESERIALIZER, "deserializeParams"),
            (DESERIALIZER, "convert"),
            (DESERIALIZER, "deserializeEnvelope"),
            (DESERIALIZER, "metaObject"),
            (DESERIALIZER, "asString"),
        ],
    },
    "03": {
        "methods": [
            (ROUTER, "discoverResult"),
            (ROUTER, "envelopeFor"),
            (ROUTER, "acknowledgeSubscription"),
            (ID_ADAPTER, "write"),
            (ID_ADAPTER, "read"),
        ],
        # The notification switch is chapter 3's, but the request switch is
        # shared with later chapters, so only its guards come out here.
        "notification_switch": [(ROUTER, "process")],
        "prelude": [(ROUTER, "process")],
    },
    "04": {
        "cases": [
            (ROUTER, "PROMPTS_LIST"),
            (ROUTER, "PROMPTS_GET"),
            (ROUTER, "TOOLS_LIST"),
            (ROUTER, "TOOLS_CALL"),
            (ROUTER, "RESOURCES_LIST"),
            (ROUTER, "RESOURCES_TEMPLATES_LIST"),
            (ROUTER, "RESOURCES_READ"),
            (ROUTER, "COMPLETION_COMPLETE"),
        ],
    },
    "05": {
        "methods": [
            (CONTINUATION, "awaitingDirectory"),
            (CONTINUATION, "encode"),
            (CONTINUATION, "decode"),
            (ROUTER, "handleKeywordSearch"),
            (ROUTER, "elicitedDirectory"),
        ],
    },
    "06": {
        "methods": [
            (APP_META, "of"),
            (APP_TOOL_BUILDER, "withResourceUri"),
            (APP_TOOL_BUILDER, "build"),
        ],
    },
    "07": {
        "methods": [
            (TASK_STORE, "create"),
            (TASK_STORE, "requireInput"),
            (TASK_STORE, "awaitInput"),
            (TASK_STORE, "applyInput"),
            (TASK_STORE, "cancel"),
            (TASK_STORE, "complete"),
            (TASK_STORE, "fail"),
            (ROUTER, "runSearchAsTaskThatAsks"),
        ],
        "cases": [
            (ROUTER, "TASKS_GET"),
            (ROUTER, "TASKS_UPDATE"),
            (ROUTER, "TASKS_CANCEL"),
        ],
    },
}


def matching_brace(text, open_index):
    """Index of the `}` closing the `{` at open_index, skipping strings."""
    depth = 0
    i = open_index
    while i < len(text):
        c = text[i]
        if c == '"':  # string literal, including text blocks
            if text[i:i + 3] == '"""':
                end = text.index('"""', i + 3)
                i = end + 3
                continue
            i += 1
            while i < len(text) and text[i] != '"':
                i += 2 if text[i] == "\\" else 1
        elif c == "'":
            i += 1
            while i < len(text) and text[i] != "'":
                i += 2 if text[i] == "\\" else 1
        elif c == "/" and text[i:i + 2] == "//":
            i = text.index("\n", i)
            continue
        elif c == "/" and text[i:i + 2] == "/*":
            i = text.index("*/", i) + 2
            continue
        elif c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    raise ValueError("unbalanced braces")


def placeholder(return_type, indent, chapter, what):
    """The body a student replaces: nothing to do, or a loud stub."""
    if return_type == "void":
        return f"{indent}    // Chapter {chapter}: implement {what}.\n"
    return (
        f"{indent}    // Chapter {chapter}: implement {what}.\n"
        f"{indent}    throw new UnsupportedOperationException(\n"
        f'{indent}            "Chapter {chapter}: {what} is not implemented yet");\n'
    )


def hollow_method(text, name, chapter, path):
    """Empty the body of every overload of `name`.

    Matches are rewritten back to front so that earlier offsets stay valid.
    """
    pattern = re.compile(
        r"^([ \t]*)(?:(?:public|private|protected|static|final|synchronized)\s+)+"
        r"(?:<[^>]+>\s*)?"
        r"([\w.$]+(?:\s*<[^;{]*?>)?(?:\[\])?)\s+"
        + re.escape(name) + r"\s*\([^;{)]*\)\s*"
        r"(?:throws [\w.,\s]+?)?\{",
        re.M | re.S,
    )
    matches = list(pattern.finditer(text))
    if not matches:
        raise SystemExit(f"  !! no method '{name}' in {path}")

    for m in reversed(matches):
        indent, ret = m.group(1), m.group(2).strip()
        open_brace = text.index("{", m.end() - 1)
        close = matching_brace(text, open_brace)
        body = placeholder(ret, indent, chapter, f"{name}(...)")
        text = text[:open_brace] + "{\n" + body + indent + "}" + text[close + 1:]

    return text, len(matches)


def hollow_case(text, label, chapter):
    """Empty the block of a `case LABEL -> { ... }` arm."""
    m = re.search(r"^([ \t]*)case " + re.escape(label) + r" -> \{", text, re.M)
    if not m:
        raise SystemExit(f"  !! no case '{label}'")
    indent = m.group(1)
    open_brace = text.index("{", m.end() - 1)
    close = matching_brace(text, open_brace)
    body = f"{indent}    // Chapter {chapter}: answer {label}.\n"
    return text[:open_brace] + "{\n" + body + indent + "}" + text[close + 1:]


def hollow_prelude(text, chapter):
    """Empty the guards that run before the request switch."""
    m = re.search(
        r"^([ \t]*)private void process\(JsonRpcRequest message\) \{", text, re.M)
    if not m:
        raise SystemExit("  !! no process(JsonRpcRequest)")
    indent = m.group(1)
    open_brace = text.index("{", m.end() - 1)
    switch_at = text.index("switch (uniqueKey) {", open_brace)
    guard = (
        f"\n{indent}    // Chapter {chapter}: before anything is routed, answer\n"
        f"{indent}    // server/discover, reject a method this revision removed,\n"
        f"{indent}    // and validate the params._meta envelope. Declare the\n"
        f"{indent}    // uniqueKey and envelope the switch below needs.\n"
        f"{indent}    UniqueKeys uniqueKey = UniqueKeys.fromValue(message.method());\n"
        f"{indent}    RequestEnvelope envelope = deserializer.deserializeEnvelope(message);\n\n"
        f"{indent}    "
    )
    return text[:open_brace + 1] + guard + text[switch_at:]


def hollow_notification_switch(text, chapter):
    """Empty the body of process(JsonRpcNotification)."""
    m = re.search(
        r"^([ \t]*)private void process\(JsonRpcNotification message\) \{", text, re.M)
    if not m:
        raise SystemExit("  !! no process(JsonRpcNotification)")
    indent = m.group(1)
    open_brace = text.index("{", m.end() - 1)
    close = matching_brace(text, open_brace)
    body = (
        f"{indent}    // Chapter {chapter}: deserialize a cancellation's params\n"
        f"{indent}    // into NotificationCancelledParams and log it; ignore any\n"
        f"{indent}    // other notification rather than rejecting it.\n"
    )
    return text[:open_brace] + "{\n" + body + indent + "}" + text[close + 1:]


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in CHAPTERS:
        raise SystemExit(f"usage: {sys.argv[0]} <{'|'.join(sorted(CHAPTERS))}>")
    chapter = sys.argv[1]
    spec = CHAPTERS[chapter]

    edits = {}
    for path, name in spec.get("methods", []):
        edits.setdefault(path, path.read_text())
    for path, _ in spec.get("cases", []):
        edits.setdefault(path, path.read_text())
    for path, _ in spec.get("prelude", []):
        edits.setdefault(path, path.read_text())
    for path, _ in spec.get("notification_switch", []):
        edits.setdefault(path, path.read_text())

    for path, name in spec.get("methods", []):
        edits[path], n = hollow_method(edits[path], name, chapter, path)
        print(f"  {path.name}: emptied {name}() x{n}")
    for path, label in spec.get("cases", []):
        edits[path] = hollow_case(edits[path], label, chapter)
        print(f"  {path.name}: emptied case {label}")
    for path, _ in spec.get("notification_switch", []):
        edits[path] = hollow_notification_switch(edits[path], chapter)
        print(f"  {path.name}: emptied process(JsonRpcNotification)")
    for path, _ in spec.get("prelude", []):
        edits[path] = hollow_prelude(edits[path], chapter)
        print(f"  {path.name}: emptied the request guards")

    for path, text in edits.items():
        path.write_text(text)


if __name__ == "__main__":
    main()
