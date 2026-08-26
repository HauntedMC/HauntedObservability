package nl.hauntedmc.observability.core;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Internal recording SPI shared by HauntedObservability integration modules. */
@FunctionalInterface
public interface TelemetryRecorder {
    TelemetryOperation start(OperationSpec operation);

    default TelemetryRegistration registerDataRegistryReadiness(BooleanSupplier readiness) {
        Objects.requireNonNull(readiness, "readiness");
        return TelemetryRegistration.noop();
    }

    static TelemetryRecorder noop() { return ignored -> TelemetryOperation.noop(); }
}
