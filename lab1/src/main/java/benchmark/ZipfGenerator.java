package benchmark;

import java.util.Random;

import static java.lang.Math.pow;

public class ZipfGenerator {
    private static final int SIZE = 1 << 20;
    private static final int MAX_VALUE = 1023;
    private static final double S = 1.15;
    private static final long SEED = 9;

    public static long[] generate() {
        var result = new long[SIZE];
        Random random = new Random(SEED);
        double[] cdf = buildCdf();

        for (int i = 0; i < SIZE; ++i) {
            result[i] = generateOne(random, cdf);
        }

        return result;
    }

    private static long generateOne(Random random, double[] cdf) {
        double r = random.nextDouble();
        for (int i = 0; i < MAX_VALUE; ++i) {
            if (cdf[i] >= r) {
                return i + 1;
            }
        }

        return MAX_VALUE;
    }

    private static double[] buildCdf() {
        double[] weights = new double[MAX_VALUE];
        double totalWeight = 0;
        for (int k = 0; k < MAX_VALUE; ++k) {
            weights[k] = 1.0 / pow(k + 1, S);
            totalWeight += weights[k];
        }

        double[] cdf = new double[MAX_VALUE];
        double accumulated = 0;
        for (int i = 0; i < MAX_VALUE; ++i) {
            accumulated += weights[i] / totalWeight;
            cdf[i] = accumulated;
        }

        return cdf;
    }
}
