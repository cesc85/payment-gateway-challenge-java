package com.checkout.payment.gateway.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentsRepositoryTest {

  private final PaymentsRepository repository = new PaymentsRepository();

  private static PostPaymentResponse payment() {
    PostPaymentResponse p = new PostPaymentResponse();
    p.setId(UUID.randomUUID());
    return p;
  }

  @Test
  void storedPaymentCanBeRetrievedById() {
    PostPaymentResponse p = payment();
    repository.add(p);
    assertSame(p, repository.get(p.getId()).orElseThrow());
  }

  @Test
  void unknownIdReturnsEmpty() {
    assertTrue(repository.get(UUID.randomUUID()).isEmpty());
  }

  @Test
  void paymentsAreKeptSeparately() {
    PostPaymentResponse a = payment();
    PostPaymentResponse b = payment();
    repository.add(a);
    repository.add(b);
    assertEquals(a.getId(), repository.get(a.getId()).orElseThrow().getId());
    assertEquals(b.getId(), repository.get(b.getId()).orElseThrow().getId());
  }
}
