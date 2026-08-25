package nl.hauntedmc.observability.core;

import nl.hauntedmc.observability.api.ObservabilityConfig;
import nl.hauntedmc.observability.api.ObservabilityEnvironment;
import nl.hauntedmc.observability.api.ObservabilityIdentity;
import nl.hauntedmc.observability.api.ObservabilityRuntime;
import nl.hauntedmc.observability.api.ObservabilityRuntimeKind;
import nl.hauntedmc.observability.api.ObservabilityRuntimeState;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ObservabilityRuntimesTest {

    @Test
    void disabledRuntimeIsNoopAndClosesIdempotently() {
        ObservabilityRuntime runtime = ObservabilityRuntimes.builder(identity())
                .config(ObservabilityConfig.disabled())
                .build();

        assertEquals(ObservabilityRuntimeState.DISABLED, runtime.state());
        assertFalse(runtime.enabled());
        assertTrue(runtime.startupFailure().isEmpty());
        assertTrue(runtime.forceFlush(Duration.ofMillis(1)));
        runtime.close();
        runtime.close();
        assertEquals(ObservabilityRuntimeState.CLOSED, runtime.state());
    }

    @Test
    void requiresIdentityAndPositiveFlushTimeout() {
        assertThrows(NullPointerException.class, () -> ObservabilityRuntimes.builder(null));
        ObservabilityRuntime runtime = ObservabilityRuntimes.builder(identity())
                .config(ObservabilityConfig.disabled())
                .build();
        assertThrows(NullPointerException.class, () -> runtime.forceFlush(null));
        runtime.close();
    }

    private static ObservabilityIdentity identity() {
        return ObservabilityIdentity.create(
                "serverfeatures",
                "3.7.0",
                ObservabilityEnvironment.DEVELOPMENT,
                ObservabilityRuntimeKind.PAPER,
                "dev-1",
                "development"
        );
    }
}
