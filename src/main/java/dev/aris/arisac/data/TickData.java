package dev.aris.arisac.data;

public record TickData(
        float deltaYaw,
        float deltaPitch,
        float accelYaw,
        float accelPitch,
        float jerkYaw,
        float jerkPitch,
        float gcdErrorYaw,
        float gcdErrorPitch
) {}
