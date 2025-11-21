package com.workshop.mcp.spec.builders;

import com.workshop.mcp.spec.Capability;
import com.workshop.mcp.spec.InitializeResult;
import com.workshop.mcp.spec.ServerCapabilities;
import com.workshop.mcp.spec.ServerInfo;

/**
 * Builder for creating {@link InitializeResult} objects with a fluent API.
 * <p>
 * This builder simplifies the construction of InitializeResult objects used in
 * the MCP initialization handshake. It provides convenient methods for setting
 * server information, capabilities, and protocol version, including defaults
 * for common configurations.
 * </p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * InitializeResult result = InitializeResultBuilder.builder()
 *     .withProtocolVersion("1.0")
 *     .withServerInfo("my-server", "1.0.0")
 *     .withDefaultCapabilities()
 *     .build();
 * }</pre>
 * 
 * @see InitializeResult
 * @see ServerInfo
 * @see ServerCapabilities
 * @see Capability
 * @since 1.0
 */
public class InitializeResultBuilder {
    private String protocolVersion;
    private ServerCapabilities capabilities;
    private ServerInfo serverInfo;

    /**
     * Private constructor to enforce the use of the static builder method.
     */
    private InitializeResultBuilder() {
    }

    /**
     * Creates a new InitializeResultBuilder instance.
     * 
     * @return a new InitializeResultBuilder instance
     */
    public static InitializeResultBuilder builder() {
        return new InitializeResultBuilder();
    }

    /**
     * Sets the protocol version for the initialization result.
     * 
     * @param protocolVersion the MCP protocol version supported by the server
     * @return this builder instance for method chaining
     */
    public InitializeResultBuilder withProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
        return this;
    }

    /**
     * Sets the server capabilities for the initialization result.
     * 
     * @param capabilities the ServerCapabilities object defining what the server supports
     * @return this builder instance for method chaining
     */
    public InitializeResultBuilder withCapabilities(ServerCapabilities capabilities) {
        this.capabilities = capabilities;
        return this;
    }

    /**
     * Sets the server information for the initialization result.
     * 
     * @param serverInfo the ServerInfo object containing server identification
     * @return this builder instance for method chaining
     */
    public InitializeResultBuilder withServerInfo(ServerInfo serverInfo) {
        this.serverInfo = serverInfo;
        return this;
    }

    /**
     * Sets the server information using name and version strings.
     * <p>
     * This is a convenience method that creates a ServerInfo object internally.
     * </p>
     * 
     * @param name the name of the server
     * @param version the version of the server
     * @return this builder instance for method chaining
     */
    public InitializeResultBuilder withServerInfo(String name, String version) {
        this.serverInfo = new ServerInfo(name, version);
        return this;
    }

    /**
     * Sets default server information for the agent-mcp-workshop.
     * <p>
     * This convenience method sets the server name to "agent-mcp-workshop"
     * and version to "0.0.1".
     * </p>
     * 
     * @return this builder instance for method chaining
     */
    public InitializeResultBuilder withDefaultServerInfo() {
        this.serverInfo = new ServerInfo("agent-mcp-workshop", "0.0.1");
        return this;
    }

    /**
     * Sets default server capabilities with all features enabled.
     * <p>
     * This convenience method creates ServerCapabilities with:
     * <ul>
     *   <li>Tools capability with listChanged enabled</li>
     *   <li>Prompts capability with listChanged enabled</li>
     *   <li>Resources capability with both listChanged and subscribe enabled</li>
     * </ul>
     * 
     * @return this builder instance for method chaining
     */
    public InitializeResultBuilder withDefaultCapabilities() {
        Capability capabilityTrue = new Capability();
        this.capabilities = new ServerCapabilities(capabilityTrue, capabilityTrue, new Capability(false, false), new Capability(null,null));
        return this;
    }

    /**
     * Builds and returns the final {@link InitializeResult} object.
     * <p>
     * This method validates that all required fields (protocol version,
     * capabilities, and server info) are set before creating the result.
     * </p>
     * 
     * @return a new InitializeResult instance with the configured properties
     * @throws IllegalStateException if any required field is not set
     */
    public InitializeResult build() {
        if (protocolVersion == null) {
            throw new IllegalStateException("Protocol version is required");
        }
        if (capabilities == null) {
            throw new IllegalStateException("Server capabilities are required");
        }
        if (serverInfo == null) {
            throw new IllegalStateException("Server info is required");
        }
        return new InitializeResult(protocolVersion, capabilities, serverInfo);
    }
}