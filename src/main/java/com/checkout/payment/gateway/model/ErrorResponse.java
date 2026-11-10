package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
  private final PaymentStatus status;
  private final String message;

  public ErrorResponse(String message) {
    this(null, message);
  }

  public ErrorResponse(PaymentStatus status, String message) {
    this.status = status;
    this.message = message;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public String getMessage() {
    return message;
  }

  @Override
  public String toString() {
    return "ErrorResponse{" +
        "status=" + status +
        ", message='" + message + '\'' +
        '}';
  }
}
