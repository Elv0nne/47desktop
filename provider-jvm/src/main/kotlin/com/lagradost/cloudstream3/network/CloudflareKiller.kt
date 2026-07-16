package com.lagradost.cloudstream3.network

/*
 * Phiên bản JVM/desktop của CloudflareKiller.
 *
 * Bản gốc trong app Android (com.lagradost.cloudstream3.network.CloudflareKiller,
 * nằm trong module `app`, KHÔNG có trong artifact library-jvm đã publish)
 * phụ thuộc vào android.webkit.CookieManager và WebViewResolver.android để
 * mở một WebView ẩn, giải Cloudflare "checking your browser" challenge rồi
 * lấy cookie cf_clearance. Trên JVM desktop không có WebView nên không thể
 * port nguyên bản.
 *
 * Bản thay thế này chỉ implement okhttp3.Interceptor để tương thích với
 * chữ ký `interceptor = interceptor` mà app.get/app.post (NiceHttp) yêu cầu.
 * Nó KHÔNG tự động bypass được Cloudflare — chỉ đơn giản forward request
 * kèm theo một User-Agent giống trình duyệt thật để giảm khả năng bị chặn,
 * và lưu lại cookie server trả về (nếu có) để dùng cho các request sau tới
 * cùng host, giống cơ chế savedCookies của bản gốc.
 *
 * Nếu trang mục tiêu bật Cloudflare challenge thực sự (JS challenge / captcha),
 * interceptor này sẽ không giải được — trường hợp đó cần một giải pháp khác
 * (vd. dùng cookie đã lấy sẵn thủ công, hoặc một headless browser bên ngoài).
 */
class CloudflareKiller : okhttp3.Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"
    }

    val savedCookies: MutableMap<String, Map<String, String>> = mutableMapOf()

    private val defaultUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val original = chain.request()
        val host = original.url.host

        val cookieHeader = savedCookies[host]
            ?.entries
            ?.joinToString("; ") { (k, v) -> "$k=$v" }

        val requestBuilder = original.newBuilder()
        if (original.header("user-agent") == null) {
            requestBuilder.header("user-agent", defaultUserAgent)
        }
        if (cookieHeader != null && original.header("cookie") == null) {
            requestBuilder.header("cookie", cookieHeader)
        }

        val response = chain.proceed(requestBuilder.build())

        // Lưu cookie mới từ response (nếu có) để dùng cho các request sau.
        val setCookieHeaders = response.headers("set-cookie")
        if (setCookieHeaders.isNotEmpty()) {
            val parsed = setCookieHeaders
                .mapNotNull { header ->
                    val pair = header.substringBefore(";").split("=", limit = 2)
                    if (pair.size == 2) pair[0].trim() to pair[1].trim() else null
                }
                .filter { it.first.isNotBlank() && it.second.isNotBlank() }
                .toMap()

            if (parsed.isNotEmpty()) {
                savedCookies[host] = (savedCookies[host] ?: emptyMap()) + parsed
            }
        }

        return response
    }
}
