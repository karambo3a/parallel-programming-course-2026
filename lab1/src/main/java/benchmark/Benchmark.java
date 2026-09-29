package benchmark;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lab.MetricsCollector;

public class Benchmark {
    private static final int TIME_SEC = 5;
    private static final int ITERATIONS = 5;

    public static double run(MetricsCollector collector, long[] values, int t, long seconds) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean stop = new AtomicBoolean();
        long[] ops = new long[t];
        Thread[] threads = new Thread[t];

        for (int k = 0; k < t; ++k) {
            int threadNum = k;
            threads[k] = new Thread(() -> {
                long localCount = 0;
                int i = threadNum * 1000;

                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                while (!stop.get()) {
                    collector.record(values[i]);
                    localCount++;
                    i++;
                    if (i == values.length) {
                        i = 0;
                    }
                }

                ops[threadNum] = localCount;
            });
            threads[k].start();
        }

        long t0 = System.currentTimeMillis();
        start.countDown();
        TimeUnit.SECONDS.sleep(seconds);
        stop.set(true);
        long t1 = System.currentTimeMillis();

        for (Thread thread : threads) {
            thread.join();
        }

        long totalSum = 0;
        for (int i = 0; i < t; ++i) {
            totalSum += ops[i];
        }
        return (double) totalSum / (t1 - t0) * 1000;
    }

    public static double measurePoint(MetricsCollector collector, long[] values, int t) throws InterruptedException {
        run(collector, values, t, TIME_SEC);

        double[] results = new double[ITERATIONS];
        for (int i = 0; i < ITERATIONS; ++i) {
            results[i] = run(collector, values, t, TIME_SEC);
        }

        System.out.println(collector.snapshot().count());

        Arrays.sort(results);
        return results[results.length / 2];
    }
}
