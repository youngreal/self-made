package org.example.makewithjava.consistenthashing.benchmark;

import org.example.makewithjava.consistenthashing.hash.Md5Hash;
import org.example.makewithjava.consistenthashing.hash.ServerNode;
import org.example.makewithjava.consistenthashing.router.ConsistentHashRouter;
import org.example.makewithjava.consistenthashing.router.HashRouter;
import org.example.makewithjava.consistenthashing.router.SimpleHashRouter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class RebalanceBenchmark {

    private static final int TOTAL_KEYS = 100_000;
    private static final int INITIAL_NODES = 3;
    private static final int ADDED_NODES = 3;
    private static final int VIRTUAL_NODE_COUNT = 150;

    static void run() {
        System.out.println("===============================================================");
        System.out.println(" Rebalance Benchmark");
        System.out.println(" - keys: " + TOTAL_KEYS + ", initial nodes: " + INITIAL_NODES + ", virtualNodes: " + VIRTUAL_NODE_COUNT);
        System.out.println(" - Measure: 노드 추가 시 다른 서버로 이동한 키 비율");
        System.out.println("===============================================================");
        System.out.println();

        System.out.printf("%-15s | %-12s | %-14s | %s%n", "node change", "Simple(%)", "Consistent(%)", "theory k/n(%)");
        System.out.println("---------------------------------------------------------------");

        List<String> keys = BenchmarkUtils.generateKeys(TOTAL_KEYS);

        for (int added = 1; added <= ADDED_NODES; added++) {
            int totalNodesAfter = INITIAL_NODES + added;
            double theoretical = 100.0 / totalNodesAfter;

            double simpleRatio = measureMovementRate(
                    new SimpleHashRouter<>(new Md5Hash(), BenchmarkUtils.createServerNodes(INITIAL_NODES)),
                    keys, added, "simple"
            );
            double consistentRatio = measureMovementRate(
                    new ConsistentHashRouter<>(new Md5Hash(), VIRTUAL_NODE_COUNT, BenchmarkUtils.createServerNodes(INITIAL_NODES)),
                    keys, added, "consistent"
            );

            System.out.printf("%-15s | %10.2f%%  | %10.2f%%  | %10.2f%%%n",
                    INITIAL_NODES + " → " + totalNodesAfter, simpleRatio, consistentRatio, theoretical);
        }
        System.out.println();
    }

    private static <R extends HashRouter<ServerNode>> double measureMovementRate(
            R router, List<String> keys, int nodesToAdd, String mode
    ) {
        Map<String, String> originalMapping = new HashMap<>();
        for (String key : keys) {
            originalMapping.put(key, router.routeNode(key).getKey());
        }

        for (int i = 0; i < nodesToAdd; i++) {
            ServerNode newNode = new ServerNode("server-new-" + i);
            if (mode.equals("simple")) {
                ((SimpleHashRouter<ServerNode>) router).addNode(newNode);
            } else {
                ((ConsistentHashRouter<ServerNode>) router).addNode(newNode);
            }
        }

        int moved = 0;
        for (String key : keys) {
            if (!router.routeNode(key).getKey().equals(originalMapping.get(key))) {
                moved++;
            }
        }
        return (double) moved / keys.size() * 100;
    }
}
