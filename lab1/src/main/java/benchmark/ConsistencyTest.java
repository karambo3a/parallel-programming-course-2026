package benchmark;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import lab.DoubleBufferingCollector;
import lab.MetricsCollector;
import lab.Snapshot;

public class ConsistencyTest {

    private static final int ITERATIONS = 10_000;

    public static void main(String[] args)
            throws InterruptedException {

        long[] values = ZipfGenerator.generate();

        MetricsCollector collector =
                new DoubleBufferingCollector();

        ConsistencyTest.test(collector, values, 4);
    }

    public static void test(MetricsCollector collector, long[] values, int t) throws InterruptedException {
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
                    throw new RuntimeException(e);
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

        start.countDown();

        long broken = 0;
        long totalLess = 0;
        long totalMore = 0;
        for (int i = 0; i < ITERATIONS; ++i) {
            Snapshot snapshot = collector.snapshot();
            long bucketsSum = 0;
            for (long s : snapshot.buckets()) {
                bucketsSum += s;
            }

            if (bucketsSum > snapshot.count()) {
                totalMore++;
                broken++;
            } else if (bucketsSum < snapshot.count()) {
                totalLess++;
                broken++;
            }
        }

        stop.set(true);

        for (Thread thread : threads) {
            thread.join();
        }

        double brokenPercent =
                broken * 100.0 / ITERATIONS;

        long totalOps = 0;
        for (int i = 0; i < t; ++i) {
            totalOps += ops[i];
        }

        Snapshot finalSnapshot = collector.snapshot();
        long diff = finalSnapshot.count() - totalOps;

        System.out.println("total less: " + totalLess);
        System.out.println("total more: " + totalMore);
        System.out.println("final count: " + finalSnapshot.count());
        System.out.println("real calls: " + totalOps);
        System.out.println("difference: " + diff);
        System.out.println("broken percent: " + brokenPercent);
    }
}
