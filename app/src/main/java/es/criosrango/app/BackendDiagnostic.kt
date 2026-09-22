package es.criosrango.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

internal object BackendDiagnosticRequest

internal data class BackendDiagnosticResult(
    val label: String,
    val error: String? = null,
    val status: Int? = null,
    val totalMs: Long? = null,
    val ttfbMs: Long? = null,
    val bodyDownloadMs: Long? = null,
    val bodyBytes: Long? = null,
    val dnsMs: Long? = null,
    val connectMs: Long? = null,
    val tlsMs: Long? = null,
    val protocol: String? = null,
    val connectionReused: Boolean? = null,
    val cacheControl: String? = null,
    val age: String? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val vary: String? = null,
    val setCookie: Boolean? = null,
    val server: String? = null,
    val via: String? = null,
    val xCache: String? = null,
    val cfCacheStatus: String? = null,
    val cacheEquivalent: String? = null,
    val cookieStored: Boolean? = null,
    val cartTokenStored: Boolean? = null,
    val nonceStored: Boolean? = null,
    val cookieFinal: Boolean? = null,
    val cartTokenFinal: Boolean? = null,
    val nonceFinal: Boolean? = null
)

internal class BackendNetworkEventListener : EventListener() {
    var callStartNs = 0L
    var dnsStartNs: Long? = null
    var dnsEndNs: Long? = null
    var connectStartNs: Long? = null
    var connectEndNs: Long? = null
    var tlsStartNs: Long? = null
    var tlsEndNs: Long? = null
    var responseHeadersStartNs: Long? = null
    var responseHeadersEndNs: Long? = null
    var responseBodyStartNs: Long? = null
    var responseBodyEndNs: Long? = null
    var callEndNs: Long? = null
    var connectionReused = false
    private var connectStarted = false

    private fun now() = System.nanoTime()

    override fun callStart(call: okhttp3.Call) { callStartNs = now() }
    override fun dnsStart(call: okhttp3.Call, domainName: String) { dnsStartNs = now() }
    override fun dnsEnd(call: okhttp3.Call, domainName: String, inetAddressList: List<java.net.InetAddress>) { dnsEndNs = now() }

    override fun connectStart(
        call: okhttp3.Call,
        inetSocketAddress: java.net.InetSocketAddress,
        proxy: java.net.Proxy
    ) {
        connectStarted = true
        connectStartNs = now()
    }

    override fun connectEnd(
        call: okhttp3.Call,
        inetSocketAddress: java.net.InetSocketAddress,
        proxy: java.net.Proxy,
        protocol: okhttp3.Protocol?
    ) {
        connectEndNs = now()
    }

    override fun secureConnectStart(call: okhttp3.Call) { tlsStartNs = now() }
    override fun secureConnectEnd(call: okhttp3.Call, handshake: okhttp3.Handshake?) { tlsEndNs = now() }

    override fun connectionAcquired(call: okhttp3.Call, connection: okhttp3.Connection) {
        connectionReused = !connectStarted
    }

    override fun responseHeadersStart(call: okhttp3.Call) { responseHeadersStartNs = now() }

    override fun responseHeadersEnd(call: okhttp3.Call, response: Response) {
        responseHeadersEndNs = now()
    }

    override fun responseBodyStart(call: okhttp3.Call) { responseBodyStartNs = now() }
    override fun responseBodyEnd(call: okhttp3.Call, byteCount: Long) { responseBodyEndNs = now() }
    override fun callEnd(call: okhttp3.Call) { callEndNs = now() }
}

internal class BackendDiagnosticRunner(private val session: StoreSession) {

    suspend fun run(categoryId: Int): String = withContext(Dispatchers.IO) {
        val categoryUrl = STORE_API_BASE_URL + "products?per_page=24&page=1&category=" + categoryId
        val allProductsUrl = STORE_API_BASE_URL + "products?per_page=24&page=1"

        val anonymousClient = OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        try {
            val anonymous = listOf(
                measure(anonymousClient, categoryUrl, "ANÓNIMA #1"),
                measure(anonymousClient, categoryUrl, "ANÓNIMA #2"),
                measure(anonymousClient, categoryUrl, "ANÓNIMA #3")
            )

            val normalClient = OkHttpClient.Builder()
                .cookieJar(CookieJar.NO_COOKIES)
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val builder = chain.request().newBuilder()
                    session.cartToken?.let { builder.header("Cart-Token", it) }
                    session.nonce?.let { builder.header("Nonce", it) }
                    session.cookieHeader?.let { builder.header("Cookie", it) }
                    chain.proceed(builder.build())
                }
                .build()
            val normal = measure(
                normalClient,
                categoryUrl,
                "SESIÓN APP",
                !session.cookieHeader.isNullOrBlank(),
                !session.cartToken.isNullOrBlank(),
                !session.nonce.isNullOrBlank()
            )

            val noCategory = measure(anonymousClient, allProductsUrl, "SIN CATEGORY")

            buildString {
                appendLine("DIAGNÓSTICO BACKEND")
                appendLine()
                appendLine("CATEGORY_ID = " + categoryId)
                appendLine()
                anonymous.forEach {
                    appendResult(it)
                    appendLine()
                }
                appendResult(normal)
                appendLine()
                appendResult(noCategory)
            }.trim()
        } finally {
            anonymousClient.connectionPool.evictAll()
            anonymousClient.dispatcher.executorService.shutdown()
            normalClientCleanup()
        }
    }

    private fun StringBuilder.appendResult(result: BackendDiagnosticResult) {
        appendLine(result.label)
        if (result.error != null) {
            appendLine("ERROR = " + result.error)
            return
        }
        appendLine("HTTP status = " + (result.status ?: "N/A"))
        appendLine("TOTAL = " + (result.totalMs?.let { it.toString() + " ms" } ?: "N/A"))
        appendLine("TTFB = " + (result.ttfbMs?.let { it.toString() + " ms" } ?: "N/A"))
        appendLine("BODY DOWNLOAD = " + (result.bodyDownloadMs?.let { it.toString() + " ms" } ?: "N/A"))
        appendLine("BODY BYTES = " + (result.bodyBytes ?: "N/A"))
        appendLine("DNS = " + (result.dnsMs?.let { it.toString() + " ms" } ?: "N/A"))
        appendLine("CONNECT = " + (result.connectMs?.let { it.toString() + " ms" } ?: "N/A"))
        appendLine("TLS = " + (result.tlsMs?.let { it.toString() + " ms" } ?: "N/A"))
        appendLine("HTTP protocol = " + (result.protocol ?: "N/A"))
        appendLine("CONNECTION REUSED = " + (result.connectionReused?.let { if (it) "SI" else "NO" } ?: "N/A"))
        result.cookieStored?.let { appendLine("COOKIE ALMACENADA/DISPONIBLE = " + if (it) "SI" else "NO") }
        result.cartTokenStored?.let { appendLine("CART-TOKEN ALMACENADO = " + if (it) "SI" else "NO") }
        result.nonceStored?.let { appendLine("NONCE ALMACENADO = " + if (it) "SI" else "NO") }
        result.cookieFinal?.let { appendLine("COOKIE FINAL EN REQUEST = " + if (it) "SI" else "NO") }
        result.cartTokenFinal?.let { appendLine("CART-TOKEN FINAL EN REQUEST = " + if (it) "SI" else "NO") }
        result.nonceFinal?.let { appendLine("NONCE FINAL EN REQUEST = " + if (it) "SI" else "NO") }
        appendLine("Cache-Control = " + (result.cacheControl ?: "N/A"))
        appendLine("Age = " + (result.age ?: "N/A"))
        appendLine("ETag = " + (result.etag ?: "N/A"))
        appendLine("Last-Modified = " + (result.lastModified ?: "N/A"))
        appendLine("Vary = " + (result.vary ?: "N/A"))
        appendLine("Set-Cookie = " + (result.setCookie?.let { if (it) "SI" else "NO" } ?: "N/A"))
        appendLine("Server = " + (result.server ?: "N/A"))
        appendLine("Via = " + (result.via ?: "N/A"))
        appendLine("X-Cache = " + (result.xCache ?: "N/A"))
        appendLine("CF-Cache-Status = " + (result.cfCacheStatus ?: "N/A"))
        appendLine("CACHE EQUIVALENT = " + (result.cacheEquivalent ?: "N/A"))
    }

    private fun normalClientCleanup() = Unit

    private fun measure(
        client: OkHttpClient,
        url: String,
        label: String,
        cookieStored: Boolean? = null,
        cartTokenStored: Boolean? = null,
        nonceStored: Boolean? = null
    ): BackendDiagnosticResult {
        val listener = BackendNetworkEventListener()
        val request = Request.Builder()
            .url(url)
            .get()
            .tag(BackendDiagnosticRequest::class.java, BackendDiagnosticRequest)
            .build()

        var finalCookie = false
        var finalCartToken = false
        var finalNonce = false

        val measuredClient = client.newBuilder()
            .eventListener(listener)
            .addNetworkInterceptor { networkChain ->
                val finalRequest = networkChain.request()
                finalCookie = finalRequest.header("Cookie") != null
                finalCartToken = finalRequest.header("Cart-Token") != null
                finalNonce = finalRequest.header("Nonce") != null
                networkChain.proceed(finalRequest)
            }
            .build()

        return try {
            measuredClient.newCall(request).execute().use { response ->
                var bytes = 0L
                response.body?.byteStream()?.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        bytes += read
                    }
                }

                val headers = response.headers
                val excluded = setOf("cache-control", "age", "etag", "last-modified", "vary", "x-cache", "cf-cache-status")
                val cacheEquivalent = headers.names()
                    .filter { name ->
                        val lower = name.lowercase()
                        (lower.contains("cache") || lower.contains("cdn") || lower.contains("surrogate") ||
                            lower.contains("varnish") || lower.contains("proxy")) && lower !in excluded
                    }
                    .sorted()
                    .joinToString("; ") { name -> name + "=" + headers[name].orEmpty() }

                BackendDiagnosticResult(
                    label = label,
                    status = response.code,
                    totalMs = listener.callEndNs?.let { elapsedMs(listener.callStartNs, it) },
                    ttfbMs = listener.responseHeadersStartNs?.let { elapsedMs(listener.callStartNs, it) },
                    bodyDownloadMs =
                        if (listener.responseBodyStartNs != null && listener.responseBodyEndNs != null)
                            elapsedMs(listener.responseBodyStartNs!!, listener.responseBodyEndNs!!)
                        else null,
                    bodyBytes = bytes,
                    dnsMs = duration(listener.dnsStartNs, listener.dnsEndNs),
                    connectMs = duration(listener.connectStartNs, listener.connectEndNs),
                    tlsMs = duration(listener.tlsStartNs, listener.tlsEndNs),
                    protocol = response.protocol.toString(),
                    connectionReused = listener.connectionReused,
                    cacheControl = headers["Cache-Control"],
                    age = headers["Age"],
                    etag = headers["ETag"],
                    lastModified = headers["Last-Modified"],
                    vary = headers["Vary"],
                    setCookie = headers.values("Set-Cookie").isNotEmpty(),
                    server = headers["Server"],
                    via = headers["Via"],
                    xCache = headers["X-Cache"],
                    cfCacheStatus = headers["CF-Cache-Status"],
                    cacheEquivalent = cacheEquivalent.ifBlank { null },
                    cookieStored = cookieStored,
                    cartTokenStored = cartTokenStored,
                    nonceStored = nonceStored,
                    cookieFinal = finalCookie,
                    cartTokenFinal = finalCartToken,
                    nonceFinal = finalNonce
                )
            }
        } catch (exception: Exception) {
            BackendDiagnosticResult(
                label = label,
                error = exception::class.simpleName + ": " + (exception.message ?: "sin detalle"),
                totalMs = listener.callEndNs?.let { elapsedMs(listener.callStartNs, it) },
                dnsMs = duration(listener.dnsStartNs, listener.dnsEndNs),
                connectMs = duration(listener.connectStartNs, listener.connectEndNs),
                tlsMs = duration(listener.tlsStartNs, listener.tlsEndNs),
                connectionReused = if (listener.callStartNs != 0L) listener.connectionReused else null,
                cookieStored = cookieStored,
                cartTokenStored = cartTokenStored,
                nonceStored = nonceStored,
                cookieFinal = finalCookie,
                cartTokenFinal = finalCartToken,
                nonceFinal = finalNonce
            )
        }
    }

    private fun duration(start: Long?, end: Long?): Long? =
        if (start != null && end != null) elapsedMs(start, end) else null

    private fun elapsedMs(start: Long, end: Long): Long =
        (end - start).coerceAtLeast(0L) / 1_000_000L
}
