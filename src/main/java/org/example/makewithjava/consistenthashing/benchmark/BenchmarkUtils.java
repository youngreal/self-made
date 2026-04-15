package org.example.makewithjava.consistenthashing.benchmark;

import org.example.makewithjava.consistenthashing.hash.ServerNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class BenchmarkUtils {

    private BenchmarkUtils() {
    }

    static List<ServerNode> createServerNodes(int count) {
        List<ServerNode> nodes = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            nodes.add(new ServerNode("server-" + i));
        }
        return nodes;
    }

    static List<String> generateKeys(int count) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            keys.add(UUID.randomUUID().toString());
        }
        return keys;
    }

    static String bar(int value, int max, int width) {
        int filled = (int) ((double) value / max * width);
        return "#".repeat(Math.max(0, filled));
    }
}
