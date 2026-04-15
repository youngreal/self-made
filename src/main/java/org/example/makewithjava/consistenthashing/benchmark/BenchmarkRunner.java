package org.example.makewithjava.consistenthashing.benchmark;

public class BenchmarkRunner {

    public static void main(String[] args) {
        System.out.println();
        System.out.println("==================================================================");
        System.out.println(" Consistent Hashing Benchmark Suite");
        System.out.println("==================================================================");
        System.out.println();

        RebalanceBenchmark.run();
        VirtualNodeBenchmark.run();

        System.out.println("==================================================================");
        System.out.println(" Benchmark completed.");
        System.out.println("==================================================================");
    }
}
