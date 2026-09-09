package com.workshop.mcp.spec.builders;

import com.workshop.mcp.spec.CacheScope;
import com.workshop.mcp.spec.Capability;
import com.workshop.mcp.spec.DiscoverResult;
import com.workshop.mcp.spec.MetaKeys;
import com.workshop.mcp.spec.RequestEnvelope;
import com.workshop.mcp.spec.ServerCapabilities;
import com.workshop.mcp.spec.ServerInfo;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for creating {@link DiscoverResult} objects with a fluent API.
 * <p>
 * This replaces {@code InitializeResultBuilder}. Because
 * {@code server/discover} is answered by a throwaway probe process on stdio,
 * everything here is derived from static configuration.
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * DiscoverResult result = DiscoverResultBuilder.builder()
 *     .withDefaultCapabilities()
 *     .withTasksExtension()
 *     .withDefaultServerInfo()
 *     .build();
 * }</pre>
 *
 * @see DiscoverResult
 * @see ServerCapabilities
 * @since 1.0
 */
public class DiscoverResultBuilder {

    private List<String> supportedVersions = RequestEnvelope.SUPPORTED_VERSIONS;
    private ServerCapabilities capabilities;
    private String instructions;
    private Long ttlMs = CacheScope.DEFAULT_TTL_MS;
    private String cacheScope = CacheScope.PRIVATE;
    private ServerInfo serverInfo;
    private final Map<String, Object> experimental = new HashMap<>();
    private final Map<String, Object> extensions = new LinkedHashMap<>();

    private DiscoverResultBuilder() {
    }

    /**
     * Creates a new DiscoverResultBuilder instance.
     *
     * @return a new DiscoverResultBuilder instance
     */
    public static DiscoverResultBuilder builder() {
        return new DiscoverResultBuilder();
    }

    /**
     * Overrides the advertised protocol revisions.
     *
     * @param supportedVersions the revisions this server speaks
     * @return this builder instance for method chaining
     */
    public DiscoverResultBuilder withSupportedVersions(List<String> supportedVersions) {
        this.supportedVersions = supportedVersions;
        return this;
    }

    /**
     * Sets natural-language guidance for a model using this server.
     *
     * @param instructions the guidance text
     * @return this builder instance for method chaining
     */
    public DiscoverResultBuilder withInstructions(String instructions) {
        this.instructions = instructions;
        return this;
    }

    /**
     * Sets the caching hints for the discovery result.
     *
     * @param ttlMs      how long the client may cache this result, non-negative
     * @param cacheScope one of {@link CacheScope#PUBLIC} or {@link CacheScope#PRIVATE}
     * @return this builder instance for method chaining
     */
    public DiscoverResultBuilder withCacheHints(long ttlMs, String cacheScope) {
        this.ttlMs = ttlMs;
        this.cacheScope = cacheScope;
        return this;
    }

    /**
     * Sets the server capabilities directly.
     *
     * @param capabilities the capabilities to advertise
     * @return this builder instance for method chaining
     */
    public DiscoverResultBuilder withCapabilities(ServerCapabilities capabilities) {
        this.capabilities = capabilities;
        return this;
    }

    /**
     * Declares support for an MCP extension under {@code capabilities.extensions}.
     * <p>
     * Extensions use the format {@code {vendor-prefix}/{extension-name}};
     * official ones use the {@code io.modelcontextprotocol} prefix. An empty
     * configuration object is the conventional way to say "supported, no
     * settings".
     * </p>
     *
     * @param identifier the extension identifier, e.g. {@link MetaKeys#TASKS_EXTENSION}
     * @param config     the configuration object for this extension
     * @return this builder instance for method chaining
     */
    public DiscoverResultBuilder withExtension(String identifier, Object config) {
        this.extensions.put(identifier, config);
        return this;
    }

    /**
     * Declares the tasks extension, which is what tells a client it may
     * receive a task handle instead of an immediate result.
     *
     * @return this builder instance for method chaining
     */
    public DiscoverResultBuilder withTasksExtension() {
        return withExtension(MetaKeys.TASKS_EXTENSION, Map.of());
    }

    /**
     * Declares a non-standard capability under {@code capabilities.experimental}.
     *
     * @param identifier the capability name
     * @param config     the configuration object
     * @return this builder instance for method chaining
     */
    public DiscoverResultBuilder withExperimentalCapability(String identifier, Object config) {
        this.experimental.put(identifier, config);
        return this;
    }

    /**
     * Sets default server capabilities for this workshop server.
     * <p>
     * {@code listChanged} is deliberately left unset on tools and prompts. A
     * client that sees {@code listChanged: true} opens a
     * {@code subscriptions/listen} stream, and this server only advertises
     * that once it can acknowledge the stream correctly.
     * </p>
     *
     * @return this builder instance for method chaining
     */
    public DiscoverResultBuilder withDefaultCapabilities() {
        Capability unset = new Capability(null, null);
        this.capabilities = new ServerCapabilities(
                experimental.isEmpty() ? null : experimental,
                null,
                unset,
                new Capability(false, null),
                new Capability(false, false),
                new Capability(false, null),
                extensions.isEmpty() ? null : extensions
        );
        return this;
    }

    /**
     * Sets the server information using name and version strings.
     *
     * @param name    the name of the server
     * @param version the version of the server
     * @return this builder instance for method chaining
     */
    public DiscoverResultBuilder withServerInfo(String name, String version) {
        this.serverInfo = new ServerInfo(name, version);
        return this;
    }

    /**
     * Sets default server information for the agent-mcp-workshop.
     *
     * @return this builder instance for method chaining
     */
    public DiscoverResultBuilder withDefaultServerInfo() {
        return withServerInfo("agent-mcp-workshop", "0.0.1");
    }

    /**
     * Builds and returns the final {@link DiscoverResult}.
     * <p>
     * Capabilities configured through {@link #withExtension} or
     * {@link #withExperimentalCapability} after {@link #withDefaultCapabilities}
     * are folded in here, so declaration order in the calling chain does not
     * matter.
     * </p>
     *
     * @return a new DiscoverResult with the configured properties
     * @throws IllegalStateException if capabilities or server info are not set
     */
    public DiscoverResult build() {
        if (capabilities == null) {
            throw new IllegalStateException("Server capabilities are required");
        }
        if (serverInfo == null) {
            throw new IllegalStateException("Server info is required");
        }
        ServerCapabilities resolved = new ServerCapabilities(
                experimental.isEmpty() ? null : experimental,
                capabilities.logging(),
                capabilities.completions(),
                capabilities.prompts(),
                capabilities.resources(),
                capabilities.tools(),
                extensions.isEmpty() ? null : extensions
        );
        Map<String, Object> meta = new HashMap<>();
        meta.put(MetaKeys.SERVER_INFO, serverInfo);
        return new DiscoverResult(supportedVersions, resolved, instructions, ttlMs, cacheScope, meta);
    }
}
