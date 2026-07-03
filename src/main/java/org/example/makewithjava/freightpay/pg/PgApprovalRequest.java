package org.example.makewithjava.freightpay.pg;

public record PgApprovalRequest(String idempotencyKey, String orderId, long amountWon) {
}
