package nl.hauntedmc.observability.core;

import java.util.Objects;
import java.util.regex.Pattern;

/** Bounded internal descriptor used by the three neutral SPI adapters. */
public record OperationSpec(
        ObservabilityLayer layer,
        String operation,
        String featureId,
        String pluginId,
        String databaseType,
        String ownerScope
) {
    private static final Pattern OPERATION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    public OperationSpec {
        layer = Objects.requireNonNull(layer, "layer");
        operation = bounded(operation, "operation", 128);
        if (!OPERATION.matcher(operation).matches()) throw new IllegalArgumentException("operation must use the stable bounded operation vocabulary.");
        featureId = optional(featureId, "featureId", 64);
        pluginId = optional(pluginId, "pluginId", 128);
        databaseType = optional(databaseType, "databaseType", 64);
        ownerScope = optional(ownerScope, "ownerScope", 256);
        switch (layer) {
            case FEATUREFRAMEWORK -> {
                if (pluginId != null || databaseType != null || ownerScope != null) throw new IllegalArgumentException("FeatureFramework operations may only include featureId.");
            }
            case DATAPROVIDER -> {
                if (featureId != null || pluginId == null || databaseType == null) throw new IllegalArgumentException("DataProvider operations require pluginId/databaseType and no featureId.");
            }
            case DATAREGISTRY -> {
                if (featureId != null || pluginId != null || databaseType != null || ownerScope != null) throw new IllegalArgumentException("DataRegistry operations contain only the bounded operation name.");
            }
        }
    }

    public static OperationSpec featureFramework(String operation, String featureId) {
        return new OperationSpec(ObservabilityLayer.FEATUREFRAMEWORK, operation, featureId, null, null, null);
    }
    public static OperationSpec dataProvider(String operation, String pluginId, String databaseType, String ownerScope) {
        return new OperationSpec(ObservabilityLayer.DATAPROVIDER, operation, null, pluginId, databaseType, ownerScope);
    }
    public static OperationSpec dataRegistry(String operation) {
        return new OperationSpec(ObservabilityLayer.DATAREGISTRY, operation, null, null, null, null);
    }
    private static String bounded(String value, String field, int maxLength) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) throw new IllegalArgumentException(field + " must be non-blank and at most " + maxLength + " characters.");
        return normalized;
    }
    private static String optional(String value, String field, int maxLength) {
        return value == null ? null : bounded(value, field, maxLength);
    }
}
