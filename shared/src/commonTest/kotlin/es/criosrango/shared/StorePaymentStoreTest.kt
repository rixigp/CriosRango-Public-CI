        }
        assertEquals(false, result)
    }

    @Test
    fun callbackReconciliationIsExactlyOnce() = runTest {
        var paymentStatusCalls = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/payment-status")) {
                paymentStatusCalls++
                respond(
                    content = """{"order_id":123,"status":"processing","paid":true,"terminal":true}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            } else {
                respond(
                    content = """{"items":[],"totals":{"total_price":"0","currency_symbol":"€","currency_minor_unit":2}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }
        }
        val api = StoreApiClient(client = HttpClient(engine))
        val pendingStore = FakePendingCardPaymentStore()
        val cartStore = StoreCartStore(api, this)
        val paymentStore = StorePaymentStore(api, cartStore, pendingStore, this)

        val redirectUrl = paymentStore.startCardPayment(
            CheckoutResponse(
                orderId = 123,
                orderKey = "wc_order_123",
                paymentMethod = "cecabank_gateway",
                redirectUrl = "https://payment.example/123"
            )
        )

        assertEquals("https://payment.example/123", redirectUrl)
        paymentStore.markPaymentOpened()

        paymentStore.handlePaymentReturn("ok", 123)
        advanceUntilIdle()

        assertEquals(1, paymentStatusCalls)

    }
    @Test
    fun duplicateForegroundAndCallbackAfterPaidAreIgnored() = runTest {
        var paymentStatusCalls = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/payment-status")) {
                paymentStatusCalls++
                respond(
                    content = """{"order_id":123,"status":"processing","paid":true,"terminal":true}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            } else {
                respond(
                    content = """{"items":[],"totals":{"total_price":"0","currency_symbol":"€","currency_minor_unit":2}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }
        }
        val api = StoreApiClient(client = HttpClient(engine))
        val pendingStore = FakePendingCardPaymentStore()
        val cartStore = StoreCartStore(api, this)