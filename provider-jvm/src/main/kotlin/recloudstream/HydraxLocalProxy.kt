package recloudstream

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.net.InetSocketAddress
import java.net.URLDecoder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

/**
 * Real local HTTP server standing in for [HydraxInterceptor].
 *
 * On Android, CloudStream's ExoPlayer sends all requests through an
 * OkHttpClient that has [HydraxInterceptor] attached, which intercepts the
 * fake `hydrax-relay.internal` URL and answers with decrypted segment bytes
 * on the fly. A desktop player like VLC/mpv has no such interceptor — it
 * only understands real HTTP(S) URLs — so this class exposes the same
 * decrypt-and-relay logic as an actual `http://127.0.0.1:<port>/...` server.
 *
 * Usage: start it once, then rewrite any ExtractorLink whose host is
 * [HydraxExtractor.RELAY_HOST] to point at this local server instead
 * (see [rewriteToLocal]) before handing the URL to VLC/mpv.
 */
class HydraxLocalProxy(private val port: Int = 47471) {

    private val client = OkHttpClient()
    private var server: HttpServer? = null
    private val fragmentSize = 2_097_152L

    val baseUrl: String get() = "http://127.0.0.1:$port"

    fun start() {
        if (server != null) return
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        s.createContext("/video.mp4", ::handle)
        s.executor = java.util.concurrent.Executors.newCachedThreadPool()
        s.start()
        server = s
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    /**
     * Rewrites a relay URL produced by HydraxExtractor
     * (`https://hydrax-relay.internal/video.mp4?...`) into a real local URL
     * (`http://127.0.0.1:47471/video.mp4?...`) this server will answer.
     * Call [start] before the player requests this URL.
     */
    fun rewriteToLocal(relayUrl: String): String {
        val host = HydraxExtractor.RELAY_HOST
        return relayUrl.replace("https://$host", baseUrl).replace("http://$host", baseUrl)
    }

    private fun handle(exchange: HttpExchange) {
        try {
            val query = parseQuery(exchange.requestURI.rawQuery ?: "")
            val cdnBaseUrl = query["base"]
            val md5Id = query["md5"]?.toIntOrNull()
            val resId = query["res"]?.toIntOrNull()
            val size = query["size"]?.toLongOrNull()

            if (cdnBaseUrl == null || md5Id == null || resId == null || size == null) {
                exchange.sendResponseHeaders(400, -1)
                exchange.close()
                return
            }

            val rangeHeader = exchange.requestHeaders.getFirst("Range")
            val (start, endInclusive) = parseRange(rangeHeader, size)

            val bodyBytes = readRange(cdnBaseUrl, md5Id, resId, size, start, endInclusive)
            val headers = exchange.responseHeaders
            headers.add("Content-Type", "video/mp4")
            headers.add("Accept-Ranges", "bytes")

            if (rangeHeader != null) {
                headers.add("Content-Range", "bytes $start-$endInclusive/$size")
                exchange.sendResponseHeaders(206, bodyBytes.size.toLong())
            } else {
                exchange.sendResponseHeaders(200, bodyBytes.size.toLong())
            }
            exchange.responseBody.use { it.write(bodyBytes) }
        } catch (e: Exception) {
            runCatching {
                exchange.sendResponseHeaders(500, -1)
                exchange.close()
            }
        }
    }

    private fun parseQuery(raw: String): Map<String, String> =
        raw.split("&").filter { it.contains("=") }.associate {
            val (k, v) = it.split("=", limit = 2)
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }

    private fun parseRange(header: String?, totalSize: Long): Pair<Long, Long> {
        if (header == null) return 0L to (totalSize - 1)
        val match = Regex("""bytes=(\d+)-(\d*)""").find(header) ?: return 0L to (totalSize - 1)
        val start = match.groupValues[1].toLongOrNull() ?: 0L
        val end = match.groupValues[2].toLongOrNull() ?: (totalSize - 1)
        return start to minOf(end, totalSize - 1)
    }

    /** Same segment-fetch-and-decrypt protocol as HydraxInterceptor.SegmentSource. */
    private fun readRange(
        cdnBaseUrl: String, md5Id: Int, resId: Int, totalSize: Long,
        startByte: Long, endByteInclusive: Long
    ): ByteArray {
        val out = Buffer()
        var currentPos = startByte
        while (currentPos <= endByteInclusive) {
            val segIndex = (currentPos / fragmentSize).toInt()
            val segStart = segIndex.toLong() * fragmentSize
            val segmentBytes = fetchSegment(cdnBaseUrl, md5Id, resId, totalSize, segIndex)
            if (segmentBytes.isEmpty()) break
            val offsetInSeg = (currentPos - segStart).toInt().coerceIn(0, segmentBytes.size)
            val remaining = endByteInclusive - currentPos + 1
            val toTake = minOf(remaining, (segmentBytes.size - offsetInSeg).toLong()).toInt()
            if (toTake <= 0) break
            out.write(segmentBytes, offsetInSeg, toTake)
            currentPos += toTake
        }
        return out.readByteArray()
    }

    private fun fetchSegment(cdnBaseUrl: String, md5Id: Int, resId: Int, totalSize: Long, index: Int): ByteArray {
        val path = "/mp4/$md5Id/$resId/$totalSize/$fragmentSize/$index"
        val token = tokenFor(path, totalSize)
        val segUrl = "$cdnBaseUrl/sora/$totalSize/$token"
        val req = Request.Builder()
            .url(segUrl)
            .header("Referer", "https://abysscdn.com/")
            .build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) ByteArray(0) else resp.body?.bytes() ?: ByteArray(0)
            }
        }.getOrDefault(ByteArray(0))
    }

    private fun tokenFor(path: String, totalSize: Long): String {
        val key = md5HexOfDigits(totalSize)
        val encrypted = aesCtrEncryptToIso(path, key)
        return doubleBase64(encrypted)
    }

    private fun md5HexOfDigits(value: Long): String {
        val bytes = value.toString().map { c ->
            if (c.isDigit()) c.digitToInt().toByte() else c.code.toByte()
        }.toByteArray()
        val digest = java.security.MessageDigest.getInstance("MD5").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun aesCtrEncryptToIso(data: String, keyHex: String): String {
        val keyBytes = keyHex.toByteArray(Charsets.UTF_8)
        val iv = keyBytes.copyOfRange(0, 16)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return String(encrypted, Charsets.ISO_8859_1)
    }

    private fun doubleBase64(input: String): String {
        val first = Base64.getEncoder().encodeToString(input.toByteArray(Charsets.ISO_8859_1)).replace("=", "")
        return Base64.getEncoder().encodeToString(first.toByteArray()).replace("=", "")
    }
}
