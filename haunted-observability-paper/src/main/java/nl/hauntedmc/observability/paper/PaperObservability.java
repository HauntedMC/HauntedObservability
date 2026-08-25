package nl.hauntedmc.observability.paper;

import nl.hauntedmc.observability.api.ObservabilityConfig;
import nl.hauntedmc.observability.api.ObservabilityEnvironment;
import nl.hauntedmc.observability.api.ObservabilityIdentity;
import nl.hauntedmc.observability.api.ObservabilityRuntime;
import nl.hauntedmc.observability.api.ObservabilityRuntimeKind;
import nl.hauntedmc.observability.core.ObservabilityRuntimes;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Objects;

/** Thin Paper bootstrap helper for one application-owned observability runtime. */
public final class PaperObservability {
    private PaperObservability() { }

    public static Builder builder(JavaPlugin plugin) { return new Builder(plugin); }

    public static final class Builder {
        private final JavaPlugin plugin;
        private ObservabilityConfig config = ObservabilityConfig.defaults();
        private ObservabilityEnvironment environment = ObservabilityEnvironment.DEVELOPMENT;
        private String serverName;
        private String serverType;

        private Builder(JavaPlugin plugin) { this.plugin = Objects.requireNonNull(plugin, "plugin"); }
        public Builder config(ObservabilityConfig config) { this.config = Objects.requireNonNull(config, "config"); return this; }
        public Builder environment(ObservabilityEnvironment environment) { this.environment = Objects.requireNonNull(environment, "environment"); return this; }
        public Builder server(String serverName, String serverType) {
            this.serverName = Objects.requireNonNull(serverName, "serverName");
            this.serverType = Objects.requireNonNull(serverType, "serverType");
            return this;
        }
        public ObservabilityRuntime build() {
            Objects.requireNonNull(serverName, "serverName");
            Objects.requireNonNull(serverType, "serverType");
            ObservabilityIdentity identity = ObservabilityIdentity.create(
                    plugin.getPluginMeta().getName().toLowerCase(Locale.ROOT),
                    plugin.getPluginMeta().getVersion(),
                    environment,
                    ObservabilityRuntimeKind.PAPER,
                    serverName,
                    serverType
            );
            return ObservabilityRuntimes.builder(identity).config(config).build();
        }
    }
}
