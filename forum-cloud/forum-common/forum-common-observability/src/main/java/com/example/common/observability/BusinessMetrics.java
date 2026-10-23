package com.example.common.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 业务指标统一入口。
 *
 * <p>标签只使用低基数字段（type/result/scope/kind），不要把 uid/tid/email 作为标签。</p>
 */
@Component
public class BusinessMetrics {

    private final MeterRegistry registry;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void increment(String name, String... tags) {
        registry.counter(name, tags).increment();
    }

    public void increment(String name, double amount, String... tags) {
        registry.counter(name, tags).increment(amount);
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }

    public void stop(Timer.Sample sample, String name, String... tags) {
        sample.stop(registry.timer(name, tags));
    }

    public <T> T time(String name, Supplier<T> supplier, String... tags) {
        Timer.Sample sample = start();
        try {
            return supplier.get();
        } finally {
            stop(sample, name, tags);
        }
    }

    public void record(String name, double amount, String... tags) {
        registry.summary(name, tags).record(amount);
    }

    public void gauge(String name, AtomicLong value, String... tags) {
        Gauge.builder(name, value, AtomicLong::get).tags(tags).register(registry);
    }
}
