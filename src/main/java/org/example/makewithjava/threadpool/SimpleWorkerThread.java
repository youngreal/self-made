package org.example.makewithjava.threadpool;

public class SimpleWorkerThread implements Runnable {

    private final SimpleThreadPool threadPool;

    public SimpleWorkerThread(SimpleThreadPool threadPool) {
        this.threadPool = threadPool;
    }

    @Override
    public void run() {
        // 큐 셧다운 신호가 없다면
        while (!threadPool.shutdown()) {
            // 큐에서 하나 꺼낸다(비어있다면, 기다린다)
            try {
                Runnable task = this.threadPool.getQueue().take();
                task.run();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
