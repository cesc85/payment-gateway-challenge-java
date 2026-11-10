package com.checkout.payment.gateway.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.YearMonth;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** One test group per row of the "Processing a payment" validation table in the README. */
class PostPaymentRequestValidationTest {

  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  private static PostPaymentRequest valid() {
    YearMonth next = YearMonth.now().plusMonths(1);
    PostPaymentRequest r = new PostPaymentRequest();
    r.setCardNumber("2222405343248877");
    r.setExpiryMonth(next.getMonthValue());
    r.setExpiryYear(next.getYear());
    r.setCurrency("GBP");
    r.setAmount(100);
    r.setCvv("123");
    return r;
  }

  private static Set<String> invalidFields(PostPaymentRequest r) {
    return VALIDATOR.validate(r).stream()
        .map(ConstraintViolation::getPropertyPath)
        .map(Object::toString)
        .collect(Collectors.toSet());
  }

  private static void assertValid(Consumer<PostPaymentRequest> change) {
    PostPaymentRequest r = valid();
    change.accept(r);
    assertEquals(Set.of(), invalidFields(r));
  }

  private static void assertInvalid(String field, Consumer<PostPaymentRequest> change) {
    PostPaymentRequest r = valid();
    change.accept(r);
    assertEquals(Set.of(field), invalidFields(r));
  }

  @Test
  void validRequestHasNoViolations() {
    assertEquals(Set.of(), invalidFields(valid()));
  }

  @Test
  void emptyRequestReportsEveryRequiredField() {
    Set<String> fields = invalidFields(new PostPaymentRequest());
    assertEquals(
        Set.of("cardNumber", "expiryMonth", "expiryYear", "currency", "amount", "cvv"), fields);
  }

  // ---- Card number: required, 14-19 chars, numeric only ----

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "1234567890123", "12345678901234567890", "2222405343A48877",
      "2222 4053 4324 8877", "2222-4053-4324-8877", "+2222405343248877", "２２２２２２２２２２２２２２"})
  void invalidCardNumberIsRejected(String cardNumber) {
    assertInvalid("cardNumber", r -> r.setCardNumber(cardNumber));
  }

  @ParameterizedTest
  @ValueSource(strings = {"12345678901234", "1234567890123456", "1234567890123456789"})
  void cardNumberOf14To19DigitsIsAccepted(String cardNumber) {
    assertValid(r -> r.setCardNumber(cardNumber));
  }

  // ---- Expiry month: required, 1-12 ----

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = {0, 13, -1, 100})
  void invalidExpiryMonthIsRejected(Integer month) {
    PostPaymentRequest r = valid();
    r.setExpiryMonth(month);
    // A bad/missing month must be reported on the month itself, not as an expiry-date error.
    assertEquals(Set.of("expiryMonth"), invalidFields(r));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 6, 12})
  void expiryMonthFrom1To12IsAccepted(int month) {
    // Use a far-future year so the month alone is what is being tested.
    assertValid(r -> {
      r.setExpiryMonth(month);
      r.setExpiryYear(YearMonth.now().getYear() + 5);
    });
  }

  // ---- Expiry year: required; month + year combination must be in the future ----

  @Test
  void missingExpiryYearIsRejected() {
    assertInvalid("expiryYear", r -> r.setExpiryYear(null));
  }

  @Test
  void expiryNextMonthIsAccepted() {
    YearMonth next = YearMonth.now().plusMonths(1);
    assertValid(r -> {
      r.setExpiryMonth(next.getMonthValue());
      r.setExpiryYear(next.getYear());
    });
  }

  @Test
  void expiryInCurrentMonthIsRejected() {
    YearMonth now = YearMonth.now();
    assertInvalid("expiryInFuture", r -> {
      r.setExpiryMonth(now.getMonthValue());
      r.setExpiryYear(now.getYear());
    });
  }

  @Test
  void expiryLastMonthIsRejected() {
    YearMonth last = YearMonth.now().minusMonths(1);
    assertInvalid("expiryInFuture", r -> {
      r.setExpiryMonth(last.getMonthValue());
      r.setExpiryYear(last.getYear());
    });
  }

  @Test
  void expiryInPastYearIsRejected() {
    assertInvalid("expiryInFuture", r -> r.setExpiryYear(YearMonth.now().getYear() - 1));
  }

  @Test
  void laterMonthInPastYearIsStillRejected() {
    // Month alone looks "future" (December) but the combination is in the past.
    assertInvalid("expiryInFuture", r -> {
      r.setExpiryMonth(12);
      r.setExpiryYear(YearMonth.now().getYear() - 1);
    });
  }

  @Test
  void earlierMonthInFutureYearIsAccepted() {
    // Month alone looks "past" (January) but the combination is in the future.
    assertValid(r -> {
      r.setExpiryMonth(1);
      r.setExpiryYear(YearMonth.now().getYear() + 1);
    });
  }

  @Test
  void expiryYearOutsideSupportedRangeIsRejectedWithoutThrowing() {
    assertInvalid("expiryInFuture", r -> r.setExpiryYear(2_000_000_000));
  }

  // ---- Currency: required, 3 chars, at most 3 supported codes ----

  @ParameterizedTest
  @ValueSource(strings = {"USD", "GBP", "EUR"})
  void supportedCurrencyIsAccepted(String currency) {
    assertValid(r -> r.setCurrency(currency));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "GB", "GBPP", "JPY", "gbp", "Gbp", " GBP", "GBP "})
  void invalidCurrencyIsRejected(String currency) {
    assertInvalid("currency", r -> r.setCurrency(currency));
  }

  @Test
  void exactlyThreeCurrenciesAreSupported() {
    // Requirement: "validate against no more than 3 currency codes".
    long accepted = java.util.stream.Stream.of("USD", "GBP", "EUR", "JPY", "CAD", "AUD", "CHF",
            "CNY", "SEK", "NZD", "INR", "BRL", "ZAR", "MXN", "SGD", "HKD", "NOK", "KRW")
        .filter(c -> {
          PostPaymentRequest r = valid();
          r.setCurrency(c);
          return invalidFields(r).isEmpty();
        })
        .count();
    assertEquals(3, accepted);
  }

  // ---- Amount: required, integer (minor units) ----

  @ParameterizedTest
  @ValueSource(ints = {1, 100, 1050, Integer.MAX_VALUE})
  void positiveAmountIsAccepted(int amount) {
    assertValid(r -> r.setAmount(amount));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
  void missingOrNonPositiveAmountIsRejected(Integer amount) {
    assertInvalid("amount", r -> r.setAmount(amount));
  }

  // ---- CVV: required, 3-4 chars, numeric only ----

  @ParameterizedTest
  @ValueSource(strings = {"123", "1234", "000", "0123"})
  void cvvOf3Or4DigitsIsAccepted(String cvv) {
    assertValid(r -> r.setCvv(cvv));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "1", "12", "12345", "12a", "abc", "1 2", "-12", "+123"})
  void invalidCvvIsRejected(String cvv) {
    assertInvalid("cvv", r -> r.setCvv(cvv));
  }

  // ---- Sensitive data must not leak through toString (logging) ----

  @Test
  void toStringDoesNotContainCardNumberOrCvv() {
    PostPaymentRequest r = valid();
    r.setCvv("987");
    String text = r.toString();
    assertTrue(!text.contains(r.getCardNumber()) && !text.contains("987"), text);
  }
}
