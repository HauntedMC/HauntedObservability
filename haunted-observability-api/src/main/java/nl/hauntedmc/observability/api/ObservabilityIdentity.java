package nl.hauntedmc.observability.api;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Stable process resource identity attached to every exported signal. */
public record ObservabilityIdentity(
        String serviceName,
        String serviceVersion,
        String serviceInstanceId,
        ObservabilityEnvironment environment,
        ObservabilityRuntimeKind runtime,
        String serverName,
        String serverType
) {
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    public ObservabilityIdentity {
        serviceName = requireToken(serviceName, "serviceName");
        serviceVersion = requireToken(serviceVersion, "serviceVersion");
        serviceInstanceId = requireToken(serviceInstanceId, "serviceInstanceId");
        environment = Objects.requireNonNull(environment, "environment");
        runtime = Objects.requireNonNull(runtime, "runtime");
        serverName = requireToken(serverName, "serverName");
        serverType = requireToken(serverType, "serverType");
    }

    /** Creates an identity with a unique process instance id. */
    public static ObservabilityIdentity create(
            String serviceName,
            String serviceVersion,
            ObservabilityEnvironment environment,
            ObservabilityRuntimeKind runtime,
            String serverName,
            String serverType
    ) {
        return new ObservabilityIdentity(
                serviceName,
                serviceVersion,
                UUID.randomUUID().toString(),
                environment,
                runtime,
                serverName,
                serverType
        );
    }

    private static String requireToken(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (!TOKEN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " must be a stable token using letters, numbers, '.', '_' or '-'.");
        }
        return normalized;
    }
}
