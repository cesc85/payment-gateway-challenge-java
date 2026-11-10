# Design notes and assumptions

## What was added
- `POST /payment`: validates the request, calls the bank simulator (`bank.url`, default `http://localhost:8080/payments`), stores the result and returns it.
- `GET /payment/{id}` is unchanged (it already existed).

## Assumptions
1. **JSON naming.** The request uses snake_case (`card_number`, `expiry_month`, `expiry_year`), following the skeleton's existing annotations. Responses stay camelCase (`cardNumberLastFour`, ...), because the existing GET test asserts those names.
2. **`cardNumberLastFour` and `cvv` are strings.** The skeleton used `int`, which would turn "0123" into 123. The existing test was updated to match.
3. **Expiry "in the future".** Read literally. `expiry month/year` must be strictly after the current month, so a card expiring this month is rejected. Real cards are usually valid to the end of the month; if that is wanted, change `isAfter` to `!isBefore` in `PostPaymentRequest`.
4. **Currencies.** Only `USD`, `GBP` and `EUR` are accepted (the spec allows at most 3). Matching is case-sensitive, so `gbp` is rejected.
5. **Amount.** Must be an integer greater than 0. `10.5` is rejected (Jackson's `accept-float-as-int` is turned off) rather than truncated. Values beyond `int` range are rejected too.
6. **Rejected payments** return `400` with `{"status":"Rejected","message":"..."}`. The message lists field names and rules, never the submitted values, so card data is not echoed. Rejected payments are not stored and the bank is not called.
7. **Authorized and Declined** return `200` with the payment in the body, and the payment is stored for later retrieval.
8. **Bank failures** (503, 4xx, timeout, connection error, empty body) return `503 {"message":"Acquiring bank unavailable"}`. The payment is not stored and there are no retries. A bank 4xx would mean a gateway bug, since we validate first, but it is mapped the same way for simplicity.
9. **No full card number or CVV is stored, returned or logged.** Only the last four digits are kept. `PostPaymentRequest.toString()` leaves out the card number and CVV.
10. **Storage** is still the in-memory repository from the template. I changed `HashMap` to `ConcurrentHashMap` because the controller is called concurrently.
11. **No idempotency.** Submitting the same payment twice creates two payments.
12. **Bank request** is `{card_number, expiry_date "MM/YYYY", currency, amount, cvv}`, as in the README example.
13. **Loose JSON typing.** Jackson still coerces a numeric string such as `"12"` into the expiry month. I didn't tighten this.

## Testing
- Tests are in `PostPaymentTest` (46 tests) plus the 2 existing tests.
- They run through `MockMvc`, and the bank is mocked with `MockRestServiceServer` on the app's `RestTemplate`.
- They check the exact request sent to the bank, every validation rule (including boundaries), malformed bodies and each bank failure mode.
- They also check that rejected payments never reach the bank and that card data is not leaked.
- The real Mountebank simulator was **not** run (Docker was skipped by request), so the bank contract is only verified against the README's description.

## Running
Needs JDK 17 (Gradle 8.2.1 does not run on JDK 25): `JAVA_HOME=<jdk17> ./gradlew test`
