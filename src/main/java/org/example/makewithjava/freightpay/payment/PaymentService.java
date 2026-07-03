package org.example.makewithjava.freightpay.payment;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.example.makewithjava.freightpay.pg.MockPg;
import org.example.makewithjava.freightpay.pg.PgApprovalRequest;
import org.example.makewithjava.freightpay.pg.PgApprovalResponse;

public class PaymentService {

    private final MockPg pg;
    private final Map<String, PaymentResult> completed = new ConcurrentHashMap<>();

    public PaymentService(MockPg pg) {
        this.pg = pg;
    }

    public PaymentResult pay(String idempotencyKey, String orderId, long amountWon) {
        PaymentResult cached = completed.get(idempotencyKey);
        synchronized (this) {
            if (cached != null) {
                return cached;
            }
        }

        // 상태를 저장
        PgApprovalResponse response = pg.approve(new PgApprovalRequest(idempotencyKey, orderId, amountWon));
        PaymentResult result = new PaymentResult(PaymentStatus.APPROVED, response.pgTransactionId());
        completed.putIfAbsent(idempotencyKey, result);
        return result;
    }
}
