package lab;

public interface MetricsCollector {
    int BUCKETS_SIZE = 256;
    int LOCKS_SIZE = 16;

    void record(long value);

    Snapshot snapshot();
}