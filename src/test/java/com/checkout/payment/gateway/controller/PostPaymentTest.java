package com.checkout.payment.gateway.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class PostPaymentTest {

  private static final String BANK_URL = "http://localhost:8080/payments";

  @Autowired
  private MockMvc mvc;
  @Autowired
  private RestTemplate restTemplate;
  @Autowired
  private ObjectMapper objectMapper;

  private MockRestServiceServer bank;

  @BeforeEach
  void setUp() {
    bank = MockRestServiceServer.bindTo(restTemplate).build();
  }

  // ---- happy paths -------------------------------------------------------------------------

  @Test
  void authorizedPaymentIsReturnedWithMaskedCardAndForwardedToBank() throws Exception {
    YearMonth expiry = YearMonth.now().plusMonths(1);
    bank.expect(once(), requestTo(BANK_URL))
        .andExpect(method(POST))
        .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
            .jsonPath("$.card_number").value("2222405343248877"))
        .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
            .jsonPath("$.expiry_date").value(String.format("%02d/%d", expiry.getMonthValue(), expiry.getYear())))
        .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
            .jsonPath("$.currency").value("GBP"))
        .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
            .jsonPath("$.amount").value(100))
        .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
            .jsonPath("$.cvv").value("123"))
        .andRespond(withSuccess(
            "{\"authorized\":true,\"authorization_code\":\"0bb07405-6d44-4b50-a14f-7ae0beff13ad\"}",
            MediaType.APPLICATION_JSON));

    mvc.perform(post(validRequest(m -> {
          m.put("expiry_month", expiry.getMonthValue());
          m.put("expiry_year", expiry.getYear());
        })))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("8877"))
        .andExpect(jsonPath("$.expiryMonth").value(expiry.getMonthValue()))
        .andExpect(jsonPath("$.expiryYear").value(expiry.getYear()))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(100))
        // full PAN / cvv must never be returned
        .andExpect(content().string(not(containsString("2222405343248877"))))
        .andExpect(jsonPath("$.cvv").doesNotExist())
        .andExpect(jsonPath("$.cardNumber").doesNotExist());
    bank.verify();
  }

  @Test
  void declinedPaymentIsReturnedAsDeclined() throws Exception {
    bank.expect(once(), requestTo(BANK_URL))
        .andRespond(withSuccess("{\"authorized\":false,\"authorization_code\":\"\"}",
            MediaType.APPLICATION_JSON));

    mvc.perform(post(validRequest(m -> m.put("card_number", "2222405343248112"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("8112"));
    bank.verify();
  }

  @Test
  void paymentCanBeRetrievedAfterItIsProcessed() throws Exception {
    bank.expect(once(), requestTo(BANK_URL))
        .andRespond(withSuccess("{\"authorized\":true,\"authorization_code\":\"abc\"}",
            MediaType.APPLICATION_JSON));

    MvcResult result = mvc.perform(post(validRequest(m -> m.put("card_number", "4000000000000123"))))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + created.get("id").asText()))
        .andExpect(status().isOk())
        // leading zero of the last four digits must be preserved
        .andExpect(jsonPath("$.cardNumberLastFour").value("0123"))
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.id").value(created.get("id").asText()));
  }

  @ParameterizedTest
  @CsvSource({"14", "19"})
  void cardNumberLengthBoundariesAreAccepted(int length) throws Exception {
    bank.expect(once(), requestTo(BANK_URL))
        .andRespond(withSuccess("{\"authorized\":true,\"authorization_code\":\"abc\"}",
            MediaType.APPLICATION_JSON));

    mvc.perform(post(validRequest(m -> m.put("card_number", "1".repeat(length)))))
        .andExpect(status().isOk());
    bank.verify();
  }

  @ParameterizedTest
  @CsvSource({"USD", "GBP", "EUR"})
  void supportedCurrenciesAreAccepted(String currency) throws Exception {
    bank.expect(once(), requestTo(BANK_URL))
        .andRespond(withSuccess("{\"authorized\":true,\"authorization_code\":\"abc\"}",
            MediaType.APPLICATION_JSON));

    mvc.perform(post(validRequest(m -> m.put("currency", currency))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency").value(currency));
    bank.verify();
  }

  // ---- rejected: validation ----------------------------------------------------------------

  @ParameterizedTest(name = "{0} = {1} is rejected")
  @CsvSource(nullValues = "NULL", value = {
      "card_number, NULL",
      "card_number, 1234567890123",
      "card_number, 12345678901234567890",
      "card_number, 2222405343A48877",
      "card_number, '2222 4053 4324 8877'",
      "expiry_month, NULL",
      "expiry_month, 0",
      "expiry_month, 13",
      "expiry_month, -1",
      "expiry_year, NULL",
      "expiry_year, 2000",
      "currency, NULL",
      "currency, GB",
      "currency, GBPP",
      "currency, JPY",
      "currency, gbp",
      "amount, NULL",
      "amount, 0",
      "amount, -5",
      "cvv, NULL",
      "cvv, '12'",
      "cvv, '12345'",
      "cvv, '12a'",
  })
  void invalidFieldIsRejectedWithoutCallingBank(String field, String value) throws Exception {
    bank.expect(never(), requestTo(BANK_URL));

    mvc.perform(post(validRequest(m -> m.put(field, value))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").isNotEmpty());
    bank.verify();
  }

  @Test
  void currentMonthIsRejectedAsNotInTheFuture() throws Exception {
    YearMonth now = YearMonth.now();
    bank.expect(never(), requestTo(BANK_URL));

    mvc.perform(post(validRequest(m -> {
          m.put("expiry_month", now.getMonthValue());
          m.put("expiry_year", now.getYear());
        })))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").value(containsString("in the future")));
    bank.verify();
  }

  @Test
  void lastMonthIsRejectedAsNotInTheFuture() throws Exception {
    YearMonth lastMonth = YearMonth.now().minusMonths(1);
    bank.expect(never(), requestTo(BANK_URL));

    mvc.perform(post(validRequest(m -> {
          m.put("expiry_month", lastMonth.getMonthValue());
          m.put("expiry_year", lastMonth.getYear());
        })))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"));
    bank.verify();
  }

  @Test
  void expiryYearBeyondSupportedRangeIsRejectedNotServerError() throws Exception {
    bank.expect(never(), requestTo(BANK_URL));

    mvc.perform(post(validRequest(m -> m.put("expiry_year", 2_000_000_000))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"));
    bank.verify();
  }

  @Test
  void allInvalidFieldsAreReportedTogether() throws Exception {
    mvc.perform(post("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").value(containsString("cardNumber")))
        .andExpect(jsonPath("$.message").value(containsString("expiryMonth")))
        .andExpect(jsonPath("$.message").value(containsString("expiryYear")))
        .andExpect(jsonPath("$.message").value(containsString("currency")))
        .andExpect(jsonPath("$.message").value(containsString("amount")))
        .andExpect(jsonPath("$.message").value(containsString("cvv")));
  }

  @Test
  void rejectedResponseDoesNotEchoCardData() throws Exception {
    mvc.perform(post(validRequest(m -> {
          m.put("card_number", "2222-4053-4324-8877");
          m.put("cvv", "12");
        })))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(not(containsString("2222-4053-4324-8877"))));
  }

  @ParameterizedTest(name = "non-integer amount {0} is rejected")
  @CsvSource({"10.5", "\"abc\"", "99999999999"})
  void nonIntegerAmountIsRejected(String rawAmount) throws Exception {
    String body = "{\"card_number\":\"2222405343248877\",\"expiry_month\":12,\"expiry_year\":"
        + (YearMonth.now().getYear() + 1) + ",\"currency\":\"GBP\",\"amount\":" + rawAmount
        + ",\"cvv\":\"123\"}";
    bank.expect(never(), requestTo(BANK_URL));

    mvc.perform(post(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"));
    bank.verify();
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {"not json", "''", "[]"})
  void malformedBodyIsRejected(String body) throws Exception {
    bank.expect(never(), requestTo(BANK_URL));

    mvc.perform(post(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"));
    bank.verify();
  }

  // ---- bank failures -----------------------------------------------------------------------

  @Test
  void bankServiceUnavailableResultsIn503() throws Exception {
    bank.expect(once(), requestTo(BANK_URL)).andRespond(withStatus(
        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));

    mvc.perform(post(validRequest(m -> m.put("card_number", "2222405343248870"))))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.message").value("Acquiring bank unavailable"))
        .andExpect(jsonPath("$.status").doesNotExist());
    bank.verify();
  }

  @Test
  void bankBadRequestResultsIn503() throws Exception {
    bank.expect(once(), requestTo(BANK_URL)).andRespond(withStatus(
        org.springframework.http.HttpStatus.BAD_REQUEST));

    mvc.perform(post(validRequest(m -> { })))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void bankEmptyBodyResultsIn503() throws Exception {
    bank.expect(once(), requestTo(BANK_URL)).andRespond(withSuccess());

    mvc.perform(post(validRequest(m -> { })))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void bankConnectionFailureResultsIn503() throws Exception {
    bank.expect(once(), requestTo(BANK_URL)).andRespond(request -> {
      throw new java.io.IOException("connection refused");
    });

    mvc.perform(post(validRequest(m -> { })))
        .andExpect(status().isServiceUnavailable());
  }

  // ---- helpers -----------------------------------------------------------------------------

  private org.springframework.test.web.servlet.RequestBuilder post(String body) {
    return MockMvcRequestBuilders.post("/payment")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private String validRequest(Consumer<Map<String, Object>> customise) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("card_number", "2222405343248877");
    body.put("expiry_month", 12);
    body.put("expiry_year", YearMonth.now().getYear() + 1);
    body.put("currency", "GBP");
    body.put("amount", 100);
    body.put("cvv", "123");
    customise.accept(body);
    // Entries set to null are serialised as explicit nulls, which must behave like "missing".
    return objectMapper.writeValueAsString(body);
  }
}
