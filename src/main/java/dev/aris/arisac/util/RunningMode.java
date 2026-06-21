package dev.aris.arisac.util;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

public class RunningMode {
    private static final double THRESHOLD = 1e-3;
    private final Queue<Double> queue;
    private final Map<Double, Integer> freq;
    private final int maxSize;

    public RunningMode(int maxSize) {
        this.queue = new ArrayDeque<>(maxSize);
        this.freq = new HashMap<>();
        this.maxSize = maxSize;
    }

    public void add(double value) {
        if (queue.size() >= maxSize) {
            Double old = queue.poll();
            if (old != null) {
                int c = freq.getOrDefault(old, 1) - 1;
                if (c <= 0) freq.remove(old); else freq.put(old, c);
            }
        }
        for (Map.Entry<Double, Integer> e : freq.entrySet()) {
            if (Math.abs(e.getKey() - value) < THRESHOLD) {
                e.setValue(e.getValue() + 1);
                queue.add(e.getKey());
                return;
            }
        }
        freq.put(value, 1);
        queue.add(value);
    }

    public double[] getMode() {
        int max = 0; Double best = null;
        for (Map.Entry<Double, Integer> e : freq.entrySet())
            if (e.getValue() > max) { max = e.getValue(); best = e.getKey(); }
        return best == null ? new double[]{0, 0} : new double[]{best, max};
    }

    public int size() { return queue.size(); }
    public void clear() { queue.clear(); freq.clear(); }
}
