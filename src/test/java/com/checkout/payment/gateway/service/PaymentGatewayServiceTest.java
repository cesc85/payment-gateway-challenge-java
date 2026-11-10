package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentGatewayServiceTest {

  private BankClient bankClient;
  private PaymentGatewayService service;

  @BeforeEach
  void setUp() {
    bankClient = mock(BankClient.class);
    service = new PaymentGatewayService(new PaymentsRepository(), bankClient);
  }

  private static PostPaymentRequest request() {
    PostPaymentRequest r = new PostPaymentRequest();
    r.setCardNumber("2222405343248877");
    r.setExpiryMonth(4);
    r.setExpiryYear(2030);
    r.setCurrency("GBP");
    r.setAmount(1050);
    r.setCvv("123");
    return r;
  }

  private void bankReturns(boolean authorized) {
    when(bankClient.authorize(any())).thenReturn(new BankPaymentResponse(authorized));
  }

  @Test
  void bankAuthorizationGivesAuthorizedPayment() {
    bankReturns(true);
    assertEquals(PaymentStatus.AUTHORIZED, service.processPayment(request()).getStatus());
  }

  @Test
  void bankDeclineGivesDeclinedPayment() {
    bankReturns(false);
    assertEquals(PaymentStatus.DECLINED, service.processPayment(request()).getStatus());
  }

  @Test
  void responseContainsRequiredFieldsAndOnlyLastFourDigits() {
    bankReturns(true);
    PostPaymentResponse response = service.processPayment(request());

    assertNotNull(response.getId());
    assertEquals("8877", response.getCardNumberLastFour());
    assertEquals(4, response.getExpiryMonth());
    assertEquals(2030, response.getExpiryYear());
    assertEquals("GBP", response.getCurrency());
    assertEquals(1050, response.getAmount());
  }

  @Test
  void lastFourDigitsKeepLeadingZeros() {
    bankReturns(true);
    PostPaymentRequest r = request();
    r.setCardNumber("4000000000000123");
    assertEquals("0123", service.processPayment(r).getCardNumberLastFour());
  }

  @Test
  void lastFourDigitsWorkForShortestAndLongestCardNumbers() {
    bankReturns(true);
    PostPaymentRequest shortest = request();
    shortest.setCardNumber("12345678901231");
    PostPaymentRequest longest = request();
    longest.setCardNumber("1234567890123456789");
    assertEquals("1231", service.processPayment(shortest).getCardNumberLastFour());
    assertEquals("6789", service.processPayment(longest).getCardNumberLastFour());
  }

  @Test
  void eachPaymentGetsAUniqueId() {
    bankReturns(true);
    assertNotEquals(service.processPayment(request()).getId(),
        service.processPayment(request()).getId());
  }

  @Test
  void bankIsCalledWithThePaymentRequest() {
    bankReturns(true);
    service.processPayment(request());

    verify(bankClient).authorize(
        new BankPaymentRequest("2222405343248877", "04/2030", "GBP", 1050, "123"));
  }

  @Test
  void expiryDateIsZeroPaddedMonthSlashYear() {
    bankReturns(true);
    PostPaymentRequest r = request();
    r.setExpiryMonth(12);
    service.processPayment(r);

    verify(bankClient).authorize(
        new BankPaymentRequest("2222405343248877", "12/2030", "GBP", 1050, "123"));
  }

  @Test
  void processedPaymentCanBeRetrievedWithSameDetails() {
    bankReturns(false);
    PostPaymentResponse created = service.processPayment(request());

    PostPaymentResponse fetched = service.getPaymentById(created.getId());
    assertSame(created, fetched);
    assertEquals(PaymentStatus.DECLINED, fetched.getStatus());
    assertEquals("8877", fetched.getCardNumberLastFour());
  }

  @Test
  void unknownPaymentIdThrowsNotFound() {
    assertThrows(EventProcessingException.class, () -> service.getPaymentById(UUID.randomUUID()));
  }

  @Test
  void bankFailureStoresNothing() {
    PaymentsRepository mockRepo = mock(PaymentsRepository.class);
    PaymentGatewayService svc = new PaymentGatewayService(mockRepo, bankClient);
    when(bankClient.authorize(any()))
        .thenThrow(new BankUnavailableException("Acquiring bank call failed"));

    assertThrows(BankUnavailableException.class, () -> svc.processPayment(request()));
    verifyNoInteractions(mockRepo);
  }
}
