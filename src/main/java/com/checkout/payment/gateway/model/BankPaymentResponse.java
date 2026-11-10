package com.checkout.payment.gateway.model;

/**
 * Response from the acquiring bank. {@code authorized} is a wrapper so a missing field stays
 * null instead of becoming {@code false}.
 */
public record BankPaymentResponse(Boolean authorized) {
}
