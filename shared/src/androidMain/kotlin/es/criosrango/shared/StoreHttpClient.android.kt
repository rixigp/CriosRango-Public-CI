package es.criosrango.shared
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
actual fun createStoreHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = true
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 12_000
        socketTimeoutMillis = 15_000
    }
    install(ContentNegotiation) { json(Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        exceptionsWithDebugInfo = false
    }) }
}
