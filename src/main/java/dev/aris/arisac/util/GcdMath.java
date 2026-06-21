package dev.aris.arisac.util;

public final class GcdMath {
    public static final double MINIMUM_DIVISOR = ((Math.pow(0.2f, 3) * 8) * 0.15) - 1e-3;

    private GcdMath() {}

    public static double gcd(double a, double b) {
        if (a == 0) return 0;
        if (a < b) { double t = a; a = b; b = t; }
        while (b > MINIMUM_DIVISOR) {
            double t = a - (Math.floor(a / b) * b);
            a = b; b = t;
        }
        return a;
    }
}
