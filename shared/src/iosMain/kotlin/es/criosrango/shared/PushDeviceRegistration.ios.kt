package es.criosrango.shared
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import platform.Foundation.NSUserDefaults
actual object PushDeviceRegistration {
 actual suspend fun unregisterCurrentDevice() {
  val token=NSUserDefaults.standardUserDefaults.stringForKey("criosrango_apns_token") ?: return
  val client=createStoreHttpClient()
  try { client.delete("https://criosrango.es/wp-json/criosrango/v1/push/device"){contentType(ContentType.Application.Json);IosAccountTokenStore().load()?.let{header(HttpHeaders.Authorization, "Bearer $it")};setBody(mapOf("platform" to "ios","token" to token))} } finally { client.close() }
 }
}