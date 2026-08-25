package nl.hauntedmc.observability.core;

import nl.hauntedmc.observability.api.ObservabilityConfig;
import nl.hauntedmc.observability.api.ObservabilityIdentity;
import nl.hauntedmc.observability.api.ObservabilityRuntime;
import nl.hauntedmc.observability.api.ObservabilityRuntimeBuilder;
import nl.hauntedmc.observability.api.ObservabilityRuntimeState;

import java.util.Objects;

/** Entry point for constructing one process-local HauntedObservability runtime. */
public final class ObservabilityRuntimes {
    private ObservabilityRuntimes() { }

    public static ObservabilityRuntimeBuilder builder(ObservabilityIdentity identity) {
        return new Builder().identity(identity);
    }

    private static final class Builder implements ObservabilityRuntimeBuilder {
        private ObservabilityIdentity identity;
        private ObservabilityConfig config = ObservabilityConfig.defaults();

        @Override public ObservabilityRuntimeBuilder config(ObservabilityConfig config) {
            this.config = Objects.requireNonNull(config, "config"); return this;
        }
        @Override public ObservabilityRuntimeBuilder identity(ObservabilityIdentity identity) {
            this.identity = Objects.requireNonNull(identity, "identity"); return this;
        }
        @Override public ObservabilityRuntime build() {
            Objects.requireNonNull(identity, "identity");
            if (!config.enabled()) return new NoopObservabilityRuntime(ObservabilityRuntimeState.DISABLED, null);
            try {
                return SdkObservabilityRuntime.start(identity, config);
            } catch (Throwable failure) {
                if (failure instanceof VirtualMachineError virtualMachineError) throw virtualMachineError;
                if (failure instanceof ThreadDeath threadDeath) throw threadDeath;
                return new NoopObservabilityRuntime(ObservabilityRuntimeState.FAILED, failure);
            }
        }
    }
}
