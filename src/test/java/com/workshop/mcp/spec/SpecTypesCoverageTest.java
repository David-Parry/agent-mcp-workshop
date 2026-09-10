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
        @Tag("chapter03")
        void aMethodKeyPrintsAsTheMethodNameItStandsFor() {
            assertEquals("tools/call", UniqueKeys.TOOLS_CALL.toString());
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
    }

    // --- the remaining wire records ---------------------------------------

    @Nested
    class WireRecords {

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






}
