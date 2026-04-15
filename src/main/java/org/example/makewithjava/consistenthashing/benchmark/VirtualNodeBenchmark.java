package org.example.makewithjava.consistenthashing.benchmark;

import org.example.makewithjava.consistenthashing.hash.Md5Hash;
import org.example.makewithjava.consistenthashing.hash.ServerNode;
import org.example.makewithjava.consistenthashing.router.ConsistentHashRouter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class VirtualNodeBenchmark {

    private static final int TOTAL_KEYS = 100_000;
    private static final int PHYSICAL_NODES = 5;
    private static final int[] VIRTUAL_NODE_COUNTS = {1, 10, 50, 150, 500, 1000};

    static void run() {
        System.out.println("===============================================================");
        System.out.println(" Virtual Node Effect Benchmark");
        System.out.println(" - keys: " + TOTAL_KEYS + ", physicalNodes: " + PHYSICAL_NODES);
        System.out.println(" - Measure: 가상 노드 개수가 분포 균등성에 미치는 영향");
        System.out.println("===============================================================");
        System.out.println();

        double ideal = (double) TOTAL_KEYS / PHYSICAL_NODES;
        System.out.printf("이상적 분포: 각 노드당 %.0f개%n%n", ideal);

        System.out.printf("%-12s | %-12s | %-12s | %-10s | %s%n",
                "virtualNode", "표준편차", "최대편차", "max-min", "시각화 (표준편차)");
        System.out.println("--------------------------------------------------------------------------------");

        double maxStdDev = 0;
        Map<Integer, Double> results = new HashMap<>();
        for (int vnCount : VIRTUAL_NODE_COUNTS) {
            double[] stats = measure(vnCount);
            results.put(vnCount, stats[0]);
            if (stats[0] > maxStdDev) maxStdDev = stats[0];
        }

        for (int vnCount : VIRTUAL_NODE_COUNTS) {
            double[] stats = measure(vnCount);
            String bar = BenchmarkUtils.bar((int) stats[0], (int) maxStdDev, 40);
            System.out.printf("%-12d | %12.2f | %12.2f | %10.0f | %s%n",
                    vnCount, stats[0], stats[1], stats[2], bar);
        }
        System.out.println();
        System.out.println("해석: 가상 노드가 많을수록 표준편차가 작아짐 → 분포 균등성 향상");
        System.out.println();
    }

    private static double[] measure(int virtualNodeCount) {
        ConsistentHashRouter<ServerNode> router = new ConsistentHashRouter<>(
                new Md5Hash(), virtualNodeCount, BenchmarkUtils.createServerNodes(PHYSICAL_NODES)
        );

        List<String> keys = BenchmarkUtils.generateKeys(TOTAL_KEYS);
        Map<String, Integer> countPerNode = new HashMap<>();
        for (String key : keys) {
            countPerNode.merge(router.routeNode(key).getKey(), 1, Integer::sum);
        }

        double mean = (double) TOTAL_KEYS / PHYSICAL_NODES;
        double variance = 0;
        int max = Integer.MIN_VALUE;
        int min = Integer.MAX_VALUE;
        for (int count : countPerNode.values()) {
            variance += Math.pow(count - mean, 2);
            if (count > max) max = count;
            if (count < min) min = count;
        }
        variance /= PHYSICAL_NODES;
        double stdDev = Math.sqrt(variance);
        double maxDeviation = Math.max(max - mean, mean - min);
        double spread = max - min;

        return new double[]{stdDev, maxDeviation, spread};
    }
}
