package nl.hauntedmc.observability.api;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/** Immutable runtime configuration for one process-local observability SDK. */
public record ObservabilityConfig(
        boolean enabled,
        URI otlpEndpoint,
        boolean tracesEnabled,
        boolean metricsEnabled,
        boolean logsEnabled,
        boolean jvmMetricsEnabled,
        double traceSampleRatio,
        Duration metricExportInterval,
        Duration exportTimeout,
        Duration flushTimeout
) {
    private static final URI DEFAULT_ENDPOINT = URI.create("http://otel-collector:4317");

    public ObservabilityConfig {
        otlpEndpoint = Objects.requireNonNull(otlpEndpoint, "otlpEndpoint");
        String scheme = otlpEndpoint.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("otlpEndpoint must use http or https.");
        }
        if (traceSampleRatio < 0.0 || traceSampleRatio > 1.0 || Double.isNaN(traceSampleRatio)) {
            throw new IllegalArgumentException("traceSampleRatio must be between 0.0 and 1.0.");
        }
        metricExportInterval = positive(metricExportInterval, "metricExportInterval");
        exportTimeout = positive(exportTimeout, "exportTimeout");
        flushTimeout = positive(flushTimeout, "flushTimeout");
        if (jvmMetricsEnabled && !metricsEnabled) {
            jvmMetricsEnabled = false;
        }
    }

    public static ObservabilityConfig defaults() {
        return new ObservabilityConfig(
                true,
                DEFAULT_ENDPOINT,
                true,
                true,
                true,
                true,
                1.0,
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                Duration.ofSeconds(5)
        );
    }

    public static ObservabilityConfig disabled() {
        ObservabilityConfig defaults = defaults();
        return new ObservabilityConfig(
                false,
                defaults.otlpEndpoint(),
                false,
                false,
                false,
                false,
                0.0,
                defaults.metricExportInterval(),
                defaults.exportTimeout(),
                defaults.flushTimeout()
        );
    }

    private static Duration positive(Duration value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive.");
        }
        return value;
    }
}
