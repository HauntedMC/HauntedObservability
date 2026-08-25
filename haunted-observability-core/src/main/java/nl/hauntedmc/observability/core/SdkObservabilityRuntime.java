package nl.hauntedmc.observability.core;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import nl.hauntedmc.observability.api.ObservabilityConfig;
import nl.hauntedmc.observability.api.ObservabilityIdentity;
import nl.hauntedmc.observability.api.ObservabilityRuntimeState;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class SdkObservabilityRuntime implements RecorderBackedRuntime {
    private static final AttributeKey<String> SERVICE_NAMESPACE = AttributeKey.stringKey("service.namespace");
    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
    private static final AttributeKey<String> SERVICE_VERSION = AttributeKey.stringKey("service.version");
    private static final AttributeKey<String> SERVICE_INSTANCE_ID = AttributeKey.stringKey("service.instance.id");
    private static final AttributeKey<String> DEPLOYMENT_ENVIRONMENT = AttributeKey.stringKey("deployment.environment.name");
    private static final AttributeKey<String> HAUNTED_NETWORK = AttributeKey.stringKey("haunted.network");
    private static final AttributeKey<String> HAUNTED_RUNTIME = AttributeKey.stringKey("haunted.runtime");
    private static final AttributeKey<String> HAUNTED_SERVER_NAME = AttributeKey.stringKey("haunted.server.name");
    private static final AttributeKey<String> HAUNTED_SERVER_TYPE = AttributeKey.stringKey("haunted.server.type");

    private final ObservabilityConfig config;
    private final SdkTracerProvider tracerProvider;
    private final SdkMeterProvider meterProvider;
    private final SdkLoggerProvider loggerProvider;
    private final RuntimeTelemetry runtimeTelemetry;
    private final TelemetryRecorder recorder;
    private final AtomicBoolean closed = new AtomicBoolean();

    private SdkObservabilityRuntime(
            ObservabilityConfig config,
            OpenTelemetrySdk sdk,
            SdkTracerProvider tracerProvider,
            SdkMeterProvider meterProvider,
            SdkLoggerProvider loggerProvider,
            RuntimeTelemetry runtimeTelemetry
    ) {
        this.config = config;
        this.tracerProvider = tracerProvider;
        this.meterProvider = meterProvider;
        this.loggerProvider = loggerProvider;
        this.runtimeTelemetry = runtimeTelemetry;
        this.recorder = new OpenTelemetryRecorder(sdk);
    }

    static SdkObservabilityRuntime start(ObservabilityIdentity identity, ObservabilityConfig config) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(config, "config");
        Resource resource = Resource.getDefault().merge(Resource.create(Attributes.builder()
                .put(SERVICE_NAMESPACE, "hauntedmc")
                .put(SERVICE_NAME, identity.serviceName())
                .put(SERVICE_VERSION, identity.serviceVersion())
                .put(SERVICE_INSTANCE_ID, identity.serviceInstanceId())
                .put(DEPLOYMENT_ENVIRONMENT, identity.environment().resourceValue())
                .put(HAUNTED_NETWORK, "hauntedmc")
                .put(HAUNTED_RUNTIME, identity.runtime().resourceValue())
                .put(HAUNTED_SERVER_NAME, identity.serverName())
                .put(HAUNTED_SERVER_TYPE, identity.serverType())
                .build()));

        var tracerBuilder = SdkTracerProvider.builder()
                .setResource(resource)
                .setSampler(config.tracesEnabled()
                        ? Sampler.parentBased(Sampler.traceIdRatioBased(config.traceSampleRatio()))
                        : Sampler.alwaysOff());
        if (config.tracesEnabled()) {
            OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                    .setEndpoint(config.otlpEndpoint().toString())
                    .setTimeout(config.exportTimeout())
                    .build();
            tracerBuilder.addSpanProcessor(BatchSpanProcessor.builder(exporter).build());
        }
        SdkTracerProvider tracerProvider = tracerBuilder.build();

        var meterBuilder = SdkMeterProvider.builder().setResource(resource);
        if (config.metricsEnabled()) {
            OtlpGrpcMetricExporter exporter = OtlpGrpcMetricExporter.builder()
                    .setEndpoint(config.otlpEndpoint().toString())
                    .setTimeout(config.exportTimeout())
                    .build();
            meterBuilder.registerMetricReader(PeriodicMetricReader.builder(exporter)
                    .setInterval(config.metricExportInterval())
                    .build());
        }
        SdkMeterProvider meterProvider = meterBuilder.build();

        var loggerBuilder = SdkLoggerProvider.builder().setResource(resource);
        if (config.logsEnabled()) {
            OtlpGrpcLogRecordExporter exporter = OtlpGrpcLogRecordExporter.builder()
                    .setEndpoint(config.otlpEndpoint().toString())
                    .setTimeout(config.exportTimeout())
                    .build();
            loggerBuilder.addLogRecordProcessor(BatchLogRecordProcessor.builder(exporter).build());
        }
        SdkLoggerProvider loggerProvider = loggerBuilder.build();

        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setMeterProvider(meterProvider)
                .setLoggerProvider(loggerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        RuntimeTelemetry runtimeTelemetry = config.metricsEnabled() && config.jvmMetricsEnabled()
                ? RuntimeTelemetry.create(sdk)
                : null;
        return new SdkObservabilityRuntime(config, sdk, tracerProvider, meterProvider, loggerProvider, runtimeTelemetry);
    }

    @Override public ObservabilityRuntimeState state() {
        return closed.get() ? ObservabilityRuntimeState.CLOSED : ObservabilityRuntimeState.ACTIVE;
    }
    @Override public Optional<Throwable> startupFailure() { return Optional.empty(); }

    @Override
    public boolean forceFlush(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive.");
        if (closed.get()) return true;
        long deadline = System.nanoTime() + timeout.toNanos();
        return await(tracerProvider.forceFlush(), deadline)
                & await(meterProvider.forceFlush(), deadline)
                & await(loggerProvider.forceFlush(), deadline);
    }

    @Override public boolean forceFlush() { return forceFlush(config.flushTimeout()); }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (runtimeTelemetry != null) {
            try { runtimeTelemetry.close(); } catch (RuntimeException ignored) { }
        }
        long deadline = System.nanoTime() + config.flushTimeout().toNanos();
        await(tracerProvider.forceFlush(), deadline);
        await(meterProvider.forceFlush(), deadline);
        await(loggerProvider.forceFlush(), deadline);
        await(tracerProvider.shutdown(), deadline);
        await(meterProvider.shutdown(), deadline);
        await(loggerProvider.shutdown(), deadline);
    }

    @Override public TelemetryRecorder recorder() { return closed.get() ? TelemetryRecorder.noop() : recorder; }

    private static boolean await(CompletableResultCode result, long deadlineNanos) {
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0L) return false;
        try {
            result.join(Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining)), TimeUnit.MILLISECONDS);
            return result.isSuccess();
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
