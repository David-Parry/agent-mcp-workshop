#!/usr/bin/env python3
"""Build a cumulative chapter slice from the finished `complete` tree.

Chapter N keeps chapters 1..N-1 already implemented, hollows chapter N's
student work, and deletes later-chapter code, tests, and lessons.

Usage (from a checkout of complete):
    python3 tools/slice_chapter.py 04
    python3 tools/slice_chapter.py 04 --keep-filled   # skip hollowing (verify compile)
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/java/com/workshop/mcp"

CHAPTER_TITLES = {
    "01": "The Transport Layer",
    "02": "Reading JSON-RPC Messages",
    "03": "Discovery and Routing",
    "04": "Resources, Tools, and Prompts",
    "05": "Extensions and Multi Round-Trip Requests",
    "06": "MCP Apps",
    "07": "Tasks",
}

CHAPTER_BLURBS = {
    "01": "reading stdio, publishing lines, and emitting JSON",
    "02": "classifying requests, notifications, responses and errors",
    "03": "`server/discover`, the `_meta` envelope, `RequestId`",
    "04": "the keyword-search tool and Javadoc resources",
    "05": "asking the client a question when a directory is missing",
    "06": "shipping a UI with your tool",
    "07": "long-running work, polling, and task-level questions",
}

CORE_UNIQUE_KEYS = [
    "SERVER_DISCOVER",
    "PROMPTS_LIST",
    "PROMPTS_GET",
    "COMPLETION_COMPLETE",
    "NOTIFICATION_CANCELLED",
    "TOOLS_LIST",
    "NOT_FOUND",
    "TOOLS_CALL",
    "RESOURCES_LIST",
    "RESOURCES_TEMPLATES_LIST",
    "RESOURCES_READ",
    "SUBSCRIPTIONS_LISTEN",
    "NOTIFICATIONS_SUBSCRIPTIONS_ACKNOWLEDGED",
]
ELICITATION_KEYS = ["ELICITATION_CREATE"]
TASK_KEYS = ["TASKS_GET", "TASKS_UPDATE", "TASKS_CANCEL", "NOTIFICATIONS_TASKS"]

INBOUND = {
    3: ["SERVER_DISCOVER", "SUBSCRIPTIONS_LISTEN"],
    4: [
        "SERVER_DISCOVER", "PROMPTS_LIST", "PROMPTS_GET", "TOOLS_LIST", "TOOLS_CALL",
        "RESOURCES_LIST", "RESOURCES_TEMPLATES_LIST", "RESOURCES_READ",
        "COMPLETION_COMPLETE", "SUBSCRIPTIONS_LISTEN",
    ],
}
INBOUND[5] = list(INBOUND[4])
INBOUND[6] = list(INBOUND[4])
INBOUND[7] = INBOUND[4] + ["TASKS_GET", "TASKS_UPDATE", "TASKS_CANCEL"]

STUB_ROUTER = '''package com.workshop.mcp;

import com.workshop.mcp.io.IOHandler;

/**
 * Routes inbound stdio lines. Chapter 3 is where those lines become JSON-RPC
 * messages and are dispatched to handlers.
 */
public class IORouter implements Router {

    @SuppressWarnings("unused")
    private final IOHandler io;

    public IORouter(IOHandler io) {
        this.io = io;
    }

    @Override
    public void route(String message) {
        // Chapter 03: classify the line and dispatch it.
    }
}
'''

TOOLS_LIST_PLAIN = '''
            case TOOLS_LIST -> {
                KeyWordSearch keyWordSearch = new KeyWordSearch();
                ToolsListResult result = ToolsListResultBuilder.builder()
                        .addTool(keyWordSearch.name(), keyWordSearch.description(), keyWordSearch.schema())
                        .build();
                success(message.id(), new ToolsListResult(result.tools(), LIST_TTL_MILLIS, CacheScope.PUBLIC));
            }
'''

TOOLS_CALL_CH4 = '''
            case TOOLS_CALL -> {
                ToolCallParams toolCallParams = deserializer.deserializeParams(message, ToolCallParams.class);
                KeyWordSearch keyWordSearch = new KeyWordSearch();
                if (toolCallParams.name() == null
                    || !keyWordSearch.name().equalsIgnoreCase(toolCallParams.name())) {
                    success(message.id(), ToolCallResultBuilder
                            .builder()
                            .addTextContent("Tool not found: " + toolCallParams.name())
                            .asError()
                            .build());
                } else {
                    String directory = directoryArgument(toolCallParams);
                    if (directory == null) {
                        success(message.id(), ToolCallResultBuilder
                                .builder()
                                .addTextContent("The directory argument is required.")
                                .asError()
                                .build());
                    } else {
                        success(message.id(), keyWordSearch.call(toolCallParams, Set.of(directory)));
                    }
                }
            }
'''

RESOURCES_LIST_PLAIN = '''
            case RESOURCES_LIST -> {
                ResourcesListResultBuilder builder = ResourcesListResultBuilder
                        .builder()
                        .withResources(JavadocResources.loadAllHtmlResourcesFromFolder("javadoc/com/workshop/mcp/spec"))
                        .withNextCursor("pageNext");
                ResourcesListResult result = builder.build();
                success(message.id(), new ResourcesListResult(result.resources(), result.nextCursor(),
                                                              LIST_TTL_MILLIS, CacheScope.PUBLIC));
            }
'''

RESOURCES_READ_PLAIN = '''
            case RESOURCES_READ -> {
                ReadResourceParam param = deserializer.deserializeParams(message, ReadResourceParam.class);
                String resourceUri = param.uri();
                ReadResourceResultBuilder builder = ReadResourceResultBuilder.builder();
                if (resourceUri == null || resourceUri.isEmpty()) {
                    builder
                            .addTextContent("", DEFAULT_MIME_TYPE, "Resource URI is null or empty, returning error.")
                            .asError();
                } else {
                    try {
                        String content = JavadocResources.readResourceContent(resourceUri);
                        builder.addTextContent(resourceUri, DEFAULT_MIME_TYPE, content);
                    } catch (Exception e) {
                        logger.log("[API][SENT] resources/read — error reading resource: " + resourceUri);
                        builder.addTextContent(resourceUri, DEFAULT_MIME_TYPE, e.getMessage()).asError();
                    }
                }
                ReadResourceResult result = builder.build();
                success(message.id(), new ReadResourceResult(result.contents(), result.isError(),
                                                             LIST_TTL_MILLIS, CacheScope.PUBLIC));
            }
'''

EXECUTE_INLINE = '''
    private void executeKeyWordSearchCall(RequestId requestId, ToolCallParams params, String keyword,
                                          Set<String> directories, RequestEnvelope envelope) {
        success(requestId, new KeyWordSearch().call(resolvedCall(params, keyword), directories));
    }
'''

HANDLE_KEYWORD_SEARCH_CH5 = '''
    private void handleKeywordSearch(RequestId requestId, ToolCallParams params, RequestEnvelope envelope) {
        SearchContinuation continuation = SearchContinuation.decode(params.requestState());
        String keyword = continuation != null ? continuation.keyword() : keywordArgument(params);
        String stage = continuation != null ? continuation.stage() : null;

        Set<String> directories = new LinkedHashSet<>();

        String argument = directoryArgument(params);
        if (argument != null) {
            directories.add(argument);
        }

        if (SearchContinuation.STAGE_DIRECTORY.equals(stage)) {
            String directory = elicitedDirectory(params.inputResponse(SearchContinuation.KEY_DIRECTORY));
            if (directory != null) {
                directories.add(directory);
            }
            logger.log("[API][RECEIVED] tools/call retry — elicited directory=" + directory);
        }

        if (!directories.isEmpty()) {
            executeKeyWordSearchCall(requestId, params, keyword, directories, envelope);
            return;
        }

        if (!SearchContinuation.STAGE_DIRECTORY.equals(stage) && envelope.supportsElicitationForm()) {
            logger.log("[API][SENT] tools/call — input_required, embedding elicitation/create");
            success(requestId, InputRequiredResult.of(
                    SearchContinuation.KEY_DIRECTORY,
                    InputRequest.elicitation(ElicitationBuilder.buildSearchDirectoryElicitation()),
                    SearchContinuation.awaitingDirectory(keyword).encode()));
            return;
        }

        String workingDirectory = workingDirectory();
        logger.log("[API][SENT] tools/call — no directory to search, using the working directory "
                   + workingDirectory);
        executeKeyWordSearchCall(requestId, params, keyword, Set.of(workingDirectory), envelope);
    }
'''

PASTE_FILES = {
    4: {"lessons/JavadocResources.java", "lessons/KeyWordSearch.java"},
    5: {"lessons/SearchContinuation.java"},
    6: {"lessons/AppToolBuilder.java", "lessons/mcp-app.html"},
}

CH4_EXTRA_TESTS = '''
    @Test
    @Tag("chapter04")
    void resourcesListDoesNotAdvertiseTheAppYet() {
        router.route("""
                {"jsonrpc":"2.0","id":21,"method":"resources/list","params":{%s}}""".formatted(FULL_META));

        JsonArray resources = io.only().getAsJsonObject("result").getAsJsonArray("resources");
        assertFalse(resources.asList().stream()
                            .anyMatch(r -> r.getAsJsonObject().get("uri").getAsString().startsWith("ui://")),
                    "the MCP App resource belongs to chapter 6");
        assertTrue(resources.asList().stream()
                           .anyMatch(r -> r.getAsJsonObject().get("uri").getAsString().startsWith("javadoc/")),
                   "Javadoc pages must still be listed");
    }

    @Test
    @Tag("chapter04")
    void aToolCallWithoutADirectoryIsAToolLevelError() {
        router.route("""
                {"jsonrpc":"2.0","id":31,"method":"tools/call",
                 "params":{%s,"name":"key_word_search","arguments":{"keyword":"class"}}}"""
                             .formatted(FULL_META));

        JsonObject result = io.only().getAsJsonObject("result");
        assertTrue(result.get("isError").getAsBoolean(),
                   "chapter 4 has no round trip yet, so a missing directory is a tool error");
        assertTrue(result.getAsJsonArray("content").get(0).getAsJsonObject()
                           .get("text").getAsString().toLowerCase().contains("directory"));
    }
'''

CH5_EXTRA_TESTS = '''
    @Test
    @Tag("chapter04")
    void resourcesListDoesNotAdvertiseTheAppYet() {
        router.route("""
                {"jsonrpc":"2.0","id":21,"method":"resources/list","params":{%s}}""".formatted(FULL_META));

        JsonArray resources = io.only().getAsJsonObject("result").getAsJsonArray("resources");
        assertFalse(resources.asList().stream()
                            .anyMatch(r -> r.getAsJsonObject().get("uri").getAsString().startsWith("ui://")),
                    "the MCP App resource belongs to chapter 6");
    }
'''

# Files that appear at this chapter and stay. 8 = complete-only.
SPEC_CH2 = {
    "Capability.java", "ClientCapabilities.java", "ClientInfo.java", "Elicitation.java",
    "ErrorCodes.java", "JsonRpcError.java", "JsonRpcErrorResponse.java",
    "JsonRpcMessageDeserializer.java", "JsonRpcNotification.java", "JsonRpcRequest.java",
    "JsonRpcResponse.java", "McpGson.java", "MetaKeys.java", "NotificationCancelledParams.java",
    "RequestEnvelope.java", "RequestId.java", "RequestIdTypeAdapter.java", "Unknown.java",
}
SPEC_CH3 = SPEC_CH2 | {
    "CacheScope.java", "DiscoverResult.java", "MetaInfo.java", "ResultType.java",
    "ServerCapabilities.java", "ServerInfo.java", "SubscriptionFilter.java",
    "SubscriptionsAcknowledgedParams.java", "SubscriptionsListenParams.java",
    "SubscriptionsListenResult.java", "UniqueKeys.java",
}
SPEC_CH4 = SPEC_CH3 | {
    "Annotations.java", "Completion.java", "CompletionArgument.java", "CompletionCompleteParams.java",
    "CompletionCompleteResponse.java", "ContentItem.java", "InputSchema.java", "Message.java",
    "MessageContent.java", "Prompt.java", "PromptArgument.java", "PromptRef.java",
    "PromptsGetParams.java", "PromptsGetResult.java", "PromptsListParams.java",
    "PromptsListResult.java", "PropertySchema.java", "PropertySchemaTypeAdapter.java",
    "ReadResourceParam.java", "ReadResourceResult.java", "Resource.java",
    "ResourceTemplatesListResult.java", "ResourcesListResult.java", "Role.java",
    "TextReadResource.java", "Tool.java", "ToolCallParams.java", "ToolCallResult.java",
    "ToolsListParams.java", "ToolsListResult.java",
}
SPEC_CH5 = SPEC_CH4 | {
    "ElicitationCreateParams.java", "ElicitationCreateResult.java", "ElicitationMessage.java",
    "ElicitationQuestion.java", "ElicitationResult.java", "InputRequest.java",
    "InputRequiredResult.java",
}
SPEC_CH6 = SPEC_CH5 | {"AppMeta.java", "AppTool.java", "AppToolsListResult.java", "UiMeta.java"}
SPEC_CH7 = SPEC_CH6 | {
    "Task.java", "TaskResult.java", "TaskStatus.java", "TasksCancelParams.java",
    "TasksGetParams.java", "TasksUpdateParams.java",
}
SPEC_BY_CH = {2: SPEC_CH2, 3: SPEC_CH3, 4: SPEC_CH4, 5: SPEC_CH5, 6: SPEC_CH6, 7: SPEC_CH7}

BUILDER_CH3 = {"DiscoverResultBuilder.java"}
BUILDER_CH4 = BUILDER_CH3 | {
    "CompletionCompleteBuilder.java", "InputSchemaBuilder.java", "PromptBuilder.java",
    "PromptsGetResultBuilder.java", "PromptsListResultBuilder.java", "PropertySchemaBuilder.java",
    "ReadResourceResultBuilder.java", "ResourceBuilder.java", "ResourcesListResultBuilder.java",
    "ToolCallResultBuilder.java", "ToolsListResultBuilder.java",
}
BUILDER_CH5 = BUILDER_CH4 | {"ElicitationBuilder.java"}
BUILDER_CH6 = BUILDER_CH5 | {"AppToolBuilder.java"}
BUILDER_BY_CH = {3: BUILDER_CH3, 4: BUILDER_CH4, 5: BUILDER_CH5, 6: BUILDER_CH6, 7: BUILDER_CH6}

ALWAYS = {
    ".gitignore", "00-introduction.md", "00-setup.md", "LICENSE", "README.md",
    "build.gradle", "clean-checkout.sh", "settings.gradle", "verification.sh",
    "gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar",
    "gradle/wrapper/gradle-wrapper.properties",
    "src/main/java/com/workshop/mcp/IORouter.java",
    "src/main/java/com/workshop/mcp/Router.java",
    "src/main/java/com/workshop/mcp/Runner.java",
    "src/main/java/com/workshop/mcp/Server.java",
    "src/main/java/com/workshop/mcp/io/IOHandler.java",
    "src/main/java/com/workshop/mcp/io/IOHandlerImpl.java",
    "src/main/java/com/workshop/mcp/io/LogFile.java",
    "src/main/java/com/workshop/mcp/io/LogFileWriter.java",
    "src/test/java/com/workshop/mcp/ServerTest.java",
    "src/test/java/com/workshop/mcp/io/IOHandlerImplTest.java",
    "src/test/java/com/workshop/mcp/io/LogFileWriterTest.java",
}

HOLLOW = {
    "01": {
        "methods": [
            (SRC / "io/IOHandlerImpl.java", "startInputReader"),
            (SRC / "io/IOHandlerImpl.java", "publishLine"),
            (SRC / "io/IOHandlerImpl.java", "emit"),
        ],
    },
    "02": {
        "methods": [
            (SRC / "spec/JsonRpcMessageDeserializer.java", "deserialize"),
            (SRC / "spec/JsonRpcMessageDeserializer.java", "deserializeParams"),
            (SRC / "spec/JsonRpcMessageDeserializer.java", "convert"),
            (SRC / "spec/JsonRpcMessageDeserializer.java", "deserializeEnvelope"),
            (SRC / "spec/JsonRpcMessageDeserializer.java", "metaObject"),
            (SRC / "spec/JsonRpcMessageDeserializer.java", "asString"),
        ],
    },
    "03": {
        "methods": [
            (SRC / "IORouter.java", "discoverResult"),
            (SRC / "IORouter.java", "envelopeFor"),
            (SRC / "IORouter.java", "acknowledgeSubscription"),
        ],
        "notification_switch": True,
        "prelude": True,
    },
    "04": {
        "cases": [
            "PROMPTS_LIST", "PROMPTS_GET", "TOOLS_LIST", "TOOLS_CALL",
            "RESOURCES_LIST", "RESOURCES_TEMPLATES_LIST", "RESOURCES_READ",
            "COMPLETION_COMPLETE",
        ],
    },
    "05": {
        "methods": [
            (SRC / "tools/SearchContinuation.java", "awaitingDirectory"),
            (SRC / "tools/SearchContinuation.java", "encode"),
            (SRC / "tools/SearchContinuation.java", "decode"),
            (SRC / "IORouter.java", "handleKeywordSearch"),
            (SRC / "IORouter.java", "elicitedDirectory"),
        ],
    },
    "06": {
        "methods": [
            (SRC / "spec/AppMeta.java", "of"),
            (SRC / "spec/builders/AppToolBuilder.java", "withResourceUri"),
            (SRC / "spec/builders/AppToolBuilder.java", "build"),
        ],
    },
    "07": {
        "methods": [
            (SRC / "tasks/TaskStore.java", "create"),
            (SRC / "tasks/TaskStore.java", "requireInput"),
            (SRC / "tasks/TaskStore.java", "awaitInput"),
            (SRC / "tasks/TaskStore.java", "applyInput"),
            (SRC / "tasks/TaskStore.java", "cancel"),
            (SRC / "tasks/TaskStore.java", "complete"),
            (SRC / "tasks/TaskStore.java", "fail"),
            (SRC / "IORouter.java", "runSearchAsTaskThatAsks"),
        ],
        "cases": ["TASKS_GET", "TASKS_UPDATE", "TASKS_CANCEL"],
    },
}


def matching_brace(text, open_index):
    depth = 0
    i = open_index
    while i < len(text):
        c = text[i]
        if c == '"':
            if text[i:i + 3] == '"""':
                i = text.index('"""', i + 3) + 3
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
        elif c == "/" and text[i:i + 2] == "/*":
            i = text.index("*/", i) + 2
        elif c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    raise ValueError("unbalanced braces")


def placeholder(return_type, indent, chapter, what):
    if return_type == "void":
        return f"{indent}    // Chapter {chapter}: implement {what}.\n"
    return (
        f"{indent}    // Chapter {chapter}: implement {what}.\n"
        f"{indent}    throw new UnsupportedOperationException(\n"
        f'{indent}            "Chapter {chapter}: {what} is not implemented yet");\n'
    )


def hollow_method(text, name, chapter, path):
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
    m = re.search(r"^([ \t]*)case " + re.escape(label) + r" -> \{", text, re.M)
    if not m:
        raise SystemExit(f"  !! no case '{label}'")
    indent = m.group(1)
    open_brace = text.index("{", m.end() - 1)
    close = matching_brace(text, open_brace)
    body = f"{indent}    // Chapter {chapter}: answer {label}.\n"
    return text[:open_brace] + "{\n" + body + indent + "}" + text[close + 1:]


def hollow_prelude(text, chapter):
    m = re.search(r"^([ \t]*)private void process\(JsonRpcRequest message\) \{", text, re.M)
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
    m = re.search(r"^([ \t]*)private void process\(JsonRpcNotification message\) \{", text, re.M)
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


def replace_case(text, label, new_block):
    m = re.search(r"^([ \t]*)case " + re.escape(label) + r" -> \{", text, re.M)
    if not m:
        raise SystemExit(f"  !! no case '{label}' to replace")
    start = m.start()
    open_brace = text.index("{", m.end() - 1)
    close = matching_brace(text, open_brace)
    block = new_block.strip("\n") + "\n"
    return text[:start] + block + text[close + 1:]


def remove_case(text, label):
    m = re.search(r"^([ \t]*)case " + re.escape(label) + r" -> \{", text, re.M)
    if not m:
        return text
    open_brace = text.index("{", m.end() - 1)
    close = matching_brace(text, open_brace)
    start = m.start()
    end = close + 1
    if end < len(text) and text[end] == "\n":
        end += 1
    return text[:start] + text[end:]


def method_sig_pattern(name):
    return re.compile(
        r"^([ \t]*)(?:(?:public|private|protected|static|final|synchronized|@\w+(?:\([^)]*\))?)\s+)+"
        r"(?:<[^>]+>\s*)?"
        r"[\w.$]+(?:\s*<[^;{]*?>)?(?:\[\])?\s+"
        + re.escape(name) + r"\s*\([^;{)]*\)\s*"
        r"(?:throws [\w.,\s]+?)?\{",
        re.M,
    )


def preceding_javadoc_start(text, sig_start):
    """If a javadoc block sits immediately above the method, return its start."""
    i = sig_start
    while i > 0 and text[i - 1] in " \t\n":
        i -= 1
    if not text[:i].endswith("*/"):
        return sig_start
    javadoc = text.rfind("/**", 0, i)
    if javadoc == -1:
        return sig_start
    between = text[javadoc:sig_start]
    if between.count("/**") != 1:
        return sig_start
    return text.rfind("\n", 0, javadoc) + 1


def remove_method(text, name):
    pattern = method_sig_pattern(name)
    matches = list(pattern.finditer(text))
    for m in reversed(matches):
        open_brace = text.index("{", m.end() - 1)
        close = matching_brace(text, open_brace)
        start = preceding_javadoc_start(text, m.start())
        end = close + 1
        if end < len(text) and text[end] == "\n":
            end += 1
        text = text[:start] + text[end:]
    return text


def replace_method(text, name, new_method):
    m = method_sig_pattern(name).search(text)
    if not m:
        raise SystemExit(f"  !! no method '{name}' to replace")
    open_brace = text.index("{", m.end() - 1)
    close = matching_brace(text, open_brace)
    start = preceding_javadoc_start(text, m.start())
    return text[:start] + new_method.strip("\n") + "\n" + text[close + 1:]


def replace_inbound(text, chapter):
    names = ",\n            ".join(f"UniqueKeys.{n}" for n in INBOUND[chapter])
    block = (
        "    private static final Set<UniqueKeys> INBOUND_METHODS = EnumSet.of(\n"
        f"            {names});"
    )
    return re.sub(
        r"    private static final Set<UniqueKeys> INBOUND_METHODS = EnumSet\.of\([^;]+;",
        block,
        text,
        count=1,
        flags=re.S,
    )


def discover_method(chapter):
    instructions = "Searches a project for a keyword."
    extras = []
    if chapter >= 5:
        instructions = (
            "Searches a project for a keyword. If no directory is supplied, the tool asks "
            "for one over a Multi Round-Trip Request, and searches its own working "
            "directory if the client offers nothing."
        )
    if chapter >= 6:
        extras.append(
            '                .withExtension(MetaKeys.UI_EXTENSION,\n'
            '                               Map.of("mimeTypes", List.of(Resource.MIME_TYPE_UI_APP)))'
        )
    if chapter >= 7:
        extras.append("                .withTasksExtension()")
    extra = ("\n" + "\n".join(extras) + "\n") if extras else "\n"
    return f'''
    private DiscoverResult discoverResult() {{
        return DiscoverResultBuilder
                .builder()
                .withDefaultCapabilities(){extra}                .withInstructions("{instructions}")
                .withCacheHints(LIST_TTL_MILLIS, CacheScope.PUBLIC)
                .withDefaultServerInfo()
                .build();
    }}
'''


def strip_tasks(text):
    text = re.sub(
        r"\n    /\*\*\n     \* Whether \{@code tools/call\}.*?"
        r"    private static final boolean TASK_HANDLES_ENABLED = true;\n",
        "\n",
        text,
        count=1,
        flags=re.S,
    )
    text = re.sub(
        r"\n    /\*\*\n     \* How long a task waiting.*?"
        r"    private static final long TASK_INPUT_TIMEOUT_MILLIS = 60_000L;\n",
        "\n",
        text,
        count=1,
        flags=re.S,
    )
    text = text.replace("    private final TaskStore taskStore = new TaskStore();\n\n", "")
    text = text.replace(
        "        this.io = io;\n        this.taskStore.onStatusChange(this::sendTaskStatusNotification);\n",
        "        this.io = io;\n",
    )
    for label in ("TASKS_GET", "TASKS_UPDATE", "TASKS_CANCEL"):
        text = remove_case(text, label)
    for name in (
        "answersWithTask", "runToolAsTask", "runSearchAsTaskThatAsks",
        "askOnTask", "isTerminal", "sendTaskStatusNotification",
    ):
        text = remove_method(text, name)
    text = replace_method(text, "executeKeyWordSearchCall", EXECUTE_INLINE)
    text = replace_method(text, "handleKeywordSearch", HANDLE_KEYWORD_SEARCH_CH5)
    text = text.replace("import com.workshop.mcp.tasks.TaskStore;\n\n", "")
    return text


def strip_apps(text):
    text = re.sub(
        r"    private static final String KEYWORD_APP_URI = \"ui://keyword-search/mcp-app.html\";\n",
        "",
        text,
        count=1,
    )
    text = replace_case(text, "TOOLS_LIST", TOOLS_LIST_PLAIN)
    text = replace_case(text, "RESOURCES_LIST", RESOURCES_LIST_PLAIN)
    text = replace_case(text, "RESOURCES_READ", RESOURCES_READ_PLAIN)
    return text


def strip_mrtr(text):
    text = replace_case(text, "TOOLS_CALL", TOOLS_CALL_CH4)
    for name in (
        "handleKeywordSearch", "elicitedDirectory", "executeKeyWordSearchCall",
        "resolvedCall", "keywordArgument", "workingDirectory",
    ):
        text = remove_method(text, name)
    text = text.replace("import com.workshop.mcp.tools.SearchContinuation;\n", "")
    text = text.replace("import java.nio.file.Path;\n", "")
    text = text.replace("import java.util.LinkedHashSet;\n", "")
    return text


def strip_capabilities(text):
    for label in (
        "PROMPTS_LIST", "PROMPTS_GET", "TOOLS_LIST", "TOOLS_CALL",
        "RESOURCES_LIST", "RESOURCES_TEMPLATES_LIST", "RESOURCES_READ",
        "COMPLETION_COMPLETE",
    ):
        text = remove_case(text, label)
    text = remove_method(text, "directoryArgument")
    text = text.replace("import com.workshop.mcp.resources.JavadocResources;\n", "")
    text = text.replace("import com.workshop.mcp.tools.KeyWordSearch;\n", "")
    text = text.replace("import java.util.List;\n", "")
    # Set is still used by INBOUND_METHODS.
    text = text.replace("import static com.workshop.mcp.spec.Message.KEY_WORD_MESSAGE;\n", "")
    text = text.replace("import static com.workshop.mcp.spec.Resource.DEFAULT_MIME_TYPE;\n", "")
    return text


def transform_iorouter(complete_text, chapter):
    if chapter <= 2:
        return STUB_ROUTER
    text = complete_text
    if chapter < 7:
        text = strip_tasks(text)
    if chapter < 6:
        text = strip_apps(text)
    if chapter < 5:
        text = strip_mrtr(text)
    if chapter < 4:
        text = strip_capabilities(text)
    text = replace_inbound(text, chapter)
    text = replace_method(text, "discoverResult", discover_method(chapter))
    if "InputRequiredResult" not in text and "{@link InputRequiredResult}" in text:
        text = text.replace("{@link InputRequiredResult}", "input_required result")
    return text


def unique_keys_to_keep(chapter):
    names = list(CORE_UNIQUE_KEYS)
    if chapter >= 5:
        names += ELICITATION_KEYS
    if chapter >= 7:
        names += TASK_KEYS
    return set(names)


def strip_unique_keys(text, keep):
    pattern = re.compile(
        r"\n    /\*\*.*?\*/\n    ([A-Z0-9_]+)\(\"[^\"]*\"\)[,;]",
        re.S,
    )
    for m in reversed(list(pattern.finditer(text))):
        if m.group(1) not in keep:
            text = text[:m.start()] + text[m.end():]
    text = re.sub(
        r'(\n    [A-Z0-9_]+\("[^"]*"\)),\s*\n(\s*private final String value)',
        r"\1;\n\n\2",
        text,
        count=1,
    )
    return text


def slice_mcp_gson(text, chapter):
    if chapter >= 4:
        return text
    return text.replace(
        "                .registerTypeAdapter(RequestId.class, new RequestIdTypeAdapter())\n"
        "                .registerTypeAdapter(PropertySchema.class, new PropertySchemaTypeAdapter())\n",
        "                .registerTypeAdapter(RequestId.class, new RequestIdTypeAdapter())\n",
    )


def slice_io_handler(text, chapter):
    if chapter >= 2:
        return text
    text = text.replace("import com.workshop.mcp.spec.McpGson;\n", "")
    text = text.replace("    private final Gson gson = McpGson.create();", "    private final Gson gson = new Gson();")
    return text


def keyword_search_ch4(text):
    text = re.sub(
        r"@see SearchContinuation\n \* @since 1.0",
        "@since 1.0",
        text,
    )
    text = text.replace(
        "Pass a directory to search it "
        "directly; omit it and the server asks for one over a Multi Round-Trip Request, falling back to "
        "its own working directory if nothing is offered.",
        "The directory argument is required.",
    )
    text = text.replace(
        "the absolute directory to search; omit it to have the "
        "server ask for one over a Multi Round-Trip Request and "
        "fall back to its working directory",
        "the absolute directory to search",
    )
    return text


def disable_jacoco(text):
    return text.replace(
        """check {
    dependsOn jacocoTestReport, jacocoTestCoverageVerification
}""",
        """check {
    // Coverage is a property of the finished server on `complete`, not of a chapter in progress.
}""",
    )


def restrict_test_tags(text, chapter):
    tags = ", ".join(f"'chapter{i:02d}'" for i in range(1, chapter + 1))
    tagged = f"    useJUnitPlatform {{\n        includeTags {tags}\n    }}"
    text = text.replace("    useJUnitPlatform()", tagged, 1)
    text = text.replace(
        "    useJUnitPlatform(){\n\n    testLogging {",
        tagged + "\n\n    testLogging {",
        1,
    )
    # The logging test {} block still says useJUnitPlatform() on its own line.
    text = text.replace(
        """test {
    useJUnitPlatform()

    testLogging {""",
        f"""test {{
{tagged}

    testLogging {{""",
        1,
    )
    return text


def file_chapter(rel: str) -> int:
    rel = rel.replace("\\", "/")
    if rel in ALWAYS:
        return 1
    if rel.startswith("src/main/java/com/workshop/mcp/spec/builders/"):
        name = Path(rel).name
        for ch, names in BUILDER_BY_CH.items():
            if name in names:
                return ch
        return 8
    if rel.startswith("src/main/java/com/workshop/mcp/spec/"):
        name = Path(rel).name
        for ch, names in SPEC_BY_CH.items():
            if name in names:
                return ch
        return 8
    if rel == "src/main/java/com/workshop/mcp/resources/JavadocResources.java":
        return 4
    if rel == "src/main/java/com/workshop/mcp/tools/KeyWordSearch.java":
        return 4
    if rel == "src/main/java/com/workshop/mcp/tools/Tool.java":
        return 4
    if rel == "src/main/java/com/workshop/mcp/tools/SearchContinuation.java":
        return 5
    if rel == "src/main/java/com/workshop/mcp/tasks/TaskStore.java":
        return 7
    if rel.startswith("src/main/resources/javadoc/"):
        name = Path(rel).name
        if name.endswith(".html"):
            simple = name.removesuffix(".html")
            if simple in {"package-summary", "package-tree", "index", "allclasses-index", "index-all"}:
                return 4
            if "." in simple:
                simple = simple.split(".", 1)[0]
            java = simple + ".java"
            for ch, names in BUILDER_BY_CH.items():
                if java in names:
                    return ch
            for ch, names in SPEC_BY_CH.items():
                if java in names:
                    return ch
            return 8
        return 4
    if rel == "src/main/resources/lesson/mcp-app.html":
        return 6
    if rel.startswith("src/test/resources/javadoc-fixtures/"):
        return 4
    if rel == "src/test/java/com/workshop/mcp/spec/JsonRpcMessageDeserializerTest.java":
        return 2
    if rel == "src/test/java/com/workshop/mcp/IORouterTest.java":
        return 3
    if rel == "src/test/java/com/workshop/mcp/spec/SpecTypesCoverageTest.java":
        return 2
    if rel == "src/test/java/com/workshop/mcp/spec/builders/BuildersCoverageTest.java":
        return 3
    if rel == "src/test/java/com/workshop/mcp/resources/JavadocResourcesTest.java":
        return 4
    if rel == "src/test/java/com/workshop/mcp/tools/KeyWordSearchTest.java":
        return 4
    if rel == "src/test/java/com/workshop/mcp/tools/SearchContinuationTest.java":
        return 5
    if rel == "src/test/java/com/workshop/mcp/tasks/TaskStoreTest.java":
        return 7
    if rel.startswith("inspector/"):
        return 3
    m = re.match(r"lessons/0([1-7])-", rel)
    if m:
        return int(m.group(1))
    if rel == "lessons/JavadocResources.java" or rel == "lessons/KeyWordSearch.java":
        return 4
    if rel == "lessons/SearchContinuation.java":
        return 5
    if rel in {"lessons/AppToolBuilder.java", "lessons/mcp-app.html"}:
        return 6
    return 8


def keep_for(chapter: int, rel: str) -> bool:
    if rel.startswith(".git/") or rel in {".git"}:
        return True
    if rel.startswith("lessons/"):
        m = re.match(r"lessons/0([1-7])-", rel)
        if m:
            return int(m.group(1)) == chapter
        return rel in PASTE_FILES.get(chapter, set())
    return file_chapter(rel) <= chapter


def available_simple_names(chapter: int) -> set[str]:
    names: set[str] = set()
    spec = SPEC_BY_CH.get(chapter, SPEC_CH2 if chapter >= 2 else set())
    names.update(n.removesuffix(".java") for n in spec)
    builders = BUILDER_BY_CH.get(chapter, set())
    names.update(n.removesuffix(".java") for n in builders)
    if chapter >= 4:
        names.update({"JavadocResources", "KeyWordSearch"})
    if chapter >= 5:
        names.add("SearchContinuation")
    if chapter >= 7:
        names.add("TaskStore")
    return names


def strip_missing_imports(text: str, chapter: int) -> str:
    available = available_simple_names(chapter)
    kept = []
    for line in text.splitlines(keepends=True):
        m = re.match(
            r"import com\.workshop\.mcp\.(?:spec(?:\.builders)?|tools|tasks|resources)\.(\w+);\n",
            line,
        )
        if m and m.group(1) not in available:
            continue
        kept.append(line)
    return "".join(kept)


def strip_coverage_helpers(text: str, chapter: int) -> str:
    if "class SpecTypesCoverageTest" not in text:
        return text
    drop = []
    if chapter < 4:
        drop += ["inputSchema", "tool", "prompt", "resource", "textResource"]
    if chapter < 6:
        drop.append("appTool")
    for name in drop:
        text = remove_method(text, name)
    return text


def strip_router_test_imports(text: str, chapter: int) -> str:
    if "class IORouterTest" not in text:
        return text
    if chapter < 4:
        text = text.replace("import com.workshop.mcp.tools.KeyWordSearch;\n", "")
    if chapter < 5:
        text = text.replace("import com.workshop.mcp.tools.SearchContinuation;\n", "")
    return text


def filter_tagged_members(text, chapter):
    """Drop @Test methods and @Nested classes tagged for a later chapter."""
    tag_re = re.compile(r'@Tag\("chapter(\d+)"\)')
    for m in reversed(list(tag_re.finditer(text))):
        n = int(m.group(1))
        if n <= chapter:
            continue
        after = text[m.end():]
        look = re.match(r"(?:\s*@\w+(?:\([^;]*?\))?)*\s*(class|void|[\w.<>,\[\]]+\s+\w+\s*\()", after, re.S)
        kind = look.group(1) if look else "void"
        start = m.start()
        line_start = text.rfind("\n", 0, start) + 1
        # include preceding javadoc / annotations
        block_start = line_start
        while True:
            prev_nl = text.rfind("\n", 0, block_start - 1)
            prev = text[prev_nl + 1:block_start]
            if prev.strip().startswith("@") or prev.strip().startswith("/*") or prev.strip().startswith("*") or prev.strip().startswith("//"):
                block_start = prev_nl + 1
                if prev.strip().startswith("/*") or "/**" in prev:
                    # walk back to javadoc start
                    javadoc = text.rfind("/**", 0, block_start)
                    if javadoc != -1:
                        javadoc_line = text.rfind("\n", 0, javadoc) + 1
                        block_start = javadoc_line
                    break
                continue
            if prev.strip() == "":
                block_start = prev_nl + 1
                continue
            break
        if kind == "class":
            brace = text.index("{", m.end())
            end = matching_brace(text, brace) + 1
        else:
            brace = text.index("{", m.end())
            end = matching_brace(text, brace) + 1
        if end < len(text) and text[end] == "\n":
            end += 1
        text = text[:block_start] + text[end:]
    return text


def strip_unused_helpers(text, chapter):
    if chapter < 6:
        text = remove_method(text, "appTool")
    return text


def inject_extra_tests(text, chapter):
    if "class IORouterTest" not in text:
        return text
    extra = ""
    if chapter == 4:
        extra = CH4_EXTRA_TESTS
    elif chapter == 5:
        extra = CH5_EXTRA_TESTS
    if not extra:
        return text
    # Drop the complete-only app-alongside assertion when we inject the opposite.
    text = filter_out_method(text, "resourcesListServesTheJavadocPagesAndTheAppAlongside")
    if chapter == 4:
        text = filter_out_method(text, "aCallWithNoArgumentsAtAllStillAsksForSomewhereToSearch")
    marker = "    // --- prompts, resources, completion"
    if marker in text:
        return text.replace(marker, extra + "\n" + marker, 1)
    return text + extra


def filter_out_method(text, name):
    m = re.search(r"void " + re.escape(name) + r"\s*\(", text)
    if not m:
        return text
    # walk back to @Test
    test_at = text.rfind("@Test", 0, m.start())
    if test_at == -1:
        return text
    line_start = text.rfind("\n", 0, test_at) + 1
    brace = text.index("{", m.end())
    end = matching_brace(text, brace) + 1
    if end < len(text) and text[end] == "\n":
        end += 1
    return text[:line_start] + text[end:]


def write_readme(chapter: str):
    n = int(chapter)
    nxt = f"{n + 1:02d}-chapter" if n < 7 else "complete"
    title = CHAPTER_TITLES[chapter]
    blurb = CHAPTER_BLURBS[chapter]
    return f'''# Agent MCP Workshop

> **You are on `{chapter}-chapter`: {title}** — {blurb}.
>
> The work this chapter covers is waiting for you; earlier chapters are already
> written, and later chapters are not on this branch. Your instructions are in
> [lessons/{chapter}-instructions.md](lessons/{chapter}-instructions.md),
> and you are finished when `./gradlew chapterTest -Pchapter={chapter}` is green.

Build a Java implementation of the Model Context Protocol from scratch, one chapter at a time, on revision `2026-07-28` — the stateless revision that removed the `initialize` handshake.

This is an **instructor-led** workshop. You can work through it alone, but a lot of the reasoning is discussed live.

## Before class

Follow [00-setup.md](00-setup.md) and run `./verification.sh` from **`trunk`**. Every step must pass before the first session.

## How the workshop is organised

Each chapter is a git branch. The branch contains the server **as far as this lesson**, with this chapter's methods emptied out, plus the tests that grade them. Later chapters' code is not here.

When this chapter's tests are green, move on with:

```bash
./clean-checkout.sh {nxt}
```

That discards uncommitted work on purpose: the next branch already contains the solutions for chapters 1–{n}.

### Knowing when you are done

```bash
./gradlew chapterTest -Pchapter={chapter}
```

Red means keep going; green means move on. `./gradlew test` on this branch runs only the chapters you have reached.

> **Careful:** `clean-checkout.sh` runs `git clean -fdx`, which deletes every untracked and ignored file — your work in progress, IDE settings, and build output included. Commit or copy anything you want to keep before switching chapters.

## Reference branches

- `complete` — the finished server, all chapters done. It is what the instructor demonstrates from, where these chapter branches are generated from, and where to look when you are stuck.
- `trunk` — setup and environment verification only. No server code, no lessons.

## Building

```bash
./gradlew clean build
```

This produces the server JAR at `build/libs/agent-mcp-workshop-0.0.1.jar`''' + (
        ", which is what `inspector/config.json` points at." if n >= 3 else "."
    ) + '''

## Feedback

Issues and pull requests are welcome, and please tell your instructor directly during the session — clarity problems and missing prerequisites are the most useful things to hear about.
'''


def tracked_files():
    import subprocess
    tracked = subprocess.check_output(["git", "ls-files"], cwd=ROOT, text=True)
    extra = subprocess.check_output(
        ["git", "ls-files", "--others", "--exclude-standard"], cwd=ROOT, text=True
    )
    return [line for line in (tracked + extra).splitlines() if line]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("chapter", choices=["01", "02", "03", "04", "05", "06", "07"])
    parser.add_argument("--keep-filled", action="store_true",
                        help="Leave student methods implemented, for compile/test checks")
    args = parser.parse_args()
    chapter = args.chapter
    n = int(chapter)

    complete_router = (SRC / "IORouter.java").read_text()

    for rel in tracked_files():
        if not keep_for(n, rel):
            path = ROOT / rel
            if path.exists():
                path.unlink()
                parent = path.parent
                while parent != ROOT and parent.exists() and not any(parent.iterdir()):
                    parent.rmdir()
                    parent = parent.parent
                print(f"  drop {rel}")

    listing = ROOT / "src/main/resources/javadoc/com/workshop/mcp/spec/html-files.txt"
    if listing.exists():
        kept_lines = []
        for line in listing.read_text().splitlines():
            if not line.strip():
                continue
            html = ROOT / "src/main/resources" / line.strip()
            if html.exists():
                kept_lines.append(line)
        listing.write_text(("\n".join(kept_lines) + "\n") if kept_lines else "")
        print(f"  filtered javadoc listing to {len(kept_lines)} pages")

    (SRC / "IORouter.java").write_text(transform_iorouter(complete_router, n))
    print("  wrote IORouter for chapter", chapter)

    uk = ROOT / "src/main/java/com/workshop/mcp/spec/UniqueKeys.java"
    if uk.exists() and n >= 3:
        uk.write_text(strip_unique_keys(uk.read_text(), unique_keys_to_keep(n)))

    gson = ROOT / "src/main/java/com/workshop/mcp/spec/McpGson.java"
    if gson.exists():
        gson.write_text(slice_mcp_gson(gson.read_text(), n))

    io_impl = SRC / "io/IOHandlerImpl.java"
    io_impl.write_text(slice_io_handler(io_impl.read_text(), n))

    kws = SRC / "tools/KeyWordSearch.java"
    if kws.exists() and n < 5:
        kws.write_text(keyword_search_ch4(kws.read_text()))
    lesson_kws = ROOT / "lessons/KeyWordSearch.java"
    if lesson_kws.exists() and n < 5:
        lesson_kws.write_text(keyword_search_ch4(lesson_kws.read_text()))

    gradle = ROOT / "build.gradle"
    gradle.write_text(restrict_test_tags(disable_jacoco(gradle.read_text()), n))

    for test in [
        ROOT / "src/test/java/com/workshop/mcp/IORouterTest.java",
        ROOT / "src/test/java/com/workshop/mcp/spec/SpecTypesCoverageTest.java",
        ROOT / "src/test/java/com/workshop/mcp/spec/builders/BuildersCoverageTest.java",
        ROOT / "src/test/java/com/workshop/mcp/tools/KeyWordSearchTest.java",
    ]:
        if test.exists():
            text = filter_tagged_members(test.read_text(), n)
            text = strip_unused_helpers(text, n)
            text = strip_coverage_helpers(text, n)
            text = strip_router_test_imports(text, n)
            text = strip_missing_imports(text, n)
            if test.name == "IORouterTest.java":
                text = inject_extra_tests(text, n)
            test.write_text(text)
            print(f"  filtered {test.name}")

    (ROOT / "README.md").write_text(write_readme(chapter))

    if not args.keep_filled:
        spec = HOLLOW[chapter]
        for path, name in spec.get("methods", []):
            if not path.exists():
                raise SystemExit(f"  !! missing {path}")
            text, count = hollow_method(path.read_text(), name, chapter, path)
            path.write_text(text)
            print(f"  hollowed {path.name}.{name}() x{count}")
        for label in spec.get("cases", []):
            router = SRC / "IORouter.java"
            router.write_text(hollow_case(router.read_text(), label, chapter))
            print(f"  hollowed case {label}")
        if spec.get("notification_switch"):
            router = SRC / "IORouter.java"
            router.write_text(hollow_notification_switch(router.read_text(), chapter))
            print("  hollowed process(JsonRpcNotification)")
        if spec.get("prelude"):
            router = SRC / "IORouter.java"
            router.write_text(hollow_prelude(router.read_text(), chapter))
            print("  hollowed request guards")

    print(f"Sliced chapter {chapter}.")


if __name__ == "__main__":
    main()
