package com.workshop.mcp.spec.builders;

import com.workshop.mcp.spec.Annotations;
import com.workshop.mcp.spec.CacheScope;
import com.workshop.mcp.spec.Capability;
import com.workshop.mcp.spec.CompletionCompleteResponse;
import com.workshop.mcp.spec.ContentItem;
import com.workshop.mcp.spec.DiscoverResult;
import com.workshop.mcp.spec.ElicitationCreateParams;
import com.workshop.mcp.spec.InputSchema;
import com.workshop.mcp.spec.Message;
import com.workshop.mcp.spec.MessageContent;
import com.workshop.mcp.spec.MetaKeys;
import com.workshop.mcp.spec.Prompt;
import com.workshop.mcp.spec.PromptArgument;
import com.workshop.mcp.spec.PromptsGetResult;
import com.workshop.mcp.spec.PromptsListResult;
import com.workshop.mcp.spec.PropertySchema;
import com.workshop.mcp.spec.ReadResourceResult;
import com.workshop.mcp.spec.RequestEnvelope;
import com.workshop.mcp.spec.Resource;
import com.workshop.mcp.spec.ResourcesListResult;
import com.workshop.mcp.spec.Role;
import com.workshop.mcp.spec.ServerCapabilities;
import com.workshop.mcp.spec.ServerInfo;
import com.workshop.mcp.spec.TextReadResource;
import com.workshop.mcp.spec.Tool;
import com.workshop.mcp.spec.ToolCallResult;
import com.workshop.mcp.spec.ToolsListResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the fluent builders in this package directly, rather than through
 * the handful of shapes the server happens to construct at runtime. Each
 * builder's optional setters, its collection-replacing setters, and every
 * validation path it can refuse on are covered here.
 */
class BuildersCoverageTest {

    // --- CompletionCompleteBuilder -------------------------------------

    @Nested
    @Tag("chapter04")
    class CompletionCompleteBuilderTest {

        @Test
        void aFreshBuilderCompletesToAnEmptyListOfOne() {
            CompletionCompleteResponse response = new CompletionCompleteBuilder().build();

            assertEquals(List.of(), response.completion().values());
            assertEquals(1, response.total());
            assertFalse(response.hasMore());
        }

        @Test
        void withValueSeedsTheBuilderWithASingleValue() {
            CompletionCompleteResponse response = CompletionCompleteBuilder.withValue("alpha").build();

            assertEquals(List.of("alpha"), response.completion().values());
        }

        @Test
        void valuesAppendsToWhateverWasAlreadyAdded() {
            CompletionCompleteResponse response = CompletionCompleteBuilder.withValue("alpha")
                    .value("beta")
                    .values(List.of("gamma", "delta"))
                    .total(42)
                    .hasMore(true)
                    .build();

            assertEquals(List.of("alpha", "beta", "gamma", "delta"), response.completion().values());
            assertEquals(42, response.total());
            assertTrue(response.hasMore());
        }
    }

    // --- DiscoverResultBuilder -----------------------------------------

    @Nested
    @Tag("chapter03")
    class DiscoverResultBuilderTest {

        @Test
        void theDefaultsAdvertiseTheSupportedRevisionAndLeaveOptionalMapsOff() {
            DiscoverResult result = DiscoverResultBuilder.builder()
                    .withDefaultCapabilities()
                    .withDefaultServerInfo()
                    .build();

            assertEquals(RequestEnvelope.SUPPORTED_VERSIONS, result.supportedVersions());
            assertEquals(CacheScope.DEFAULT_TTL_MS, result.ttlMs());
            assertEquals(CacheScope.PRIVATE, result.cacheScope());
            assertNull(result.instructions());
            assertNull(result.capabilities().experimental());
            assertNull(result.capabilities().extensions());
            assertNull(result.capabilities().logging());
            assertEquals(new ServerInfo("agent-mcp-workshop", "0.0.1"),
                         result._meta().get(MetaKeys.SERVER_INFO));
        }

        @Test
        void theDefaultCapabilitiesLeaveListChangedOffEverywhereItWouldOpenAStream() {
            ServerCapabilities capabilities = DiscoverResultBuilder.builder()
                    .withDefaultCapabilities()
                    .withDefaultServerInfo()
                    .build()
                    .capabilities();

            assertEquals(new Capability(null, null), capabilities.completions());
            assertEquals(new Capability(false, null), capabilities.prompts());
            assertEquals(new Capability(false, false), capabilities.resources());
            assertEquals(new Capability(false, null), capabilities.tools());
        }

        @Test
        void extensionsDeclaredAfterTheDefaultCapabilitiesStillReachTheResult() {
            DiscoverResult result = DiscoverResultBuilder.builder()
                    .withDefaultCapabilities()
                    .withTasksExtension()
                    .withExperimentalCapability("workshop/tracing", Map.of("enabled", true))
                    .withDefaultServerInfo()
                    .build();

            assertEquals(Map.of(), result.capabilities().extensions().get(MetaKeys.TASKS_EXTENSION));
            assertEquals(Map.of("enabled", true), result.capabilities().experimental().get("workshop/tracing"));
        }

        @Test
        void extensionsDeclaredBeforeTheDefaultCapabilitiesReachItToo() {
            DiscoverResult result = DiscoverResultBuilder.builder()
                    .withExtension("workshop/echo", Map.of())
                    .withExperimentalCapability("workshop/tracing", Map.of())
                    .withDefaultCapabilities()
                    .withDefaultServerInfo()
                    .build();

            assertTrue(result.capabilities().extensions().containsKey("workshop/echo"));
            assertTrue(result.capabilities().experimental().containsKey("workshop/tracing"));
        }

        @Test
        void everyDiscoveryFieldCanBeOverriddenExplicitly() {
            ServerCapabilities supplied = new ServerCapabilities(
                    null, new Capability(true), new Capability(true),
                    new Capability(true), new Capability(true, true), new Capability(true), null);

            DiscoverResult result = DiscoverResultBuilder.builder()
                    .withSupportedVersions(List.of("2026-07-28", "2099-01-01"))
                    .withCapabilities(supplied)
                    .withInstructions("Search before you ask.")
                    .withCacheHints(5_000L, CacheScope.PUBLIC)
                    .withServerInfo("custom", "9.9.9")
                    .build();

            assertEquals(List.of("2026-07-28", "2099-01-01"), result.supportedVersions());
            assertEquals("Search before you ask.", result.instructions());
            assertEquals(5_000L, result.ttlMs());
            assertEquals(CacheScope.PUBLIC, result.cacheScope());
            assertEquals(new Capability(true, true), result.capabilities().resources());
            assertEquals(new ServerInfo("custom", "9.9.9"), result._meta().get(MetaKeys.SERVER_INFO));
        }

        @Test
        void discoveryWithoutCapabilitiesIsRejected() {
            DiscoverResultBuilder builder = DiscoverResultBuilder.builder().withDefaultServerInfo();

            assertEquals("Server capabilities are required",
                         assertThrows(IllegalStateException.class, builder::build).getMessage());
        }

        @Test
        void discoveryWithoutServerInfoIsRejected() {
            DiscoverResultBuilder builder = DiscoverResultBuilder.builder().withDefaultCapabilities();

            assertEquals("Server info is required",
                         assertThrows(IllegalStateException.class, builder::build).getMessage());
        }
    }

    // --- ElicitationBuilder --------------------------------------------

    @Nested
    @Tag("chapter05")
    class ElicitationBuilderTest {

        @Test
        void theDirectoryFormAsksForOneRequiredAbsolutePath() {
            ElicitationCreateParams params = ElicitationBuilder.buildSearchDirectoryElicitation();

            assertEquals(ElicitationCreateParams.MODE_FORM, params.mode());
            assertEquals("Pick a directory for the keyword-search tool to search in:", params.message());
            assertEquals("object", params.requestedSchema().get("type"));
            assertEquals(List.of("directory"), params.requestedSchema().get("required"));

            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) params.requestedSchema().get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> directory = (Map<String, Object>) properties.get("directory");
            assertEquals("string", directory.get("type"));
            assertEquals("Search Directory", directory.get("title"));
        }

        @Test
        void theFactoryIsStillInstantiableEvenThoughItHoldsOnlyStaticMembers() {
            assertNotNull(new ElicitationBuilder());
        }
    }

    // --- InputSchemaBuilder --------------------------------------------

    @Nested
    @Tag("chapter04")
    class InputSchemaBuilderTest {

        @Test
        void anAddedPropertyIsMarkedRequiredOnlyWhenItSaysItIs() {
            InputSchema schema = InputSchemaBuilder.builder()
                    .addProperty("keyword", "string", "The keyword", true)
                    .addProperty("directory", "string", "Where to look")
                    .build();

            assertEquals("object", schema.type());
            assertEquals(List.of("keyword"), schema.required());
            assertEquals(new PropertySchema("keyword", "string", "The keyword", true),
                         schema.properties().get("keyword"));
            assertEquals(new PropertySchema("directory", "string", "Where to look", false),
                         schema.properties().get("directory"));
        }

        @Test
        void aPropertyCanBeHandedOverPreBuiltOrAsItsOwnBuilder() {
            InputSchema schema = InputSchemaBuilder.builder()
                    .addProperty(new PropertySchema("keyword", "string", "The keyword", true))
                    .addProperty(PropertySchemaBuilder.builder()
                                         .withKey("directory")
                                         .withType("string")
                                         .withDescription("Where to look"))
                    .build();

            assertEquals(List.of("keyword"), schema.required());
            assertEquals(2, schema.properties().size());
        }

        @Test
        void theTypePropertiesAndRequiredListCanAllBeReplacedWholesale() {
            Map<String, PropertySchema> properties = new HashMap<>();
            properties.put("keyword", new PropertySchema("keyword", "string", "The keyword", true));

            InputSchema schema = InputSchemaBuilder.builder()
                    .addProperty("discarded", "string", "Replaced below", true)
                    .withType("array")
                    .withProperties(properties)
                    .withRequired(new ArrayList<>(List.of("keyword")))
                    .build();

            assertEquals("array", schema.type());
            assertEquals(List.of("keyword"), schema.required());
            assertEquals(List.of("keyword"), List.copyOf(schema.properties().keySet()));
        }

        @Test
        void addRequiredDoesNotRepeatANameThatIsAlreadyRequired() {
            InputSchema schema = InputSchemaBuilder.builder()
                    .addProperty("keyword", "string", "The keyword", true)
                    .addRequired("keyword")
                    .addRequired("directory")
                    .build();

            assertEquals(List.of("keyword", "directory"), schema.required());
        }
    }

    // --- PromptBuilder -------------------------------------------------

    @Nested
    @Tag("chapter04")
    class PromptBuilderTest {

        @Test
        void argumentsAreCollectedInTheOrderTheyWereAdded() {
            Prompt prompt = PromptBuilder.builder("search_keyword", "Search for a keyword")
                    .withArgument("keyword", "The keyword", true)
                    .withArguments(List.of(new PromptArgument("limit", "Max results", false)))
                    .build();

            assertEquals("search_keyword", prompt.name());
            assertEquals("Search for a keyword", prompt.description());
            assertEquals(List.of(new PromptArgument("keyword", "The keyword", true),
                                 new PromptArgument("limit", "Max results", false)),
                         prompt.arguments());
        }

        @Test
        void aBuiltPromptDoesNotShareItsArgumentListWithTheBuilder() {
            PromptBuilder builder = PromptBuilder.builder("search_keyword", "Search for a keyword")
                    .withArgument("keyword", "The keyword", true);
            Prompt first = builder.build();

            builder.withArgument("limit", "Max results", false);

            assertEquals(1, first.arguments().size());
            assertEquals(2, builder.build().arguments().size());
        }
    }

    // --- PromptsGetResultBuilder ---------------------------------------

    @Nested
    @Tag("chapter04")
    class PromptsGetResultBuilderTest {

        @Test
        void aTextMessageIsWrappedInATextContent() {
            PromptsGetResult result = PromptsGetResultBuilder.builder()
                    .withDescription("Search prompt")
                    .addTextMessage("user", "Search for it")
                    .build();

            assertEquals("Search prompt", result.description());
            assertEquals(new Message("user", new MessageContent("Search for it", "text")),
                         result.messages().get(0));
        }

        @Test
        void aMessageCanBeHandedOverPreBuiltOrAsARoleAndContent() {
            PromptsGetResult result = PromptsGetResultBuilder.builder()
                    .withDescription("Search prompt")
                    .addMessage(new Message("user", new MessageContent("first", "text")))
                    .addMessage("assistant", new MessageContent("second", "text"))
                    .build();

            assertEquals(List.of("first", "second"),
                         result.messages().stream().map(message -> message.content().text()).toList());
        }

        @Test
        void theMessageListCanBeReplacedWholesale() {
            PromptsGetResult result = PromptsGetResultBuilder.builder()
                    .withDescription("Search prompt")
                    .addTextMessage("user", "discarded")
                    .withMessages(new ArrayList<>(List.of(new Message("user", new MessageContent("kept", "text")))))
                    .build();

            assertEquals(1, result.messages().size());
            assertEquals("kept", result.messages().get(0).content().text());
        }

        @Test
        void aKeywordArgumentIsAppendedToTheTextInSingleQuotes() {
            PromptsGetResult result = PromptsGetResultBuilder.builder()
                    .withDescription("Search prompt")
                    .addTextMessage("user", "Search for ", Map.of("keyword", "Gson"))
                    .build();

            assertEquals("Search for 'Gson'", result.messages().get(0).content().text());
        }

        @Test
        void absentArgumentsLeaveTheTextExactlyAsGiven() {
            PromptsGetResult result = PromptsGetResultBuilder.builder()
                    .withDescription("Search prompt")
                    .addTextMessage("user", "no map", null)
                    .addTextMessage("assistant", "empty map", Map.of())
                    .build();

            assertEquals("no map", result.messages().get(0).content().text());
            assertEquals("empty map", result.messages().get(1).content().text());
        }

        @Test
        void argumentsCarryingNoUsableKeywordLeaveTheTextAlone() {
            // Testing the map for emptiness rather than for a keyword used to
            // append the literal 'null', which told the model to go and search
            // for the word "null".
            PromptsGetResult result = PromptsGetResultBuilder.builder()
                    .withDescription("Search prompt")
                    .addTextMessage("user", "other keys", Map.of("unexpected", "value"))
                    .addTextMessage("assistant", "blank keyword", Map.of("keyword", "   "))
                    .build();

            assertEquals("other keys", result.messages().get(0).content().text());
            assertEquals("blank keyword", result.messages().get(1).content().text());
        }

        @Test
        void aResultWithoutADescriptionIsRejected() {
            PromptsGetResultBuilder builder = PromptsGetResultBuilder.builder().addTextMessage("user", "orphan");

            assertEquals("Description is required",
                         assertThrows(IllegalStateException.class, builder::build).getMessage());
        }
    }

    // --- PromptsListResultBuilder --------------------------------------

    @Nested
    @Tag("chapter04")
    class PromptsListResultBuilderTest {

        @Test
        void aStatefullyBuiltPromptIsFinalisedByBuild() {
            PromptsListResult result = PromptsListResultBuilder.builder()
                    .withPrompt("search_keyword", "Search for a keyword")
                    .withPromptArgument("keyword", "The keyword", true)
                    .build();

            assertEquals(1, result.prompts().size());
            assertEquals("search_keyword", result.prompts().get(0).name());
            assertEquals(List.of(new PromptArgument("keyword", "The keyword", true)),
                         result.prompts().get(0).arguments());
        }

        @Test
        void startingASecondPromptFinalisesTheFirst() {
            PromptsListResult result = PromptsListResultBuilder.builder()
                    .withPrompt("first", "The first")
                    .withPromptArgument("a", "An argument", true)
                    .withPrompt("second", "The second")
                    .build();

            assertEquals(List.of("first", "second"),
                         result.prompts().stream().map(Prompt::name).toList());
            assertEquals(1, result.prompts().get(0).arguments().size());
            assertEquals(List.of(), result.prompts().get(1).arguments());
        }

        @Test
        void anArgumentWithNoPromptToAttachItToIsRejected() {
            PromptsListResultBuilder builder = PromptsListResultBuilder.builder();

            assertEquals("Must call withPrompt() before adding arguments",
                         assertThrows(IllegalStateException.class,
                                      () -> builder.withPromptArgument("keyword", "The keyword", true))
                                 .getMessage());
        }

        @Test
        void aPromptCanBeHandedOverPreBuiltAsFieldsOrAsItsOwnBuilder() {
            PromptsListResult result = PromptsListResultBuilder.builder()
                    .addPrompt(new Prompt("first", "The first", List.of()))
                    .addPrompt("second", "The second", List.of(new PromptArgument("a", "An argument", true)))
                    .addPromptBuilder(PromptBuilder.builder("third", "The third"))
                    .build();

            assertEquals(List.of("first", "second", "third"),
                         result.prompts().stream().map(Prompt::name).toList());
            assertNull(result.nextCursor());
        }

        @Test
        void thePromptListAndCursorCanBeReplacedWholesale() {
            PromptsListResult result = PromptsListResultBuilder.builder()
                    .addPrompt(new Prompt("discarded", "Replaced below", List.of()))
                    .withPrompts(new ArrayList<>(List.of(new Prompt("kept", "The kept one", List.of()))))
                    .withNextCursor("page-2")
                    .build();

            assertEquals(List.of("kept"), result.prompts().stream().map(Prompt::name).toList());
            assertEquals("page-2", result.nextCursor());
        }
    }

    // --- PropertySchemaBuilder -----------------------------------------

    @Nested
    @Tag("chapter04")
    class PropertySchemaBuilderTest {

        @Test
        void requiredAndOptionalAndIsRequiredAllDriveTheSameFlag() {
            PropertySchemaBuilder builder = PropertySchemaBuilder.builder()
                    .withKey("keyword")
                    .withType("string")
                    .withDescription("The keyword");

            assertFalse(builder.build().isRequired());
            assertTrue(builder.required().build().isRequired());
            assertFalse(builder.optional().build().isRequired());
            assertTrue(builder.isRequired(true).build().isRequired());
            assertFalse(builder.isRequired(false).build().isRequired());
        }

        @Test
        void aFullyConfiguredPropertyKeepsEveryFieldItWasGiven() {
            PropertySchema property = PropertySchemaBuilder.builder()
                    .withKey("keyword")
                    .withType("string")
                    .withDescription("The keyword")
                    .required()
                    .build();

            assertEquals(new PropertySchema("keyword", "string", "The keyword", true), property);
        }

        @Test
        void aPropertyWithoutAKeyIsRejected() {
            PropertySchemaBuilder builder = PropertySchemaBuilder.builder()
                    .withType("string")
                    .withDescription("The keyword");

            assertEquals("Property key is required",
                         assertThrows(IllegalStateException.class, builder::build).getMessage());
        }

        @Test
        void anEmptyKeyIsRefusedJustLikeAMissingOne() {
            PropertySchemaBuilder builder = PropertySchemaBuilder.builder()
                    .withKey("")
                    .withType("string")
                    .withDescription("The keyword");

            assertThrows(IllegalStateException.class, builder::build);
        }

        @Test
        void aPropertyWithoutATypeIsRejected() {
            PropertySchemaBuilder builder = PropertySchemaBuilder.builder()
                    .withKey("keyword")
                    .withDescription("The keyword");

            assertEquals("Property type is required",
                         assertThrows(IllegalStateException.class, builder::build).getMessage());
        }

        @Test
        void anEmptyTypeIsRefusedJustLikeAMissingOne() {
            PropertySchemaBuilder builder = PropertySchemaBuilder.builder()
                    .withKey("keyword")
                    .withType("")
                    .withDescription("The keyword");

            assertThrows(IllegalStateException.class, builder::build);
        }

        @Test
        void aPropertyWithoutADescriptionIsRejected() {
            PropertySchemaBuilder builder = PropertySchemaBuilder.builder()
                    .withKey("keyword")
                    .withType("string");

            assertEquals("Property description is required",
                         assertThrows(IllegalStateException.class, builder::build).getMessage());
        }

        @Test
        void anEmptyDescriptionIsRefusedJustLikeAMissingOne() {
            PropertySchemaBuilder builder = PropertySchemaBuilder.builder()
                    .withKey("keyword")
                    .withType("string")
                    .withDescription("");

            assertThrows(IllegalStateException.class, builder::build);
        }
    }

    // --- ReadResourceResultBuilder -------------------------------------

    @Nested
    @Tag("chapter04")
    class ReadResourceResultBuilderTest {

        @Test
        void aTextResourceCanBeAddedFromItsPartsOrPreBuilt() {
            ReadResourceResult result = ReadResourceResultBuilder.builder()
                    .addTextContent("ui://app/mcp-app.html", "text/html", "<html></html>")
                    .addContent(new TextReadResource("file:///notes.txt", "text/plain", "notes"))
                    .build();

            assertEquals(new TextReadResource("ui://app/mcp-app.html", "text/html", "<html></html>"),
                         result.contents().get(0));
            assertEquals("notes", result.contents().get(1).text());
            assertFalse(result.isError());
        }

        @Test
        void theContentListCanBeReplacedWholesale() {
            ReadResourceResult result = ReadResourceResultBuilder.builder()
                    .addTextContent("file:///discarded.txt", "text/plain", "discarded")
                    .withContents(new ArrayList<>(List.of(
                            new TextReadResource("file:///kept.txt", "text/plain", "kept"))))
                    .build();

            assertEquals(1, result.contents().size());
            assertEquals("kept", result.contents().get(0).text());
        }

        @Test
        void asErrorAndWithErrorBothSetTheErrorFlag() {
            assertTrue(ReadResourceResultBuilder.builder().asError().build().isError());
            assertTrue(ReadResourceResultBuilder.builder().withError(true).build().isError());
            assertFalse(ReadResourceResultBuilder.builder().asError().withError(false).build().isError());
        }
    }

    // --- ResourceBuilder -----------------------------------------------

    @Nested
    @Tag("chapter04")
    class ResourceBuilderTest {

        @Test
        void aFullyConfiguredResourceKeepsEveryFieldItWasGiven() {
            Resource resource = ResourceBuilder.builder()
                    .withUri("file:///notes.txt")
                    .withName("Notes")
                    .withDescription("Some notes")
                    .withMimeType("text/plain")
                    .addAudience(Role.USER)
                    .addAudience(Role.ASSISTANT)
                    .withPriority(0.5)
                    .build();

            assertEquals("file:///notes.txt", resource.uri());
            assertEquals("Notes", resource.name());
            assertEquals("Some notes", resource.description());
            assertEquals("text/plain", resource.mimeType());
            assertEquals(new Annotations(List.of(Role.USER, Role.ASSISTANT), 0.5), resource.annotations());
        }

        @Test
        void addAudienceAppendsToAnAudienceThatWasSetWholesale() {
            Resource resource = ResourceBuilder.builder()
                    .withUri("file:///notes.txt")
                    .withName("Notes")
                    .withAudience(new ArrayList<>(List.of(Role.USER)))
                    .addAudience(Role.ASSISTANT)
                    .build();

            assertEquals(List.of(Role.USER, Role.ASSISTANT), resource.annotations().audience());
            assertNull(resource.annotations().priority());
        }

        @Test
        void aPriorityOnItsOwnIsStillEnoughToProduceAnnotations() {
            Resource resource = ResourceBuilder.builder()
                    .withUri("file:///notes.txt")
                    .withName("Notes")
                    .withPriority(1.0)
                    .build();

            assertEquals(new Annotations(null, 1.0), resource.annotations());
        }

        @Test
        void aResourceWithNoAudienceOrPriorityOmitsAnnotations() {
            Resource resource = ResourceBuilder.builder()
                    .withUri("file:///notes.txt")
                    .withName("Notes")
                    .build();

            assertNull(resource.annotations());
            assertNull(resource.description());
            assertEquals(Resource.DEFAULT_MIME_TYPE, resource.mimeType());
        }

        @Test
        void explicitAnnotationsWinOverAudienceAndPriority() {
            Annotations supplied = new Annotations(List.of(Role.ASSISTANT), 0.1);

            Resource resource = ResourceBuilder.builder()
                    .withUri("file:///notes.txt")
                    .withName("Notes")
                    .withAnnotations(supplied)
                    .addAudience(Role.USER)
                    .withPriority(0.9)
                    .build();

            assertSame(supplied, resource.annotations());
        }

        @Test
        void withJsonMimeTypeIsAShorthandForTheJsonMimeType() {
            Resource resource = ResourceBuilder.builder()
                    .withUri("file:///config.json")
                    .withName("Configuration")
                    .withJsonMimeType()
                    .build();

            assertEquals(Resource.MIME_TYPE_JSON, resource.mimeType());
        }

        @Test
        void aResourceWithoutAUriIsRejected() {
            ResourceBuilder builder = ResourceBuilder.builder().withName("Notes");

            assertEquals("URI is required for Resource",
                         assertThrows(IllegalStateException.class, builder::build).getMessage());
        }

        @Test
        void aResourceWithoutANameIsRejected() {
            ResourceBuilder builder = ResourceBuilder.builder().withUri("file:///notes.txt");

            assertEquals("Name is required for Resource",
                         assertThrows(IllegalStateException.class, builder::build).getMessage());
        }
    }

    // --- ResourcesListResultBuilder ------------------------------------

    @Nested
    @Tag("chapter04")
    class ResourcesListResultBuilderTest {

        @Test
        void aResourceCanBeHandedOverPreBuiltAsFieldsOrAsItsOwnBuilder() {
            ResourcesListResult result = ResourcesListResultBuilder.builder()
                    .addResource(new Resource("file:///a.txt", "A", "The first"))
                    .addResource("file:///b.txt", "B", "The second")
                    .addResource(ResourceBuilder.builder()
                                         .withUri("file:///c.json")
                                         .withName("C")
                                         .withJsonMimeType())
                    .build();

            assertEquals(List.of("A", "B", "C"), result.resources().stream().map(Resource::name).toList());
            assertEquals(Resource.DEFAULT_MIME_TYPE, result.resources().get(1).mimeType());
            assertEquals(Resource.MIME_TYPE_JSON, result.resources().get(2).mimeType());
            assertNull(result.nextCursor());
        }

        @Test
        void theResourceListAndCursorCanBeReplacedWholesale() {
            ResourcesListResult result = ResourcesListResultBuilder.builder()
                    .addResource("file:///discarded.txt", "Discarded", "Replaced below")
                    .withResources(new ArrayList<>(List.of(new Resource("file:///kept.txt", "Kept", "The kept one"))))
                    .withNextCursor("page-2")
                    .build();

            assertEquals(List.of("Kept"), result.resources().stream().map(Resource::name).toList());
            assertEquals("page-2", result.nextCursor());
        }
    }

    // --- ToolCallResultBuilder -----------------------------------------

    @Nested
    @Tag("chapter04")
    class ToolCallResultBuilderTest {

        @Test
        void contentCanBeAddedAsPlainTextOrPreBuilt() {
            ToolCallResult result = ToolCallResultBuilder.builder()
                    .addTextContent("Operation completed")
                    .addContent(new ContentItem("Processed 100 items", "text"))
                    .build();

            assertEquals(new ContentItem("Operation completed", "text"), result.content().get(0));
            assertEquals("Processed 100 items", result.content().get(1).text());
            assertFalse(result.isError());
        }

        @Test
        void theContentListCanBeReplacedWholesale() {
            ToolCallResult result = ToolCallResultBuilder.builder()
                    .addTextContent("discarded")
                    .withContent(new ArrayList<>(List.of(new ContentItem("kept", "text"))))
                    .build();

            assertEquals(1, result.content().size());
            assertEquals("kept", result.content().get(0).text());
        }

        @Test
        void asErrorAndWithErrorBothSetTheErrorFlag() {
            assertTrue(ToolCallResultBuilder.builder().asError().build().isError());
            assertTrue(ToolCallResultBuilder.builder().withError(true).build().isError());
            assertFalse(ToolCallResultBuilder.builder().asError().withError(false).build().isError());
        }
    }

    // --- ToolsListResultBuilder ----------------------------------------

    @Nested
    @Tag("chapter04")
    class ToolsListResultBuilderTest {

        @Test
        void aToolCanBeHandedOverPreBuiltOrAsNameDescriptionAndSchema() {
            InputSchema schema = InputSchemaBuilder.builder()
                    .addProperty("keyword", "string", "The keyword", true)
                    .build();

            ToolsListResult result = ToolsListResultBuilder.builder()
                    .addTool(new Tool("first", "The first", null))
                    .addTool("second", "The second", schema)
                    .build();

            assertEquals(List.of("first", "second"), result.tools().stream().map(Tool::name).toList());
            assertSame(schema, result.tools().get(1).inputSchema());
        }

        @Test
        void aToolsSchemaCanBeBuiltInlineFromAConsumer() {
            ToolsListResult result = ToolsListResultBuilder.builder()
                    .addTool("search", "Search for items",
                             schema -> schema.addProperty("query", "string", "Search query", true))
                    .build();

            assertEquals(List.of("query"), result.tools().get(0).inputSchema().required());
        }

        @Test
        void addToolWithBuilderConfiguresNameDescriptionAndSchemaTogether() {
            ToolsListResult result = ToolsListResultBuilder.builder()
                    .addToolWithBuilder(tool -> tool
                            .withName("calculate")
                            .withDescription("Perform calculations")
                            .withInputSchema(schema -> schema
                                    .addProperty("expression", "string", "Math expression", true)))
                    .build();

            Tool tool = result.tools().get(0);
            assertEquals("calculate", tool.name());
            assertEquals("Perform calculations", tool.description());
            assertEquals(List.of("expression"), tool.inputSchema().required());
        }

        @Test
        void theToolListCanBeReplacedWholesale() {
            ToolsListResult result = ToolsListResultBuilder.builder()
                    .addTool(new Tool("discarded", "Replaced below", null))
                    .withTools(new ArrayList<>(List.of(new Tool("kept", "The kept one", null))))
                    .build();

            assertEquals(List.of("kept"), result.tools().stream().map(Tool::name).toList());
        }

        @Test
        void aToolBuilderAlsoAcceptsAPreBuiltSchema() {
            InputSchema schema = InputSchemaBuilder.builder()
                    .addProperty("keyword", "string", "The keyword", true)
                    .build();

            Tool tool = new ToolsListResultBuilder.ToolBuilder()
                    .withName("search")
                    .withDescription("Search for items")
                    .withInputSchema(schema)
                    .build();

            assertSame(schema, tool.inputSchema());
        }

        @Test
        void aToolWithoutANameIsRejected() {
            ToolsListResultBuilder.ToolBuilder builder =
                    new ToolsListResultBuilder.ToolBuilder().withDescription("The second");

            assertEquals("Name and description are required for Tool",
                         assertThrows(IllegalStateException.class, builder::build).getMessage());
        }

        @Test
        void aToolWithoutADescriptionIsRejected() {
            ToolsListResultBuilder.ToolBuilder builder =
                    new ToolsListResultBuilder.ToolBuilder().withName("second");

            assertThrows(IllegalStateException.class, builder::build);
        }
    }
}
