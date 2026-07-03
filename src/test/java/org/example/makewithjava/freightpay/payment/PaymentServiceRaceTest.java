package org.example.makewithjava.freightpay.payment;

import org.example.makewithjava.freightpay.pg.MockPg;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PaymentServiceRaceTest {

    @Test
    void 같은_멱등키로_순차_재시도하면_PG는_한_번만_과금되고_같은_결과를_replay_받는다() {
        MockPg pg = new MockPg();
        PaymentService service = new PaymentService(pg);

        PaymentResult first = service.pay("KEY-1", "ORDER-1", 300_000L);
        PaymentResult retry = service.pay("KEY-1", "ORDER-1", 300_000L);

        assertEquals(1, pg.chargedCount());
        assertEquals(PaymentStatus.APPROVED, first.status());
        assertEquals(first.pgTransactionId(), retry.pgTransactionId());
    }

    @Test
    void 더블클릭으로_같은_멱등키_요청_2개가_동시에_도착해도_PG는_한_번만_과금되어야_한다() throws Exception {
        MockPg pg = new MockPg();
        pg.setLatencyMillis(300);
        PaymentService service = new PaymentService(pg);

        int requestCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<PaymentResult>> futures = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            futures.add(executor.submit(() -> {
                startGate.await();
                return service.pay("KEY-1", "ORDER-1", 300_000L);
            }));
        }
        startGate.countDown();

        List<PaymentResult> results = new ArrayList<>();
        for (Future<PaymentResult> future : futures) {
            results.add(future.get(5, TimeUnit.SECONDS));
        }
        executor.shutdown();

        long approvedCount = results.stream()
                .filter(result -> result.status() == PaymentStatus.APPROVED)
                .count();

        assertEquals(1, pg.chargedCount(), "화주 카드에서 돈이 나간 횟수");
        assertTrue(approvedCount >= 1, "최소 한 요청은 승인 결과를 받아야 한다");
    }
}
