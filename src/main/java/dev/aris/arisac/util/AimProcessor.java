package dev.aris.arisac.util;

import dev.aris.arisac.data.TickData;

public class AimProcessor {
    private static final int SIGNIFICANT_THRESHOLD = 15;
    private static final int TOTAL_THRESHOLD = 80;
    private static final float MAX_DELTA_FOR_GCD = 5.0f;

    private final RunningMode xMode = new RunningMode(TOTAL_THRESHOLD);
    private final RunningMode yMode = new RunningMode(TOTAL_THRESHOLD);

    private float lastXRot, lastYRot;
    private float lastYaw, lastPitch;
    private float lastDeltaYaw, lastDeltaPitch;
    private float lastAccelYaw, lastAccelPitch;
    private float accelYaw, accelPitch;
    private double modeX, modeY;
    private boolean hasFirst;

    public TickData process(float yaw, float pitch) {
        if (!hasFirst) {
            lastYaw = yaw; lastPitch = pitch;
            hasFirst = true;
            return new TickData(0, 0, 0, 0, 0, 0, 0, 0);
        }

        float dYaw = normalize(yaw - lastYaw);
        float dPitch = pitch - lastPitch;
        float dYawAbs = Math.abs(dYaw);
        float dPitchAbs = Math.abs(dPitch);

        lastAccelYaw = accelYaw;
        lastAccelPitch = accelPitch;
        accelYaw = dYaw - lastDeltaYaw;
        accelPitch = dPitch - lastDeltaPitch;

        float jerkYaw = accelYaw - lastAccelYaw;
        float jerkPitch = accelPitch - lastAccelPitch;

        double divX = GcdMath.gcd(dYawAbs, lastXRot);
        if (dYawAbs > 0 && dYawAbs < MAX_DELTA_FOR_GCD && divX > GcdMath.MINIMUM_DIVISOR) {
            xMode.add(divX); lastXRot = dYawAbs;
        }
        double divY = GcdMath.gcd(dPitchAbs, lastYRot);
        if (dPitchAbs > 0 && dPitchAbs < MAX_DELTA_FOR_GCD && divY > GcdMath.MINIMUM_DIVISOR) {
            yMode.add(divY); lastYRot = dPitchAbs;
        }

        if (xMode.size() > SIGNIFICANT_THRESHOLD) {
            double[] m = xMode.getMode();
            if (m[1] > SIGNIFICANT_THRESHOLD) modeX = m[0];
        }
        if (yMode.size() > SIGNIFICANT_THRESHOLD) {
            double[] m = yMode.getMode();
            if (m[1] > SIGNIFICANT_THRESHOLD) modeY = m[0];
        }

        float gcdErrYaw = calcError(dYaw, modeX);
        float gcdErrPitch = calcError(dPitch, modeY);

        lastYaw = yaw; lastPitch = pitch;
        lastDeltaYaw = dYaw; lastDeltaPitch = dPitch;

        return new TickData(dYaw, dPitch, accelYaw, accelPitch, jerkYaw, jerkPitch, gcdErrYaw, gcdErrPitch);
    }

    public int getSensitivity() {
        if (modeY <= 0) return -1;
        double f = Math.cbrt(modeY / 1.2);
        return (int) Math.round(((f - 0.2) / 0.6) * 200);
    }

    public double getModeX() { return modeX; }
    public double getModeY() { return modeY; }
    public int getSamplesX() { return xMode.size(); }
    public int getSamplesY() { return yMode.size(); }

    public void reset() {
        xMode.clear(); yMode.clear();
        lastXRot = lastYRot = lastYaw = lastPitch = 0;
        lastDeltaYaw = lastDeltaPitch = lastAccelYaw = lastAccelPitch = 0;
        accelYaw = accelPitch = 0;
        modeX = modeY = 0;
        hasFirst = false;
    }

    private float normalize(float a) {
        while (a > 180) a -= 360;
        while (a < -180) a += 360;
        return a;
    }

    private float calcError(float delta, double mode) {
        if (mode == 0) return 0;
        double abs = Math.abs(delta);
        double rem = abs % mode;
        return (float) Math.min(rem, mode - rem);
    }
}
