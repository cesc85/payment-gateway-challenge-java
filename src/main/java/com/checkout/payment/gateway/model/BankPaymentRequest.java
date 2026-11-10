package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Body sent to the acquiring bank (field names are the simulator's contract). */
public record BankPaymentRequest(
    @JsonProperty("card_number") String cardNumber,
    @JsonProperty("expiry_date") String expiryDate,
    String currency,
    int amount,
    String cvv) {

  public static BankPaymentRequest from(PostPaymentRequest request) {
    return new BankPaymentRequest(
        request.getCardNumber(),
        String.format("%02d/%d", request.getExpiryMonth(), request.getExpiryYear()),
        request.getCurrency(),
        request.getAmount(),
        request.getCvv());
  }

  // The generated record toString would include the card number and CVV.
  @Override
  public String toString() {
    return "BankPaymentRequest{" +
        "cardNumberLastFour='" + lastFour(cardNumber) + '\'' +
        ", expiryDate='" + expiryDate + '\'' +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        '}';
  }

  private static String lastFour(String cardNumber) {
    if (cardNumber == null || cardNumber.length() < 4) {
      return "";
    }
    return cardNumber.substring(cardNumber.length() - 4);
  }
}
