package nl.hauntedmc.observability.testkit;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import nl.hauntedmc.observability.api.ObservabilityRuntime;
import nl.hauntedmc.observability.api.ObservabilityRuntimeState;
import nl.hauntedmc.observability.core.OpenTelemetryRecorder;
import nl.hauntedmc.observability.core.RecorderBackedRuntime;
import nl.hauntedmc.observability.core.TelemetryRecorder;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** In-memory OpenTelemetry runtime for integration and adapter tests. */
public final class InMemoryObservability implements RecorderBackedRuntime {
    private final InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
    private final InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    private final InMemoryLogRecordExporter logExporter = InMemoryLogRecordExporter.create();
    private final SdkTracerProvider tracerProvider;
    private final SdkMeterProvider meterProvider;
    private final SdkLoggerProvider loggerProvider;
    private final TelemetryRecorder recorder;
    private final AtomicBoolean closed = new AtomicBoolean();

    private InMemoryObservability() {
        tracerProvider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spanExporter)).build();
        meterProvider = SdkMeterProvider.builder().registerMetricReader(metricReader).build();
        loggerProvider = SdkLoggerProvider.builder().addLogRecordProcessor(SimpleLogRecordProcessor.create(logExporter)).build();
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setMeterProvider(meterProvider)
                .setLoggerProvider(loggerProvider)
                .build();
        recorder = new OpenTelemetryRecorder(sdk);
    }

    public static InMemoryObservability create() { return new InMemoryObservability(); }
    public ObservabilityRuntime runtime() { return this; }
    public List<SpanData> spans() { return spanExporter.getFinishedSpanItems(); }
    public List<MetricData> metrics() { return List.copyOf(metricReader.collectAllMetrics()); }
    public List<LogRecordData> logs() { return logExporter.getFinishedLogRecordItems(); }
    public void reset() { spanExporter.reset(); logExporter.reset(); metricReader.collectAllMetrics(); }

    @Override public ObservabilityRuntimeState state() { return closed.get() ? ObservabilityRuntimeState.CLOSED : ObservabilityRuntimeState.ACTIVE; }
    @Override public Optional<Throwable> startupFailure() { return Optional.empty(); }
    @Override public boolean forceFlush(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive.");
        if (closed.get()) return true;
        long millis = Math.max(1L, timeout.toMillis());
        tracerProvider.forceFlush().join(millis, TimeUnit.MILLISECONDS);
        meterProvider.forceFlush().join(millis, TimeUnit.MILLISECONDS);
        loggerProvider.forceFlush().join(millis, TimeUnit.MILLISECONDS);
        return true;
    }
    @Override public boolean forceFlush() { return forceFlush(Duration.ofSeconds(1)); }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        tracerProvider.shutdown().join(1, TimeUnit.SECONDS);
        meterProvider.shutdown().join(1, TimeUnit.SECONDS);
        loggerProvider.shutdown().join(1, TimeUnit.SECONDS);
    }
    @Override public TelemetryRecorder recorder() { return closed.get() ? TelemetryRecorder.noop() : recorder; }
}
