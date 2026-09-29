package lab;

import java.util.concurrent.atomic.AtomicLong;

import static lab.MetricsSupport.bucketFor;
import static lab.MetricsSupport.percentile;

public class LockStripingCollector implements MetricsCollector {

    private final long[] buckets;
    private final AtomicLong count;
    private final AtomicLong sum;
    private final AtomicLong min;
    private final AtomicLong max;

    private final Object[] locks;

    public LockStripingCollector() {
        buckets = new long[MetricsCollector.BUCKETS_SIZE];
        count = new AtomicLong();
        sum = new AtomicLong();
        min = new AtomicLong(Long.MAX_VALUE);
        max = new AtomicLong();

        locks = new Object[MetricsCollector.LOCKS_SIZE];
        for (int i = 0; i < MetricsCollector.LOCKS_SIZE; ++i) {
            locks[i] = new Object();
        }
    }

    @Override
    public void record(long value) {
        int bucket = bucketFor(value);
        ;

        int lockNum = bucket % MetricsCollector.LOCKS_SIZE;
        synchronized (locks[lockNum]) {
            buckets[bucket]++;
        }

        count.incrementAndGet();
        sum.addAndGet(value);
        updateMin(value);
        updateMax(value);
    }

    @Override
    public Snapshot snapshot() {
        long[] bucketsCopy = new long[MetricsCollector.BUCKETS_SIZE];
        for (int lock = 0; lock < MetricsCollector.LOCKS_SIZE; lock++) {
            synchronized (locks[lock]) {
                for (int bucket = lock; bucket < MetricsCollector.BUCKETS_SIZE; bucket += MetricsCollector.LOCKS_SIZE) {
                    bucketsCopy[bucket] = buckets[bucket];
                }
            }
        }

        long countCopy = count.get();
        long sumCopy = sum.get();
        long minCopy = min.get();
        long maxCopy = max.get();

        return new Snapshot(
                bucketsCopy,
                countCopy,
                sumCopy,
                minCopy,
                maxCopy,
                percentile(0.5, countCopy, bucketsCopy),
                percentile(0.99, countCopy, bucketsCopy)
        );
    }

    private void updateMin(long value) {
        while (true) {
            long currMin = min.get();

            if (currMin <= value) {
                return;
            }

            if (min.compareAndSet(currMin, value)) {
                return;
            }
        }
    }

    private void updateMax(long value) {
        while (true) {
            long currMax = max.get();

            if (currMax >= value) {
                return;
            }

            if (max.compareAndSet(currMax, value)) {
                return;
            }
        }
    }
}