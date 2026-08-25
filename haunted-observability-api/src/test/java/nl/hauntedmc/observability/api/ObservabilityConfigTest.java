package nl.hauntedmc.observability.api;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservabilityConfigTest {

    @Test
    void defaultsMatchOperationalBaseline() {
        ObservabilityConfig config = ObservabilityConfig.defaults();

        assertTrue(config.enabled());
        assertEquals(URI.create("http://otel-collector:4317"), config.otlpEndpoint());
        assertTrue(config.tracesEnabled());
        assertTrue(config.metricsEnabled());
        assertTrue(config.logsEnabled());
        assertTrue(config.jvmMetricsEnabled());
        assertEquals(1.0, config.traceSampleRatio());
        assertEquals(Duration.ofSeconds(30), config.metricExportInterval());
    }

    @Test
    void disabledConfigurationDisablesEverySignal() {
        ObservabilityConfig config = ObservabilityConfig.disabled();

        assertFalse(config.enabled());
        assertFalse(config.tracesEnabled());
        assertFalse(config.metricsEnabled());
        assertFalse(config.logsEnabled());
        assertFalse(config.jvmMetricsEnabled());
        assertEquals(0.0, config.traceSampleRatio());
    }

    @Test
    void jvmMetricsCannotRemainEnabledWithoutMetrics() {
        ObservabilityConfig config = new ObservabilityConfig(
                true,
                URI.create("http://collector:4317"),
                true,
                false,
                true,
                true,
                0.5,
                Duration.ofSeconds(10),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2)
        );

        assertFalse(config.jvmMetricsEnabled());
    }

    @Test
    void rejectsUnsafeOrUnboundedConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> configWith(URI.create("ftp://collector"), 0.5, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> configWith(URI.create("http://collector"), -0.1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> configWith(URI.create("http://collector"), 1.1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> configWith(URI.create("http://collector"), 0.5, Duration.ZERO));
    }

    private static ObservabilityConfig configWith(URI endpoint, double ratio, Duration interval) {
        return new ObservabilityConfig(
                true,
                endpoint,
                true,
                true,
                true,
                true,
                ratio,
                interval,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        );
    }
}
