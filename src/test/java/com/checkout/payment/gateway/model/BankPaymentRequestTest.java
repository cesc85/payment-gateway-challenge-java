package com.checkout.payment.gateway.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BankPaymentRequestTest {

  @Test
  void toStringKeepsLastFourAndOmitsCardNumberAndCvv() {
    BankPaymentRequest request =
        new BankPaymentRequest("2222405343248877", "04/2030", "GBP", 1050, "9876");

    String text = request.toString();

    assertFalse(text.contains("2222405343248877"), text);
    assertFalse(text.contains("9876"), text);
    assertTrue(text.contains("8877"), text);
    assertTrue(text.contains("04/2030"), text);
  }
}
