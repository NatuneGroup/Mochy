// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.modules

import com.metrolist.shared.network.createHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.time.Clock

@Serializable
data class WebModuleManifest(
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val description: String = "",
    val author: String? = null,
    val icon: String? = null,
    val banner: String? = null,
    val resources: List<JsonElement> = emptyList(),
    val look: Map<String, JsonPrimitive> = emptyMap(),
    val settings: List<Setting> = emptyList(),
    val url: String? = null,
    val player: JsonElement? = null,
    val font: JsonElement? = null,
    val pages: JsonElement? = null,
    val actions: JsonElement? = null,
    val glass: JsonElement? = null,
    val players: JsonElement? = null,
    val bar: JsonElement? = null,
    val mini: JsonElement? = null,
) {
    @Serializable
    data class Setting(
        val key: String = "",
        val label: String? = null,
        val type: String? = null,
        val default: JsonElement? = null,
        val options: List<JsonElement> = emptyList(),
        val description: String? = null,
        val hint: String? = null,
        val look: Map<String, JsonPrimitive> = emptyMap(),
        val min: Float? = null,
        val max: Float? = null,
        val step: Float? = null,
    ) {
        val defaultValue: String? get() = default.queryValue()

        val values: List<String> get() = options.mapNotNull { optionValue(it) }

        val kind: FieldKind
            get() =
                when (type?.trim()?.lowercase()) {
                    "select", "list", "choice" -> {
                        FieldKind.Select
                    }

                    "toggle", "switch", "bool", "boolean" -> {
                        FieldKind.Toggle
                    }

                    "text", "string", "input", "slider", "range", "number" -> {
                        FieldKind.Text
                    }

                    else -> {
                        when {
                            options.isNotEmpty() -> FieldKind.Select
                            defaultValue?.lowercase() in setOf("true", "false") -> FieldKind.Toggle
                            else -> FieldKind.Text
                        }
                    }
                }

        fun toField(): ModuleField =
            ModuleField(
                id = key,
                label = label?.takeIf { it.isNotBlank() } ?: key.replaceFirstChar { it.uppercase() },
                kind = kind,
                options =
                    options.mapNotNull { option ->
                        val value = optionValue(option) ?: return@mapNotNull null
                        val o = option as? JsonObject
                        StreamOption(value, o?.textField("label") ?: value, o?.textField("description").orEmpty())
                    },
                default =
                    defaultValue ?: when (kind) {
                        FieldKind.Select -> values.firstOrNull().orEmpty()
                        FieldKind.Toggle -> "false"
                        FieldKind.Text -> ""
                    },
                hint = hint.orEmpty(),
                description = description.orEmpty(),
            )

        fun lookAt(value: String?): Map<String, String> =
            when (kind) {
                FieldKind.Toggle -> {
                    if (value?.lowercase() == "true") look.asTextMap() else emptyMap()
                }

                FieldKind.Select -> {
                    options
                        .firstOrNull { optionValue(it) == value }
                        ?.let { opt ->
                            ((opt as? JsonObject)?.get("look") as? JsonObject)
                                ?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }
                                ?.toMap()
                        }.orEmpty()
                }

                FieldKind.Text -> {
                    emptyMap()
                }
            }

        val changesLook: Boolean
            get() =
                look.isNotEmpty() ||
                    options.any { option -> (option as? JsonObject)?.let { it["look"] is JsonObject } == true }

        private fun optionValue(option: JsonElement): String? = (option as? JsonObject)?.get("value").queryValue() ?: option.queryValue()
    }

    val resourceNames: Set<String>
        get() =
            resources
                .mapNotNull { element ->
                    ((element as? JsonPrimitive) ?: ((element as? JsonObject)?.get("name") as? JsonPrimitive))
                        ?.contentOrNull
                        ?.lowercase()
                }.toSet()

    val changesLook: Boolean
        get() =
            look.isNotEmpty() ||
                player != null ||
                players != null ||
                bar != null ||
                mini != null ||
                font != null ||
                pages != null ||
                actions != null ||
                glass != null ||
                settings.any { it.changesLook }

    val playsSongs: Boolean get() = (resourceNames.isEmpty() && !changesLook) || "search" in resourceNames

    val hasLyrics: Boolean get() = "lyrics" in resourceNames

    val hasMetadata: Boolean get() = "metadata" in resourceNames

    val lookValues: Map<String, String> get() = look.asTextMap()

    companion object {
        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        private val siteKeys = listOf("start_url", "display", "short_name", "icons")
        private val moduleKeys = listOf("resources", "look", "player", "players", "bar", "mini", "font", "pages", "actions", "glass")

        fun parse(text: String): WebModuleManifest? =
            runCatching { json.decodeFromString(serializer(), text) }
                .getOrNull()
                ?.takeIf { it.name.isNotBlank() || it.id.isNotBlank() }

        fun isWebsite(text: String): Boolean {
            val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return false
            return moduleKeys.none { it in root } && siteKeys.any { it in root }
        }

        fun baseOf(link: String): String? = normalizeWebBaseUrl(link)

        fun idFor(
            base: String,
            manifest: WebModuleManifest,
        ): String {
            val own = manifest.id.ifBlank { extractHostName(base) }
            return "web." + own.lowercase().replace(Regex("[^a-z0-9._-]"), "-")
        }
    }
}

/** A Mochy web module installed from a URL, implementing search/stream/lyrics/metadata/look over HTTP. */
class WebModule(
    val base: String,
    val manifest: WebModuleManifest,
) : SourceModule,
    LyricsModule,
    LookModule,
    MetadataModule,
    ModuleArt,
    ModuleFields {
    private val siteName = extractHostName(base)
    private lateinit var host: ModuleHost
    private var fieldValues: Map<String, String> = emptyMap()

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    override val info =
        ModuleInfo(
            id = WebModuleManifest.idFor(base, manifest),
            name = manifest.name.ifBlank { siteName },
            version = manifest.version.ifBlank { "1" },
            description = manifest.description.ifBlank { "Web module from $siteName" },
            author = manifest.author,
        )

    override val icon: String? = resolveArt(manifest.icon)
    override val banner: String? = resolveArt(manifest.banner)

    override val adds: Set<ModuleFeature> =
        buildSet {
            if (manifest.playsSongs) add(ModuleFeature.AudioSource)
            if (manifest.hasLyrics) add(ModuleFeature.Lyrics)
            if (manifest.hasMetadata) add(ModuleFeature.Metadata)
            if (manifest.changesLook) add(ModuleFeature.Look)
        }

    override val fields: List<ModuleField> =
        manifest.settings
            .filterNot { manifest.playsSongs && it.key.lowercase() in setOf("quality", "atmos") }
            .map { it.toField() }

    override fun onFields(values: Map<String, String>) {
        fieldValues = values
    }

    override val look: Map<String, String>
        get() {
            val out = LinkedHashMap(manifest.lookValues)
            manifest.settings.forEach { setting ->
                val value = fieldValues[setting.key] ?: setting.defaultValue
                out.putAll(setting.lookAt(value))
            }
            return out
        }

    override val choices: List<ModuleChoice> =
        if (ModuleFeature.AudioSource !in adds) {
            emptyList()
        } else {
            listOf(
                ModuleChoice(
                    id = CHOICE_QUALITY,
                    label = "Stream quality",
                    options =
                        listOf(
                            StreamOption("lossless", "Lossless", "FLAC / CD quality"),
                            StreamOption("hires", "Hi-Res", "24-bit lossless when available"),
                            StreamOption("high", "High", "320 kbps lossy"),
                            StreamOption("low", "Low", "Data-saving stream"),
                        ),
                ),
            )
        }

    override fun attach(host: ModuleHost) {
        this.host = host
    }

    override fun accountState(): AccountState {
        val reason = host.storage.get("unreachable") ?: return AccountState.SignedIn(NO_ACCOUNT_LABEL)
        val markedAt = host.storage.get("unreachable_at")?.toLongOrNull() ?: 0L
        return if (Clock.System.now().toEpochMilliseconds() - markedAt < UNREACHABLE_RETRY_MS) {
            AccountState.Expired(reason)
        } else {
            AccountState.SignedIn(NO_ACCOUNT_LABEL)
        }
    }

    override suspend fun verifyAccount(): AccountState {
        val response = runCatching { host.http(HttpRequest("$base/manifest.json", headers = defaultHeaders)) }.getOrNull()
        return if (response?.ok == true) {
            host.storage.set("unreachable", null)
            AccountState.SignedIn("$NO_ACCOUNT_LABEL · $siteName reachable")
        } else {
            val reason = "$siteName unreachable (${response?.code ?: "offline"})"
            val nowMs = Clock.System.now().toEpochMilliseconds()
            host.storage.set("unreachable", reason)
            host.storage.set("unreachable_at", nowMs.toString())
            AccountState.Expired(reason)
        }
    }

    override suspend fun resolve(
        track: TrackQuery,
        picks: Map<String, String>,
    ): ResolveResult = diagnose(track, picks) {}

    override suspend fun diagnose(
        track: TrackQuery,
        picks: Map<String, String>,
        report: (DiagnosticStep) -> Unit,
    ): ResolveResult {
        if (!manifest.playsSongs) return ResolveResult.NotFound("Module does not provide audio")
        val quality = picks[CHOICE_QUALITY] ?: "lossless"
        val query = "${track.artists.firstOrNull().orEmpty()} ${TrackMatcher.core(track.title)}".trim()
        report(DiagnosticStep("Search", null, "Searching $siteName…"))
        val searchUrl = buildUrl("$base/search", mapOf("q" to query, "quality" to quality) + fieldValues)
        val response =
            runCatching { host.http(HttpRequest(searchUrl, headers = defaultHeaders)) }.getOrElse {
                val reason = "$siteName did not answer"
                report(DiagnosticStep("Search", false, reason))
                return ResolveResult.Unavailable(reason)
            }
        if (!response.ok) {
            val reason = "$siteName returned HTTP ${response.code}"
            report(DiagnosticStep("Search", false, reason))
            return ResolveResult.Unavailable(reason)
        }

        val candidates = parseSearchTracks(response.text)
        val best = TrackMatcher.best(track, candidates.map { it.first })
        if (best == null) {
            report(DiagnosticStep("Search", false, "No matching recording found"))
            return ResolveResult.NotFound("No matching recording on $siteName")
        }
        val matchedPair = candidates.first { it.first === best.track }
        report(DiagnosticStep("Search", true, "${best.track.title} · ${best.track.artist}"))

        val direct = matchedPair.second
        if (!direct.isNullOrBlank()) {
            report(DiagnosticStep("Stream", true, "Direct stream ready"))
            return ResolveResult.Found(
                SourceStream(
                    url = direct,
                    format = formatOf(direct),
                    sourceLabel = info.name,
                    qualityLabel = quality.uppercase(),
                    matched =
                        MatchedTrack(
                            id = best.track.id,
                            title = best.track.title,
                            artist = best.track.artist,
                            album = best.track.album,
                            durationMs = best.track.durationMs,
                            confidence = best.score,
                        ),
                ),
            )
        }

        report(DiagnosticStep("Stream", null, "Requesting stream URL…"))
        val streamUrl = buildUrl("$base/stream", mapOf("id" to best.track.id, "quality" to quality) + fieldValues)
        val streamResp =
            runCatching { host.http(HttpRequest(streamUrl, headers = defaultHeaders)) }.getOrElse {
                report(DiagnosticStep("Stream", false, "$siteName stream failed"))
                return ResolveResult.Unavailable("$siteName stream failed")
            }
        if (!streamResp.ok) {
            report(DiagnosticStep("Stream", false, "$siteName stream HTTP ${streamResp.code}"))
            return ResolveResult.Unavailable("HTTP ${streamResp.code}")
        }
        val obj = runCatching { json.parseToJsonElement(streamResp.text) as? JsonObject }.getOrNull()
        val mediaUrl = obj?.textField("url") ?: obj?.textField("streamUrl") ?: obj?.textField("stream_url")
        if (mediaUrl.isNullOrBlank()) {
            report(DiagnosticStep("Stream", false, "No stream URL in response"))
            return ResolveResult.Unavailable("No stream URL in response")
        }
        val codec = obj?.textField("codec")
        val qualityLabel = obj?.textField("quality") ?: quality.uppercase()
        report(DiagnosticStep("Stream", true, qualityLabel))
        return ResolveResult.Found(
            SourceStream(
                url = mediaUrl,
                format = formatOf(mediaUrl),
                sourceLabel = info.name,
                qualityLabel = qualityLabel,
                codec = codec,
                matched =
                    MatchedTrack(
                        id = best.track.id,
                        title = best.track.title,
                        artist = best.track.artist,
                        album = best.track.album,
                        durationMs = best.track.durationMs,
                        confidence = best.score,
                    ),
            ),
        )
    }

    override suspend fun fetchLyrics(track: TrackQuery): String? {
        if (!manifest.hasLyrics) return null
        val url =
            buildUrl(
                "$base/lyrics",
                mapOf("title" to track.title, "artist" to track.artists.firstOrNull().orEmpty()) + fieldValues,
            )
        val resp = runCatching { host.http(HttpRequest(url, headers = defaultHeaders)) }.getOrNull() ?: return null
        if (!resp.ok) return null
        val obj = runCatching { json.parseToJsonElement(resp.text) as? JsonObject }.getOrNull()
        return obj?.textField("syncedLyrics") ?: obj?.textField("lyrics") ?: resp.text.takeIf { it.isNotBlank() }
    }

    override suspend fun fetchMetadata(track: TrackQuery): TrackMetadata? {
        if (!manifest.hasMetadata) return null
        val url =
            buildUrl(
                "$base/metadata",
                mapOf("title" to track.title, "artist" to track.artists.firstOrNull().orEmpty()) + fieldValues,
            )
        val resp = runCatching { host.http(HttpRequest(url, headers = defaultHeaders)) }.getOrNull() ?: return null
        if (!resp.ok) return null
        val obj = runCatching { json.parseToJsonElement(resp.text) as? JsonObject }.getOrNull() ?: return null
        return TrackMetadata(
            title = obj.textField("title"),
            artist = obj.textField("artist"),
            album = obj.textField("album"),
            artworkUrl = obj.textField("artwork") ?: obj.textField("cover"),
            isrc = obj.textField("isrc"),
        )
    }

    private fun parseSearchTracks(text: String): List<Pair<CatalogTrack, String?>> {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        val array =
            when (root) {
                is JsonArray -> {
                    root
                }

                is JsonObject -> {
                    (root["tracks"] as? JsonArray)
                        ?: (root["results"] as? JsonArray)
                        ?: (root["items"] as? JsonArray)
                        ?: (root["data"] as? JsonArray)
                }

                else -> {
                    null
                }
            } ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.textField("id") ?: return@mapNotNull null
            val title = obj.textField("title") ?: obj.textField("name") ?: return@mapNotNull null
            val artist = obj.textField("artist") ?: obj.textField("artists") ?: return@mapNotNull null
            val durSec = (obj["duration"] as? JsonPrimitive)?.doubleOrNull
            val durationMs =
                (obj["durationMs"] as? JsonPrimitive)?.doubleOrNull?.toLong()
                    ?: durSec?.let { if (it > 10_000) it.toLong() else (it * 1000.0).toLong() }
            CatalogTrack(
                id = id,
                title = title,
                artist = artist,
                album = obj.textField("album"),
                durationMs = durationMs,
                isrc = obj.textField("isrc"),
            ) to (obj.textField("stream") ?: obj.textField("url"))
        }
    }

    private fun resolveArt(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "$base/${trimmed.trimStart('/')}"
        }
    }

    private fun buildUrl(
        endpoint: String,
        params: Map<String, String>,
    ): String {
        val query =
            params.entries
                .filter { it.value.isNotBlank() }
                .joinToString("&") { (k, v) -> "${urlEncodeComponent(k)}=${urlEncodeComponent(v)}" }
        return if (query.isEmpty()) endpoint else "$endpoint?$query"
    }

    private fun formatOf(url: String): StreamFormat =
        when {
            ".m3u8" in url.lowercase() -> StreamFormat.Hls
            ".mpd" in url.lowercase() -> StreamFormat.Dash
            else -> StreamFormat.Progressive
        }

    companion object {
        const val CHOICE_QUALITY = "quality"
        private const val UNREACHABLE_RETRY_MS = 10L * 60 * 1000
        private val defaultHeaders = mapOf("Accept" to "application/json")
    }
}

data class CatalogModule(
    val name: String,
    val description: String,
    val url: String,
    val adds: Set<ModuleFeature>,
    val author: String?,
    val icon: String? = null,
)

/** Loads the remote Mochy module catalog (`catalog.json`) across Android, Desktop, and iOS. */
class ModuleCatalog(
    private val client: HttpClient = createHttpClient(),
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    suspend fun load(catalogUrl: String = DEFAULT_CATALOG_URL): Result<List<CatalogModule>> =
        runCatching {
            val response = client.get(catalogUrl)
            if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
            val root = json.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: return@runCatching emptyList()
            val array = root["modules"] as? JsonArray ?: return@runCatching emptyList()
            array.mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                val url = item.textField("url") ?: return@mapNotNull null
                val adds =
                    (item["adds"] as? JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let(::featureOf) }
                        ?.toSet()
                        .orEmpty()
                CatalogModule(
                    name = item.textField("name") ?: url,
                    description = item.textField("description").orEmpty(),
                    url = url,
                    adds = adds,
                    author = item.textField("author"),
                    icon = item.textField("icon")?.takeIf { it.startsWith("https://") },
                )
            }
        }

    private fun featureOf(name: String): ModuleFeature? =
        when (name.lowercase()) {
            "audio", "source", "songs" -> ModuleFeature.AudioSource
            "lyrics" -> ModuleFeature.Lyrics
            "look", "theme" -> ModuleFeature.Look
            "metadata", "info" -> ModuleFeature.Metadata
            else -> null
        }

    companion object {
        const val DEFAULT_CATALOG_URL =
            "https://raw.githubusercontent.com/realidkroo/Mochy-Music/main/modules/catalog.json"
    }
}

private fun JsonElement?.queryValue(): String? {
    val primitive = (this as? JsonPrimitive)?.takeIf { it !is JsonNull } ?: return null
    return primitive.content.takeIf { it.isNotBlank() }
}

private fun JsonObject.textField(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun Map<String, JsonPrimitive>.asTextMap(): Map<String, String> =
    mapNotNull { (key, value) -> value.contentOrNull?.let { key to it } }.toMap()
