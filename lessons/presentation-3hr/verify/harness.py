#!/usr/bin/env python3
"""stdio JSON-RPC verification harness for the 3-hour workshop deck.

Spawns the built server JAR, drives it with line-delimited JSON-RPC over
stdin/stdout, and asserts on the responses. Each scenario checks a claim the
deck makes about protocol revision 2026-07-28 — the stateless revision that
removed the initialize handshake.

Usage:
    python3 lessons/presentation-3hr/verify/harness.py <scenario> [<scenario> ...]
    python3 lessons/presentation-3hr/verify/harness.py all
    python3 lessons/presentation-3hr/verify/harness.py --chapter 05

--chapter runs only the scenarios a chapter branch's server can pass: each
scenario names the chapter that builds what it checks. Chapter branches from
03 on ship this file, so a student can run it against their own server.

Exit code is the number of failed scenarios.
"""

import base64
import glob
import hashlib
import hmac
import json
import os
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

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
# revision; -32000..-32019 is legacy, and new implementations should not use it.
PARSE_ERROR = -32700
INVALID_REQUEST = -32600
METHOD_NOT_FOUND = -32601
INVALID_PARAMS = -32602
UNSUPPORTED_PROTOCOL_VERSION = -32022
MISSING_REQUIRED_CLIENT_CAPABILITY = -32021

# Retired by this revision: a conforming server must never emit either.
# -32002 was resource-not-found and is now INVALID_PARAMS; -32042 was the URL
# elicitation signal that existed only on 2025-11-25. Clients still accept
# -32002 from older servers, so the prohibition binds servers only.
RETIRED_ERROR_CODES = {-32002, -32042}

# The seven optional slots a 2026-07-28 server may declare. Anything else,
# tasks included, belongs under extensions.
CAPABILITY_SLOTS = {
    "experimental", "logging", "completions", "prompts", "resources", "tools", "extensions",
}

# Exactly these six methods carry freshness hints. server/discover belongs on
# the list: discovery is a read like any other, and a client that cached it can
# skip the probe entirely.
CACHEABLE_METHODS = [
    "server/discover",
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
# A client that can open a URL but cannot show a form.
URL_ONLY = {"elicitation": {"url": {}}}

# The key the keyword-search tool answers its directory question under.
KEY_DIRECTORY = "search_directory"
# The keyword question, asked beside it when the call has neither, and the
# URL-mode question that sends a URL-only client to a page instead.
KEY_KEYWORD = "search_keyword"
KEY_PAGE = "search_page"

# The server seals requestState with HMAC-SHA256, so entering the exchange
# partway through means signing the way the server does. Every server this
# harness starts is given this run's key, which is never printed.
STATE_KEY = os.urandom(32)
STATE_LIFETIME_MS = 10 * 60 * 1000
SERVER_ENV = {"WORKSHOP_STATE_KEY": base64.b64encode(STATE_KEY).decode()}

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


def b64url(data):
    return base64.urlsafe_b64encode(data).decode().rstrip("=")


def unb64url(text):
    return base64.urlsafe_b64decode(text + "=" * (-len(text) % 4))


def encode_state(stage, key=None, expires_in_ms=STATE_LIFETIME_MS, **planted):
    """Mint a requestState the way SearchContinuation and StateSigner do.

    The continuation JSON with its expiry, in epoch milliseconds, under exp;
    url-safe base64 with the padding stripped; then a dot and an HMAC-SHA256
    of that first part. Real clients treat the value as opaque and echo back
    whatever the server sent; this is only for entering the exchange partway
    through, or for forging a value the server never sent.
    """
    state = {**planted, "stage": stage, "exp": int(time.time() * 1000) + expires_in_ms}
    body = b64url(json.dumps(state, separators=(",", ":")).encode())
    return body + "." + b64url(hmac.new(key or STATE_KEY, body.encode(), hashlib.sha256).digest())


def decode_state(request_state):
    """Open a requestState the server sealed, checking it was signed with this run's key."""
    body, _, signature = request_state.partition(".")
    expected = hmac.new(STATE_KEY, body.encode(), hashlib.sha256).digest()
    if not signature or not hmac.compare_digest(expected, unb64url(signature)):
        raise Failure(f"requestState is not signed with the key the server was given: {request_state}")
    state = json.loads(unb64url(body).decode())
    state.pop("exp", None)
    return state


def http(method, url, form=None):
    """One request to a page the server serves, bypassing any proxy; returns (status, body)."""
    data = urllib.parse.urlencode(form).encode() if form is not None else None
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(urllib.request.Request(url, data=data, method=method), timeout=5) as response:
            return response.status, response.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


class Failure(Exception):
    pass


class Client:
    """One server process plus a background stdout reader."""

    def __init__(self, env=None):
        self.proc = subprocess.Popen(
            ["java", "-jar", find_jar()],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            cwd=REPO_ROOT,
            env={**os.environ, **SERVER_ENV, **(env or {})},
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
        self.send_line(json.dumps(obj))

    def send_line(self, text):
        """Send one line exactly as given, JSON or not."""
        if self.proc.poll() is not None:
            raise Failure(f"server process already exited (code {self.proc.returncode}) before send")
        self.proc.stdin.write(text + "\n")
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
        code = (msg.get("error") or {}).get("code")
        if code in RETIRED_ERROR_CODES:
            raise Failure(
                f"{code} was retired by {PROTOCOL_VERSION} and must never be emitted: {msg['error']}"
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


def scenario(name, topic, chapter, env=None):
    """Register a check; chapter is the first branch whose server can pass it.

    env is added to the server's environment, for the runtime switches the
    stretch goals read at startup.
    """
    def wrap(fn):
        SCENARIOS.append((name, topic, chapter, fn, env or {}))
        return fn
    return wrap


# ------------------------------------------------- Discovery replaces the handshake

@scenario("discover", "flow-diagram, capabilities", chapter=3)
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


def discovered_extensions(c, id_):
    c.request(id_, "server/discover")
    return c.expect_result(id_)["capabilities"].get("extensions") or {}


@scenario("capability_shape", "capabilities", chapter=3)
def s_capability_shape(c):
    c.request("probe", "server/discover")
    capabilities = c.expect_result("probe")["capabilities"]

    unknown = set(capabilities) - CAPABILITY_SLOTS
    if unknown:
        raise Failure(f"capabilities has slots outside the seven allowed: {sorted(unknown)}")
    if "tasks" in capabilities:
        raise Failure(f"the top-level tasks slot was removed; it lives under extensions: {capabilities}")

    # subscribe survives even though resources/subscribe does not — it now
    # gates the resourceSubscriptions field of subscriptions/listen.
    if "subscribe" not in (capabilities.get("resources") or {}):
        raise Failure(f"resources.subscribe should still be declared: {capabilities}")

    # The stretch server announces tool-list changes on a timer, so it invites
    # a listen stream for them. Its prompt and resource lists stay fixed.
    if (capabilities.get("tools") or {}).get("listChanged") is not True:
        raise Failure(f"tools.listChanged should be true while the timer is on: {capabilities}")
    if (capabilities.get("resources") or {}).get("listChanged") is not False:
        raise Failure(f"resources.listChanged should be false on this server: {capabilities}")


@scenario("list_changed_off", "capabilities", chapter=3, env={"WORKSHOP_LIST_CHANGED_MS": "0"})
def s_list_changed_off(c):
    # With the timer off the server has nothing to announce, so it advertises
    # nothing and a stream asking for tool changes closes at once.
    c.request("probe", "server/discover")
    capabilities = c.expect_result("probe")["capabilities"]
    if (capabilities.get("tools") or {}).get("listChanged") is not False:
        raise Failure(f"tools.listChanged should be false with the timer off: {capabilities}")

    c.request("listen:off", "subscriptions/listen", {"notifications": {"toolsListChanged": True}})
    ack = c.expect_notification("notifications/subscriptions/acknowledged")
    if (ack.get("params") or {}).get("notifications") != {}:
        raise Failure(f"nothing should be agreed with the timer off: {ack}")
    c.expect_result("listen:off")


# ------------------------------------------------- The per-request envelope

@scenario("envelope_required", "protocol, capabilities", chapter=3)
def s_envelope_required(c):
    # subscriptions/listen, because it is the one enveloped method chapter 3
    # already answers; tools/list would be -32601 there, and method existence
    # is settled before the envelope is read.
    #
    # No _meta at all: the two required members are missing.
    c.raw_request(1, "subscriptions/listen", {})
    c.expect_error(1, code=INVALID_PARAMS)

    # A revision this server does not speak. The error data is the only way a
    # client learns what to renegotiate to, so it is required.
    c.raw_request(2, "subscriptions/listen", {"_meta": {
        META_PROTOCOL_VERSION: LEGACY_VERSION,
        META_CLIENT_CAPABILITIES: {},
    }})
    error = c.expect_error(2, code=UNSUPPORTED_PROTOCOL_VERSION)
    data = error.get("data") or {}
    if data.get("requested") != LEGACY_VERSION:
        raise Failure(f"-32022 data.requested should echo the rejected revision: {error}")
    if data.get("supported") != [PROTOCOL_VERSION]:
        raise Failure(f"-32022 data.supported should list this server's revisions: {error}")

    # Discovery is exempt from the version check: a client calls it precisely
    # to find out which revisions the server speaks, so rejecting the guess
    # would be circular.
    c.raw_request(3, "server/discover", {"_meta": {
        META_PROTOCOL_VERSION: LEGACY_VERSION,
        META_CLIENT_CAPABILITIES: {},
    }})
    c.expect_result(3)

    # It is not exempt from the envelope, though: a request missing a
    # required member is malformed whatever its method.
    c.raw_request(4, "server/discover", {"_meta": {META_PROTOCOL_VERSION: PROTOCOL_VERSION}})
    c.expect_error(4, code=INVALID_PARAMS)


@scenario("removed_methods", "protocol, trace-log", chapter=3)
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
        # A client still on the handshake cannot move forward by itself, so
        # its error should say which revisions would work.
        if method == "initialize" and (error.get("data") or {}).get("supported") != [PROTOCOL_VERSION]:
            raise Failure(f"the initialize error should name the supported revisions: {error}")


@scenario("malformed_messages", "protocol, trace-log", chapter=3)
def s_malformed_messages(c):
    def id_less_error(desc):
        message = c.take(lambda m: "error" in m and "id" not in m, desc=desc)
        return message["error"].get("code")

    # Text that is not JSON has no id to quote back. The error goes out with
    # no id member at all: the schema has no null RequestId.
    c.send_line('{"jsonrpc":"2.0","id":1,"method":')
    code = id_less_error("the -32700 answering text that is not JSON")
    if code != PARSE_ERROR:
        raise Failure(f"text that is not JSON should be {PARSE_ERROR}, got {code}")

    # Well-formed JSON that is not a JSON-RPC 2.0 message is an invalid
    # request, not a parse error: a batch, and an object without "jsonrpc".
    for text in ('[{"jsonrpc":"2.0","id":2,"method":"server/discover"}]',
                 '{"id":3,"method":"server/discover"}'):
        c.send_line(text)
        code = id_less_error(f"the -32600 answering {text}")
        if code != INVALID_REQUEST:
            raise Failure(f"{text} should be {INVALID_REQUEST}, got {code}")

    # Method names are case-sensitive.
    c.request(4, "SERVER/DISCOVER")
    c.expect_error(4, code=METHOD_NOT_FOUND)


@scenario("embedded_only_methods", "extensions", chapter=3)
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

@scenario("cacheable_results", "capabilities, protocol", chapter=4)
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


@scenario("uncacheable_results", "capabilities", chapter=4)
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
    for field in ("total", "hasMore"):
        if field in completion:
            raise Failure(f"{field} belongs inside completion, not beside it: {completion}")


@scenario("unknown_names", "capabilities, protocol", chapter=4)
def s_unknown_names(c):
    # Naming a tool, prompt or resource the server does not have is a bad
    # parameter, not a result that reports failure. Resource-not-found used to
    # be -32002, which this revision retired in favour of -32602.
    unknown = [
        ("tools/call", {"name": "no_such_tool", "arguments": {}}),
        ("tools/call", {"name": "KEY_WORD_SEARCH", "arguments": {"keyword": "x"}}),
        ("prompts/get", {"name": "no_such_prompt", "arguments": {"keyword": "x"}}),
        ("prompts/get", {"name": "search_keyword", "arguments": {}}),
        ("resources/read", {"uri": "javadoc/com/workshop/mcp/spec/NoSuchPage.html"}),
        ("resources/read", {"uri": ""}),
    ]
    for i, (method, params) in enumerate(unknown):
        c.request(40 + i, method, params)
        c.expect_error(40 + i, code=INVALID_PARAMS)

    # A client pages until nextCursor is absent. prompts/list has one page and
    # ignores the cursor it is sent, so offering one would loop the client.
    c.request(50, "prompts/list")
    result = c.expect_result(50)
    if "nextCursor" in result:
        raise Failure(f"prompts/list has one page and must not offer a nextCursor: {result.get('nextCursor')}")


@scenario("resource_paging", "capabilities, protocol", chapter=4)
def s_resource_paging(c):
    # resources/list is cut into pages of 20 joined by opaque cursors.
    uris, scopes, cursor, page = [], set(), None, 0
    while True:
        params = {} if cursor is None else {"cursor": cursor}
        c.request(70 + page, "resources/list", params)
        result = c.expect_result(70 + page)
        resources = result.get("resources") or []
        if len(resources) > 20:
            raise Failure(f"page {page} has {len(resources)} entries; pages hold 20")
        uris.extend(r["uri"] for r in resources)
        scopes.add(result.get("cacheScope"))
        page += 1
        if "nextCursor" not in result:
            break
        cursor = result["nextCursor"]
        if cursor.isdigit():
            raise Failure(f"a cursor should be opaque, not a bare offset: {cursor!r}")
    if page < 2:
        raise Failure(f"the resource list should span several pages, got {page}")
    if len(uris) != len(set(uris)):
        raise Failure("an entry appeared on more than one page")
    if APP_URI not in uris[:20]:
        raise Failure("the app should be on the first page, where a host finds it without paging")
    if len(scopes) != 1:
        raise Failure(f"every page of one list must carry the same cacheScope: {scopes}")

    # A cursor the server never issued is an error, not the first page again.
    # The empty string is a valid cursor in general, but not one this server issues.
    for i, bogus in enumerate(("", "not-a-cursor")):
        c.request(90 + i, "resources/list", {"cursor": bogus})
        c.expect_error(90 + i, code=INVALID_PARAMS)


@scenario("completion_prefix", "capabilities", chapter=4)
def s_completion_prefix(c):
    def complete(id_, typed):
        c.request(id_, "completion/complete", {
            "ref": {"type": "ref/prompt", "name": "search_keyword"},
            "argument": {"name": "keyword", "value": typed},
        })
        return c.expect_result(id_)["completion"]

    for i, typed in enumerate(("re", "RE")):
        completion = complete(100 + i, typed)
        values = completion.get("values") or []
        if not values or not all(v.lower().startswith("re") for v in values):
            raise Failure(f"only words starting with {typed!r} should be offered: {values}")
        if completion.get("total") != len(values) or completion.get("hasMore") is not False:
            raise Failure(f"total should count the matches, and nothing is left over: {completion}")

    nothing = complete(102, "zzz")
    if nothing.get("values") != [] or nothing.get("total") != 0:
        raise Failure(f"a prefix nothing starts with should offer nothing: {nothing}")


@scenario("structured_content", "extensions", chapter=4)
def s_structured_content(c):
    c.request(110, "tools/list")
    tool = c.expect_result(110)["tools"][0]
    schema = tool.get("outputSchema") or {}
    if schema.get("type") != "object" or set(schema.get("required") or []) != {"keyword", "totalMatches", "files"}:
        raise Failure(f"key_word_search should declare an object outputSchema: {schema}")

    directory = make_keyword_dir(occurrences=3)
    with open(os.path.join(directory, "second.txt"), "w") as f:
        f.write("needleword\n")
    c.request(111, "tools/call", {"name": "key_word_search",
                                  "arguments": {"keyword": "needleword", "directory": directory}},
              capabilities=NO_CAPABILITIES)
    result = c.expect_result(111)
    structured = result.get("structuredContent") or {}
    files = structured.get("files") or []
    if structured.get("keyword") != "needleword" or structured.get("totalMatches") != 4 or len(files) != 2:
        raise Failure(f"structuredContent should hold the keyword, the total and one entry per file: {structured}")
    if any(set(f) != {"path", "count"} for f in files):
        raise Failure(f"each file entry is a path and a count: {files}")
    if len(result.get("content") or []) != 2:
        raise Failure(f"the text content a model reads should still be there: {result.get('content')}")


# ------------------------------------------------- Multi Round-Trip Requests

@scenario("mrtr_elicitation", "extensions, flow-diagram", chapter=5)
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
    if decode_state(directory_state) != {"stage": "directory"}:
        raise Failure(f"requestState should hold the stage and nothing the retry repeats: {directory_state}")

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

    # Even a state the server signed is not where the keyword comes from. A
    # keyword planted in one must not replace the one the retried call's
    # arguments name.
    c.request(6, "tools/call", search_call(
        request_state=encode_state("directory", keyword="absentword"),
        input_responses={KEY_DIRECTORY: form_answer(directory)},
    ))
    forged = c.expect_result(6)
    if "keyword_count=3" not in json.dumps(forged.get("content") or []):
        raise Failure(f"the search must look for the argument's keyword, not the state's: {forged}")

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

    # An elicitation object that names no mode is how a client declared form
    # before there were modes to name, so it is asked like one.
    c.request(7, "tools/call", search_call(), capabilities={"elicitation": {}})
    require_result_type(c.expect_result(7), "input_required", "a call from a client declaring elicitation: {}")


@scenario("sealed_state", "extensions", chapter=5)
def s_sealed_state(c):
    """requestState is signed and expires: a client can carry it, not forge or replay it.

    Each value goes back with a declined form. A server that believed it would
    treat the question as asked and search its working directory; one that
    sees through it treats the retry as a first attempt and asks again.
    """
    decline = {KEY_DIRECTORY: {"action": "decline"}}
    genuine = encode_state("directory")
    forgeries = [
        ("an unsigned state", b64url(json.dumps({"stage": "directory"}, separators=(",", ":")).encode())),
        ("a state signed with another key", encode_state("directory", key=os.urandom(32))),
        ("a state past its ten minutes", encode_state("directory", expires_in_ms=-1000)),
        ("a state whose body was altered",
         encode_state("keyword_and_directory").partition(".")[0] + "." + genuine.partition(".")[2]),
    ]
    for id_, (what, state) in enumerate(forgeries, start=1):
        c.request(id_, "tools/call", search_call(request_state=state, input_responses=decline))
        require_result_type(c.expect_result(id_), "input_required", f"{what}, treated as a first attempt,")

    c.request(9, "tools/call", search_call(request_state=genuine, input_responses=decline))
    require_result_type(c.expect_result(9), "complete", "a decline carrying a genuine state")


@scenario("two_questions", "extensions, flow-diagram", chapter=5)
def s_two_questions(c):
    """A call with neither a keyword nor a directory is asked for both in one round."""
    directory = make_keyword_dir()

    call = search_call()
    del call["arguments"]["keyword"]
    c.request(1, "tools/call", call)
    asked = c.expect_result(1)
    require_result_type(asked, "input_required", "a call with neither a keyword nor a directory")
    keys = list((asked.get("inputRequests") or {}).keys())
    if keys != [KEY_KEYWORD, KEY_DIRECTORY]:
        raise Failure(f"both questions belong in one round, keyword first: {keys}")
    keyword_form = asked["inputRequests"][KEY_KEYWORD].get("params") or {}
    if "keyword" not in ((keyword_form.get("requestedSchema") or {}).get("required") or []):
        raise Failure(f"the keyword form should require 'keyword': {keyword_form}")
    if decode_state(asked["requestState"]) != {"stage": "keyword_and_directory"}:
        raise Failure(f"the state should record that both were asked: {asked['requestState']}")

    retry = search_call(request_state=asked["requestState"], input_responses={
        KEY_KEYWORD: {"action": "accept", "content": {"keyword": "needleword"}},
        KEY_DIRECTORY: form_answer(directory),
    })
    del retry["arguments"]["keyword"]
    c.request(2, "tools/call", retry)
    done = c.expect_result(2)
    require_result_type(done, "complete", "the retry answering both")
    if "keyword_count=3" not in json.dumps(done.get("content") or []):
        raise Failure(f"the answered keyword should have been searched for in the answered directory: {done}")


@scenario("url_elicitation", "extensions, flow-diagram", chapter=5)
def s_url_elicitation(c):
    """A client that can open a URL but cannot show a form is sent to a page."""
    directory = make_keyword_dir()

    c.request(1, "tools/call", search_call(), capabilities=URL_ONLY)
    asked = c.expect_result(1)
    require_result_type(asked, "input_required", "a call from a URL-only client")
    embedded = (asked.get("inputRequests") or {}).get(KEY_PAGE) or {}
    params = embedded.get("params") or {}
    if embedded.get("method") != "elicitation/create" or params.get("mode") != "url":
        raise Failure(f"expected a URL-mode elicitation/create under {KEY_PAGE!r}: {asked}")
    url = params.get("url") or ""
    if not url.startswith("http://127.0.0.1:"):
        raise Failure(f"the page should be served on the loopback interface only: {params}")
    for absent in ("requestedSchema", "elicitationId"):
        if absent in params:
            raise Failure(f"a URL-mode elicitation carries no {absent}: {params}")
    state = decode_state(asked["requestState"])
    if state.get("stage") != "page" or not url.endswith("/" + str(state.get("token"))):
        raise Failure(f"the state should find the page again: {state}")

    # Confirming before the page was submitted sends the user back to it.
    confirm = {KEY_PAGE: {"action": "accept"}}
    c.request(2, "tools/call", search_call(request_state=asked["requestState"], input_responses=confirm),
              capabilities=URL_ONLY)
    again = c.expect_result(2)
    require_result_type(again, "input_required", "a confirmation before the page was submitted")
    if (((again.get("inputRequests") or {}).get(KEY_PAGE) or {}).get("params") or {}).get("url") != url:
        raise Failure(f"the user should be sent back to the same page: {again}")

    status, page = http("GET", url)
    if status != 200 or 'name="directory"' not in page:
        raise Failure(f"the page should be a form asking for a directory: {status} {page[:200]}")
    status, _ = http("POST", url, {"directory": directory})
    if status != 200:
        raise Failure(f"submitting the page should succeed, got {status}")

    c.request(3, "tools/call", search_call(request_state=again["requestState"], input_responses=confirm),
              capabilities=URL_ONLY)
    done = c.expect_result(3)
    require_result_type(done, "complete", "the retry after the page was submitted")
    if "keyword_count=3" not in json.dumps(done.get("content") or []):
        raise Failure(f"the directory submitted on the page should have been searched: {done}")
    if http("GET", url)[0] != 404:
        raise Failure("a page whose answer was taken should be gone")


@scenario("task_client_is_not_asked", "extensions, tasks", chapter=7)
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


@scenario("directory_argument", "extensions", chapter=4)
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

@scenario("subscriptions", "capabilities, flow-diagram", chapter=3, env={"WORKSHOP_LIST_CHANGED_MS": "200"})
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

    # Only what the server advertised is agreed: tool-list changes, which the
    # timer announces, and not resource-list changes, which never happen.
    if ack_params.get("notifications") != {"toolsListChanged": True}:
        raise Failure(f"only toolsListChanged should be agreed: {ack}")

    # The stream stays open, and the timer tells it the tool list changed.
    changed = c.expect_notification("notifications/tools/list_changed", timeout=5.0)
    if ((changed.get("params") or {}).get("_meta") or {}).get(META_SUBSCRIPTION_ID) != listen_id:
        raise Failure(f"a stream notification must carry _meta.{META_SUBSCRIPTION_ID}: {changed}")

    # A stream asking only for what the server never sends is closed at once.
    closed_id = "listen:1"
    c.request(closed_id, "subscriptions/listen", {"notifications": {"resourcesListChanged": True}})
    ack = c.expect_notification("notifications/subscriptions/acknowledged",
                                lambda m: m["params"]["_meta"].get(META_SUBSCRIPTION_ID) == closed_id)
    if ack["params"].get("notifications") != {}:
        raise Failure(f"nothing should be agreed for resource-list changes: {ack}")
    close = c.expect_response(closed_id)
    if "result" not in close:
        raise Failure(f"a graceful close is a result, not an error: {close}")
    require_result_type(close["result"], "complete", "the subscription close")
    if (close["result"].get("_meta") or {}).get(META_SUBSCRIPTION_ID) != closed_id:
        raise Failure(f"the close result must carry the same subscriptionId: {close}")
    # Every result names the server, not only discover's.
    if ((close["result"].get("_meta") or {}).get(META_SERVER_INFO) or {}).get("name") != "agent-mcp-workshop":
        raise Failure(f"the close result should carry _meta.{META_SERVER_INFO} too: {close}")

    # A client tears a stream down by naming the listen id; requestId is
    # required on notifications/cancelled now. A cancelled request is never
    # answered, so the open stream is forgotten rather than closed.
    c.notify("notifications/cancelled", {"requestId": listen_id, "reason": "user navigated away"})
    if not c.log_contains(f"notifications/cancelled requestId={listen_id} reason=user navigated away"):
        raise Failure("the cancellation naming the listen request was never deserialized")
    if not c.log_contains(f"the client closed stream {listen_id}"):
        raise Failure("the cancelled stream was not forgotten")
    time.sleep(0.5)
    with c.cond:
        c.inbox.clear()
    time.sleep(0.6)
    with c.cond:
        late = [m for m in c.inbox if m.get("method") == "notifications/tools/list_changed" or m.get("id") == listen_id]
    if late:
        raise Failure(f"a cancelled stream should hear nothing more: {late}")


@scenario("require_form_for_listen", "capabilities, protocol", chapter=3,
          env={"WORKSHOP_REQUIRE_FORM_FOR_LISTEN": "true", "WORKSHOP_LIST_CHANGED_MS": "0"})
def s_require_form_for_listen(c):
    # -32021 is for a request the server cannot serve without something the
    # client did not declare; data.requiredCapabilities says what.
    c.request("listen:no-form", "subscriptions/listen", capabilities=NO_CAPABILITIES)
    error = c.expect_error("listen:no-form", MISSING_REQUIRED_CLIENT_CAPABILITY)
    if (error.get("data") or {}).get("requiredCapabilities") != {"elicitation": {"form": {}}}:
        raise Failure(f"requiredCapabilities should name elicitation.form: {error}")

    c.request("listen:form", "subscriptions/listen", capabilities=FORMS_ONLY)
    c.expect_notification("notifications/subscriptions/acknowledged")
    c.expect_result("listen:form")


# ------------------------------------------------- Tasks extension

@scenario("tasks", "tasks, flow-diagram", chapter=7)
def s_tasks(c):
    directory = make_keyword_dir()

    if TASKS_EXTENSION not in discovered_extensions(c, "probe"):
        raise Failure(f"{TASKS_EXTENSION} should be declared under capabilities.extensions")

    # Task creation is the server's decision now; params.task was removed and
    # a handle only ever goes to a client that declared the extension.
    c.request(1, "tools/call", search_call(
        request_state=encode_state("directory"),
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

    # A server must not send a notification type nobody asked for, and the
    # only way to ask for notifications/tasks is a subscriptions/listen that
    # names the task. This client only polled.
    if c.saw_notification("notifications/tasks"):
        raise Failure(f"notifications/tasks were pushed without a subscription: {c.raw_lines}")

    # Cancelling only asks: a finished task is acknowledged with an empty
    # result and keeps its status. So is an answer it is no longer waiting for.
    c.request("cancel-done", "tasks/cancel", {"taskId": task_id}, capabilities=TASK_CLIENT)
    require_empty_acknowledgement(c.expect_result("cancel-done"), "tasks/cancel on a finished task")
    c.request("update-done", "tasks/update",
              {"taskId": task_id, "inputResponses": {KEY_DIRECTORY: {"action": "decline"}}},
              capabilities=TASK_CLIENT)
    require_empty_acknowledgement(c.expect_result("update-done"), "tasks/update on a finished task")
    c.request("after", "tasks/get", {"taskId": task_id}, capabilities=TASK_CLIENT)
    if c.expect_result("after").get("status") != "completed":
        raise Failure("a terminal status must never change")

    # A task id that was never issued must not be guessable or resolvable.
    c.request("inspector-ext-99", "tasks/get", {"taskId": "no-such-task"}, capabilities=TASK_CLIENT)
    c.expect_error("inspector-ext-99", code=INVALID_PARAMS)

    # Capabilities travel with each request, so a task method from a client
    # that left the extension off is refused, even with a real task id.
    for method in ("tasks/get", "tasks/update", "tasks/cancel"):
        c.request(f"undeclared-{method}", method, {"taskId": task_id}, capabilities=DEPRECATED_AND_FORMS)
        error = c.expect_error(f"undeclared-{method}", code=MISSING_REQUIRED_CLIENT_CAPABILITY)
        required = ((error.get("data") or {}).get("requiredCapabilities") or {}).get("extensions") or {}
        if TASKS_EXTENSION not in required:
            raise Failure(f"-32021 should name the extension the client has to declare: {error}")

    # So is a listen stream that asks for task status. A client that declared
    # the extension is agreed no task ids, because this server pushes none,
    # and with nothing agreed to its stream closes.
    task_status = {"notifications": {"taskIds": [task_id]}}
    c.request("listen-undeclared", "subscriptions/listen", task_status, capabilities=DEPRECATED_AND_FORMS)
    error = c.expect_error("listen-undeclared", code=MISSING_REQUIRED_CLIENT_CAPABILITY)
    required = ((error.get("data") or {}).get("requiredCapabilities") or {}).get("extensions") or {}
    if TASKS_EXTENSION not in required:
        raise Failure(f"-32021 should name the extension the client has to declare: {error}")
    c.request("listen-declared", "subscriptions/listen", task_status, capabilities=TASK_CLIENT)
    ack = c.expect_notification(
        "notifications/subscriptions/acknowledged",
        lambda n: ((n.get("params") or {}).get("_meta") or {}).get(META_SUBSCRIPTION_ID) == "listen-declared",
    )
    if "taskIds" in ((ack.get("params") or {}).get("notifications") or {}):
        raise Failure(f"this server pushes no task status, so it agrees to no task ids: {ack}")
    require_result_type(c.expect_result("listen-declared"), "complete", "the subscription close")


def require_empty_acknowledgement(result, what):
    """tasks/update and tasks/cancel acknowledge with an empty result: no task fields."""
    require_result_type(result, "complete", what)
    extra = set(result) - {"resultType", "_meta"}
    if extra:
        raise Failure(f"{what} should be an empty acknowledgement, but carries {sorted(extra)}: {result}")


@scenario("tasks_input_required", "tasks, extensions", chapter=7)
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

    # An answer under a key the task never asked is ignored, not refused, and
    # the task goes on waiting for the one it did ask.
    c.request("stray", "tasks/update",
              {"taskId": task_id, "inputResponses": {"not_a_key_it_asked": {"action": "decline"}}},
              capabilities=TASK_CLIENT)
    require_empty_acknowledgement(c.expect_result("stray"), "tasks/update answering nothing the task asked")
    c.request("still-asking", "tasks/get", {"taskId": task_id}, capabilities=TASK_CLIENT)
    if c.expect_result("still-asking").get("status") != "input_required":
        raise Failure("an update that answers nothing the task asked must leave it waiting")

    # tasks/update is the round trip.
    c.request(2, "tasks/update",
              {"taskId": task_id, "inputResponses": {KEY_DIRECTORY: form_answer(directory)}},
              capabilities=TASK_CLIENT)
    require_empty_acknowledgement(c.expect_result(2), "tasks/update answering what the task asked")

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


@scenario("tasks_gated", "tasks", chapter=7)
def s_tasks_gated(c):
    directory = make_keyword_dir()

    # The same call from a client that did not declare the extension runs
    # inline: handing a task to a client that cannot poll would strand it.
    c.request(1, "tools/call", search_call(
        request_state=encode_state("directory"),
        input_responses={KEY_DIRECTORY: form_answer(directory)},
    ), capabilities=DEPRECATED_AND_FORMS)
    result = c.expect_result(1)
    require_result_type(result, "complete", "a tools/call from a client without the tasks extension")
    if "keyword_count=3" not in json.dumps(result.get("content") or []):
        raise Failure(f"the inline path should return the search hits: {result}")


# ------------------------------------------------- MCP Apps

@scenario("mcp_apps", "mcp-apps", chapter=6)
def s_mcp_apps(c):
    extensions = discovered_extensions(c, "probe")
    if UI_EXTENSION not in extensions:
        raise Failure(f"{UI_EXTENSION} should be declared under capabilities.extensions")
    # mimeTypes is a host's setting, the list of what it can render. MCP Apps
    # defines no settings for a server.
    if extensions[UI_EXTENSION] != {}:
        raise Failure(f"{UI_EXTENSION} should be declared with no settings: {extensions[UI_EXTENSION]}")

    c.request(1, "tools/list")
    tool = (c.expect_result(1).get("tools") or [{}])[0]

    meta = tool.get("_meta") or {}
    if (meta.get("ui") or {}).get("resourceUri") != APP_URI:
        raise Failure(f"tool _meta.ui.resourceUri wrong: {tool}")
    # The flat key is deprecated by the MCP Apps extension in favour of the
    # nested one, and this server does not send it.
    if "ui/resourceUri" in meta:
        raise Failure(f"the deprecated flat _meta['ui/resourceUri'] key should not be sent: {tool}")

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
    order = {name: (topic, fn, env) for name, topic, _, fn, env in SCENARIOS}
    if names == ["all"]:
        names = [name for name, _, _, _, _ in SCENARIOS]
    unknown = [n for n in names if n not in order]
    if unknown:
        print(f"unknown scenario(s): {unknown}; available: {list(order)}")
        return 2
    failures = 0
    for name in names:
        topic, fn, env = order[name]
        client = None
        try:
            client = Client(env)
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


def reached(chapter_arg):
    chapter = int(chapter_arg)
    if not 3 <= chapter <= 7:
        print(f"There is no harness for chapter {chapter_arg}: it starts at 03, the first "
              "chapter whose server answers requests, and ends at 07.")
        sys.exit(2)
    return [name for name, _, first, _, _ in SCENARIOS if first <= chapter]


if __name__ == "__main__":
    args = sys.argv[1:]
    if len(args) == 2 and args[0] == "--chapter":
        sys.exit(run(reached(args[1])))
    if not args:
        print(__doc__)
        print("scenarios:", " ".join(name for name, _, _, _, _ in SCENARIOS))
        sys.exit(2)
    sys.exit(run(args))
