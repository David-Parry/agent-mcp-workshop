#!/usr/bin/env python3
"""stdio JSON-RPC verification harness for the 3-hour workshop deck.

Spawns the built server JAR, drives it with line-delimited JSON-RPC over
stdin/stdout, and asserts on the responses. Each scenario checks a claim the
deck makes about protocol revision 2026-07-28 — the stateless revision that
removed the initialize handshake.

Usage:
    python3 lessons/presentation-3hr/verify/harness.py <scenario> [<scenario> ...]
    python3 lessons/presentation-3hr/verify/harness.py all

Exit code is the number of failed scenarios.
"""

import base64
import glob
import json
import os
import subprocess
import sys
import tempfile
import threading
import time

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))

PROTOCOL_VERSION = "2026-07-28"
LEGACY_VERSION = "2025-11-25"

# The _meta slots that carry the lifecycle now that the handshake is gone.
META_PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion"
META_CLIENT_CAPABILITIES = "io.modelcontextprotocol/clientCapabilities"
META_CLIENT_INFO = "io.modelcontextprotocol/clientInfo"
META_SERVER_INFO = "io.modelcontextprotocol/serverInfo"
META_SUBSCRIPTION_ID = "io.modelcontextprotocol/subscriptionId"
TASKS_EXTENSION = "io.modelcontextprotocol/tasks"
UI_EXTENSION = "io.modelcontextprotocol/ui"

APP_URI = "ui://keyword-search/mcp-app.html"
APP_MIME_TYPE = "text/html;profile=mcp-app"

# Error codes. -32020..-32099 is reserved for the specification on this
# revision; -32000..-32019 stays implementation-defined.
METHOD_NOT_FOUND = -32601
INVALID_PARAMS = -32602
UNSUPPORTED_PROTOCOL_VERSION = -32022

# The seven optional slots a 2026-07-28 server may declare. Anything else,
# tasks included, belongs under extensions.
CAPABILITY_SLOTS = {
    "experimental", "logging", "completions", "prompts", "resources", "tools", "extensions",
}

# Exactly these five methods may carry freshness hints.
CACHEABLE_METHODS = [
    "tools/list", "prompts/list", "resources/list", "resources/templates/list", "resources/read",
]

# Client capability presets. These arrive per request now, not once.
#
# The roots declaration is deliberate: roots is deprecated under SEP-2577, which
# tells clients to keep declaring what they support for the whole transition, so
# a real client still sends this. The server models no roots capability at all,
# and these presets prove that the unrecognised key is simply ignored rather
# than upsetting the capability it does read.
FORMS_ONLY = {"elicitation": {"form": {}, "url": {}}}
DEPRECATED_AND_FORMS = {"roots": {"listChanged": True}, "sampling": {},
                        "elicitation": {"form": {}, "url": {}}}
TASK_CLIENT = {"roots": {"listChanged": True}, "extensions": {TASKS_EXTENSION: {}}}
TASK_CLIENT_WITH_FORMS = {"elicitation": {"form": {}, "url": {}},
                          "extensions": {TASKS_EXTENSION: {}}}
NO_CAPABILITIES = {}

# The key the keyword-search tool answers its one embedded request under.
KEY_DIRECTORY = "search_directory"

# The key the removed roots stage used to answer under. Kept only so the
# scenarios can assert it never appears again.
KEY_ROOTS_GONE = "search_roots"


def find_jar():
    jars = glob.glob(os.path.join(REPO_ROOT, "build", "libs", "agent-mcp-workshop-*.jar"))
    if not jars:
        raise RuntimeError("No JAR found under build/libs — run ./gradlew build first")
    return max(jars, key=os.path.getmtime)


def envelope(capabilities):
    """The _meta block every 2026-07-28 request has to carry.

    logLevel is deliberately omitted: while it is absent the server must not
    emit notifications/message, which the discover scenario asserts.
    """
    return {
        META_PROTOCOL_VERSION: PROTOCOL_VERSION,
        META_CLIENT_INFO: {"name": "walkthrough-harness", "version": "2.0"},
        META_CLIENT_CAPABILITIES: capabilities,
    }


def encode_state(keyword, stage):
    """Mint a requestState the way SearchContinuation does.

    Url-safe base64 of the continuation JSON, padding stripped. Real clients
    treat the value as opaque and echo back whatever the server sent; this is
    only for entering the exchange partway through.
    """
    raw = json.dumps({"keyword": keyword, "stage": stage}, separators=(",", ":"))
    return base64.urlsafe_b64encode(raw.encode()).decode().rstrip("=")


def decode_state(request_state):
    padded = request_state + "=" * (-len(request_state) % 4)
    return json.loads(base64.urlsafe_b64decode(padded).decode())


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

    def raw_request(self, id_, method, params=None):
        """Send a request exactly as given — for the envelope-validation scenarios."""
        msg = {"jsonrpc": "2.0", "id": id_, "method": method}
        if params is not None:
            msg["params"] = params
        self.send(msg)

    def request(self, id_, method, params=None, capabilities=DEPRECATED_AND_FORMS):
        """Send a request carrying the stateless _meta envelope."""
        body = dict(params or {})
        body["_meta"] = envelope(capabilities)
        self.raw_request(id_, method, body)

    def notify(self, method, params=None):
        msg = {"jsonrpc": "2.0", "method": method}
        if params is not None:
            msg["params"] = params
        self.send(msg)

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

    def expect_notification(self, method, pred=None, timeout=8.0):
        def match(m):
            if m.get("method") != method or "id" in m:
                return False
            return pred is None or pred(m)
        return self.take(match, timeout, f"notification method={method}")

    def saw_notification(self, method):
        """Whether a notification with this method has ever arrived."""
        with self.cond:
            return any(f'"method":"{method}"' in line for line in self.raw_lines)

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


def form_answer(directory):
    """The bare result an embedded elicitation/create would have returned."""
    return {"action": "accept", "content": {"directory": directory}}


def search_call(keyword="needleword", request_state=None, input_responses=None):
    params = {"name": "key_word_search", "arguments": {"keyword": keyword}}
    if request_state is not None:
        params["requestState"] = request_state
    if input_responses is not None:
        params["inputResponses"] = input_responses
    return params


def require_result_type(result, expected, label):
    actual = result.get("resultType")
    if actual != expected:
        raise Failure(f"{label} should carry resultType={expected!r}, got {actual!r}: {result}")


SCENARIOS = []


def scenario(name, topic):
    def wrap(fn):
        SCENARIOS.append((name, topic, fn))
        return fn
    return wrap


# ------------------------------------------------- Discovery replaces the handshake

@scenario("discover", "flow-diagram, capabilities")
def s_discover(c):
    # The Inspector probes with a string id, which is why RequestId exists.
    probe_id = "server-discover-probe-1"
    c.request(probe_id, "server/discover")
    response = c.expect_response(probe_id)

    if not isinstance(response.get("id"), str):
        raise Failure(
            f"a string id must echo back as a JSON string, not a number: {response.get('id')!r}"
        )

    result = response["result"]
    require_result_type(result, "complete", "server/discover")

    if result.get("supportedVersions") != [PROTOCOL_VERSION]:
        raise Failure(f"server/discover should advertise [{PROTOCOL_VERSION!r}]: {result}")
    if "protocolVersion" in result:
        raise Failure(f"the client picks from supportedVersions; there is no single protocolVersion: {result}")

    # serverInfo moved out of the body and into _meta.
    if "serverInfo" in result:
        raise Failure(f"serverInfo must not be a body field on this revision: {result}")
    server_info = (result.get("_meta") or {}).get(META_SERVER_INFO) or {}
    if server_info.get("name") != "agent-mcp-workshop":
        raise Failure(f"_meta.{META_SERVER_INFO} missing or wrong: {result.get('_meta')}")

    # No session was created, so nothing is remembered — but the answer must
    # still be self-describing enough for the client to cache it.
    if not isinstance(result.get("ttlMs"), int) or result["ttlMs"] < 0:
        raise Failure(f"server/discover needs a non-negative ttlMs: {result}")
    if result.get("cacheScope") not in ("public", "private"):
        raise Failure(f"server/discover cacheScope must be public or private: {result}")

    # No logLevel was declared, so the server must stay quiet.
    if c.saw_notification("notifications/message"):
        raise Failure("server emitted notifications/message without a logLevel in the envelope")


@scenario("capability_shape", "capabilities")
def s_capability_shape(c):
    c.request("probe", "server/discover")
    capabilities = c.expect_result("probe")["capabilities"]

    unknown = set(capabilities) - CAPABILITY_SLOTS
    if unknown:
        raise Failure(f"capabilities has slots outside the seven allowed: {sorted(unknown)}")
    if "tasks" in capabilities:
        raise Failure(f"the top-level tasks slot was removed; it lives under extensions: {capabilities}")

    extensions = capabilities.get("extensions") or {}
    for identifier in (TASKS_EXTENSION, UI_EXTENSION):
        if identifier not in extensions:
            raise Failure(f"{identifier} should be declared under capabilities.extensions: {extensions}")

    # subscribe survives even though resources/subscribe does not — it now
    # gates the resourceSubscriptions field of subscriptions/listen.
    if "subscribe" not in (capabilities.get("resources") or {}):
        raise Failure(f"resources.subscribe should still be declared: {capabilities}")

    # This server's lists are fixed, so it must not invite a listen stream.
    if (capabilities.get("tools") or {}).get("listChanged") is not False:
        raise Failure(f"tools.listChanged should be false on this server: {capabilities}")


# ------------------------------------------------- The per-request envelope

@scenario("envelope_required", "protocol, capabilities")
def s_envelope_required(c):
    # No _meta at all: the two required members are missing.
    c.raw_request(1, "tools/list", {})
    c.expect_error(1, code=INVALID_PARAMS)

    # A revision this server does not speak. The error data is the only way a
    # client learns what to renegotiate to, so it is required.
    c.raw_request(2, "tools/list", {"_meta": {
        META_PROTOCOL_VERSION: LEGACY_VERSION,
        META_CLIENT_CAPABILITIES: {},
    }})
    error = c.expect_error(2, code=UNSUPPORTED_PROTOCOL_VERSION)
    data = error.get("data") or {}
    if data.get("requested") != LEGACY_VERSION:
        raise Failure(f"-32022 data.requested should echo the rejected revision: {error}")
    if data.get("supported") != [PROTOCOL_VERSION]:
        raise Failure(f"-32022 data.supported should list this server's revisions: {error}")

    # Discovery is exempt: a client calls it precisely to find out which
    # revisions the server speaks, so rejecting the guess would be circular.
    c.raw_request(3, "server/discover", {"_meta": {META_PROTOCOL_VERSION: LEGACY_VERSION}})
    c.expect_result(3)


@scenario("removed_methods", "protocol, trace-log")
def s_removed_methods(c):
    # Deletion in this revision is physical: absence from the method registry.
    # Method existence is settled before the envelope is looked at, so a legacy
    # client is told the method is gone, not that its _meta is malformed.
    removed = [
        "initialize",
        "ping",
        "notifications/initialized",
        "logging/setLevel",
        "notifications/roots/list_changed",
        "resources/subscribe",
        "resources/unsubscribe",
        "tasks/result",
        "tasks/list",
    ]
    for i, method in enumerate(removed):
        c.raw_request(100 + i, method, {})
        error = c.expect_error(100 + i)
        if error.get("code") != METHOD_NOT_FOUND:
            raise Failure(
                f"{method} was removed in {PROTOCOL_VERSION} and must be {METHOD_NOT_FOUND}, got {error}"
            )


@scenario("embedded_only_methods", "extensions")
def s_embedded_only_methods(c):
    # These three are things a server asks for inside a result, never things it
    # answers, because a modern client discards inbound requests. All three are
    # rejected inbound even though this server embeds only the last of them:
    # roots and sampling are both deprecated under SEP-2577, so it builds
    # neither. Being deprecated does not make them answerable.
    for i, method in enumerate(["roots/list", "sampling/createMessage", "elicitation/create"]):
        c.request(200 + i, method)
        c.expect_error(200 + i, code=METHOD_NOT_FOUND)


# ------------------------------------------------- Cacheable vs. uncacheable results

@scenario("cacheable_results", "capabilities, protocol")
def s_cacheable_results(c):
    # resources/read needs a real uri, so list first and reuse one.
    c.request(1, "resources/list")
    resources = c.expect_result(1).get("resources") or []
    javadoc_uri = next(r["uri"] for r in resources if r["uri"].startswith("javadoc/"))

    params_for = {
        "resources/read": {"uri": javadoc_uri},
    }
    for i, method in enumerate(CACHEABLE_METHODS):
        id_ = 10 + i
        c.request(id_, method, params_for.get(method))
        result = c.expect_result(id_)
        require_result_type(result, "complete", method)
        ttl = result.get("ttlMs")
        if not isinstance(ttl, int) or isinstance(ttl, bool) or ttl < 0:
            raise Failure(f"{method} must carry a non-negative ttlMs, got {ttl!r}: {result}")
        if result.get("cacheScope") not in ("public", "private"):
            raise Failure(f"{method} must carry cacheScope public or private: {result}")

    # Newly load-bearing: clients fetch it whenever a server declares any
    # resource capability, so it has to be answered even when empty.
    c.request(30, "resources/templates/list")
    templates = c.expect_result(30)
    if templates.get("resourceTemplates") != []:
        raise Failure(f"resources/templates/list should answer with an empty list: {templates}")


@scenario("uncacheable_results", "capabilities")
def s_uncacheable_results(c):
    c.request(1, "prompts/get", {"name": "search_keyword", "arguments": {"keyword": "needle"}})
    prompts_get = c.expect_result(1)

    c.request(2, "completion/complete", {
        "ref": {"type": "ref/prompt", "name": "search_keyword"},
        "argument": {"name": "keyword", "value": "ja"},
    })
    completion = c.expect_result(2)

    for label, result in (("prompts/get", prompts_get), ("completion/complete", completion)):
        require_result_type(result, "complete", label)
        for hint in ("ttlMs", "cacheScope"):
            if hint in result:
                raise Failure(f"{label} is not cacheable and must not carry {hint}: {result}")

    if "java" not in ((completion.get("completion") or {}).get("values") or []):
        raise Failure(f"completion/complete should still offer 'java': {completion}")


# ------------------------------------------------- Multi Round-Trip Requests

@scenario("mrtr_elicitation", "extensions, flow-diagram")
def s_mrtr_elicitation(c):
    directory = make_keyword_dir()

    # Nothing to search yet, so the server asks the user for a directory. This
    # used to be a two-stage escalation that embedded a roots/list first and
    # only fell through to the form when the roots answer came back empty.
    # Roots is deprecated under SEP-2577, so the form is now the only question.
    c.request(1, "tools/call", search_call())
    asked = c.expect_result(1)
    require_result_type(asked, "input_required", "an unanswerable tools/call")

    requests = asked.get("inputRequests")
    if not isinstance(requests, dict):
        raise Failure(f"inputRequests is a keyed map, not an array: {asked}")
    if KEY_ROOTS_GONE in requests:
        raise Failure(f"the deprecated roots/list stage must not be embedded any more: {asked}")
    embedded = requests.get(KEY_DIRECTORY)
    if not embedded or embedded.get("method") != "elicitation/create":
        raise Failure(f"expected an embedded elicitation/create under {KEY_DIRECTORY!r}: {asked}")
    for absent in ("jsonrpc", "id"):
        if absent in embedded:
            raise Failure(f"an embedded request carries no {absent}: {embedded}")

    params = embedded.get("params") or {}
    if params.get("mode") != "form":
        raise Failure(f"elicitation params carry mode form|url on this revision: {embedded}")
    if "elicitationId" in params:
        raise Failure(f"elicitationId was removed — the retry is the completion signal: {embedded}")
    if "directory" not in ((params.get("requestedSchema") or {}).get("required") or []):
        raise Failure(f"the form should require 'directory': {embedded}")

    # There is no session, so the continuation travels to the client and back.
    directory_state = asked.get("requestState")
    if not isinstance(directory_state, str) or not directory_state:
        raise Failure(f"input_required must carry an opaque requestState: {asked}")
    if decode_state(directory_state) != {"keyword": "needleword", "stage": "directory"}:
        raise Failure(f"requestState did not preserve the keyword and stage: {directory_state}")

    # The retry is a brand new request with a brand new id, carrying the
    # answers plus the state echoed back byte-exact.
    c.request(2, "tools/call", search_call(
        request_state=directory_state,
        input_responses={KEY_DIRECTORY: form_answer(directory)},
    ))
    done = c.expect_result(2)
    require_result_type(done, "complete", "the elicited retry")
    if done.get("isError"):
        raise Failure(f"the answered retry should have run the search: {done}")
    if "keyword_count=3" not in json.dumps(done.get("content") or []):
        raise Failure(f"the elicited directory should have been searched: {done}")

    # Declining exhausts the asking, so the server searches its own working
    # directory rather than failing.
    c.request(3, "tools/call", search_call(
        request_state=directory_state,
        input_responses={KEY_DIRECTORY: {"action": "decline"}},
    ))
    declined = c.expect_result(3)
    require_result_type(declined, "complete", "a declined form")
    if declined.get("isError"):
        raise Failure(f"a declined form falls back to the working directory, it does not fail: {declined}")

    # A client that cannot render a form is not asked at all, and lands on the
    # same fallback.
    c.request(4, "tools/call", search_call(), capabilities=NO_CAPABILITIES)
    unasked = c.expect_result(4)
    require_result_type(unasked, "complete", "a call to a client with no answerable capability")
    if unasked.get("isError"):
        raise Failure(f"a client that cannot be asked still gets a search: {unasked}")

    # A client whose only answerable capability is the deprecated one the
    # server dropped is in exactly the same position.
    c.request(5, "tools/call", search_call(),
              capabilities={"roots": {"listChanged": True}})
    roots_only = c.expect_result(5)
    require_result_type(roots_only, "complete", "a call from a roots-only client")
    if roots_only.get("inputRequests"):
        raise Failure(f"a roots-only client must not be asked anything: {roots_only}")


@scenario("task_client_is_not_asked", "extensions, tasks")
def s_task_client_is_not_asked(c):
    """A request committed to a task handle must not answer input_required.

    The two are alternative result types for the same response, so the server
    has to decide the shape before the tool considers asking for anything.
    Getting this wrong is what produces the Inspector's "Unsupported result
    type 'input_required' for tools/call" — its task path never enables
    multi-round-trip auto-fulfilment.
    """
    # Declares elicitation AND tasks: the tool would like to ask, and must not.
    c.request(1, "tools/call", search_call(), capabilities=TASK_CLIENT_WITH_FORMS)
    handle = c.expect_result(1)
    require_result_type(handle, "task", "a tools/call from a task-declaring client")
    if not handle.get("taskId"):
        raise Failure(f"a task result must carry a taskId: {handle}")


@scenario("directory_argument", "extensions")
def s_directory_argument(c):
    """An explicit directory argument must complete without any round trip.

    MRTR is optional for clients, so a tool that can only be reached through
    an input_required is unusable by the ones that do not implement it.
    """
    directory = make_keyword_dir()

    call = search_call()
    call["arguments"]["directory"] = directory
    c.request(1, "tools/call", call)

    done = c.expect_result(1)
    require_result_type(done, "complete", "a tools/call carrying a directory argument")
    if done.get("isError"):
        raise Failure(f"a supplied directory should just be searched: {done}")
    if "keyword_count=3" not in json.dumps(done.get("content") or []):
        raise Failure(f"the supplied directory should have been searched: {done}")

    c.request(2, "tools/list")
    schema = ((c.expect_result(2).get("tools") or [{}])[0]).get("inputSchema") or {}
    if "directory" not in (schema.get("properties") or {}):
        raise Failure(f"the directory argument should be discoverable in the schema: {schema}")
    if "directory" in (schema.get("required") or []):
        raise Failure(f"the directory argument is optional, not required: {schema}")

    # The deck prints this schema as valid JSON Schema. PropertySchema carries a
    # key and a requiredness flag for the builder, both already expressed by the
    # enclosing schema and neither a JSON Schema keyword.
    for name, prop in (schema.get("properties") or {}).items():
        strays = sorted(set(prop) - {"type", "description"})
        if strays:
            raise Failure(
                f"property {name!r} writes non-JSON-Schema keywords {strays}: {prop}")
    if schema.get("required") != ["keyword"]:
        raise Failure(f"requiredness must survive in the required array: {schema}")


# ------------------------------------------------- Subscriptions

@scenario("subscriptions", "capabilities, flow-diagram")
def s_subscriptions(c):
    listen_id = "listen:0"
    c.request(listen_id, "subscriptions/listen", {"notifications": {
        "toolsListChanged": True,
        "resourcesListChanged": True,
    }})

    # Without the subscriptionId on the acknowledgment the client waits
    # forever, with no error and no timeout.
    ack = c.expect_notification("notifications/subscriptions/acknowledged")
    ack_params = ack.get("params") or {}
    if (ack_params.get("_meta") or {}).get(META_SUBSCRIPTION_ID) != listen_id:
        raise Failure(f"the acknowledgment must carry _meta.{META_SUBSCRIPTION_ID}: {ack}")

    # This server advertises no listChanged support, so nothing is honored and
    # the stream closes rather than dangling.
    if ack_params.get("notifications") != {}:
        raise Failure(f"nothing should be honored on this server: {ack}")

    close = c.expect_response(listen_id)
    if "result" not in close:
        raise Failure(f"a graceful close is a result, not an error: {close}")
    require_result_type(close["result"], "complete", "the subscription close")
    if (close["result"].get("_meta") or {}).get(META_SUBSCRIPTION_ID) != listen_id:
        raise Failure(f"the close result must carry the same subscriptionId: {close}")

    # A client tears a stream down by naming the listen id; requestId is
    # required on notifications/cancelled now.
    c.notify("notifications/cancelled", {"requestId": listen_id, "reason": "user navigated away"})
    if not c.log_contains(f"notifications/cancelled requestId={listen_id} reason=user navigated away"):
        raise Failure("the cancellation naming the listen request was never deserialized")


# ------------------------------------------------- Tasks extension

@scenario("tasks", "tasks, flow-diagram")
def s_tasks(c):
    directory = make_keyword_dir()

    # Task creation is the server's decision now; params.task was removed and
    # a handle only ever goes to a client that declared the extension.
    c.request(1, "tools/call", search_call(
        request_state=encode_state("needleword", "directory"),
        input_responses={KEY_DIRECTORY: form_answer(directory)},
    ), capabilities=TASK_CLIENT)
    handle = c.expect_result(1)
    require_result_type(handle, "task", "a tools/call from a task-capable client")

    task_id = handle.get("taskId")
    if not task_id:
        raise Failure(f"a task handle needs a taskId: {handle}")
    for renamed in ("ttlMs", "pollIntervalMs"):
        if renamed not in handle:
            raise Failure(f"the tasks extension renamed the poll hints; {renamed} missing: {handle}")
    if handle.get("status") != "working":
        raise Failure(f"a fresh task should be working: {handle}")

    # The bare method name; the notifications/tasks/ prefix is reserved.
    working = c.expect_notification(
        "notifications/tasks",
        pred=lambda m: (m.get("params") or {}).get("status") == "working",
    )
    if (working.get("params") or {}).get("taskId") != task_id:
        raise Failure(f"the status notification should carry the task snapshot: {working}")

    # Clients poll tasks/get, and the Inspector polls it with string ids.
    c.request("inspector-ext-1", "tasks/get", {"taskId": task_id}, capabilities=TASK_CLIENT)
    polled = c.expect_result("inspector-ext-1")
    require_result_type(polled, "complete", "tasks/get")
    if polled.get("taskId") != task_id or polled.get("status") not in ("working", "completed"):
        raise Failure(f"tasks/get should report this task's status: {polled}")

    # Poll until terminal, the way a client would.
    deadline = time.time() + 15.0
    poll = 2
    while time.time() < deadline:
        poll_id = f"inspector-ext-{poll}"
        c.request(poll_id, "tasks/get", {"taskId": task_id}, capabilities=TASK_CLIENT)
        snapshot = c.expect_result(poll_id)
        if snapshot.get("status") == "completed":
            underlying = snapshot.get("result") or {}
            if "keyword_count=3" not in json.dumps(underlying.get("content") or []):
                raise Failure(f"a completed tasks/get should carry the underlying result: {snapshot}")
            break
        poll += 1
        time.sleep(1.0)
    else:
        raise Failure(f"task {task_id} never reached completed")

    # A task id that was never issued must not be guessable or resolvable.
    c.request("inspector-ext-99", "tasks/get", {"taskId": "no-such-task"}, capabilities=TASK_CLIENT)
    c.expect_error("inspector-ext-99", code=INVALID_PARAMS)


@scenario("tasks_input_required", "tasks, extensions")
def s_tasks_input_required(c):
    """A task asks for input as a status, and tasks/update answers it.

    This is the task-level counterpart of the tools/call round trip. The call
    itself was already answered with a handle, and resultType holds one value,
    so the question cannot ride on that result. It surfaces as the task's own
    input_required status, which the client sees when it polls tasks/get.
    """
    directory = make_keyword_dir()

    # No directory, and no prior answers: the tool has nothing to search yet.
    # The client must be able to render a form, or the task would find nothing
    # worth asking and finish from the working directory without ever waiting.
    c.request(1, "tools/call", search_call(), capabilities=TASK_CLIENT_WITH_FORMS)
    handle = c.expect_result(1)
    require_result_type(handle, "task", "a tools/call from a task-declaring client")
    task_id = handle["taskId"]

    # Poll until the task publishes what it is waiting for.
    deadline = time.time() + 15.0
    poll = 0
    asked = None
    while time.time() < deadline:
        c.request(f"ask-{poll}", "tasks/get", {"taskId": task_id}, capabilities=TASK_CLIENT)
        snapshot = c.expect_result(f"ask-{poll}")
        if snapshot.get("status") == "input_required":
            asked = snapshot
            break
        poll += 1
        time.sleep(0.3)
    if asked is None:
        raise Failure(f"task {task_id} never asked for input")

    embedded = (asked.get("inputRequests") or {}).get(KEY_DIRECTORY)
    if not embedded or embedded.get("method") != "elicitation/create":
        raise Failure(f"a waiting task must publish its embedded request: {asked}")

    # tasks/update is the round trip. Before requireInput was wired up no task
    # ever reached input_required, so this only ever returned -32602.
    c.request(2, "tasks/update",
              {"taskId": task_id, "inputResponses": {KEY_DIRECTORY: form_answer(directory)}},
              capabilities=TASK_CLIENT)
    require_result_type(c.expect_result(2), "complete", "tasks/update answering what the task asked")

    # And the task resumes with that answer.
    deadline = time.time() + 15.0
    poll = 0
    while time.time() < deadline:
        c.request(f"done-{poll}", "tasks/get", {"taskId": task_id}, capabilities=TASK_CLIENT)
        snapshot = c.expect_result(f"done-{poll}")
        if snapshot.get("status") == "completed":
            underlying = snapshot.get("result") or {}
            if "keyword_count=3" not in json.dumps(underlying.get("content") or []):
                raise Failure(f"the task should have searched the directory it was given: {snapshot}")
            return
        poll += 1
        time.sleep(0.3)
    raise Failure(f"task {task_id} never resumed after tasks/update")


@scenario("tasks_gated", "tasks")
def s_tasks_gated(c):
    directory = make_keyword_dir()

    # The same call from a client that did not declare the extension runs
    # inline: handing a task to a client that cannot poll would strand it.
    c.request(1, "tools/call", search_call(
        request_state=encode_state("needleword", "directory"),
        input_responses={KEY_DIRECTORY: form_answer(directory)},
    ), capabilities=DEPRECATED_AND_FORMS)
    result = c.expect_result(1)
    require_result_type(result, "complete", "a tools/call from a client without the tasks extension")
    if "keyword_count=3" not in json.dumps(result.get("content") or []):
        raise Failure(f"the inline path should return the search hits: {result}")


# ------------------------------------------------- MCP Apps

@scenario("mcp_apps", "mcp-apps")
def s_mcp_apps(c):
    c.request(1, "tools/list")
    tool = (c.expect_result(1).get("tools") or [{}])[0]

    meta = tool.get("_meta") or {}
    if (meta.get("ui") or {}).get("resourceUri") != APP_URI:
        raise Failure(f"tool _meta.ui.resourceUri wrong: {tool}")
    if meta.get("ui/resourceUri") != APP_URI:
        raise Failure(f"the flat _meta['ui/resourceUri'] key is required too: {tool}")

    # Removed from tools/list: its taskSupport hint forced a warmup call.
    for gone in ("execution", "taskSupport"):
        if gone in tool:
            raise Failure(f"Tool.{gone} was removed from tools/list on this revision: {tool}")

    c.request(2, "resources/list")
    resources = c.expect_result(2).get("resources") or []
    app = next((r for r in resources if r.get("uri") == APP_URI), None)
    if not app or app.get("mimeType") != APP_MIME_TYPE:
        raise Failure(f"the app resource should be advertised as {APP_MIME_TYPE}: {app}")

    c.request(3, "resources/read", {"uri": APP_URI})
    contents = (c.expect_result(3).get("contents") or [{}])[0]
    if contents.get("mimeType") != APP_MIME_TYPE:
        raise Failure(f"reading the app resource should keep the mime type: {contents}")
    if "<html" not in (contents.get("text") or "").lower():
        raise Failure("the app resource did not return HTML")


# ---------------------------------------------------------------- runner

def run(names):
    order = {name: (topic, fn) for name, topic, fn in SCENARIOS}
    if names == ["all"]:
        names = [name for name, _, _ in SCENARIOS]
    unknown = [n for n in names if n not in order]
    if unknown:
        print(f"unknown scenario(s): {unknown}; available: {list(order)}")
        return 2
    failures = 0
    for name in names:
        topic, fn = order[name]
        client = None
        try:
            client = Client()
            fn(client)
            print(f"[PASS] {name} ({topic})")
        except Failure as e:
            failures += 1
            print(f"[FAIL] {name} ({topic}): {e}")
        except Exception as e:
            failures += 1
            print(f"[FAIL] {name} ({topic}): unexpected {type(e).__name__}: {e}")
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
