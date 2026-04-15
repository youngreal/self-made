package org.example.makewithjava.consistenthashing.hash;

public class VirtualNode<T extends Node> implements Node {

    private final T physicalNode;
    private final int replicaIndex;

    public VirtualNode(T physicalNode, int replicaIndex) {
        this.physicalNode = physicalNode;
        this.replicaIndex = replicaIndex;
    }

    @Override
    public String getKey() {
        return physicalNode.getKey() + "-" + replicaIndex;
    }

    public T getPhysicalNode() {
        return physicalNode;
    }

    public boolean isVirtualOf(T other) {
        return this.physicalNode.getKey().equals(other.getKey());
    }
}
