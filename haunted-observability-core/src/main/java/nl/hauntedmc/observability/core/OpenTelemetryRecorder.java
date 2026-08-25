package nl.hauntedmc.observability.core;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** OpenTelemetry implementation of the bounded HauntedObservability recording SPI. */
public final class OpenTelemetryRecorder implements TelemetryRecorder {
    private static final AttributeKey<String> LAYER = AttributeKey.stringKey("haunted.observability.layer");
    private static final AttributeKey<String> OPERATION = AttributeKey.stringKey("haunted.operation");
    private static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("haunted.outcome");
    private static final AttributeKey<String> FEATURE_ID = AttributeKey.stringKey("feature.id");
    private static final AttributeKey<String> DP_PLUGIN_ID = AttributeKey.stringKey("dataprovider.plugin.id");
    private static final AttributeKey<String> DP_DATABASE_TYPE = AttributeKey.stringKey("dataprovider.database.type");
    private static final AttributeKey<String> DP_OWNER_SCOPE = AttributeKey.stringKey("dataprovider.owner.scope");
    private static final AttributeKey<Long> DR_ATTEMPTS = AttributeKey.longKey("dataregistry.attempts");
    private static final AttributeKey<String> EXCEPTION_TYPE = AttributeKey.stringKey("exception.type");

    private final Tracer tracer;
    private final Logger logger;
    private final Instruments featureFramework;
    private final Instruments dataProvider;
    private final Instruments dataRegistry;
    private final LongHistogram dataRegistryAttempts;

    public OpenTelemetryRecorder(OpenTelemetry openTelemetry) {
        Objects.requireNonNull(openTelemetry, "openTelemetry");
        this.tracer = openTelemetry.tracerBuilder("nl.hauntedmc.observability").build();
        this.logger = openTelemetry.getLogsBridge().loggerBuilder("nl.hauntedmc.observability").build();
        Meter meter = openTelemetry.meterBuilder("nl.hauntedmc.observability").build();
        this.featureFramework = instruments(meter,
                "hauntedmc.featureframework.operation.count",
                "hauntedmc.featureframework.operation.duration");
        this.dataProvider = instruments(meter,
                "hauntedmc.dataprovider.operation.count",
                "hauntedmc.dataprovider.operation.duration");
        this.dataRegistry = instruments(meter,
                "hauntedmc.dataregistry.operation.count",
                "hauntedmc.dataregistry.operation.duration");
        this.dataRegistryAttempts = meter.histogramBuilder("hauntedmc.dataregistry.operation.attempts")
                .ofLongs()
                .setUnit("{attempt}")
                .setDescription("Number of attempts used by a DataRegistry operation.")
                .build();
    }

    @Override
    public TelemetryOperation start(OperationSpec spec) {
        Objects.requireNonNull(spec, "spec");
        Span span = tracer.spanBuilder(spec.layer().value() + "." + spec.operation()).startSpan();
        span.setAttribute(LAYER, spec.layer().value());
        span.setAttribute(OPERATION, spec.operation());
        if (spec.featureId() != null) span.setAttribute(FEATURE_ID, spec.featureId());
        if (spec.pluginId() != null) span.setAttribute(DP_PLUGIN_ID, spec.pluginId());
        if (spec.databaseType() != null) span.setAttribute(DP_DATABASE_TYPE, spec.databaseType());
        if (spec.ownerScope() != null) span.setAttribute(DP_OWNER_SCOPE, spec.ownerScope());
        return new RecordedOperation(spec, span, System.nanoTime());
    }

    private static Instruments instruments(Meter meter, String countName, String durationName) {
        LongCounter count = meter.counterBuilder(countName)
                .setDescription("Completed HauntedMC operational observations.")
                .build();
        DoubleHistogram duration = meter.histogramBuilder(durationName)
                .setUnit("s")
                .setDescription("Duration of HauntedMC operational observations.")
                .build();
        return new Instruments(count, duration);
    }

    private final class RecordedOperation implements TelemetryOperation {
        private final OperationSpec spec;
        private final Span span;
        private final long startedNanos;
        private final AtomicBoolean completed = new AtomicBoolean();

        private RecordedOperation(OperationSpec spec, Span span, long startedNanos) {
            this.spec = spec;
            this.span = span;
            this.startedNanos = startedNanos;
        }

        @Override
        public TelemetryScope openScope() {
            Scope scope = span.makeCurrent();
            AtomicBoolean closed = new AtomicBoolean();
            return () -> {
                if (closed.compareAndSet(false, true)) scope.close();
            };
        }

        @Override
        public void complete(String outcome, int attempts, Throwable failure) {
            if (!completed.compareAndSet(false, true)) return;
            String normalizedOutcome = normalizeOutcome(outcome);
            int normalizedAttempts = Math.max(1, attempts);
            try {
                Attributes metricAttributes = metricAttributes(spec, normalizedOutcome);
                Instruments instruments = switch (spec.layer()) {
                    case FEATUREFRAMEWORK -> featureFramework;
                    case DATAPROVIDER -> dataProvider;
                    case DATAREGISTRY -> dataRegistry;
                };
                instruments.count().add(1, metricAttributes);
                double seconds = (System.nanoTime() - startedNanos) / 1_000_000_000.0;
                instruments.duration().record(Math.max(0.0, seconds), metricAttributes);
                span.setAttribute(OUTCOME, normalizedOutcome);
                if (spec.layer() == ObservabilityLayer.DATAREGISTRY) {
                    span.setAttribute(DR_ATTEMPTS, (long) normalizedAttempts);
                    dataRegistryAttempts.record(normalizedAttempts, metricAttributes);
                }
                if (failure != null) {
                    span.recordException(failure);
                    span.setStatus(StatusCode.ERROR);
                    emitFailureLog(spec, normalizedOutcome, failure, span);
                }
            } finally {
                span.end();
            }
        }
    }

    private void emitFailureLog(OperationSpec spec, String outcome, Throwable failure, Span span) {
        logger.logRecordBuilder()
                .setSeverity(Severity.ERROR)
                .setBody("Observed operation failed")
                .setContext(span.storeInContext(Context.current()))
                .setAttribute(LAYER, spec.layer().value())
                .setAttribute(OPERATION, spec.operation())
                .setAttribute(OUTCOME, outcome)
                .setAttribute(EXCEPTION_TYPE, failure.getClass().getName())
                .emit();
    }

    private static Attributes metricAttributes(OperationSpec spec, String outcome) {
        AttributesBuilder builder = Attributes.builder()
                .put(OPERATION, spec.operation())
                .put(OUTCOME, outcome);
        switch (spec.layer()) {
            case FEATUREFRAMEWORK -> {
                if (spec.featureId() != null) builder.put(FEATURE_ID, spec.featureId());
            }
            case DATAPROVIDER -> builder
                    .put(DP_PLUGIN_ID, spec.pluginId())
                    .put(DP_DATABASE_TYPE, spec.databaseType());
            case DATAREGISTRY -> { }
        }
        return builder.build();
    }

    private static String normalizeOutcome(String outcome) {
        Objects.requireNonNull(outcome, "outcome");
        String normalized = outcome.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.isEmpty() || normalized.length() > 64) {
            throw new IllegalArgumentException("outcome must be a bounded stable value.");
        }
        return normalized;
    }

    private record Instruments(LongCounter count, DoubleHistogram duration) { }
}
