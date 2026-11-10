package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class BankClientTest {

  private static final String BANK_URL = "http://bank/payments";

  private RestTemplate restTemplate;
  private BankClient bankClient;

  @BeforeEach
  void setUp() {
    restTemplate = mock(RestTemplate.class);
    bankClient = new BankClient(restTemplate, BANK_URL);
  }

  private static BankPaymentRequest request() {
    return new BankPaymentRequest("2222405343248877", "04/2030", "GBP", 1050, "123");
  }

  @Test
  void returnsTheBankResponse() {
    when(restTemplate.postForObject(BANK_URL, request(), BankPaymentResponse.class))
        .thenReturn(new BankPaymentResponse(true));

    assertEquals(new BankPaymentResponse(true), bankClient.authorize(request()));
  }

  @Test
  void serverErrorIsReportedAsBankUnavailable() {
    when(restTemplate.postForObject(eq(BANK_URL), eq(request()), eq(BankPaymentResponse.class)))
        .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThrows(BankUnavailableException.class, () -> bankClient.authorize(request()));
  }

  @Test
  void connectionFailureIsReportedAsBankUnavailable() {
    when(restTemplate.postForObject(eq(BANK_URL), eq(request()), eq(BankPaymentResponse.class)))
        .thenThrow(new ResourceAccessException("connection refused"));

    assertThrows(BankUnavailableException.class, () -> bankClient.authorize(request()));
  }

  @Test
  void emptyResponseIsReportedAsBankUnavailable() {
    when(restTemplate.postForObject(BANK_URL, request(), BankPaymentResponse.class))
        .thenReturn(null);

    assertThrows(BankUnavailableException.class, () -> bankClient.authorize(request()));
  }

  @Test
  void missingAuthorizedIsReportedAsBankUnavailable() {
    when(restTemplate.postForObject(BANK_URL, request(), BankPaymentResponse.class))
        .thenReturn(new BankPaymentResponse(null));

    assertThrows(BankUnavailableException.class, () -> bankClient.authorize(request()));
  }
}
