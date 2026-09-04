#!/usr/bin/env python3
"""stdio JSON-RPC verification harness for the 3-hour workshop walkthrough.

Spawns the built server JAR, drives it with line-delimited JSON-RPC over
stdin/stdout, and asserts on the responses. Each scenario maps to one or more
walkthrough steps (see lessons/presentation-3hr/walkthrough.md).

Usage:
    python3 lessons/presentation-3hr/verify/harness.py <scenario> [<scenario> ...]
    python3 lessons/presentation-3hr/verify/harness.py all

Exit code is the number of failed scenarios.
"""

import glob
import json
import os
import subprocess
import sys
import tempfile
import threading
import time

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
PROTOCOL_VERSION = "2025-11-25"


def find_jar():
    jars = glob.glob(os.path.join(REPO_ROOT, "build", "libs", "agent-mcp-workshop-*.jar"))
    if not jars:
        raise RuntimeError("No JAR found under build/libs — run ./gradlew build first")
    return max(jars, key=os.path.getmtime)


class Failure(Exception):
    pass


class Client:
    """One server process plus a background stdout reader."""

    def __init__(self):
        self.proc = subprocess.Popen(
            ["java", "-jar", find_jar()],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            cwd=REPO_ROOT,
            text=True,
            bufsize=1,
        )
        self.inbox = []          # parsed, not-yet-consumed messages
        self.raw_lines = []      # everything ever received (for diagnostics)
        self.cond = threading.Condition()
        self.reader = threading.Thread(target=self._read_loop, daemon=True)
        self.reader.start()

    def _read_loop(self):
        for line in self.proc.stdout:
            line = line.strip()
            if not line:
                continue
            try:
                msg = json.loads(line)
            except json.JSONDecodeError:
                msg = {"_raw": line}
            with self.cond:
                self.inbox.append(msg)
                self.raw_lines.append(line)
                self.cond.notify_all()

    def send(self, obj):
        if self.proc.poll() is not None:
            raise Failure(f"server process already exited (code {self.proc.returncode}) before send")
        self.proc.stdin.write(json.dumps(obj) + "\n")
        self.proc.stdin.flush()

    def request(self, id_, method, params=None):
        msg = {"jsonrpc": "2.0", "id": id_, "method": method}
        if params is not None:
            msg["params"] = params
        self.send(msg)

    def notify(self, method, params=None):
        msg = {"jsonrpc": "2.0", "method": method}
        if params is not None:
            msg["params"] = params
        self.send(msg)

    def respond(self, id_, result):
        self.send({"jsonrpc": "2.0", "id": id_, "result": result})

    def take(self, pred, timeout=8.0, desc="message"):
        """Wait for the first unconsumed message matching pred and consume it."""
        deadline = time.time() + timeout
        with self.cond:
            while True:
                for i, msg in enumerate(self.inbox):
                    if pred(msg):
                        return self.inbox.pop(i)
                remaining = deadline - time.time()
                if remaining <= 0:
                    raise Failure(
                        f"timed out waiting for {desc}; traffic so far: {self.raw_lines}"
                    )
                self.cond.wait(min(remaining, 0.25))

    def expect_response(self, id_, timeout=8.0):
        msg = self.take(
            lambda m: m.get("id") == id_ and ("result" in m or "error" in m),
            timeout,
            f"response to id={id_}",
        )
        return msg

    def expect_result(self, id_, timeout=8.0):
        msg = self.expect_response(id_, timeout)
        if "error" in msg:
            raise Failure(f"expected success for id={id_}, got error: {msg['error']}")
        return msg["result"]

    def expect_error(self, id_, code=None, timeout=8.0):
        msg = self.expect_response(id_, timeout)
        if "error" not in msg:
            raise Failure(f"expected error for id={id_}, got result: {msg.get('result')}")
        if code is not None and msg["error"].get("code") != code:
            raise Failure(f"expected error code {code} for id={id_}, got {msg['error']}")
        return msg["error"]

    def expect_request(self, method, id_=None, timeout=8.0):
        def pred(m):
            if m.get("method") != method:
                return False
            return id_ is None or m.get("id") == id_
        return self.take(pred, timeout, f"server request method={method} id={id_}")

    def expect_notification(self, method, pred=None, timeout=8.0):
        def match(m):
            if m.get("method") != method or "id" in m:
                return False
            return pred is None or pred(m)
        return self.take(match, timeout, f"notification method={method}")

    def expect_silence(self, id_, within=2.0):
        """Assert that no response to id_ arrives within the window."""
        deadline = time.time() + within
        with self.cond:
            while time.time() < deadline:
                for m in self.inbox:
                    if m.get("id") == id_ and ("result" in m or "error" in m):
                        raise Failure(f"expected NO response to id={id_} yet, but got: {m}")
                self.cond.wait(0.2)

    def log_file(self):
        pattern = os.path.join(REPO_ROOT, "logs", f"agent-mcp-workshop-{self.proc.pid}-*.log")
        matches = glob.glob(pattern)
        return matches[0] if matches else None

    def log_contains(self, needle, timeout=5.0):
        deadline = time.time() + timeout
        while time.time() < deadline:
            path = self.log_file()
            if path:
                with open(path, "r", errors="replace") as f:
                    if needle in f.read():
                        return True
            time.sleep(0.3)
        return False

    def close(self):
        try:
            if self.proc.poll() is None:
                self.proc.stdin.close()
                self.proc.wait(timeout=5)
        except Exception:
            self.proc.kill()


def make_keyword_dir(keyword="needleword", occurrences=3):
    d = tempfile.mkdtemp(prefix="mcp-harness-")
    with open(os.path.join(d, "sample.txt"), "w") as f:
        f.write(("something " + keyword + "\n") * occurrences)
    return d


def initialize(client, caps=None, id_=1):
    params = {
        "protocolVersion": PROTOCOL_VERSION,
        "capabilities": caps if caps is not None else {},
        "clientInfo": {"name": "walkthrough-harness", "version": "1.0"},
    }
    client.request(id_, "initialize", params)
    return client.expect_result(id_)


def set_roots(client, directory, name="harness-root"):
    """Run the roots flow: notifications/initialized -> answer roots/list."""
    client.notify("notifications/initialized")
    req = client.expect_request("roots/list", id_=-1000)
    client.respond(req["id"], {"roots": [{"uri": "file://" + directory, "name": name}]})


SCENARIOS = []


def scenario(name, steps):
    def wrap(fn):
        SCENARIOS.append((name, steps, fn))
        return fn
    return wrap


# ---------------------------------------------------------------- Section 2

@scenario("handshake", "step 6")
def s_handshake(c):
    result = initialize(c, {"roots": {"listChanged": True}, "sampling": {}})
    if result.get("protocolVersion") != PROTOCOL_VERSION:
        raise Failure(f"protocolVersion mismatch: {result}")
    if "capabilities" not in result or "serverInfo" not in result:
        raise Failure(f"initialize result missing capabilities/serverInfo: {result}")


@scenario("ping_sampling", "step 7")
def s_ping_sampling(c):
    initialize(c, {"sampling": {}})
    c.request(2, "ping")
    result = c.expect_result(2)
    if result != {}:
        raise Failure(f"ping should return an empty object, got: {result}")
    req = c.expect_request("sampling/createMessage", id_=-2000)
    if not req.get("params", {}).get("messages"):
        raise Failure(f"sampling/createMessage missing messages: {req}")


@scenario("roots", "step 8")
def s_roots(c):
    initialize(c, {"roots": {"listChanged": True}})
    c.notify("notifications/initialized")
    req = c.expect_request("roots/list", id_=-1000)
    c.respond(req["id"], {"roots": [{"uri": "file:///tmp", "name": "tmp"}]})
    if not c.log_contains("roots/list response — populated 1 root(s)"):
        raise Failure("log never showed roots being populated")
    c.notify("notifications/roots/list_changed")
    c.expect_request("roots/list", id_=-1000)


@scenario("cancelled", "step 9")
def s_cancelled(c):
    initialize(c)
    c.notify("notifications/cancelled", {"requestId": 99, "reason": "user changed mind"})
    if not c.log_contains("notifications/cancelled reason=user changed mind"):
        raise Failure("log never showed the cancellation reason being deserialized")
    c.request(3, "ping")
    c.expect_result(3)


# ---------------------------------------------------------------- Section 3

@scenario("resources_list", "step 11")
def s_resources_list(c):
    initialize(c)
    c.request(4, "resources/list")
    result = c.expect_result(4)
    resources = result.get("resources") or []
    if not resources:
        raise Failure(f"resources/list returned no resources: {result}")
    if result.get("nextCursor") != "pageNext":
        raise Failure(f"nextCursor should be 'pageNext': {result}")
    for r in resources:
        if not r.get("uri") or not r.get("name"):
            raise Failure(f"resource missing uri/name: {r}")


@scenario("resources_read", "step 12")
def s_resources_read(c):
    initialize(c)
    c.request(4, "resources/list")
    resources = c.expect_result(4)["resources"]
    uri = resources[0]["uri"]
    c.request(5, "resources/read", {"uri": uri})
    result = c.expect_result(5)
    contents = result.get("contents") or []
    if not contents or not contents[0].get("text"):
        raise Failure(f"resources/read returned no text for {uri}: {result}")
    if contents[0].get("uri") != uri:
        raise Failure(f"resources/read echoed wrong uri: {contents[0]}")
    c.request(6, "resources/read", {"uri": "javadoc/does-not-exist.html"})
    err_result = c.expect_result(6)
    if not err_result.get("isError"):
        raise Failure(f"bogus uri should produce isError=true result: {err_result}")


@scenario("tools_list", "step 14")
def s_tools_list(c):
    initialize(c)
    c.request(7, "tools/list")
    result = c.expect_result(7)
    tools = result.get("tools") or []
    match = [t for t in tools if t.get("name") == "key_word_search"]
    if not match:
        raise Failure(f"tools/list missing key_word_search: {result}")
    tool = match[0]
    if not tool.get("description") or not tool.get("inputSchema"):
        raise Failure(f"key_word_search missing description/inputSchema: {tool}")


@scenario("tools_call", "step 15")
def s_tools_call(c):
    d = make_keyword_dir()
    initialize(c, {"roots": {"listChanged": True}})
    set_roots(c, d)
    time.sleep(0.3)  # let the roots response land before calling the tool
    c.request(8, "tools/call", {"name": "key_word_search", "arguments": {"keyword": "needleword"}})
    result = c.expect_result(8)
    if result.get("isError"):
        raise Failure(f"tools/call returned error result: {result}")
    text = json.dumps(result.get("content") or [])
    if "keyword_count=3" not in text:
        raise Failure(f"expected keyword_count=3 in content: {result}")
    c.request(9, "tools/call", {"name": "no_such_tool", "arguments": {}})
    bad = c.expect_result(9)
    if not bad.get("isError"):
        raise Failure(f"unknown tool should be an error-shaped result: {bad}")


# ---------------------------------------------------------------- Section 4

@scenario("prompts_list", "step 16")
def s_prompts_list(c):
    initialize(c)
    c.request(9, "prompts/list")
    result = c.expect_result(9)
    prompts = result.get("prompts") or []
    match = [p for p in prompts if p.get("name") == "search_keyword"]
    if not match:
        raise Failure(f"prompts/list missing search_keyword: {result}")
    args = match[0].get("arguments") or []
    if not any(a.get("name") == "keyword" and a.get("required") for a in args):
        raise Failure(f"search_keyword missing required keyword argument: {match[0]}")


@scenario("prompts_get", "step 17")
def s_prompts_get(c):
    initialize(c)
    c.request(10, "prompts/get", {"name": "search_keyword", "arguments": {"keyword": "needle"}})
    result = c.expect_result(10)
    messages = result.get("messages") or []
    if result.get("description") != "keyword" or not messages:
        raise Failure(f"prompts/get unexpected shape: {result}")
    text = messages[0].get("content", {}).get("text", "")
    if "key_word_search" not in text:
        raise Failure(f"prompt message does not reference the tool: {messages[0]}")


@scenario("completion", "step 18")
def s_completion(c):
    initialize(c)
    c.request(11, "completion/complete", {
        "ref": {"type": "ref/prompt", "name": "search_keyword"},
        "argument": {"name": "keyword", "value": "ja"},
    })
    result = c.expect_result(11)
    values = (result.get("completion") or {}).get("values") or []
    if "java" not in values:
        raise Failure(f"completion should offer 'java': {result}")
    if result.get("total") != 3 or result.get("hasMore") is not True:
        raise Failure(f"completion total/hasMore unexpected: {result}")


# ---------------------------------------------------------------- Section 5

@scenario("elicit_capability", "step 19")
def s_elicit_capability(c):
    result = initialize(c, {"elicitation": {}})
    experimental = (result.get("capabilities") or {}).get("experimental") or {}
    if "io.modelcontextprotocol/elicitation" not in experimental:
        raise Failure(f"experimental elicitation capability not declared: {result}")


@scenario("elicit_defer", "step 21")
def s_elicit_defer(c):
    initialize(c, {"elicitation": {}})
    c.request(12, "tools/call", {"name": "key_word_search", "arguments": {"keyword": "needleword"}})
    req = c.expect_request("elicitation/create", id_=-4000)
    schema = (req.get("params") or {}).get("requestedSchema") or {}
    if "directory" not in (schema.get("required") or []):
        raise Failure(f"elicitation form should require 'directory': {req}")
    c.expect_silence(12, within=2.0)


@scenario("elicit_resume", "step 22")
def s_elicit_resume(c):
    d = make_keyword_dir()
    initialize(c, {"elicitation": {}})
    c.request(12, "tools/call", {"name": "key_word_search", "arguments": {"keyword": "needleword"}})
    req = c.expect_request("elicitation/create", id_=-4000)
    c.respond(req["id"], {"action": "accept", "content": {"directory": d}})
    result = c.expect_result(12)
    if result.get("isError"):
        raise Failure(f"resumed tools/call returned error: {result}")
    if "keyword_count=3" not in json.dumps(result.get("content") or []):
        raise Failure(f"resumed tools/call missing search results: {result}")
    # client-initiated elicitation/create must be acknowledged
    c.request(13, "elicitation/create", {"message": "hello"})
    ack = c.expect_result(13)
    if ack != {}:
        raise Failure(f"client-initiated elicitation/create should get empty ack: {ack}")


# ---------------------------------------------------------------- Section 6

@scenario("apps_capability", "step 23")
def s_apps_capability(c):
    result = initialize(c, {"elicitation": {}})
    experimental = (result.get("capabilities") or {}).get("experimental") or {}
    if "io.modelcontextprotocol/apps" not in experimental:
        raise Failure(f"experimental apps capability not declared: {result}")


@scenario("apps_tools_list", "step 24")
def s_apps_tools_list(c):
    initialize(c)
    c.request(14, "tools/list")
    result = c.expect_result(14)
    tools = result.get("tools") or []
    if not tools:
        raise Failure(f"tools/list returned no tools: {result}")
    meta = tools[0].get("_meta") or {}
    uri = (meta.get("ui") or {}).get("resourceUri")
    if uri != "ui://keyword-search/mcp-app.html":
        raise Failure(f"tool _meta.ui.resourceUri wrong: {tools[0]}")


@scenario("apps_resources_list", "step 25")
def s_apps_resources_list(c):
    initialize(c)
    c.request(15, "resources/list")
    result = c.expect_result(15)
    resources = result.get("resources") or []
    match = [r for r in resources if r.get("uri") == "ui://keyword-search/mcp-app.html"]
    if not match:
        raise Failure(f"resources/list missing ui:// app resource: {result}")
    if match[0].get("mimeType") != "text/html;profile=mcp-app":
        raise Failure(f"app resource mimeType wrong: {match[0]}")


@scenario("apps_resources_read", "step 26")
def s_apps_resources_read(c):
    initialize(c)
    c.request(16, "resources/read", {"uri": "ui://keyword-search/mcp-app.html"})
    result = c.expect_result(16)
    contents = result.get("contents") or []
    if not contents:
        raise Failure(f"ui:// read returned no contents: {result}")
    item = contents[0]
    if item.get("mimeType") != "text/html;profile=mcp-app":
        raise Failure(f"ui:// read mimeType wrong: {item}")
    if "<html" not in (item.get("text") or "").lower():
        raise Failure("ui:// read did not return HTML")
    # javadoc reads must still work after the upgrade
    c.request(17, "resources/list")
    resources = c.expect_result(17)["resources"]
    javadoc_uri = [r["uri"] for r in resources if r["uri"].startswith("javadoc/")][0]
    c.request(18, "resources/read", {"uri": javadoc_uri})
    jd = c.expect_result(18)
    if not (jd.get("contents") or [{}])[0].get("text"):
        raise Failure(f"javadoc read broke after ui:// upgrade: {jd}")


# ---------------------------------------------------------------- Section 7

@scenario("tasks_capability", "step 29")
def s_tasks_capability(c):
    result = initialize(c, {"tasks": {}})
    caps = result.get("capabilities") or {}
    if not caps.get("tasks"):
        raise Failure(f"initialize result missing tasks capability: {result}")


@scenario("tasks_tools_list", "step 30")
def s_tasks_tools_list(c):
    initialize(c, {"tasks": {}})
    c.request(19, "tools/list")
    result = c.expect_result(19)
    tool = (result.get("tools") or [{}])[0]
    execution = tool.get("execution") or {}
    if execution.get("taskSupport") != "optional":
        raise Failure(f"tool execution.taskSupport should be 'optional': {tool}")


def start_task(c, id_, keyword_dir, ttl=60000):
    c.request(id_, "tools/call", {
        "name": "key_word_search",
        "arguments": {"keyword": "needleword"},
        "task": {"ttl": ttl},
    })
    result = c.expect_result(id_)
    task = result.get("task") or {}
    task_id = task.get("taskId")
    if not task_id:
        raise Failure(f"task-augmented call did not return a task: {result}")
    meta = result.get("_meta") or {}
    related = meta.get("io.modelcontextprotocol/related-task") or {}
    if related.get("taskId") != task_id:
        raise Failure(f"CreateTaskResult missing related-task meta: {result}")
    return task_id, task


@scenario("task_call", "steps 28+31")
def s_task_call(c):
    d = make_keyword_dir()
    initialize(c, {"tasks": {}, "roots": {"listChanged": True}})
    set_roots(c, d)
    time.sleep(0.3)
    task_id, task = start_task(c, 20, d)
    if task.get("status") not in ("working", "submitted", "input_required"):
        raise Failure(f"fresh task has unexpected status: {task}")
    c.expect_notification(
        "notifications/tasks/status",
        pred=lambda m: (m.get("params") or {}).get("taskId") == task_id
        and (m.get("params") or {}).get("status") == "completed",
        timeout=12.0,
    )


@scenario("tasks_get_result", "step 32")
def s_tasks_get_result(c):
    d = make_keyword_dir()
    initialize(c, {"tasks": {}, "roots": {"listChanged": True}})
    set_roots(c, d)
    time.sleep(0.3)
    task_id, _ = start_task(c, 21, d)
    c.request(22, "tasks/get", {"taskId": task_id})
    snapshot = c.expect_result(22)
    if snapshot.get("taskId") != task_id:
        raise Failure(f"tasks/get returned wrong task: {snapshot}")
    c.request(23, "tasks/result", {"taskId": task_id})
    result = c.expect_result(23, timeout=15.0)
    if "keyword_count=3" not in json.dumps(result.get("content") or []):
        raise Failure(f"tasks/result payload missing search results: {result}")
    related = (result.get("_meta") or {}).get("io.modelcontextprotocol/related-task") or {}
    if related.get("taskId") != task_id:
        raise Failure(f"tasks/result missing related-task meta: {result}")
    c.request(24, "tasks/get", {"taskId": "no-such-task"})
    c.expect_error(24, code=-32602)


@scenario("tasks_list_cancel", "step 33")
def s_tasks_list_cancel(c):
    d = make_keyword_dir()
    initialize(c, {"tasks": {}, "roots": {"listChanged": True}})
    set_roots(c, d)
    time.sleep(0.3)
    task_id, _ = start_task(c, 25, d)
    c.request(26, "tasks/list")
    listed = c.expect_result(26)
    ids = [t.get("taskId") for t in (listed.get("tasks") or [])]
    if task_id not in ids:
        raise Failure(f"tasks/list missing {task_id}: {listed}")
    # cancel while still inside the 4s work window
    c.request(27, "tasks/cancel", {"taskId": task_id})
    cancelled = c.expect_result(27)
    if cancelled.get("status") != "cancelled":
        raise Failure(f"tasks/cancel should return cancelled snapshot: {cancelled}")
    c.request(28, "tasks/cancel", {"taskId": task_id})
    c.expect_error(28, code=-32602)
    c.request(29, "tasks/cancel", {"taskId": "no-such-task"})
    c.expect_error(29, code=-32602)


# ---------------------------------------------------------------- runner

def run(names):
    order = {name: (steps, fn) for name, steps, fn in SCENARIOS}
    if names == ["all"]:
        names = [name for name, _, _ in SCENARIOS]
    unknown = [n for n in names if n not in order]
    if unknown:
        print(f"unknown scenario(s): {unknown}; available: {list(order)}")
        return 2
    failures = 0
    for name in names:
        steps, fn = order[name]
        client = None
        try:
            client = Client()
            fn(client)
            print(f"[PASS] {name} ({steps})")
        except Failure as e:
            failures += 1
            print(f"[FAIL] {name} ({steps}): {e}")
        except Exception as e:
            failures += 1
            print(f"[FAIL] {name} ({steps}): unexpected {type(e).__name__}: {e}")
        finally:
            if client is not None:
                client.close()
    return failures


if __name__ == "__main__":
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        print("scenarios:", " ".join(name for name, _, _ in SCENARIOS))
        sys.exit(2)
    sys.exit(run(args))
