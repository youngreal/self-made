package org.example.makewithjava.threadpool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;

public class SimpleThreadPool {
    private final List<Thread> threads = new ArrayList<>();
    private final int poolSize;
    private final int queueSize;
    private final BlockingQueue<Runnable> queue;
    private volatile boolean shutdown = false;
    private volatile boolean shutdownNow = false;


    public SimpleThreadPool(int poolSize, int queueSize) {
        this.poolSize = poolSize;
        this.queueSize = queueSize;
        this.queue = new ArrayBlockingQueue<>(this.queueSize);
        for (int i = 0; i < poolSize; i++) {
            this.threads.add(new Thread(new SimpleWorkerThread(this)));
        }

        for (Thread thread : threads) {
            thread.start();
        }
    }

    public void submit(Runnable task) throws InterruptedException, RejectedExecutionException {
        if (shutdown) {
            throw new RejectedExecutionException("Executor has been shutdown");
        }

        this.queue.put(task);
    }

    public void shutdown() {
        this.shutdown = true;
        for (Thread thread : threads) {
            thread.interrupt();
        }
    }


    public List<Runnable> shutdownNow() {
        this.shutdown = true;
        this.shutdownNow = true;
        List<Runnable> remainingTasks = new ArrayList<>();
        this.queue.drainTo(remainingTasks);
        for (Thread thread : threads) {
            thread.interrupt();
        }
        return remainingTasks;
    }

    public boolean isShutdownNow() {
        return this.shutdownNow;
    }


    public boolean isShutdown() {
        return this.shutdown;
    }

    public BlockingQueue getQueue() {
        return queue;
    }
}
