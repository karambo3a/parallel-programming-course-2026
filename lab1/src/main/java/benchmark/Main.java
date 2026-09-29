package benchmark;

import lab.DoubleBufferingCollector;
import lab.MetricsCollector;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        long[] values = ZipfGenerator.generate();

        MetricsCollector collector =
                new DoubleBufferingCollector();

        double result =
                Benchmark.measurePoint(collector, values, 1);

        System.out.println(result / 1_000_000 + " M ops/sec");
    }
}
