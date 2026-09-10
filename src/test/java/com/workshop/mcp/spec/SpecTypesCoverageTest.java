package com.workshop.mcp.spec;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the wire vocabulary of {@code com.workshop.mcp.spec} directly, rather
 * than through the router: the enums and their reverse lookups, the constant
 * holders that must never be instantiated, the two Gson adapters that give
 * {@link RequestId} and {@link PropertySchema} their JSON forms, and the
 * convenience constructors that supply the {@code resultType} and caching
 * hints protocol revision {@code 2026-07-28} requires on every result.
 */
class SpecTypesCoverageTest {

    // --- enums ---------------------------------------------------------

    @Nested
    class Enums {

        @Test
        @Tag("chapter04")
        void aRoleIsWrittenAsItsLowercaseWireValue() {
            assertEquals("user", Role.USER.getValue());
            assertEquals("assistant", Role.ASSISTANT.getValue());
            assertSame(Role.ASSISTANT, Role.valueOf("ASSISTANT"));
            assertEquals(2, Role.values().length);
        }

        @Test
        @Tag("chapter04")
        void eitherRoleIsFoundFromItsWireValue() {
            // fromValue is an instance method, so the lookup searches the whole
            // enum but has to be reached through one of its constants.
            assertSame(Role.USER, Role.USER.fromValue("user"));
            assertSame(Role.ASSISTANT, Role.USER.fromValue("assistant"));
        }

        @Test
        @Tag("chapter04")
        void anUnknownOrAbsentRoleValueIsNotFound() {
            assertNull(Role.USER.fromValue("moderator"));
            assertNull(Role.USER.fromValue(null));
        }

        @Test
        @Tag("chapter07")
        void onlyTheThreeFinalTaskStatusesAreTerminal() {
            assertFalse(TaskStatus.WORKING.isTerminal());
            assertFalse(TaskStatus.INPUT_REQUIRED.isTerminal());
            assertTrue(TaskStatus.COMPLETED.isTerminal());
            assertTrue(TaskStatus.FAILED.isTerminal());
            assertTrue(TaskStatus.CANCELLED.isTerminal());
        }

        @Test
        @Tag("chapter07")
        void everyTaskStatusIsFoundFromItsWireValueWhateverItsCase() {
            for (TaskStatus status : TaskStatus.values()) {
                assertSame(status, TaskStatus.fromValue(status.getValue()));
                assertSame(status, TaskStatus.fromValue(status.getValue().toUpperCase(Locale.ROOT)));
            }
        }

        @Test
        @Tag("chapter07")
        void anUnknownOrAbsentTaskStatusIsNotFound() {
            assertNull(TaskStatus.fromValue("paused"));
            assertNull(TaskStatus.fromValue(null));
        }

        @Test
        @Tag("chapter03")
        void aMethodKeyPrintsAsTheMethodNameItStandsFor() {
            assertEquals("tools/call", UniqueKeys.TOOLS_CALL.toString());
        }

        @Test
        @Tag("chapter07")
        void aTaskMethodKeyPrintsAsTheMethodNameItStandsFor() {
            assertEquals("notifications/tasks", UniqueKeys.NOTIFICATIONS_TASKS.getValue());
        }

        @Test
        @Tag("chapter03")
        void aMethodNameThisRevisionRemovedResolvesToNotFoundRatherThanNull() {
            assertSame(UniqueKeys.NOT_FOUND, UniqueKeys.fromValue("initialize"));
            assertSame(UniqueKeys.SERVER_DISCOVER, UniqueKeys.fromValue("SERVER/DISCOVER"));
        }
    }

    // --- constant holders ----------------------------------------------

    @Nested
    @Tag("chapter03")
    class ConstantHolders {

        @Test
        void aConstantHolderRefusesToBeInstantiatedEvenReflectively() throws Exception {
            for (Class<?> holder : List.of(MetaKeys.class, ErrorCodes.class,
                                           CacheScope.class, ResultType.class)) {
                Constructor<?> constructor = holder.getDeclaredConstructor();
                constructor.setAccessible(true);
                InvocationTargetException refused =
                        assertThrows(InvocationTargetException.class, constructor::newInstance);
                assertInstanceOf(AssertionError.class, refused.getCause(), holder.getSimpleName());
            }
        }

        @Test
        void theCodesThisRevisionAddedSitInItsReservedRange() {
            // 2026-07-28 partitioned the server-error range: -32000 to -32019
            // stays implementation-defined, -32020 down is the specification's.
            assertEquals(-32020, ErrorCodes.HEADER_MISMATCH);
            assertEquals(-32021, ErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY);
            assertEquals(-32022, ErrorCodes.UNSUPPORTED_PROTOCOL_VERSION);
        }
    }

    // --- RequestId and its adapter --------------------------------------

    @Nested
    @Tag("chapter02")
    class RequestIds {

        @Test
        void anIdIsEitherAStringOrANumberButNeverBothAndNeverNeither() {
            assertThrows(IllegalArgumentException.class, () -> new RequestId("1", 1L));
            assertThrows(IllegalArgumentException.class, () -> new RequestId(null, null));
        }

        @Test
        void anIdPrintsInWhicheverFormItHolds() {
            assertEquals("listen:0", RequestId.of("listen:0").toString());
            assertEquals("7", RequestId.of(7L).toString());
        }

        @Test
        void eachIdFormIsWrittenBackAsItArrived() throws IOException {
            assertEquals("\"server-discover-probe-1\"", write(RequestId.of("server-discover-probe-1")));
            assertEquals("1", write(RequestId.of(1L)), "a number id must not come back as 1.0");
        }

        @Test
        void anAbsentIdIsWrittenAsJsonNull() throws IOException {
            assertEquals("null", write(null));
        }

        @Test
        void eachIdFormIsReadWithoutBeingCoercedToTheOther() throws IOException {
            assertEquals(RequestId.of("listen:0"), read("\"listen:0\""));
            assertEquals(RequestId.of(1L), read("1"));
            assertNull(read("null"));
        }

        @Test
        void anIdThatIsNeitherAStringNorANumberIsRejected() {
            JsonParseException rejected = assertThrows(JsonParseException.class, () -> read("true"));
            assertTrue(rejected.getMessage().contains("BOOLEAN"), rejected.getMessage());
        }

        private String write(RequestId id) throws IOException {
            StringWriter json = new StringWriter();
            try (JsonWriter out = new JsonWriter(json)) {
                new RequestIdTypeAdapter().write(out, id);
            }
            return json.toString();
        }

        private RequestId read(String json) throws IOException {
            try (JsonReader in = new JsonReader(new StringReader(json))) {
                return new RequestIdTypeAdapter().read(in);
            }
        }
    }

    // --- PropertySchema and its adapter ---------------------------------

    @Nested
    @Tag("chapter04")
    class PropertySchemas {

        @Test
        void onlyTheTwoJsonSchemaKeywordsAreWritten() throws IOException {
            assertEquals("{\"type\":\"string\",\"description\":\"The keyword to search for\"}",
                         write(new PropertySchema("keyword", "string", "The keyword to search for", true)),
                         "key and isRequired belong to the enclosing schema");
        }

        @Test
        void anAbsentKeywordIsOmittedRatherThanWrittenAsNull() throws IOException {
            assertEquals("{\"type\":\"string\"}", write(new PropertySchema("keyword", "string", null, true)));
            assertEquals("{\"description\":\"untyped\"}", write(new PropertySchema("keyword", null, "untyped", false)));
            assertEquals("{}", write(new PropertySchema(null, null, null, false)));
        }

        @Test
        void anAbsentPropertyIsWrittenAsJsonNull() throws IOException {
            assertEquals("null", write(null));
        }

        @Test
        void aPropertyIsReadBackWithoutTheTwoComponentsTheEnclosingSchemaOwns() throws IOException {
            PropertySchema property = read("{\"type\":\"string\",\"description\":\"The keyword\"}");

            assertEquals("string", property.type());
            assertEquals("The keyword", property.description());
            assertNull(property.key(), "a property is named by its position, so the key is unrecoverable here");
            assertFalse(property.isRequired(), "requiredness lives in the enclosing schema's required array");
        }

        @Test
        void jsonSchemaKeywordsThisServerDoesNotModelAreSkippedRatherThanRejected() throws IOException {
            PropertySchema property = read("""
                    {"minLength":1,"type":"string","enum":["a","b"],"items":{"type":"number"}}""");

            assertEquals("string", property.type());
            assertNull(property.description());
        }

        @Test
        void anEmptyPropertyAndAJsonNullAreBothRead() throws IOException {
            PropertySchema empty = read("{}");
            assertNull(empty.type());
            assertNull(empty.description());
            assertNull(read("null"));
        }

        @Test
        void theConfiguredGsonDrivesTheAdapterInBothDirections() {
            Gson gson = McpGson.create();

            String json = gson.toJson(new PropertySchema("keyword", "string", "The keyword", true));
            assertEquals("{\"type\":\"string\",\"description\":\"The keyword\"}", json);
            assertEquals(new PropertySchema(null, "string", "The keyword", false),
                         gson.fromJson(json, PropertySchema.class));
        }

        private String write(PropertySchema property) throws IOException {
            StringWriter json = new StringWriter();
            try (JsonWriter out = new JsonWriter(json)) {
                new PropertySchemaTypeAdapter().write(out, property);
            }
            return json.toString();
        }

        private PropertySchema read(String json) throws IOException {
            try (JsonReader in = new JsonReader(new StringReader(json))) {
                return new PropertySchemaTypeAdapter().read(in);
            }
        }
    }

    // --- the per-request envelope ---------------------------------------

    @Nested
    class Envelopes {

        @Test
        @Tag("chapter02")
        void anEnvelopeIsCompleteOnlyWhenBothRequiredFieldsArePresent() {
            assertTrue(envelope(RequestEnvelope.SUPPORTED_VERSION, capabilities()).isComplete());
            assertFalse(envelope(RequestEnvelope.SUPPORTED_VERSION, null).isComplete());
            assertFalse(envelope(null, capabilities()).isComplete());
        }

        @Test
        @Tag("chapter02")
        void onlyTheSingleImplementedRevisionIsSupported() {
            assertTrue(envelope(RequestEnvelope.SUPPORTED_VERSION, null).isSupportedVersion());
            assertFalse(envelope("2025-11-25", null).isSupportedVersion());
            assertFalse(envelope(null, null).isSupportedVersion());
            assertEquals(List.of("2026-07-28"), RequestEnvelope.SUPPORTED_VERSIONS);
        }

        @Test
        @Tag("chapter02")
        void formElicitationNeedsTheFormModeAndNotMerelyAnElicitationDeclaration() {
            assertTrue(envelope(null, new ClientCapabilities(new Elicitation(new Capability(), null), null))
                               .supportsElicitationForm());
            assertFalse(envelope(null, new ClientCapabilities(new Elicitation(null, new Capability()), null))
                                .supportsElicitationForm(),
                        "a url-only client cannot be shown an in-conversation form");
            assertFalse(envelope(null, capabilities()).supportsElicitationForm());
            assertFalse(envelope(null, null).supportsElicitationForm());
        }

        @Test
        @Tag("chapter07")
        void tasksSupportIsReadFromExtensionsRatherThanARetiredTopLevelSlot() {
            assertTrue(envelope(null, declaring(MetaKeys.TASKS_EXTENSION)).supportsTasks());
            assertFalse(envelope(null, declaring(MetaKeys.UI_EXTENSION)).supportsTasks());
            assertFalse(envelope(null, capabilities()).supportsTasks());
            assertFalse(envelope(null, null).supportsTasks());
        }

        @Test
        @Tag("chapter02")
        void theRecommendedHalfOfTheEnvelopeIsCarriedVerbatim() {
            RequestEnvelope envelope = new RequestEnvelope(RequestEnvelope.SUPPORTED_VERSION, capabilities(),
                                                           new ClientInfo("inspector", "0.17.0"), "debug");

            assertEquals("inspector", envelope.clientInfo().name());
            assertEquals("0.17.0", envelope.clientInfo().version());
            assertEquals("debug", envelope.logLevel());
        }
    }

    // --- subscriptions ---------------------------------------------------

    @Nested
    @Tag("chapter03")
    class Subscriptions {

        @Test
        void everyRequestIsHonoredWhenTheServerAdvertisesTheMatchingCapability() {
            SubscriptionFilter honored = requestingEverything().honoredUnder(
                    advertising(new Capability(true), new Capability(true, true), new Capability(true)));

            assertEquals(Boolean.TRUE, honored.toolsListChanged());
            assertEquals(Boolean.TRUE, honored.promptsListChanged());
            assertEquals(Boolean.TRUE, honored.resourcesListChanged());
            assertEquals(List.of("file:///a"), honored.resourceSubscriptions());
            assertFalse(honored.isEmpty());
        }

        @Test
        void aServerAdvertisingNothingDropsEveryRequestInsteadOfAnsweringFalse() {
            SubscriptionFilter honored = requestingEverything()
                    .honoredUnder(new ServerCapabilities(null, null, null, null, null, null, null));

            // Dropped rather than set to false, so the acknowledgment never
            // says anything the server cannot back.
            assertNull(honored.toolsListChanged());
            assertNull(honored.promptsListChanged());
            assertNull(honored.resourcesListChanged());
            assertNull(honored.resourceSubscriptions());
            assertTrue(honored.isEmpty());
        }

        @Test
        void aCapabilityDeclaredWithoutListChangedOrSubscribeHonorsNothing() {
            SubscriptionFilter honored = new SubscriptionFilter(false, null, null, null).honoredUnder(
                    advertising(new Capability(false), new Capability(true, false), new Capability(false)));

            assertNull(honored.toolsListChanged());
            assertNull(honored.promptsListChanged());
            assertNull(honored.resourcesListChanged());
            assertNull(honored.resourceSubscriptions());
        }

        @Test
        void aFilterAskingForAnyOneNotificationIsNotEmpty() {
            assertFalse(new SubscriptionFilter(true, null, null, null).isEmpty());
            assertFalse(new SubscriptionFilter(null, true, null, null).isEmpty());
            assertFalse(new SubscriptionFilter(null, null, true, null).isEmpty());
            assertFalse(new SubscriptionFilter(null, null, null, List.of("file:///a")).isEmpty());
        }

        @Test
        void aFilterIsEmptyWhetherItsUriListIsAbsentOrMerelyEmpty() {
            assertTrue(new SubscriptionFilter(null, null, null, null).isEmpty());
            assertTrue(new SubscriptionFilter(false, false, false, List.of()).isEmpty());
        }

        @Test
        void aCapabilityDefaultsToAnnouncingListChangedWithNoSubscribeSupport() {
            assertEquals(new Capability(true, null), new Capability());
            assertEquals(new Capability(false, null), new Capability(false));
        }

        private SubscriptionFilter requestingEverything() {
            return new SubscriptionFilter(true, true, true, List.of("file:///a"));
        }

        private ServerCapabilities advertising(Capability prompts, Capability resources, Capability tools) {
            return new ServerCapabilities(null, null, null, prompts, resources, tools, null);
        }
    }

    // --- cacheable results -----------------------------------------------

    @Nested
    class CacheableResults {

        @Test
        @Tag("chapter04")
        void aToolsListIsCompleteAndUnshareableUnlessToldOtherwise() {
            ToolsListResult defaults = new ToolsListResult(List.of(tool()));
            assertEquals(ResultType.COMPLETE, defaults.resultType());
            assertEquals(CacheScope.DEFAULT_TTL_MS, defaults.ttlMs());
            assertEquals(CacheScope.PRIVATE, defaults.cacheScope());

            ToolsListResult cacheable = new ToolsListResult(List.of(tool()), 60_000L, CacheScope.PUBLIC);
            assertEquals(ResultType.COMPLETE, cacheable.resultType());
            assertEquals(60_000L, cacheable.ttlMs());
            assertEquals(CacheScope.PUBLIC, cacheable.cacheScope());
        }

        @Test
        @Tag("chapter06")
        void anAppToolsListIsCompleteAndUnshareableUnlessToldOtherwise() {
            AppToolsListResult defaults = new AppToolsListResult(List.of(appTool()));

            assertEquals(ResultType.COMPLETE, defaults.resultType());
            assertEquals(CacheScope.DEFAULT_TTL_MS, defaults.ttlMs());
            assertEquals(CacheScope.PRIVATE, defaults.cacheScope());
            assertEquals("ui://keyword-search/mcp-app.html", defaults.tools().get(0)._meta().resourceUri());
        }

        @Test
        @Tag("chapter04")
        void aPromptsListIsCompleteAndUnshareableUnlessToldOtherwise() {
            PromptsListResult defaults = new PromptsListResult(List.of(prompt()), null);
            assertEquals(ResultType.COMPLETE, defaults.resultType());
            assertNull(defaults.nextCursor());
            assertEquals(CacheScope.DEFAULT_TTL_MS, defaults.ttlMs());
            assertEquals(CacheScope.PRIVATE, defaults.cacheScope());

            PromptsListResult page = new PromptsListResult(List.of(prompt()), "page-2", 60_000L, CacheScope.PUBLIC);
            assertEquals(ResultType.COMPLETE, page.resultType());
            assertEquals("page-2", page.nextCursor());
            assertEquals(60_000L, page.ttlMs());
            assertEquals(CacheScope.PUBLIC, page.cacheScope());
        }

        @Test
        @Tag("chapter04")
        void aResourcesListIsCompleteAndUnshareableUnlessToldOtherwise() {
            ResourcesListResult defaults = new ResourcesListResult(List.of(resource()), null);
            assertEquals(ResultType.COMPLETE, defaults.resultType());
            assertNull(defaults.nextCursor());
            assertEquals(CacheScope.DEFAULT_TTL_MS, defaults.ttlMs());
            assertEquals(CacheScope.PRIVATE, defaults.cacheScope());

            ResourcesListResult page = new ResourcesListResult(List.of(resource()), "page-2", 60_000L, CacheScope.PUBLIC);
            assertEquals(ResultType.COMPLETE, page.resultType());
            assertEquals("page-2", page.nextCursor());
            assertEquals(60_000L, page.ttlMs());
            assertEquals(CacheScope.PUBLIC, page.cacheScope());
        }

        @Test
        @Tag("chapter04")
        void aResourceReadIsCompleteAndUnshareableUnlessToldOtherwise() {
            ReadResourceResult defaults = new ReadResourceResult(List.of(textResource()));
            assertEquals(ResultType.COMPLETE, defaults.resultType());
            assertFalse(defaults.isError());
            assertEquals(CacheScope.DEFAULT_TTL_MS, defaults.ttlMs());
            assertEquals(CacheScope.PRIVATE, defaults.cacheScope());

            assertTrue(new ReadResourceResult(List.of(), true).isError());

            ReadResourceResult cacheable =
                    new ReadResourceResult(List.of(textResource()), false, 60_000L, CacheScope.PUBLIC);
            assertEquals(60_000L, cacheable.ttlMs());
            assertEquals(CacheScope.PUBLIC, cacheable.cacheScope());
        }

        @Test
        @Tag("chapter04")
        void aServerWithNoTemplatesStillAnswersTheTemplatesListWithAnEmptyOne() {
            ResourceTemplatesListResult empty = ResourceTemplatesListResult.empty(60_000L);

            assertEquals(ResultType.COMPLETE, empty.resultType());
            assertEquals(List.of(), empty.resourceTemplates());
            assertNull(empty.nextCursor());
            assertEquals(60_000L, empty.ttlMs());
            assertEquals(CacheScope.PUBLIC, empty.cacheScope());
        }

        @Test
        @Tag("chapter04")
        void aCompletionResponseCarriesNoFreshnessHintsBecauseItIsNotCacheable() {
            CompletionCompleteResponse response =
                    new CompletionCompleteResponse(new Completion(List.of("search_keyword")), 1, false);

            assertEquals(ResultType.COMPLETE, response.resultType());
            assertEquals(List.of("search_keyword"), response.completion().values());
            assertEquals(1, response.total());
            assertEquals(Boolean.FALSE, response.hasMore());

            String json = McpGson.create().toJson(response);
            assertFalse(json.contains("ttlMs"), json);
            assertFalse(json.contains("cacheScope"), json);
        }
    }

    // --- the remaining wire records ---------------------------------------

    @Nested
    class WireRecords {

        @Test
        @Tag("chapter04")
        void aResourceDefaultsToHtmlWithNoAnnotations() {
            Resource resource = new Resource("ui://keyword-search/mcp-app.html", "Keyword Search", "The app shell");

            assertEquals(Resource.DEFAULT_MIME_TYPE, resource.mimeType());
            assertNull(resource.annotations());
        }

        @Test
        @Tag("chapter04")
        void aResourceMayNameItsAudienceAndPriorityInstead() {
            Resource annotated = new Resource("file:///notes.json", "Notes", "Scratch notes",
                                              Resource.MIME_TYPE_JSON,
                                              new Annotations(List.of(Role.ASSISTANT), 0.5));

            assertEquals("application/json", annotated.mimeType());
            assertEquals(List.of(Role.ASSISTANT), annotated.annotations().audience());
            assertEquals(0.5, annotated.annotations().priority());
        }

        @Test
        @Tag("chapter04")
        void readContentCarriesItsUriAndMimeTypeAlongsideTheText() {
            TextReadResource content = textResource();

            assertEquals("ui://keyword-search/mcp-app.html", content.uri());
            assertEquals(Resource.MIME_TYPE_UI_APP, content.mimeType());
            assertEquals("<html></html>", content.text());
        }

        @Test
        @Tag("chapter04")
        void aToolStatesRequirednessOnceThroughItsSchemaRequiredList() {
            Tool tool = tool();

            assertEquals("key_word_search", tool.name());
            assertEquals("object", tool.inputSchema().type());
            assertEquals(List.of("keyword"), tool.inputSchema().required());
            assertEquals("string", tool.inputSchema().properties().get("keyword").type());
        }

        @Test
        @Tag("chapter04")
        void aPromptDeclaresItsArgumentsAndWhichOfThemAreRequired() {
            Prompt prompt = prompt();
            PromptArgument argument = prompt.arguments().get(0);

            assertEquals("search_keyword", prompt.name());
            assertEquals("keyword", argument.name());
            assertEquals("The keyword to search for", argument.description());
            assertTrue(argument.required());
        }

        @Test
        @Tag("chapter04")
        void aCompletionRequestNamesTheArgumentAndThePromptItBelongsTo() {
            CompletionCompleteParams params = new CompletionCompleteParams(
                    new MetaInfo(7),
                    new CompletionArgument("keyword", "rec"),
                    new PromptRef("ref/prompt", "search_keyword"));

            assertEquals(7, params._meta().progressToken());
            assertEquals("keyword", params.argument().name());
            assertEquals("rec", params.argument().value());
            assertEquals("ref/prompt", params.ref().type());
            assertEquals("search_keyword", params.ref().name());
        }

        @Test
        @Tag("chapter05")
        void anElicitationMessageWrapsItsQuestionsInAJsonRpcResult() {
            ElicitationQuestion question =
                    new ElicitationQuestion("directory", "Which directory should be searched?", List.of("."));
            ElicitationMessage message =
                    new ElicitationMessage("2.0", "elicit-001", new ElicitationResult("questions", List.of(question)));

            assertEquals("2.0", message.jsonrpc());
            assertEquals("elicit-001", message.id());
            assertEquals("questions", message.result().type());
            assertEquals("directory", message.result().questions().get(0).id());
            assertEquals(List.of("."), message.result().questions().get(0).options());
        }

        @Test
        @Tag("chapter05")
        void anOpenEndedQuestionOffersNoOptions() {
            assertNull(new ElicitationQuestion("directory", "Which directory?", null).options());
        }

        @Test
        @Tag("chapter04")
        void aListRequestCarriesNothingBeyondItsOptionalProgressToken() {
            assertEquals(7, new ToolsListParams(new MetaInfo(7))._meta().progressToken());
            assertNull(new PromptsListParams(null)._meta());
            assertEquals("ui://keyword-search/mcp-app.html",
                         new ReadResourceParam(null, "ui://keyword-search/mcp-app.html").uri());
        }

        @Test
        @Tag("chapter02")
        void aCancellationNamesTheRequestItAbandons() {
            NotificationCancelledParams cancelled =
                    new NotificationCancelledParams(RequestId.of("listen:0"), "the client closed the stream");

            assertEquals("listen:0", cancelled.requestId().toString());
            assertEquals("the client closed the stream", cancelled.reason());
        }

        @Test
        @Tag("chapter02")
        void anUnrecognizedMessageKeepsItsRawJson() {
            assertEquals("{\"jsonrpc\":\"2.0\"}", new Unknown("{\"jsonrpc\":\"2.0\"}").json());
        }

        @Test
        @Tag("chapter05")
        void aRetryLooksUpItsAnswersUnderTheKeysTheServerChose() {
            Map<String, Object> elicited = Map.of("action", "accept", "content", Map.of("directory", "/srv/code"));
            ToolCallParams retry = new ToolCallParams(null, "key_word_search", Map.of("keyword", "record"),
                                                      Map.of("search_directory", elicited),
                                                      "cmVjb3Jk");

            assertEquals(elicited, retry.inputResponse("search_directory"));
            assertNull(retry.inputResponse("search_elsewhere"), "an unanswered question reads as absent");
            assertEquals("cmVjb3Jk", retry.requestState());
        }

        @Test
        @Tag("chapter04")
        void aFirstAttemptCarriesNoAnswersAtAll() {
            ToolCallParams first =
                    new ToolCallParams(null, "key_word_search", Map.of("keyword", "record"), null, null);

            assertNull(first.inputResponse("search_directory"));
        }
    }

    // --- helpers -----------------------------------------------------------

    private static RequestEnvelope envelope(String protocolVersion, ClientCapabilities capabilities) {
        return new RequestEnvelope(protocolVersion, capabilities, null, null);
    }

    private static ClientCapabilities capabilities() {
        return new ClientCapabilities(null, null);
    }

    private static ClientCapabilities declaring(String extension) {
        return new ClientCapabilities(null, Map.of(extension, Map.of()));
    }

    private static InputSchema inputSchema() {
        return new InputSchema(
                "object",
                Map.of("keyword", new PropertySchema("keyword", "string", "The keyword to search for", true)),
                List.of("keyword"));
    }

    private static Tool tool() {
        return new Tool("key_word_search", "Searches for a keyword across all project files.", inputSchema());
    }

    private static AppTool appTool() {
        return new AppTool("key_word_search", "Searches for a keyword across all project files.",
                           inputSchema(), AppMeta.of("ui://keyword-search/mcp-app.html"));
    }

    private static Prompt prompt() {
        return new Prompt("search_keyword", "Search the project for a keyword",
                          List.of(new PromptArgument("keyword", "The keyword to search for", true)));
    }

    private static Resource resource() {
        return new Resource("ui://keyword-search/mcp-app.html", "Keyword Search", "The app shell");
    }

    private static TextReadResource textResource() {
        return new TextReadResource("ui://keyword-search/mcp-app.html", Resource.MIME_TYPE_UI_APP, "<html></html>");
    }
}
