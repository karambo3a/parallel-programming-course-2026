package lab;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static lab.MetricsSupport.bucketFor;
import static lab.MetricsSupport.percentile;

public class DoubleBufferingCollector implements MetricsCollector {
    private final List<ThreadBuffers> allBuffers = new ArrayList<>();
    private final Object listLock = new Object();
    private final ThreadLocal<ThreadBuffers> myBuffer = ThreadLocal.withInitial(() -> {
        ThreadBuffers s = new ThreadBuffers();
        synchronized (listLock) {
            allBuffers.add(s);
        }
        return s;
    });

    private volatile int active = 0;
    private final long[] buckets = new long[MetricsCollector.BUCKETS_SIZE];

    private long count = 0;
    private long sum = 0;
    private long min = Long.MAX_VALUE;
    private long max = 0;

    @Override
    public void record(long value) {
        ThreadBuffers currBuffer = myBuffer.get();
        int b;

        while (true) {
            b = active;
            currBuffer.inside.set(b);

            if (active == b) {
                break;
            }

            currBuffer.inside.setRelease(-1);
        }

        int bucket = bucketFor(value);
        currBuffer.buckets[b][bucket]++;
        currBuffer.count[b]++;
        currBuffer.sum[b] += value;
        currBuffer.min[b] = min(value, currBuffer.min[b]);
        currBuffer.max[b] = max(value, currBuffer.max[b]);

        currBuffer.inside.setRelease(-1);
    }


    @Override
    public Snapshot snapshot() {
        synchronized (listLock) {
            int old = active;
            active = 1 - old;

            for (ThreadBuffers buffer : allBuffers) {
                while (buffer.inside.get() == old) {
                    Thread.onSpinWait();
                }

                for (int i = 0; i < MetricsCollector.BUCKETS_SIZE; i++) {
                    buckets[i] += buffer.buckets[old][i];
                }
                count += buffer.count[old];
                sum += buffer.sum[old];
                min = Math.min(min, buffer.min[old]);
                max = Math.max(max, buffer.max[old]);

                Arrays.fill(buffer.buckets[old], 0);

                buffer.count[old] = 0;
                buffer.sum[old] = 0;
                buffer.min[old] = Long.MAX_VALUE;
                buffer.max[old] = 0;
            }

            long[] bucketsCopy = Arrays.copyOf(buckets, buckets.length);
            long p50 = percentile(0.5, count, bucketsCopy);
            long p99 = percentile(0.99, count, bucketsCopy);
            return new Snapshot(
                    bucketsCopy,
                    count,
                    sum,
                    min,
                    max,
                    p50,
                    p99
            );
        }
    }

    static final class ThreadBuffers {
        final long[][] buckets = new long[2][256];
        final long[] count = new long[2];
        final long[] sum = new long[2];
        final long[] min = {Long.MAX_VALUE, Long.MAX_VALUE};
        final long[] max = {0, 0};

        final AtomicInteger inside = new AtomicInteger(-1);
    }
}
