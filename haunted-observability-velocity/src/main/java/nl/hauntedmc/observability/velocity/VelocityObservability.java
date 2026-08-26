package nl.hauntedmc.observability.velocity;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.observability.api.ObservabilityConfig;
import nl.hauntedmc.observability.api.ObservabilityEnvironment;
import nl.hauntedmc.observability.api.ObservabilityIdentity;
import nl.hauntedmc.observability.api.ObservabilityRuntime;
import nl.hauntedmc.observability.api.ObservabilityRuntimeKind;
import nl.hauntedmc.observability.core.ObservabilityRuntimes;

import java.util.Objects;

/** Thin Velocity bootstrap helper for one application-owned observability runtime. */
public final class VelocityObservability {
    private VelocityObservability() { }

    public static Builder builder(Object pluginInstance, ProxyServer proxyServer) {
        Objects.requireNonNull(pluginInstance, "pluginInstance");
        Objects.requireNonNull(proxyServer, "proxyServer");
        PluginContainer container = proxyServer.getPluginManager().fromInstance(pluginInstance)
                .orElseThrow(() -> new IllegalArgumentException("Plugin instance is not registered with Velocity."));
        return new Builder(container);
    }

    public static Builder builder(PluginContainer plugin) { return new Builder(Objects.requireNonNull(plugin, "plugin")); }

    public static final class Builder {
        private final PluginContainer plugin;
        private ObservabilityConfig config = ObservabilityConfig.defaults();
        private ObservabilityEnvironment environment = ObservabilityEnvironment.DEVELOPMENT;
        private String serverName;
        private String serverType = "proxy";

        private Builder(PluginContainer plugin) { this.plugin = plugin; }
        public Builder config(ObservabilityConfig config) { this.config = Objects.requireNonNull(config, "config"); return this; }
        public Builder environment(ObservabilityEnvironment environment) { this.environment = Objects.requireNonNull(environment, "environment"); return this; }
        public Builder server(String serverName) { this.serverName = Objects.requireNonNull(serverName, "serverName"); return this; }
        public Builder server(String serverName, String serverType) {
            this.serverName = Objects.requireNonNull(serverName, "serverName");
            this.serverType = Objects.requireNonNull(serverType, "serverType");
            return this;
        }
        public ObservabilityRuntime build() {
            Objects.requireNonNull(serverName, "serverName");
            ObservabilityIdentity identity = ObservabilityIdentity.create(
                    plugin.getDescription().getId(),
                    plugin.getDescription().getVersion().orElse("unknown"),
                    environment,
                    ObservabilityRuntimeKind.VELOCITY,
                    serverName,
                    serverType
            );
            return ObservabilityRuntimes.builder(identity).config(config).build();
        }
    }
}
