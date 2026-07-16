package com.thitbokobe.host

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TorrentLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.SubtitleFile
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.URLClassLoader

/**
 * LoadResponse has no common `dataUrl` field — each concrete subtype
 * carries the string that loadLinks() needs differently:
 *   - MovieLoadResponse / LiveStreamLoadResponse: single `dataUrl` string
 *   - TorrentLoadResponse: `magnet` or `torrent`
 *   - AnimeLoadResponse: `episodes` is Map<DubStatus, List<Episode>>
 *   - TvSeriesLoadResponse: `episodes` is a flat List<Episode>
 * Episode.data is itself the string to pass into loadLinks(). This picks
 * the first playable one found so the demo has something to run.
 */
private fun firstPlayableData(response: LoadResponse): String? {
    return when (response) {
        is MovieLoadResponse -> response.dataUrl
        is LiveStreamLoadResponse -> response.dataUrl
        is TorrentLoadResponse -> response.magnet ?: response.torrent
        is AnimeLoadResponse -> response.episodes.values.flatten().firstOrNull()?.data
        is TvSeriesLoadResponse -> response.episodes.firstOrNull()?.data
        else -> null
    }
}

/**
 * Minimal desktop host for CloudStream-style plugin jars.
 *
 * Usage:
 *   java -jar cs3-desktop-host.jar <path-to-plugin.jar> <search query>
 *
 * The plugin jar is a normal JVM jar built from the same MainAPI subclass
 * source you already maintain (e.g. Anime47Provider.kt), compiled against
 * `library-jvm` instead of packaged as a .cs3 (dex) file. See README.md
 * in this project for how to build that jar from your existing provider.
 */
fun main(args: Array<String>) {
    if (args.size < 2) {
        println("Usage: java -jar cs3-desktop-host.jar <plugin.jar> <search query>")
        return
    }

    val pluginJarPath = args[0]
    val query = args.drop(1).joinToString(" ")

    val pluginFile = File(pluginJarPath)
    if (!pluginFile.exists()) {
        println("Plugin jar not found: ${pluginFile.absolutePath}")
        return
    }

    val provider = loadProvider(pluginFile)
    if (provider == null) {
        println("No MainAPI subclass found inside ${pluginFile.name}")
        return
    }

    println("Loaded provider: ${provider.name} (${provider.mainUrl})")

    runBlocking {
        println("\n== search(\"$query\") ==")
        val results: List<SearchResponse> = provider.search(query) ?: emptyList()

        if (results.isEmpty()) {
            println("No results.")
            return@runBlocking
        }

        results.forEachIndexed { i, r -> println("[$i] ${r.name}  ->  ${r.url}") }

        val chosen = results.first()
        println("\n== load(\"${chosen.url}\") ==")
        val loadResponse = provider.load(chosen.url)
        if (loadResponse == null) {
            println("load() returned null.")
            return@runBlocking
        }
        println("Title: ${loadResponse.name}")

        val playableData = firstPlayableData(loadResponse)
        if (playableData == null) {
            println(
                "Could not find a playable data string on this LoadResponse " +
                        "(${loadResponse::class.simpleName}). If your provider returns a " +
                        "custom/different LoadResponse subtype, extend firstPlayableData() " +
                        "in Main.kt to handle it."
            )
            return@runBlocking
        }

        println("\n== loadLinks() ==")
        val foundLinks = mutableListOf<ExtractorLink>()
        val foundSubs = mutableListOf<SubtitleFile>()

        val ok = provider.loadLinks(
            data = playableData,
            isCasting = false,
            subtitleCallback = { sub -> foundSubs.add(sub) },
            callback = { link -> foundLinks.add(link) }
        )

        println("loadLinks() returned: $ok")
        println("Links found: ${foundLinks.size}")
        foundLinks.forEach { link ->
            println("  [${link.source}] ${link.name} -> ${link.url}")
        }
        println("Subtitles found: ${foundSubs.size}")
        foundSubs.forEach { sub ->
            println("  [${sub.lang}] ${sub.url}")
        }

        val chosenLink = foundLinks.firstOrNull()
        if (chosenLink == null) {
            println("\nNo playable link found — nothing to open.")
            return@runBlocking
        }

        val playableUrl = resolvePlayableUrl(chosenLink.url, provider)
        println("\nOpening in player: $playableUrl")
        openInExternalPlayer(playableUrl, chosenLink.referer.takeIf { it.isNotBlank() })
    }
}

/**
 * Some providers (e.g. Anime47's Hydrax/Abyss server) return a fake relay
 * URL that only makes sense to an OkHttp Interceptor bundled with the
 * plugin — a real player like VLC can't fetch it directly. If the plugin
 * jar ships a class named `recloudstream.HydraxLocalProxy` (or a similarly
 * named `*LocalProxy` class) with `start()` / `baseUrl` / `rewriteToLocal(String)`
 * members, we spin it up via reflection and rewrite the URL through it.
 * Providers without any such class are returned unchanged.
 */
private fun resolvePlayableUrl(url: String, provider: MainAPI): String {
    val proxyClass = try {
        Class.forName("recloudstream.HydraxLocalProxy", false, provider::class.java.classLoader)
    } catch (_: ClassNotFoundException) {
        return url
    }

    return try {
        val proxyInstance = proxyClass.getDeclaredConstructor().newInstance()
        proxyClass.getMethod("start").invoke(proxyInstance)
        val rewritten = proxyClass.getMethod("rewriteToLocal", String::class.java)
            .invoke(proxyInstance, url) as String
        if (rewritten != url) {
            println("(Hydrax relay link detected — started local proxy to serve it)")
        }
        rewritten
    } catch (e: Throwable) {
        println("Warning: found HydraxLocalProxy but failed to use it (${e.message}); using original URL.")
        url
    }
}

/**
 * Tries common players in PATH order: mpv, then VLC. Prints the URL either
 * way so you can always paste it manually if neither is installed.
 */
private fun openInExternalPlayer(url: String, referer: String?) {
    val candidates = listOf(
        listOf("mpv", url) + (referer?.let { listOf("--referrer=$it") } ?: emptyList()),
        listOf("vlc", url) + (referer?.let { listOf("--http-referrer=$it") } ?: emptyList())
    )

    for (command in candidates) {
        try {
            ProcessBuilder(command).start()
            println("Launched: ${command.first()}")
            return
        } catch (_: Exception) {
            // player not found on PATH, try the next one
        }
    }

    println(
        "Could not find mpv or vlc on PATH. Copy this URL into your player manually:\n$url" +
                (referer?.let { "\n(set Referer header to: $it if the player supports it)" } ?: "")
    )
}

/**
 * Loads the plugin jar in an isolated classloader (parent = this app's
 * classloader, so it can still see library-jvm classes) and instantiates
 * the first concrete MainAPI subclass it finds.
 */
private fun loadProvider(pluginFile: File): MainAPI? {
    val classLoader = URLClassLoader(
        arrayOf(pluginFile.toURI().toURL()),
        MainAPI::class.java.classLoader
    )

    val jarFile = java.util.jar.JarFile(pluginFile)
    val entries = jarFile.entries()

    var found: MainAPI? = null
    val loadErrors = mutableListOf<Pair<String, Throwable>>()

    while (entries.hasMoreElements()) {
        val entry = entries.nextElement()
        if (!entry.name.endsWith(".class") || entry.name.contains("$")) continue

        val className = entry.name.removeSuffix(".class").replace('/', '.')
        try {
            val clazz = Class.forName(className, false, classLoader)
            if (MainAPI::class.java.isAssignableFrom(clazz) &&
                !clazz.isInterface &&
                !java.lang.reflect.Modifier.isAbstract(clazz.modifiers)
            ) {
                found = clazz.getDeclaredConstructor().newInstance() as MainAPI
                break
            }
        } catch (e: Throwable) {
            // Most classes in the jar are unrelated (data classes, helpers)
            // and irrelevant here — but if the REAL MainAPI subclass itself
            // fails to load (e.g. it still imports android.* somewhere),
            // silently skipping it would just print a confusing "not found"
            // at the end. So we keep every failure and only show them if
            // no provider was found at all.
            loadErrors.add(className to e)
        }
    }
    jarFile.close()

    if (found == null && loadErrors.isNotEmpty()) {
        println("Warning: ${loadErrors.size} class(es) in the jar failed to load. " +
                "If your provider class is among these, it likely still references " +
                "an android.* API that has no equivalent on plain JVM:")
        loadErrors.take(10).forEach { (name, e) ->
            println("  - $name: ${e::class.simpleName}: ${e.message}")
        }
    }

    return found
}
