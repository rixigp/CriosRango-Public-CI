package es.criosrango.app

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertTrue

class LiveImageOptimizationAuditTest {
    private data class Product(val id: Long, val name: String, val src: String, val thumbnail: String, val srcSet: String)
    private data class Measurement(val bytes: Long, val status: Int)
    private data class Row(val p: Product, val target: Int, val selected: String, val width: Int?, val original: Measurement?, val optimized: Measurement?, val error: String?)

    private val endpoint = "https://criosrango.es/wp-json/wc/store/v1/products"
    private val targets = listOf(360, 480, 600, 234, 312)

    @Test
    fun liveAudit() {
        val products = fetchProducts()
        assertTrue(products.size >= 20, "Insufficient live sample: " + products.size)

        val cache = mutableMapOf<String, Measurement>()
        val rows = products.flatMap { p ->
            targets.map { target ->
                val selected = selectResponsiveImageUrl(
                    p.src, p.thumbnail, p.srcSet, target,
                    preferThumbnailFallback = target == 234 || target == 312
                )
                val width = findSelectedWidth(p.srcSet, selected)
                val original = measure(p.src, cache)
                val optimized = measure(selected, cache)
                val errors = buildList {
                    if (original != null && original.status !in 200..399) add("original HTTP " + original.status)
                    if (optimized != null && optimized.status !in 200..399) add("selected HTTP " + optimized.status)
                    if (width != null && width < target) add("selected " + width + "w below target " + target + "px")
                }
                Row(p, target, selected, width, original, optimized, errors.joinToString("; ").ifBlank { null })
            }
        }
        writeSummary(rows)

        val usable = rows.count { it.original?.status in 200..399 && it.optimized?.status in 200..399 }
        val failures = rows.count { it.error != null }
        val belowTarget = rows.count { it.width != null && it.width < it.target }
        val originalTotal = rows.mapNotNull { it.original?.takeIf { m -> m.status in 200..399 }?.bytes }.sum()
        val optimizedTotal = rows.mapNotNull { it.optimized?.takeIf { m -> m.status in 200..399 }?.bytes }.sum()
        val result = when {
            usable < 20 * targets.size -> "B = no demostrada"
            failures > 0 || belowTarget > 0 -> "C = problema"
            optimizedTotal >= originalTotal -> "B = no demostrada"
            else -> "A = optimización demostrada"
        }
        appendResult(result)
        assertTrue(result == "A = optimización demostrada", "Live audit result: " + result)
    }

    private fun fetchProducts(): List<Product> {
        val out = mutableListOf<Product>()
        for (page in 1..5) {
            if (out.size >= 20) break
            val c = URI(endpoint + "?per_page=100&page=" + page).toURL().openConnection() as HttpURLConnection
            c.requestMethod = "GET"; c.instanceFollowRedirects = true; c.connectTimeout = 20000; c.readTimeout = 30000
            c.setRequestProperty("Accept", "application/json"); c.setRequestProperty("User-Agent", "CriosRango-image-audit/1.0")
            try {
                assertTrue(c.responseCode in 200..299, "Products endpoint HTTP " + c.responseCode)
                val array = JsonParser.parseString(c.inputStream.bufferedReader().use { it.readText() }).asJsonArray
                if (array.size() == 0) break
                array.forEach { e ->
                    if (out.size >= 20) return@forEach
                    val p = e.asJsonObject.toProductOrNull()
                    if (p != null && p.src.isNotBlank() && out.none { it.id == p.id }) out += p
                }
            } finally { c.disconnect() }
        }
        return out
    }

    private fun JsonObject.toProductOrNull(): Product? {
        val image = getAsJsonArray("images")?.firstOrNull()?.asJsonObject ?: return null
        val id = get("id")?.asLong ?: return null
        return Product(
            id,
            get("name")?.asString.orEmpty(),
            image.get("src")?.asString.orEmpty(),
            image.get("thumbnail")?.asString.orEmpty(),
            image.get("srcset")?.asString.orEmpty()
        )
    }

    private fun findSelectedWidth(srcSet: String, selected: String): Int? =
        srcSet.split(',').mapNotNull { entry ->
            val t = entry.trim().split(Regex("\\s+"))
            if (t.size < 2 || !t.last().endsWith('w')) return@mapNotNull null
            val w = t.last().dropLast(1).toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            if (t.dropLast(1).joinToString(" ").trim() == selected) w else null
        }.minOrNull()

    private fun measure(url: String, cache: MutableMap<String, Measurement>): Measurement? {
        if (url.isBlank()) return null
        return cache.getOrPut(url) { head(url) ?: get(url) }
    }

    private fun head(url: String): Measurement? = try {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.requestMethod = "HEAD"; c.instanceFollowRedirects = true; c.connectTimeout = 15000; c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "CriosRango-image-audit/1.0")
        try {
            val status = c.responseCode
            val length = c.getHeaderFieldLong("Content-Length", -1L)
            if (status in 200..399 && length >= 0) Measurement(length, status) else null
        } finally { c.disconnect() }
    } catch (_: Exception) { null }

    private fun get(url: String): Measurement {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.requestMethod = "GET"; c.instanceFollowRedirects = true; c.connectTimeout = 15000; c.readTimeout = 30000
        c.setRequestProperty("User-Agent", "CriosRango-image-audit/1.0")
        return try {
            val status = c.responseCode
            if (status !in 200..399) return Measurement(0, status)
            var bytes = 0L
            BufferedInputStream(c.inputStream).use { input ->
                val buffer = ByteArray(65536)
                while (true) { val n = input.read(buffer); if (n < 0) break; bytes += n }
            }
            Measurement(bytes, status)
        } finally { c.disconnect() }
    }

    private fun writeSummary(rows: List<Row>) {
        val path = System.getenv("GITHUB_STEP_SUMMARY") ?: return
        val ok = rows.filter { it.original?.status in 200..399 && it.optimized?.status in 200..399 }
        val originals = ok.map { it.original!!.bytes }
        val optimized = ok.map { it.optimized!!.bytes }
        val originalTotal = originals.sum()
        val optimizedTotal = optimized.sum()
        val saving = originalTotal - optimizedTotal
        val pct = if (originalTotal > 0) saving * 100.0 / originalTotal else 0.0
        fun median(v: List<Long>): Long { if (v.isEmpty()) return 0; val s = v.sorted(); return s[(s.size - 1) / 2] }

        val text = buildString {
            appendLine("# Image optimization live audit")
            appendLine()
            appendLine("- Endpoint: " + endpoint)
            appendLine("- GET/HEAD only; no authentication; no writes")
            appendLine("- Selector: `app/src/main/java/es/criosrango/app/ImageSourceSelector.kt`")
            appendLine()
            appendLine("## Summary")
            appendLine()
            appendLine("| Métrica | Valor |")
            appendLine("|---|---:|")
            appendLine("| Productos analizados | " + rows.map { it.p.id }.distinct().size + " |")
            appendLine("| Selecciones srcset | " + rows.count { it.width != null } + " |")
            appendLine("| Fallback src | " + rows.count { it.selected == it.p.src } + " |")
            appendLine("| Fallback thumbnail | " + rows.count { it.selected == it.p.thumbnail && it.p.thumbnail.isNotBlank() } + " |")
            appendLine("| Media original | " + originals.size + " |")
            appendLine("| Media optimizada | " + optimized.size + " |")
            appendLine("| Mediana original | " + median(originals) + " bytes |")
            appendLine("| Mediana optimizada | " + median(optimized) + " bytes |")
            appendLine("| Bytes totales originales | " + originalTotal + " |")
            appendLine("| Bytes totales optimizados | " + optimizedTotal + " |")
            appendLine("| Ahorro total | " + saving + " bytes (" + String.format(Locale.US, "%.2f", pct) + "%) |")
            appendLine("| Errores HTTP/selector | " + rows.count { it.error != null } + " |")
            appendLine()
            appendLine("## Productos / targets")
            appendLine()
            appendLine("| Producto | Target | Ancho elegido | Original bytes | Optimizada bytes | Ahorro bytes | Ahorro % |")
            appendLine("|---|---:|---:|---:|---:|---:|---:|")
            rows.forEach { r ->
                val o = r.original?.takeIf { it.status in 200..399 }?.bytes
                val n = r.optimized?.takeIf { it.status in 200..399 }?.bytes
                val s = if (o != null && n != null) o - n else null
                val p = if (o != null && o > 0 && s != null) s * 100.0 / o else null
                val w = r.width?.toString() ?: when {
                    r.selected == r.p.src -> "src"
                    r.selected == r.p.thumbnail -> "thumbnail"
                    else -> "—"
                }
                appendLine("| " + r.p.name.take(60).replace("|", "\\|") + " (#" + r.p.id + ") | " + r.target + "px | " + w + " | " + (o ?: "—") + " | " + (n ?: "—") + " | " + (s ?: "—") + " | " + (p?.let { String.format(Locale.US, "%.2f%%", it) } ?: "—") + " |")
            }
            rows.filter { it.error != null }.forEach { r -> appendLine("- Error #" + r.p.id + " / " + r.target + "px: " + r.error) }
        }
        File(path).writeText(text)
    }

    private fun appendResult(result: String) {
        val path = System.getenv("GITHUB_STEP_SUMMARY") ?: return
        File(path).appendText("\n## Resultado\n\n**" + result + "**\n")
    }
}
