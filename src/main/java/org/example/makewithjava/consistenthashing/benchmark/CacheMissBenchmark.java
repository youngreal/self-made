package org.example.makewithjava.consistenthashing.benchmark;

import org.example.makewithjava.consistenthashing.hash.Md5Hash;
import org.example.makewithjava.consistenthashing.hash.ServerNode;
import org.example.makewithjava.consistenthashing.router.ConsistentHashRouter;
import org.example.makewithjava.consistenthashing.router.HashRouter;
import org.example.makewithjava.consistenthashing.router.SimpleHashRouter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

final class CacheMissBenchmark {

    private static final int UNIQUE_KEYS = 1000;
    private static final int TOTAL_REQUESTS = 100_000;
    private static final double ZIPF_SKEW = 1.0;
    private static final int INITIAL_NODES = 3;
    private static final int VIRTUAL_NODE_COUNT = 150;
    private static final long SEED = 42L;

    static void run() {
        System.out.println("===============================================================");
        System.out.println(" Cache Miss Benchmark (Zipfian traffic simulation)");
        System.out.println(" - unique keys: " + UNIQUE_KEYS + ", total requests: " + TOTAL_REQUESTS);
        System.out.println(" - distribution: Zipf(s=" + ZIPF_SKEW + ")");
        System.out.println(" - scenarios: ADD / REMOVE-first / REMOVE-busiest");
        System.out.println("===============================================================");
        System.out.println();

        List<String> uniqueKeys = BenchmarkUtils.generateKeys(UNIQUE_KEYS);
        List<String> requests = generateZipfianRequests(uniqueKeys, TOTAL_REQUESTS, ZIPF_SKEW, SEED);
        System.out.printf("요청: 총 %,d 건 / distinct %d 개 (top-10 키가 전체 %.1f%% 차지)%n%n",
                requests.size(), requests.stream().distinct().count(), topNShare(requests, 10));

        Result simpleAdd = simulateAdd(newSimple(), uniqueKeys, requests);
        Result consistentAdd = simulateAdd(newConsistent(), uniqueKeys, requests);
        Result simpleRemoveFirst = simulateRemove(newSimple(), uniqueKeys, requests, RemovalStrategy.FIRST);
        Result consistentRemoveFirst = simulateRemove(newConsistent(), uniqueKeys, requests, RemovalStrategy.FIRST);
        Result simpleRemoveBusiest = simulateRemove(newSimple(), uniqueKeys, requests, RemovalStrategy.BUSIEST);
        Result consistentRemoveBusiest = simulateRemove(newConsistent(), uniqueKeys, requests, RemovalStrategy.BUSIEST);

        printHeader();
        printRow("ADD (+1)",       "Simple",     simpleAdd);
        printRow("ADD (+1)",       "Consistent", consistentAdd);
        printRow("REMOVE first",   "Simple",     simpleRemoveFirst);
        printRow("REMOVE first",   "Consistent", consistentRemoveFirst);
        printRow("REMOVE busiest", "Simple",     simpleRemoveBusiest);
        printRow("REMOVE busiest", "Consistent", consistentRemoveBusiest);
        System.out.println();

        System.out.println("해석:");
        System.out.println(" - ADD: 기존 키의 일부만 새 노드로 이동. Consistent는 k/n ≈ 25% 근처.");
        System.out.println(" - REMOVE-first: 첫 번째 노드 제거. Simple은 거의 전부 재배치.");
        System.out.println(" - REMOVE-busiest: 트래픽 최다 노드 제거. Consistent도 미스 많아짐 (인기키 집중).");
        System.out.println(" - 부하불균형(σ): 남은 노드의 트래픽 편차. 작을수록 고른 분산.");
        System.out.println();
    }

    private enum RemovalStrategy { FIRST, BUSIEST }

    private record Result(
            double rebalanceRate,
            double missRate,
            int missCount,
            double loadStdDev,
            String removedNodeName
    ) {}

    private static Result simulateAdd(
            RouterBundle bundle,
            List<String> uniqueKeys,
            List<String> requests
    ) {
        Map<String, Set<String>> warmCache = new HashMap<>();
        Map<String, String> originalMapping = new HashMap<>();
        warmUp(bundle.router(), uniqueKeys, warmCache, originalMapping);

        bundle.addNodeFn().accept(new ServerNode("server-new"));

        return measure(bundle.router(), uniqueKeys, requests, warmCache, originalMapping, "-");
    }

    private static Result simulateRemove(
            RouterBundle bundle,
            List<String> uniqueKeys,
            List<String> requests,
            RemovalStrategy strategy
    ) {
        Map<String, Set<String>> warmCache = new HashMap<>();
        Map<String, String> originalMapping = new HashMap<>();
        warmUp(bundle.router(), uniqueKeys, warmCache, originalMapping);

        ServerNode toRemove = switch (strategy) {
            case FIRST -> bundle.nodes().get(0);
            case BUSIEST -> findBusiestNode(bundle.router(), bundle.nodes(), requests);
        };
        bundle.removeNodeFn().accept(toRemove);

        return measure(bundle.router(), uniqueKeys, requests, warmCache, originalMapping, toRemove.getKey());
    }

    private static void warmUp(
            HashRouter<ServerNode> router,
            List<String> uniqueKeys,
            Map<String, Set<String>> warmCache,
            Map<String, String> originalMapping
    ) {
        for (String key : uniqueKeys) {
            String nodeKey = router.routeNode(key).getKey();
            originalMapping.put(key, nodeKey);
            warmCache.computeIfAbsent(nodeKey, k -> new HashSet<>()).add(key);
        }
    }

    private static Result measure(
            HashRouter<ServerNode> router,
            List<String> uniqueKeys,
            List<String> requests,
            Map<String, Set<String>> warmCache,
            Map<String, String> originalMapping,
            String removedNodeName
    ) {
        int moved = 0;
        for (String key : uniqueKeys) {
            if (!router.routeNode(key).getKey().equals(originalMapping.get(key))) {
                moved++;
            }
        }
        double rebalanceRate = (double) moved / uniqueKeys.size() * 100;

        int hits = 0;
        int misses = 0;
        Map<String, Integer> newTraffic = new HashMap<>();
        for (String key : requests) {
            String nodeKey = router.routeNode(key).getKey();
            newTraffic.merge(nodeKey, 1, Integer::sum);
            if (warmCache.getOrDefault(nodeKey, Collections.emptySet()).contains(key)) {
                hits++;
            } else {
                misses++;
            }
        }
        double missRate = (double) misses / requests.size() * 100;
        double loadStdDev = stdDev(newTraffic.values());

        return new Result(rebalanceRate, missRate, misses, loadStdDev, removedNodeName);
    }

    private static ServerNode findBusiestNode(
            HashRouter<ServerNode> router,
            List<ServerNode> nodes,
            List<String> requests
    ) {
        Map<String, Integer> traffic = new HashMap<>();
        for (String key : requests) {
            traffic.merge(router.routeNode(key).getKey(), 1, Integer::sum);
        }
        String busiestKey = traffic.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .orElseThrow()
                .getKey();
        return nodes.stream()
                .filter(n -> n.getKey().equals(busiestKey))
                .findFirst()
                .orElseThrow();
    }

    private record RouterBundle(
            HashRouter<ServerNode> router,
            Consumer<ServerNode> addNodeFn,
            Consumer<ServerNode> removeNodeFn,
            List<ServerNode> nodes
    ) {}

    private static RouterBundle newSimple() {
        List<ServerNode> nodes = BenchmarkUtils.createServerNodes(INITIAL_NODES);
        SimpleHashRouter<ServerNode> router = new SimpleHashRouter<>(new Md5Hash(), new ArrayList<>(nodes));
        return new RouterBundle(router, router::addNode, router::removeNode, nodes);
    }

    private static RouterBundle newConsistent() {
        List<ServerNode> nodes = BenchmarkUtils.createServerNodes(INITIAL_NODES);
        ConsistentHashRouter<ServerNode> router = new ConsistentHashRouter<>(new Md5Hash(), VIRTUAL_NODE_COUNT, nodes);
        return new RouterBundle(router, router::addNode, router::removeNode, nodes);
    }

    private static void printHeader() {
        System.out.printf("%-16s | %-12s | %-8s | %-8s | %-10s | %-10s | %s%n",
                "scenario", "router", "rebalance", "missRate", "missCount", "loadStdDev", "removed");
        System.out.println("----------------------------------------------------------------------------------------------");
    }

    private static void printRow(String scenario, String routerName, Result r) {
        System.out.printf("%-16s | %-12s | %7.2f%% | %7.2f%% | %,10d | %10.2f | %s%n",
                scenario, routerName, r.rebalanceRate(), r.missRate(), r.missCount(), r.loadStdDev(), r.removedNodeName());
    }

    private static double stdDev(Collection<Integer> values) {
        if (values.isEmpty()) return 0;
        double mean = values.stream().mapToInt(Integer::intValue).average().orElse(0);
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average().orElse(0);
        return Math.sqrt(variance);
    }

    private static List<String> generateZipfianRequests(List<String> uniqueKeys, int totalRequests, double skew, long seed) {
        int n = uniqueKeys.size();
        double harmonic = 0;
        for (int i = 1; i <= n; i++) harmonic += 1.0 / Math.pow(i, skew);

        double[] cdf = new double[n];
        double cumul = 0;
        for (int i = 1; i <= n; i++) {
            cumul += (1.0 / Math.pow(i, skew)) / harmonic;
            cdf[i - 1] = cumul;
        }

        Random random = new Random(seed);
        List<String> requests = new ArrayList<>(totalRequests);
        for (int i = 0; i < totalRequests; i++) {
            double r = random.nextDouble();
            int idx = Arrays.binarySearch(cdf, r);
            if (idx < 0) idx = -idx - 1;
            if (idx >= n) idx = n - 1;
            requests.add(uniqueKeys.get(idx));
        }
        return requests;
    }

    private static double topNShare(List<String> requests, int topN) {
        Map<String, Integer> counts = new HashMap<>();
        for (String key : requests) counts.merge(key, 1, Integer::sum);
        return counts.values().stream()
                .sorted(Collections.reverseOrder())
                .limit(topN)
                .mapToInt(Integer::intValue).sum() * 100.0 / requests.size();
    }
}
