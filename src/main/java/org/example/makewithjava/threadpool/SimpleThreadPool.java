package org.example.makewithjava.threadpool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class SimpleThreadPool {
    private final List<SimpleWorkerThread> threads = new ArrayList<>();
    private final int poolSize;
    private final int queueSize;
    private final BlockingQueue<Runnable> queue;
    private boolean shutdown = false;


    public SimpleThreadPool(int poolSize, int queueSize) {
        this.poolSize = poolSize;
        this.queueSize = queueSize;
        this.queue = new ArrayBlockingQueue<>(10);
        for (int i = 0; i < poolSize; i++) {
            this.threads.add(new SimpleWorkerThread(this));
        }
        for (SimpleWorkerThread worker : threads) {
            new Thread(worker).start();
        }
    }

    public void submit(Runnable task) {
        this.queue.add(task);
    }

    public boolean shutdown() {
        return this.shutdown;
    }

    public BlockingQueue getQueue() {
        return queue;
    }
}
