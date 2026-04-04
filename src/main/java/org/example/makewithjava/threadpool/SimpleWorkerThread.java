package org.example.makewithjava.threadpool;

import java.util.concurrent.TimeUnit;

public class SimpleWorkerThread implements Runnable {

    private final SimpleThreadPool threadPool;

    public SimpleWorkerThread(SimpleThreadPool threadPool) {
        this.threadPool = threadPool;
    }

    @Override
    public void run() {
        // 큐 셧다운 신호가 없다면
        while (!threadPool.isShutdownNow() && (!threadPool.isShutdown() || !threadPool.getQueue().isEmpty())) {


            // 큐에서 하나 꺼낸다(비어있다면, 기다린다)
            try {
                Runnable task = (Runnable) this.threadPool.getQueue().poll(5, TimeUnit.SECONDS);
                if(task != null){
                    task.run();
                }
            } catch (InterruptedException e) {
                if (!threadPool.isShutdown()) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
