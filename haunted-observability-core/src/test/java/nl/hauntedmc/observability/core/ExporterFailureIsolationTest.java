package nl.hauntedmc.observability.core;

import nl.hauntedmc.observability.api.ObservabilityConfig;
import nl.hauntedmc.observability.api.ObservabilityEnvironment;
import nl.hauntedmc.observability.api.ObservabilityIdentity;
import nl.hauntedmc.observability.api.ObservabilityRuntime;
import nl.hauntedmc.observability.api.ObservabilityRuntimeKind;
import nl.hauntedmc.observability.api.ObservabilityRuntimeState;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExporterFailureIsolationTest {

    @Test
    void unavailableCollectorKeepsRecordingPathAndShutdownBounded() {
        ObservabilityConfig config = new ObservabilityConfig(
                true,
                URI.create("http://127.0.0.1:1"),
                true,
                true,
                true,
                false,
                1.0,
                Duration.ofMillis(25),
                Duration.ofMillis(50),
                Duration.ofMillis(250)
        );

        ObservabilityRuntime runtime = ObservabilityRuntimes.builder(identity())
                .config(config)
                .build();
        assertEquals(ObservabilityRuntimeState.ACTIVE, runtime.state());

        TelemetryRecorder recorder = ObservabilityAccess.recorder(runtime);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            for (int index = 0; index < 10_000; index++) {
                recorder.start(OperationSpec.featureFramework("feature_enable", "stress"))
                        .complete("SUCCESS", 1, null);
            }
        });

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> runtime.forceFlush(Duration.ofMillis(250)));
        assertTimeoutPreemptively(Duration.ofSeconds(1), runtime::close);
        assertEquals(ObservabilityRuntimeState.CLOSED, runtime.state());
        assertTrue(runtime.forceFlush(Duration.ofMillis(1)));
        runtime.close();
    }

    private static ObservabilityIdentity identity() {
        return ObservabilityIdentity.create(
                "serverfeatures",
                "3.7.0",
                ObservabilityEnvironment.DEVELOPMENT,
                ObservabilityRuntimeKind.PAPER,
                "acceptance-1",
                "development"
        );
    }
}
