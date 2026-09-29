package lab;

public class EmptyLockCollector implements MetricsCollector {

    @Override
    synchronized public void record(long value) {
    }

    @Override
    synchronized public Snapshot snapshot() {
        return new Snapshot(
                new long[BUCKETS_SIZE],
                0,
                0,
                Long.MAX_VALUE,
                0,
                0,
                0
        );
    }
}
