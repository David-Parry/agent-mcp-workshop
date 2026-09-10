package com.workshop.mcp.spec.builders;

import com.workshop.mcp.spec.CacheScope;
import com.workshop.mcp.spec.Capability;
import com.workshop.mcp.spec.DiscoverResult;
import com.workshop.mcp.spec.MetaKeys;
import com.workshop.mcp.spec.RequestEnvelope;
import com.workshop.mcp.spec.ServerCapabilities;
import com.workshop.mcp.spec.ServerInfo;
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
}
