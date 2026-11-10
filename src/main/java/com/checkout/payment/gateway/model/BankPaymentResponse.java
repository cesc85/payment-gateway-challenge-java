package com.checkout.payment.gateway.model;

/** Response from the acquiring bank; only the authorization outcome is needed. */
public record BankPaymentResponse(boolean authorized) {
}
