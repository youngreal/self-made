package org.example.makewithjava.freightpay.pg;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class MockPg {

    private final AtomicInteger chargedCount = new AtomicInteger();
    private volatile long latencyMillis = 0;

    public PgApprovalResponse approve(PgApprovalRequest request) {
        sleep(latencyMillis);
        // 멱등키를 검사해주지 않는 PG다 — 중복 방어는 전적으로 호출자의 몫
        chargedCount.incrementAndGet();
        return new PgApprovalResponse(UUID.randomUUID().toString(), true);
    }

    public void setLatencyMillis(long latencyMillis) {
        this.latencyMillis = latencyMillis;
    }

    public int chargedCount() {
        return chargedCount.get();
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
