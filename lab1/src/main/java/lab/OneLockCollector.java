package lab;

import java.util.Arrays;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static lab.MetricsSupport.bucketFor;
import static lab.MetricsSupport.percentile;

public class OneLockCollector implements MetricsCollector {
    private final long[] buckets = new long[MetricsCollector.BUCKETS_SIZE];
    private long count;
    private long sum;
    private long min = Long.MAX_VALUE;
    private long max;

    @Override
    synchronized public void record(long value) {
        int bucket = bucketFor(value);
        ;
        buckets[bucket]++;
        count++;
        sum += value;
        min = min(value, min);
        max = max(value, max);
    }

    @Override
    synchronized public Snapshot snapshot() {
        long[] bucketsCopy = Arrays.copyOf(buckets, MetricsCollector.BUCKETS_SIZE);

        return new Snapshot(
                bucketsCopy,
                count,
                sum,
                min,
                max,
                percentile(0.5, count, bucketsCopy),
                percentile(0.99, count, bucketsCopy)
        );
    }
}
