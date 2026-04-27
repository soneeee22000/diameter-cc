package dev.pseonkyaw.diametercc.observability;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import dev.pseonkyaw.diametercc.gy.CcRequestType;

/**
 * Centralised Micrometer metrics for the Diameter Credit-Control flow.
 *
 * <p>All metrics are exposed via {@code /actuator/prometheus} and consumed
 * by the bundled Grafana dashboard (Day 2 Block 6).
 *
 * <ul>
 *   <li>{@code diameter_ccr_total{type, result_code}} — counter per CCR
 *       outcome, broken down by request type and Result-Code.</li>
 *   <li>{@code diameter_ccr_latency_seconds{type}} — handle() latency timer
 *       with explicit SLO buckets at 5/10/25/50/100/250 ms (p99 target
 *       per docs/ARCHITECTURE-diameter.md §7).</li>
 *   <li>{@code diameter_active_sessions} — gauge of OPEN CcSessions.</li>
 *   <li>{@code diameter_quota_granted_units_total} / {@code _used_units_total}
 *       — running counters for cumulative grant / usage.</li>
 *   <li>{@code diameter_idempotent_replay_total} — counter of replayed
 *       CCRs short-circuited by the reservation cache.</li>
 * </ul>
 */
@Component
public class DiameterMetrics {

    private final MeterRegistry registry;
    private final AtomicLong activeSessions = new AtomicLong(0);
    private final Counter quotaGranted;
    private final Counter quotaUsed;
    private final Counter replays;

    public DiameterMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge("diameter_active_sessions", activeSessions);
        this.quotaGranted = Counter.builder("diameter_quota_granted_units_total")
            .description("Cumulative units granted across all CCRs")
            .register(registry);
        this.quotaUsed = Counter.builder("diameter_quota_used_units_total")
            .description("Cumulative units reported as used across all CCRs")
            .register(registry);
        this.replays = Counter.builder("diameter_idempotent_replay_total")
            .description("CCRs short-circuited by the (session_id, cc_request_number) idempotency cache")
            .register(registry);
    }

    public void recordHandled(CcRequestType type, int resultCode, Duration latency) {
        Counter.builder("diameter_ccr_total")
            .tag("type", type.name())
            .tag("result_code", String.valueOf(resultCode))
            .register(registry)
            .increment();

        Timer.builder("diameter_ccr_latency_seconds")
            .tag("type", type.name())
            .publishPercentileHistogram()
            .serviceLevelObjectives(
                Duration.ofMillis(5),
                Duration.ofMillis(10),
                Duration.ofMillis(25),
                Duration.ofMillis(50),
                Duration.ofMillis(100),
                Duration.ofMillis(250))
            .register(registry)
            .record(latency);
    }

    public void recordReplay() {
        replays.increment();
    }

    public void recordGrant(long units) {
        if (units > 0) quotaGranted.increment(units);
    }

    public void recordUsage(long units) {
        if (units > 0) quotaUsed.increment(units);
    }

    public void sessionOpened() {
        activeSessions.incrementAndGet();
    }

    public void sessionClosed() {
        activeSessions.updateAndGet(n -> Math.max(0L, n - 1L));
    }
}
