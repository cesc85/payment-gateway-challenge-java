package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.io.Serializable;
import java.time.DateTimeException;
import java.time.YearMonth;

public class PostPaymentRequest implements Serializable {

  @NotNull
  @Pattern(regexp = "\\d{14,19}", message = "must be 14-19 numeric characters")
  @JsonProperty("card_number")
  private String cardNumber;
  @NotNull
  @Min(1)
  @Max(12)
  @JsonProperty("expiry_month")
  private Integer expiryMonth;
  @NotNull
  @JsonProperty("expiry_year")
  private Integer expiryYear;
  @NotNull
  @Pattern(regexp = "USD|GBP|EUR", message = "must be one of USD, GBP, EUR")
  private String currency;
  @NotNull
  @Positive
  private Integer amount;
  @NotNull
  @Pattern(regexp = "\\d{3,4}", message = "must be 3-4 numeric characters")
  private String cvv;

  public String getCardNumber() {
    return cardNumber;
  }

  public void setCardNumber(String cardNumber) {
    this.cardNumber = cardNumber;
  }

  public Integer getExpiryMonth() {
    return expiryMonth;
  }

  public void setExpiryMonth(Integer expiryMonth) {
    this.expiryMonth = expiryMonth;
  }

  public Integer getExpiryYear() {
    return expiryYear;
  }

  public void setExpiryYear(Integer expiryYear) {
    this.expiryYear = expiryYear;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Integer getAmount() {
    return amount;
  }

  public void setAmount(Integer amount) {
    this.amount = amount;
  }

  public String getCvv() {
    return cvv;
  }

  public void setCvv(String cvv) {
    this.cvv = cvv;
  }

  // Skipped when month/year are missing or invalid: those fields report their own errors.
  @JsonIgnore
  @AssertTrue(message = "expiry month and year must be in the future")
  public boolean isExpiryInFuture() {
    if (expiryMonth == null || expiryYear == null || expiryMonth < 1 || expiryMonth > 12) {
      return true;
    }
    try {
      return YearMonth.of(expiryYear, expiryMonth).isAfter(YearMonth.now());
    } catch (DateTimeException e) {
      return false;
    }
  }

  // Deliberately excludes card number and CVV so they can never end up in logs.
  @Override
  public String toString() {
    return "PostPaymentRequest{" +
        "expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        '}';
  }
}
