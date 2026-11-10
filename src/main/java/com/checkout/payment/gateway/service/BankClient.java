package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class BankClient {

  private final RestTemplate restTemplate;
  private final String bankUrl;

  public BankClient(RestTemplate restTemplate, @Value("${bank.url}") String bankUrl) {
    this.restTemplate = restTemplate;
    this.bankUrl = bankUrl;
  }

  public BankPaymentResponse authorize(BankPaymentRequest request) {
    try {
      BankPaymentResponse response =
          restTemplate.postForObject(bankUrl, request, BankPaymentResponse.class);
      if (response == null) {
        throw new BankUnavailableException("Empty response from acquiring bank");
      }
      return response;
    } catch (RestClientException e) {
      throw new BankUnavailableException("Acquiring bank call failed", e);
    }
  }
}
