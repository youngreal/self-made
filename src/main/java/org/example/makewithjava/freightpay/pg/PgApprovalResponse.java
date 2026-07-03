package org.example.makewithjava.freightpay.pg;

public record PgApprovalResponse(String pgTransactionId, boolean approved) {
}
