package dev.aris.arisac.data;

import org.bukkit.Location;

import java.util.ArrayDeque;
import java.util.Deque;

public class PositionHistory {
    private record Entry(long time, double x, double y, double z, float width, float height) {}

    private final Deque<Entry> history = new ArrayDeque<>();
    private final int maxTicks;

    public PositionHistory(int maxTicks) {
        this.maxTicks = maxTicks;
    }

    public void record(Location loc, float width, float height) {
        history.addLast(new Entry(System.currentTimeMillis(), loc.getX(), loc.getY(), loc.getZ(), width, height));
        long cutoff = System.currentTimeMillis() - (maxTicks * 50L + 200);
        while (!history.isEmpty() && history.peekFirst().time() < cutoff) {
            history.pollFirst();
        }
    }

    public double[] getPositionAt(long targetTime) {
        if (history.isEmpty()) return null;
        Entry best = null;
        long bestDiff = Long.MAX_VALUE;
        for (Entry e : history) {
            long diff = Math.abs(e.time() - targetTime);
            if (diff < bestDiff) { bestDiff = diff; best = e; }
        }
        if (best == null) return null;
        return new double[]{best.x(), best.y() + best.height() / 2.0, best.z(), best.width(), best.height()};
    }

    public void clear() { history.clear(); }
}
