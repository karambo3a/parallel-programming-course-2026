package lab;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

import static lab.MetricsSupport.bucketFor;
import static lab.MetricsSupport.percentile;

public class ThreadLocalCollector implements MetricsCollector {
    private final List<ThreadState> allStates = new ArrayList<>();
    private final Object listLock = new Object();
    private final ThreadLocal<ThreadState> myState = ThreadLocal.withInitial(() -> {
        ThreadState s = new ThreadState();
        synchronized (listLock) {
            allStates.add(s);
        }
        return s;
    });

    @Override
    public void record(long value) {
        ThreadState s = myState.get();
        int b = bucketFor(value);
        ;

        s.buckets.setRelease(b, s.buckets.getPlain(b) + 1);
        s.count.setRelease(s.count.getPlain() + 1);
        s.sum.setRelease(s.sum.getPlain() + value);

        if (value < s.min.getPlain()) {
            s.min.setRelease(value);
        }
        if (value > s.max.getPlain()) {
            s.max.setRelease(value);
        }
    }

    @Override
    public Snapshot snapshot() {
        List<ThreadState> copyOfStates;
        synchronized (listLock) {
            copyOfStates = new ArrayList<>(allStates);
        }

        long[] out = new long[MetricsCollector.BUCKETS_SIZE];
        long count = 0;
        long sum = 0;
        long min = Long.MAX_VALUE;
        long max = 0;

        for (ThreadState s : copyOfStates) {
            for (int i = 0; i < MetricsCollector.BUCKETS_SIZE; i++) {
                out[i] += s.buckets.get(i);
            }
            count += s.count.get();

            sum += s.sum.get();
            min = Math.min(min, s.min.get());
            max = Math.max(max, s.max.get());
        }

        long p50 = percentile(0.5, count, out);
        long p99 = percentile(0.99, count, out);
        return new Snapshot(out, count, sum, min, max, p50, p99);
    }

    static final class ThreadState {
        final AtomicLongArray buckets = new AtomicLongArray(MetricsCollector.BUCKETS_SIZE);
        final AtomicLong count = new AtomicLong();
        final AtomicLong sum = new AtomicLong();
        final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong max = new AtomicLong(0);
    }
}
