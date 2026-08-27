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
import java.util.function.Supplier;

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

        SdkTracerProvider tracerProvider = null;
        SdkMeterProvider meterProvider = null;
        SdkLoggerProvider loggerProvider = null;
        RuntimeTelemetry runtimeTelemetry = null;
        try {
            Resource resource = resource(identity);
            tracerProvider = tracerProvider(config, resource);
            meterProvider = meterProvider(config, resource);
            loggerProvider = loggerProvider(config, resource);

            OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .setMeterProvider(meterProvider)
                    .setLoggerProvider(loggerProvider)
                    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                    .build();
            runtimeTelemetry = config.metricsEnabled() && config.jvmMetricsEnabled()
                    ? RuntimeTelemetry.create(sdk)
                    : null;
            return new SdkObservabilityRuntime(
                    config,
                    sdk,
                    tracerProvider,
                    meterProvider,
                    loggerProvider,
                    runtimeTelemetry
            );
        } catch (RuntimeException failure) {
            closeRuntimeTelemetry(runtimeTelemetry);
            long deadline = System.nanoTime() + config.flushTimeout().toNanos();
            shutdownBounded(loggerProvider, deadline);
            shutdownBounded(meterProvider, deadline);
            shutdownBounded(tracerProvider, deadline);
            throw failure;
        }
    }

    private static Resource resource(ObservabilityIdentity identity) {
        return Resource.getDefault().merge(Resource.create(Attributes.builder()
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
    }

    private static SdkTracerProvider tracerProvider(ObservabilityConfig config, Resource resource) {
        var builder = SdkTracerProvider.builder()
                .setResource(resource)
                .setSampler(config.tracesEnabled()
                        ? Sampler.parentBased(Sampler.traceIdRatioBased(config.traceSampleRatio()))
                        : Sampler.alwaysOff());
        if (config.tracesEnabled()) {
            OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                    .setEndpoint(config.otlpEndpoint().toString())
                    .setTimeout(config.exportTimeout())
                    .build();
            builder.addSpanProcessor(BatchSpanProcessor.builder(exporter).build());
        }
        return builder.build();
    }

    private static SdkMeterProvider meterProvider(ObservabilityConfig config, Resource resource) {
        var builder = SdkMeterProvider.builder().setResource(resource);
        if (config.metricsEnabled()) {
            OtlpGrpcMetricExporter exporter = OtlpGrpcMetricExporter.builder()
                    .setEndpoint(config.otlpEndpoint().toString())
                    .setTimeout(config.exportTimeout())
                    .build();
            builder.registerMetricReader(PeriodicMetricReader.builder(exporter)
                    .setInterval(config.metricExportInterval())
                    .build());
        }
        return builder.build();
    }

    private static SdkLoggerProvider loggerProvider(ObservabilityConfig config, Resource resource) {
        var builder = SdkLoggerProvider.builder().setResource(resource);
        if (config.logsEnabled()) {
            OtlpGrpcLogRecordExporter exporter = OtlpGrpcLogRecordExporter.builder()
                    .setEndpoint(config.otlpEndpoint().toString())
                    .setTimeout(config.exportTimeout())
                    .build();
            builder.addLogRecordProcessor(BatchLogRecordProcessor.builder(exporter).build());
        }
        return builder.build();
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
        closeRuntimeTelemetry(runtimeTelemetry);
        long deadline = System.nanoTime() + config.flushTimeout().toNanos();
        await(tracerProvider.forceFlush(), deadline);
        await(meterProvider.forceFlush(), deadline);
        await(loggerProvider.forceFlush(), deadline);
        shutdownBounded(tracerProvider, deadline);
        shutdownBounded(meterProvider, deadline);
        shutdownBounded(loggerProvider, deadline);
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

    private static void shutdownBounded(SdkTracerProvider provider, long deadlineNanos) {
        if (provider != null) shutdownBounded(provider::shutdown, deadlineNanos);
    }

    private static void shutdownBounded(SdkMeterProvider provider, long deadlineNanos) {
        if (provider != null) shutdownBounded(provider::shutdown, deadlineNanos);
    }

    private static void shutdownBounded(SdkLoggerProvider provider, long deadlineNanos) {
        if (provider != null) shutdownBounded(provider::shutdown, deadlineNanos);
    }

    private static void shutdownBounded(Supplier<CompletableResultCode> shutdown, long deadlineNanos) {
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0L) return;

        Thread worker = Thread.ofVirtual().name("haunted-observability-shutdown").start(() -> {
            try {
                await(shutdown.get(), deadlineNanos);
            } catch (RuntimeException ignored) {
                // Provider shutdown is best effort and must never extend application shutdown indefinitely.
            }
        });
        try {
            worker.join(Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining)));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        if (worker.isAlive()) worker.interrupt();
    }

    private static void closeRuntimeTelemetry(RuntimeTelemetry runtimeTelemetry) {
        if (runtimeTelemetry == null) return;
        try {
            runtimeTelemetry.close();
        } catch (RuntimeException ignored) {
            // Observability cleanup must remain fail-open.
        }
    }
}
