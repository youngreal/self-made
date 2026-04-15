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
class SimpleHashRouterTest {

    // hashCode를 사용했다면 JVM재시작할때마다 달라질수있음 + 분산시스템에서 JVM마다 다를수있으며 서로 다른 서버가 다른 해시 계산. MD5같은 결정론적 해시를 쓰는 이유
    @Test
    void MD5_같은_키는_항상_같은_노드로_라우팅된다() {
        SimpleHashRouter<ServerNode> router = createRouter(3);

        ServerNode first = router.routeNode("user_123");
        ServerNode second = router.routeNode("user_123");
        ServerNode third = router.routeNode("user_123");

        assertEquals(first.getKey(), second.getKey());
        assertEquals(second.getKey(), third.getKey());
    }

    @Test
    void 키_10000개를_3노드에_뿌리면_대략_균등하게_분포된다_각_노드_25에서_42퍼센트() {
        SimpleHashRouter<ServerNode> router = createRouter(3);
        int totalKeys = 10_000;

        Map<String, Integer> countPerNode = new HashMap<>();
        for (int i = 0; i < totalKeys; i++) {
            String key = UUID.randomUUID().toString();
            ServerNode node = router.routeNode(key);
            countPerNode.merge(node.getKey(), 1, Integer::sum);
        }

        for (Integer count : countPerNode.values()) {
            double ratio = (double) count / totalKeys;
            assertTrue(ratio >= 0.25 && ratio <= 0.42,
                    "노드 분포 비율이 25~42% 범위를 벗어남: " + ratio);
        }
    }

    // 모듈러 해싱의 rehash 문제 -> 대규모 캐시미스 발생
    @Test
    void 페이지_77_노드_3개에서_4개로_바꾸면_키의_70퍼센트_이상이_다른_노드로_이동한다() {
        SimpleHashRouter<ServerNode> router = createRouter(3);
        int totalKeys = 10_000;

        List<String> keys = new ArrayList<>();
        Map<String, String> originalMapping = new HashMap<>();
        for (int i = 0; i < totalKeys; i++) {
            String key = UUID.randomUUID().toString();
            keys.add(key);
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
        assertTrue(movedRatio >= 0.70,
                "노드 추가 시 70% 이상 이동해야 함 (실제: " + movedRatio + ")");
    }

    private SimpleHashRouter<ServerNode> createRouter(int nodeCount) {
        List<ServerNode> nodes = new ArrayList<>();
        for (int i = 1; i <= nodeCount; i++) {
            nodes.add(new ServerNode("server-" + i));
        }
        return new SimpleHashRouter<>(new Md5Hash(), nodes);
    }
}
