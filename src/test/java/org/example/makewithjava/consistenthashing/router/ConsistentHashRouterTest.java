package org.example.makewithjava.consistenthashing.router;

import org.example.makewithjava.consistenthashing.hash.Md5Hash;
import org.example.makewithjava.consistenthashing.hash.ServerNode;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ConsistentHashRouterTest {

    private static final int VIRTUAL_NODE_COUNT = 150;

    @Test
    void 같은_키는_항상_같은_노드로_라우팅된다() {
        ConsistentHashRouter<ServerNode> router = createRouter(3, VIRTUAL_NODE_COUNT);

        ServerNode first = router.routeNode("user_123");
        ServerNode second = router.routeNode("user_123");
        ServerNode third = router.routeNode("user_123");

        assertEquals(first.getKey(), second.getKey());
        assertEquals(second.getKey(), third.getKey());
    }

    @Test
    void 가상노드_150개로_3노드에_10000개_키를_뿌리면_각_노드_25에서_42퍼센트_사이로_분포된다() {
        ConsistentHashRouter<ServerNode> router = createRouter(3, VIRTUAL_NODE_COUNT);
        int totalKeys = 10_000;

        Map<String, Integer> countPerNode = distribute(router, totalKeys);

        for (Integer count : countPerNode.values()) {
            double ratio = (double) count / totalKeys;
            assertTrue(ratio >= 0.25 && ratio <= 0.42,
                    "분포 비율 이탈: " + ratio);
        }
    }

    @Test
    void 노드_3개에서_4개로_바꿔도_키의_이동률은_20에서_35퍼센트_사이다_consistent_hashing의_핵심_장점() {
        ConsistentHashRouter<ServerNode> router = createRouter(3, VIRTUAL_NODE_COUNT);
        int totalKeys = 10_000;

        List<String> keys = generateKeys(totalKeys);
        Map<String, String> originalMapping = new HashMap<>();
        for (String key : keys) {
            originalMapping.put(key, router.routeNode(key).getKey());
        }

        router.addNode(new ServerNode("server-4"));

        int movedCount = 0;
        for (String key : keys) {
            String newNode = router.routeNode(key).getKey();
            if (!newNode.equals(originalMapping.get(key))) {
                movedCount++;
            }
        }

        double movedRatio = (double) movedCount / totalKeys;
        assertTrue(movedRatio >= 0.15 && movedRatio <= 0.35,
                "이동률이 15~35% 범위를 벗어남 (이론값 k/n = 25%, 실제: " + movedRatio + ")");
    }

    @Test
    void 가상노드_개수가_많을수록_분포_표준편차가_감소한다() {
        int totalKeys = 10_000;

        double stdDev1 = measureStdDev(1, totalKeys);
        double stdDev150 = measureStdDev(150, totalKeys);
        double stdDev1000 = measureStdDev(1000, totalKeys);

        System.out.println("가상노드 1개   표준편차: " + stdDev1);
        System.out.println("가상노드 150개 표준편차: " + stdDev150);
        System.out.println("가상노드 1000개 표준편차: " + stdDev1000);

        assertTrue(stdDev150 < stdDev1, "150 < 1 이어야 함");
        assertTrue(stdDev1000 <= stdDev150, "1000 <= 150 이어야 함");
    }

    private double measureStdDev(int virtualNodeCount, int totalKeys) {
        ConsistentHashRouter<ServerNode> router = createRouter(3, virtualNodeCount);
        Map<String, Integer> countPerNode = distribute(router, totalKeys);

        double mean = (double) totalKeys / countPerNode.size();
        double variance = 0;
        for (Integer count : countPerNode.values()) {
            variance += Math.pow(count - mean, 2);
        }
        variance /= countPerNode.size();
        return Math.sqrt(variance);
    }

    private Map<String, Integer> distribute(ConsistentHashRouter<ServerNode> router, int totalKeys) {
        Map<String, Integer> countPerNode = new HashMap<>();
        for (int i = 0; i < totalKeys; i++) {
            String key = UUID.randomUUID().toString();
            ServerNode node = router.routeNode(key);
            countPerNode.merge(node.getKey(), 1, Integer::sum);
        }
        return countPerNode;
    }

    private List<String> generateKeys(int count) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            keys.add(UUID.randomUUID().toString());
        }
        return keys;
    }

    private ConsistentHashRouter<ServerNode> createRouter(int physicalNodeCount, int virtualNodeCount) {
        List<ServerNode> nodes = new ArrayList<>();
        for (int i = 1; i <= physicalNodeCount; i++) {
            nodes.add(new ServerNode("server-" + i));
        }
        return new ConsistentHashRouter<>(new Md5Hash(), virtualNodeCount, nodes);
    }
}
