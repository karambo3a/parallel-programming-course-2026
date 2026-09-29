package lab;

final public class MetricsSupport {
    static int bucketFor(long value) {
        return (int) Math.min(value / 4, MetricsCollector.BUCKETS_SIZE - 1);
    }

    static long percentile(double percentile, long count, long[] buckets) {
        if (count == 0) {
            return 0;
        }
        long threshold = (long) Math.ceil(count * percentile);
        long accumulated = 0;
        for (int i = 0; i < MetricsCollector.BUCKETS_SIZE; i++) {
            accumulated += buckets[i];
            if (accumulated >= threshold) {
                return i * 4L;
            }
        }
        return (MetricsCollector.BUCKETS_SIZE - 1) * 4L;
    }
}
