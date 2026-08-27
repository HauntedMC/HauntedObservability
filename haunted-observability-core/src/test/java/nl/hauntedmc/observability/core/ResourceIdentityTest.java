package nl.hauntedmc.observability.core;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.resources.Resource;
import nl.hauntedmc.observability.api.ObservabilityEnvironment;
import nl.hauntedmc.observability.api.ObservabilityIdentity;
import nl.hauntedmc.observability.api.ObservabilityRuntimeKind;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ResourceIdentityTest {
    private static final AttributeKey<String> SERVICE_NAMESPACE = AttributeKey.stringKey("service.namespace");
    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
    private static final AttributeKey<String> SERVICE_VERSION = AttributeKey.stringKey("service.version");
    private static final AttributeKey<String> SERVICE_INSTANCE_ID = AttributeKey.stringKey("service.instance.id");
    private static final AttributeKey<String> DEPLOYMENT_ENVIRONMENT = AttributeKey.stringKey("deployment.environment.name");
    private static final AttributeKey<String> HAUNTED_NETWORK = AttributeKey.stringKey("haunted.network");
    private static final AttributeKey<String> HAUNTED_RUNTIME = AttributeKey.stringKey("haunted.runtime");
    private static final AttributeKey<String> HAUNTED_SERVER_NAME = AttributeKey.stringKey("haunted.server.name");
    private static final AttributeKey<String> HAUNTED_SERVER_TYPE = AttributeKey.stringKey("haunted.server.type");

    @Test
    void exportsCompleteBoundedIdentityAndUniqueInstanceIds() throws Exception {
        ObservabilityIdentity first = ObservabilityIdentity.create(
                "serverfeatures",
                "3.7.0",
                ObservabilityEnvironment.PRODUCTION,
                ObservabilityRuntimeKind.PAPER,
                "survival-1",
                "survival"
        );
        ObservabilityIdentity second = ObservabilityIdentity.create(
                "serverfeatures",
                "3.7.0",
                ObservabilityEnvironment.PRODUCTION,
                ObservabilityRuntimeKind.PAPER,
                "survival-1",
                "survival"
        );

        Method resourceFactory = SdkObservabilityRuntime.class.getDeclaredMethod("resource", ObservabilityIdentity.class);
        resourceFactory.setAccessible(true);
        Resource resource = (Resource) resourceFactory.invoke(null, first);

        assertEquals("hauntedmc", resource.getAttribute(SERVICE_NAMESPACE));
        assertEquals("serverfeatures", resource.getAttribute(SERVICE_NAME));
        assertEquals("3.7.0", resource.getAttribute(SERVICE_VERSION));
        assertEquals(first.serviceInstanceId(), resource.getAttribute(SERVICE_INSTANCE_ID));
        assertEquals("production", resource.getAttribute(DEPLOYMENT_ENVIRONMENT));
        assertEquals("hauntedmc", resource.getAttribute(HAUNTED_NETWORK));
        assertEquals("paper", resource.getAttribute(HAUNTED_RUNTIME));
        assertEquals("survival-1", resource.getAttribute(HAUNTED_SERVER_NAME));
        assertEquals("survival", resource.getAttribute(HAUNTED_SERVER_TYPE));
        assertFalse(first.serviceInstanceId().isBlank());
        assertNotEquals(first.serviceInstanceId(), second.serviceInstanceId());
    }
}
