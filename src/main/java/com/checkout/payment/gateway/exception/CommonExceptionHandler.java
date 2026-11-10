package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.ErrorResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.Field;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> handleException(EventProcessingException ex) {
    LOG.error("Exception happened", ex);
    return new ResponseEntity<>(new ErrorResponse("Page not found"),
        HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    // JSON names only: never echo rejected values back, they may be card data.
    String message = ex.getBindingResult().getFieldErrors().stream()
        .map(CommonExceptionHandler::merchantMessage)
        .sorted()
        .collect(Collectors.joining("; "));
    LOG.info("Payment rejected: {}", message);
    return new ResponseEntity<>(new ErrorResponse(PaymentStatus.REJECTED, message),
        HttpStatus.BAD_REQUEST);
  }

  private static String merchantMessage(FieldError error) {
    // Not a request field. The message already names expiry_month and expiry_year.
    if ("expiryInFuture".equals(error.getField())) {
      return error.getDefaultMessage();
    }
    return jsonFieldName(error.getField()) + " " + error.getDefaultMessage();
  }

  private static String jsonFieldName(String field) {
    try {
      Field declared = PostPaymentRequest.class.getDeclaredField(field);
      JsonProperty jsonProperty = declared.getAnnotation(JsonProperty.class);
      if (jsonProperty != null && !jsonProperty.value().isEmpty()) {
        return jsonProperty.value();
      }
    } catch (NoSuchFieldException ignored) {
      // A bean property with no matching field keeps its own name.
    }
    return field;
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
    LOG.info("Payment rejected: unreadable request body");
    return new ResponseEntity<>(
        new ErrorResponse(PaymentStatus.REJECTED, "Request body is missing or malformed"),
        HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(BankUnavailableException.class)
  public ResponseEntity<ErrorResponse> handleBankUnavailable(BankUnavailableException ex) {
    LOG.error("Acquiring bank call failed", ex);
    return new ResponseEntity<>(new ErrorResponse("Acquiring bank unavailable"),
        HttpStatus.SERVICE_UNAVAILABLE);
  }
}
