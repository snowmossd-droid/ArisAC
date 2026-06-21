package dev.aris.arisac.data;

import dev.aris.arisac.util.AimProcessor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayerData {

    public final UUID uuid;
    public final PositionHistory positionHistory = new PositionHistory(40);
    public final AimProcessor aimProcessor = new AimProcessor();

    // VL per check
    private final ConcurrentHashMap<String, Double> vlMap = new ConcurrentHashMap<>();

    // Flag/punishment tracking
    private final AtomicInteger flagCount = new AtomicInteger(0);
    private final ConcurrentHashMap<String, Integer> checkFlagCount = new ConcurrentHashMap<>();
    private long lastFlagReset = System.currentTimeMillis();

    // Aim assist samples (jerk / gcd error buffers)
    public final Deque<Float> jerkYawSamples = new ArrayDeque<>();
    public final Deque<Float> jerkPitchSamples = new ArrayDeque<>();
    public final Deque<Float> gcdErrYawSamples = new ArrayDeque<>();

    // KillAura: sprint tracking
    public long stopSprintTime = -1;
    public long startSprintTime = -1;
    public long attackTime = -1;
    public boolean hadStopSprint = false;

    // KillAura: Grim double-move
    public long lastMovePacketTime = -1;
    public float lastMoveYaw = Float.NaN;
    public float lastMovePitch = Float.NaN;

    // AutoTotem: packet sequence
    public record TimedPacket(long time, String type, int slot) {}
    public final Deque<TimedPacket> recentInventoryPackets = new ArrayDeque<>();

    // Reach: last known position
    public long lastAttackTime = -1;
    public UUID lastAttackTarget = null;

    // Rotation tracking
    public float lastYaw = Float.NaN;
    public float lastPitch = Float.NaN;
    public boolean teleporting = false;
public long keepAliveId = -1;
    public long keepAliveSentTime = 0;


    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    // ── VL ──────────────────────────────────────────────────

    public void addVL(String check, double amount) {
        vlMap.merge(check, amount, Double::sum);
    }

    public double getVL(String check) {
        return vlMap.getOrDefault(check, 0.0);
    }

    public void decayVL(String check, double amount) {
        vlMap.compute(check, (k, v) -> (v == null || v <= amount) ? 0.0 : v - amount);
    }

    public void resetVL(String check) {
        vlMap.put(check, 0.0);
    }

    // ── Flag tracking ────────────────────────────────────────

    public int getFlagCount() { return flagCount.get(); }
    public int incrementFlagCount() { return flagCount.incrementAndGet(); }

    public void recordCheckFlag(String check) {
        checkFlagCount.merge(check, 1, Integer::sum);
    }

    public int getCheckFlagCount(String check) {
        return checkFlagCount.getOrDefault(check, 0);
    }

    public long getLastFlagReset() { return lastFlagReset; }
    public void resetFlags() {
        flagCount.set(0);
        checkFlagCount.clear();
        vlMap.clear();
        lastFlagReset = System.currentTimeMillis();
    }

    // ── Utility ──────────────────────────────────────────────

    public void clearKillAuraState() {
        stopSprintTime = startSprintTime = attackTime = -1;
        hadStopSprint = false;
    }

    public void pruneInventoryPackets(long windowMs) {
        long cutoff = System.currentTimeMillis() - windowMs;
        recentInventoryPackets.removeIf(p -> p.time() < cutoff);
    }

    public void addAimSample(TickData t, int maxSize) {
        if (Math.abs(t.deltaYaw()) < 0.001f && Math.abs(t.deltaPitch()) < 0.001f) return;
        addSample(jerkYawSamples, t.jerkYaw(), maxSize);
        addSample(jerkPitchSamples, t.jerkPitch(), maxSize);
        addSample(gcdErrYawSamples, t.gcdErrorYaw(), maxSize);
    }

    private void addSample(Deque<Float> d, float v, int max) {
        if (d.size() >= max) d.pollFirst();
        d.addLast(v);
    }

    public static double variance(Deque<Float> samples) {
        if (samples.size() < 2) return 0;
        double mean = samples.stream().mapToDouble(Float::doubleValue).average().orElse(0);
        return samples.stream().mapToDouble(f -> Math.pow(f - mean, 2)).average().orElse(0);
    }

    public static double mean(Deque<Float> samples) {
        return samples.stream().mapToDouble(Float::doubleValue).average().orElse(0);
    }

    // AntiSpoof state
    public dev.aris.arisac.check.spoof.AntiSpoofCheck.SpoofData spoofData =
            new dev.aris.arisac.check.spoof.AntiSpoofCheck.SpoofData();

    public void resetSpoofData() {
        spoofData = new dev.aris.arisac.check.spoof.AntiSpoofCheck.SpoofData();
    }
}
