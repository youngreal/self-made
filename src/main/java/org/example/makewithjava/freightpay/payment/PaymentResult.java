package org.example.makewithjava.freightpay.payment;

public record PaymentResult(PaymentStatus status, String pgTransactionId) {
}
