package nl.hauntedmc.observability.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObservabilityIdentityTest {

    @Test
    void createGeneratesUniqueProcessInstanceIds() {
        ObservabilityIdentity first = create();
        ObservabilityIdentity second = create();

        assertNotEquals(first.serviceInstanceId(), second.serviceInstanceId());
    }

    @Test
    void rejectsUnboundedOrUnsafeResourceTokens() {
        assertThrows(IllegalArgumentException.class, () -> new ObservabilityIdentity(
                "server features",
                "1.0.0",
                "instance-1",
                ObservabilityEnvironment.PRODUCTION,
                ObservabilityRuntimeKind.PAPER,
                "survival-1",
                "survival"
        ));
        assertThrows(IllegalArgumentException.class, () -> new ObservabilityIdentity(
                "serverfeatures",
                "1.0.0",
                "instance-1",
                ObservabilityEnvironment.PRODUCTION,
                ObservabilityRuntimeKind.PAPER,
                "survival/../../etc",
                "survival"
        ));
    }

    private static ObservabilityIdentity create() {
        return ObservabilityIdentity.create(
                "serverfeatures",
                "3.7.0",
                ObservabilityEnvironment.PRODUCTION,
                ObservabilityRuntimeKind.PAPER,
                "survival-1",
                "survival"
        );
    }
}
