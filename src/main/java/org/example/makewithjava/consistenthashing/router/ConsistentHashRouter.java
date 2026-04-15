package org.example.makewithjava.consistenthashing.router;

import org.example.makewithjava.consistenthashing.hash.HashAlgorithm;
import org.example.makewithjava.consistenthashing.hash.Node;
import org.example.makewithjava.consistenthashing.hash.VirtualNode;

import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class ConsistentHashRouter<T extends Node> implements HashRouter<T> {

    private final HashAlgorithm hashAlgorithm;
    private final int virtualNodeCount;
    private final SortedMap<Long, VirtualNode<T>> ring = new TreeMap<>();

    public ConsistentHashRouter(HashAlgorithm hashAlgorithm, int virtualNodeCount, List<T> physicalNodes) {
        this.hashAlgorithm = hashAlgorithm;
        this.virtualNodeCount = virtualNodeCount;
        for (T physicalNode : physicalNodes) {
            addNode(physicalNode);
        }
    }

    @Override
    public T routeNode(String businessKey) {
        if (ring.isEmpty()) {
            return null;
        }

        long hash = hashAlgorithm.hash(businessKey);
        SortedMap<Long, VirtualNode<T>> tail = ring.tailMap(hash);

        long targetHash = tail.isEmpty() ? ring.firstKey() : tail.firstKey();
        return ring.get(targetHash).getPhysicalNode();
    }

    public void addNode(T physicalNode) {
        for (int i = 0; i < virtualNodeCount; i++) {
            VirtualNode<T> virtualNode = new VirtualNode<>(physicalNode, i);
            long hash = hashAlgorithm.hash(virtualNode.getKey());
            ring.put(hash, virtualNode);
        }
    }

    public void removeNode(T physicalNode) {
        Iterator<VirtualNode<T>> iterator = ring.values().iterator();
        while (iterator.hasNext()) {
            VirtualNode<T> virtualNode = iterator.next();
            if (virtualNode.isVirtualOf(physicalNode)) {
                iterator.remove();
            }
        }
    }
}
