// Mochy-style SourceModule audio providers (ArchivePool, Monochrome, Qobuz, TIDAL, Deezer).
// Ported from Mochy Music (realidkroo) and Natune provider protocols (GPL-3.0-or-later).

package com.metrolist.shared.modules

import com.metrolist.shared.utils.md5Hex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock

private val providerJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.numInt(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

private fun JsonObject.numLong(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

private fun JsonObject.numDouble(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

private fun queryUrl(
    base: String,
    path: String,
    params: Map<String, String>,
): String {
    val encoded =
        params.entries
            .filter { it.value.isNotBlank() }
            .joinToString("&") { (k, v) -> "${urlEncodeComponent(k)}=${urlEncodeComponent(v)}" }
    return if (encoded.isEmpty()) "$base$path" else "$base$path?$encoded"
}

/** Represents Metrolist's built-in YouTube Music playback engine inside Mochy's module list. */
class YouTubeBuiltInModule : SourceModule {
    override val info =
        ModuleInfo(
            id = ID,
            name = "YouTube Music",
            version = "Built-in",
            description = "Native InnerTube catalog and playback engine bundled with Mochy",
        )

    override fun attach(host: ModuleHost) = Unit

    override suspend fun resolve(
        track: TrackQuery,
        picks: Map<String, String>,
    ): ResolveResult = ResolveResult.NotFound("Handled by native InnerTube resolver")

    override suspend fun diagnose(
        track: TrackQuery,
        picks: Map<String, String>,
        report: (DiagnosticStep) -> Unit,
    ): ResolveResult {
        report(DiagnosticStep("InnerTube engine", true, "Active native playback source"))
        return ResolveResult.NotFound("Handled directly by native InnerTube playback")
    }

    companion object {
        const val ID = "youtube"
    }
}

/**
 * Mochy module implementing Natune's ArchivePool v2 protocol (`GET /api/accounts` with
 * `X-Pool-Client: v2`, ETag/cooldown handling, and SHA-256-derived AES-256-GCM decryption
 * of `enc:1:` TIDAL and Qobuz leased credentials). Never contributes or reports accounts.
 */
class ArchivePoolModule :
    SourceModule,
    ModuleFields {
    override val info =
        ModuleInfo(
            id = ID,
            name = "ArchivePool",
            version = "2.0",
            description = "Leases encrypted TIDAL and Qobuz accounts from your ArchivePool deployment via GET /api/accounts v2",
            sourceUrl = "https://github.com/NatuneGroup/Natune",
            author = "Natune contributors",
        )

    override val fields: List<ModuleField> =
        listOf(
            ModuleField(
                id = FIELD_POOL_URL,
                label = "Pool URL",
                kind = FieldKind.Text,
                default = DEFAULT_POOL_URL,
                hint = DEFAULT_POOL_URL,
                description = "Your ArchivePool deployment base URL",
            ),
            ModuleField(
                id = FIELD_READ_KEY,
                label = "Read key",
                kind = FieldKind.Text,
                hint = "Pool read key",
                description = "Your ArchivePool read key (never an administrator secret)",
                secret = true,
            ),
            ModuleField(
                id = FIELD_SOURCE,
                label = "Preferred source",
                kind = FieldKind.Select,
                default = "tidal",
                options =
                    listOf(
                        StreamOption("tidal", "TIDAL first", "Try leased TIDAL accounts before Qobuz"),
                        StreamOption("qobuz", "Qobuz first", "Try leased Qobuz accounts before TIDAL"),
                    ),
            ),
        )

    override val choices: List<ModuleChoice> =
        listOf(
            ModuleChoice(
                id = CHOICE_QUALITY,
                label = "Audio quality",
                options =
                    listOf(
                        StreamOption("lossless", "Lossless", "16-bit FLAC"),
                        StreamOption("hires", "Hi-Res", "24-bit FLAC up to 192 kHz"),
                        StreamOption("lossy", "Lossy", "320 kbps AAC/MP3"),
                    ),
            ),
        )

    private data class LeasedTidal(
        val id: String,
        val token: String,
        val countryCode: String,
    )

    private data class LeasedQobuz(
        val id: String,
        val token: String,
        val appId: String,
        val appSecret: String,
    )

    private data class PoolLease(
        val identity: String,
        val tidal: List<LeasedTidal>,
        val qobuz: List<LeasedQobuz>,
        val expiresAtMs: Long,
        val etag: String?,
    )

    private lateinit var host: ModuleHost
    private var values: Map<String, String> = emptyMap()
    private var leaseCache: PoolLease? = null
    private var retryAtMs: Long = 0L

    override fun attach(host: ModuleHost) {
        this.host = host
    }

    override fun onFields(values: Map<String, String>) {
        this.values = values
        leaseCache = null
    }

    override fun accountState(): AccountState {
        val readKey = values[FIELD_READ_KEY]?.trim().orEmpty()
        return if (readKey.isEmpty()) {
            AccountState.SignedOut
        } else {
            AccountState.SignedIn("Read key configured")
        }
    }

    override suspend fun verifyAccount(): AccountState =
        runCatching {
            val lease = fetchLease()
            AccountState.SignedIn("${lease.tidal.size} TIDAL · ${lease.qobuz.size} Qobuz leases")
        }.getOrElse { err ->
            AccountState.Expired(err.message ?: "ArchivePool lease check failed")
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
        report(DiagnosticStep("ArchivePool lease", null, "Requesting v2 account lease…"))
        val lease =
            runCatching { fetchLease() }.getOrElse { err ->
                val reason = err.message ?: "ArchivePool unavailable"
                report(DiagnosticStep("ArchivePool lease", false, reason))
                return ResolveResult.Unavailable(reason)
            }
        report(
            DiagnosticStep(
                "ArchivePool lease",
                true,
                "${lease.tidal.size} TIDAL, ${lease.qobuz.size} Qobuz accounts decrypted",
            ),
        )

        val quality = picks[CHOICE_QUALITY] ?: "lossless"
        val preferQobuz = values[FIELD_SOURCE] == "qobuz"
        val order = if (preferQobuz) listOf("qobuz", "tidal") else listOf("tidal", "qobuz")

        var lastFailure: ResolveResult = ResolveResult.NotFound("Track not found on ArchivePool")
        for (service in order) {
            if (service == "tidal") {
                for (acct in lease.tidal.take(4)) {
                    val res =
                        TidalApiHelper.resolveTrack(
                            host = host,
                            track = track,
                            accessToken = acct.token,
                            countryCode = acct.countryCode.ifBlank { "US" },
                            quality =
                                when (quality) {
                                    "hires" -> "HI_RES_LOSSLESS"
                                    "lossy" -> "HIGH"
                                    else -> "LOSSLESS"
                                },
                            sourceLabel = "ArchivePool / TIDAL",
                            report = report,
                        )
                    if (res is ResolveResult.Found) return res
                    lastFailure = res
                }
            } else {
                for (acct in lease.qobuz.take(4)) {
                    val res =
                        QobuzApiHelper.resolveTrack(
                            host = host,
                            track = track,
                            appId = acct.appId,
                            appSecret = acct.appSecret,
                            userAuthToken = acct.token,
                            formatId =
                                when (quality) {
                                    "hires" -> 27
                                    "lossy" -> 5
                                    else -> 6
                                },
                            sourceLabel = "ArchivePool / Qobuz",
                            report = report,
                        )
                    if (res is ResolveResult.Found) return res
                    lastFailure = res
                }
            }
        }
        return lastFailure
    }

    private suspend fun fetchLease(): PoolLease {
        val poolUrl =
            normalizeWebBaseUrl(values[FIELD_POOL_URL]?.ifBlank { DEFAULT_POOL_URL } ?: DEFAULT_POOL_URL)
                ?: error("Configure a valid ArchivePool URL")
        val readKey = values[FIELD_READ_KEY]?.trim().orEmpty()
        require(readKey.isNotEmpty()) { "Configure your ArchivePool read key" }

        val now = Clock.System.now().toEpochMilliseconds()
        val identity = "$poolUrl\n$readKey"
        if (leaseCache?.identity != identity) {
            leaseCache = null
            retryAtMs = 0L
        }
        if (now < retryAtMs) error("ArchivePool is cooling down after a rate limit")
        leaseCache?.takeIf { now < it.expiresAtMs }?.let { return it }

        val headers =
            buildMap {
                put("Authorization", "Bearer $readKey")
                put("X-Pool-Client", "v2")
                put("Accept", "application/json")
                leaseCache?.etag?.let { put("If-None-Match", it) }
            }
        val response = host.http(HttpRequest("$poolUrl/api/accounts", headers = headers))
        if (response.code in setOf(401, 403, 429, 503)) {
            val retrySec = response.header("retry-after")?.toLongOrNull() ?: 60L
            retryAtMs = now + (retrySec.coerceIn(1L, 21_600L) * 1000L)
            error("ArchivePool refused lease (HTTP ${response.code}); check your read key")
        }
        if (response.code == 304 && leaseCache != null) {
            return leaseCache!!.copy(expiresAtMs = now + LEASE_TTL_MS).also { leaseCache = it }
        }
        if (!response.ok) error("ArchivePool failed with HTTP ${response.code}")

        val root = providerJson.parseToJsonElement(response.text) as? JsonObject ?: error("Unreadable ArchivePool JSON")
        val key = ArchivePoolCrypto.derivePoolKey(readKey)

        val tidalRows = extractServiceRows(root, "tidal")
        val tidal =
            tidalRows.take(64).mapIndexedNotNull { idx, row ->
                val token = ArchivePoolCrypto.decryptField(row.str("token"), key) ?: return@mapIndexedNotNull null
                val cc = ArchivePoolCrypto.decryptField(row.str("countryCode"), key).orEmpty()
                LeasedTidal(id = row.str("id") ?: idx.toString(), token = token, countryCode = cc)
            }

        val qobuzRows = extractServiceRows(root, "qobuz")
        val qobuz =
            qobuzRows.take(64).mapIndexedNotNull { idx, row ->
                val token = ArchivePoolCrypto.decryptField(row.str("token"), key) ?: return@mapIndexedNotNull null
                val appId = ArchivePoolCrypto.decryptField(row.str("appId"), key) ?: return@mapIndexedNotNull null
                val appSecret = ArchivePoolCrypto.decryptField(row.str("appSecret"), key) ?: return@mapIndexedNotNull null
                LeasedQobuz(id = row.str("id") ?: idx.toString(), token = token, appId = appId, appSecret = appSecret)
            }

        if (tidal.isEmpty() && qobuz.isEmpty()) error("ArchivePool returned no usable TIDAL or Qobuz accounts")
        return PoolLease(
            identity = identity,
            tidal = tidal,
            qobuz = qobuz,
            expiresAtMs = now + LEASE_TTL_MS,
            etag = response.header("etag"),
        ).also { leaseCache = it }
    }

    private fun extractServiceRows(
        root: JsonObject,
        service: String,
    ): List<JsonObject> {
        val raw = root[service] ?: return emptyList()
        val array =
            when (raw) {
                is JsonArray -> raw
                is JsonObject -> raw["accounts"] as? JsonArray
                else -> null
            } ?: return emptyList()
        return array.mapNotNull { it as? JsonObject }
    }

    companion object {
        const val ID = "archivepool"
        const val DEFAULT_POOL_URL = "https://archivepool.natune.workers.dev"
        const val FIELD_POOL_URL = "poolUrl"
        const val FIELD_READ_KEY = "readKey"
        const val FIELD_SOURCE = "source"
        const val CHOICE_QUALITY = "quality"
        private const val LEASE_TTL_MS = 30L * 60 * 1000
    }
}

/** Mochy's built-in Monochrome lossless FLAC module (no account needed). */
class MonochromeModule :
    SourceModule,
    ModuleFields {
    override val info =
        ModuleInfo(
            id = ID,
            name = "Monochrome",
            version = "2.1",
            description = "Lossless FLAC playback from Monochrome track streamers without requiring an account",
            sourceUrl = "https://github.com/monochrome-music/monochrome",
            author = "Mochy Music",
        )

    override val fields: List<ModuleField> =
        listOf(
            ModuleField(
                id = FIELD_SERVER_URL,
                label = "Custom server URL",
                kind = FieldKind.Text,
                default = DEFAULT_SERVER,
                hint = DEFAULT_SERVER,
                description = "Monochrome track streamer base URL",
            ),
        )

    private lateinit var host: ModuleHost
    private var values: Map<String, String> = emptyMap()

    override fun attach(host: ModuleHost) {
        this.host = host
    }

    override fun onFields(values: Map<String, String>) {
        this.values = values
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
        val base = normalizeWebBaseUrl(values[FIELD_SERVER_URL]?.ifBlank { DEFAULT_SERVER } ?: DEFAULT_SERVER) ?: DEFAULT_SERVER
        val q = "${track.artists.firstOrNull().orEmpty()} ${TrackMatcher.core(track.title)}".trim()
        report(DiagnosticStep("Search", null, "Searching ${extractHostName(base)}…"))
        val searchUrl = queryUrl(base, "/search", mapOf("q" to q, "limit" to "10"))
        val resp =
            runCatching { host.http(HttpRequest(searchUrl, headers = mapOf("Accept" to "application/json"))) }.getOrElse {
                report(DiagnosticStep("Search", false, "Server did not respond"))
                return ResolveResult.Unavailable("Monochrome server unavailable")
            }
        if (!resp.ok) {
            report(DiagnosticStep("Search", false, "HTTP ${resp.code}"))
            return ResolveResult.Unavailable("HTTP ${resp.code}")
        }
        val root = runCatching { providerJson.parseToJsonElement(resp.text) as? JsonObject }.getOrNull()
        val arr = (root?.get("tracks") as? JsonArray) ?: (root?.get("data") as? JsonArray) ?: JsonArray(emptyList())
        val candidates =
            arr.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val id = o.str("id") ?: return@mapNotNull null
                val title = o.str("title") ?: return@mapNotNull null
                val artist = o.str("artist") ?: (o["artist"] as? JsonObject)?.str("name") ?: return@mapNotNull null
                val dur = o.numLong("duration")?.let { if (it > 10_000) it else it * 1000L }
                CatalogTrack(id = id, title = title, artist = artist, album = o.str("album"), durationMs = dur, isrc = o.str("isrc"))
            }
        val best = TrackMatcher.best(track, candidates)
        if (best == null) {
            report(DiagnosticStep("Search", false, "No matching track found"))
            return ResolveResult.NotFound("Not on Monochrome")
        }
        report(DiagnosticStep("Search", true, "${best.track.title} · ${best.track.artist}"))

        val streamUrl = "$base/track/${urlEncodeComponent(best.track.id)}"
        report(DiagnosticStep("Stream", null, "Probing FLAC header…"))
        val head =
            runCatching {
                host.http(HttpRequest(streamUrl, headers = mapOf("Range" to "bytes=0-63")))
            }.getOrNull()
        val flac = head?.body?.let(::parseFlacHeader)
        val qualityLabel = flac?.label ?: "FLAC"
        report(DiagnosticStep("Stream", head?.ok == true, qualityLabel))
        return ResolveResult.Found(
            SourceStream(
                url = streamUrl,
                format = StreamFormat.Progressive,
                sourceLabel = "Monochrome",
                qualityLabel = qualityLabel,
                codec = "FLAC",
                sampleRateHz = flac?.sampleRate,
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

    private data class FlacInfo(
        val bitsPerSample: Int,
        val sampleRate: Int,
    ) {
        val label: String
            get() {
                val khz = if (sampleRate % 1000 == 0) "${sampleRate / 1000}" else "${sampleRate / 1000.0}"
                val prefix = if (bitsPerSample > 16 || sampleRate > 48_000) "Hi-Res FLAC" else "FLAC"
                return "$prefix · $bitsPerSample-bit / $khz kHz"
            }
    }

    private fun parseFlacHeader(bytes: ByteArray): FlacInfo? {
        if (bytes.size < 4 || bytes.copyOfRange(0, 4).decodeToString() != "fLaC") return null
        if (bytes.size < 26) return FlacInfo(16, 44_100)
        val b = IntArray(18) { bytes[8 + it].toInt() and 0xFF }
        val sampleRate = (b[10] shl 12) or (b[11] shl 4) or (b[12] ushr 4)
        val bits = (((b[12] and 0x01) shl 4) or (b[13] ushr 4)) + 1
        return if (sampleRate in 8_000..768_000 && bits in 8..32) FlacInfo(bits, sampleRate) else FlacInfo(16, 44_100)
    }

    companion object {
        const val ID = "monochrome"
        const val FIELD_SERVER_URL = "serverUrl"
        const val DEFAULT_SERVER = "https://tracks.monochrome.st"
    }
}

/** Community Qobuz instance module (`/api/get-music` & `/api/download-music`), no account required. */
class QobuzInstanceModule :
    SourceModule,
    ModuleFields {
    override val info =
        ModuleInfo(
            id = ID,
            name = "Qobuz Instance",
            version = "1.0",
            description = "Connects to a community Qobuz instance using /api/get-music and /api/download-music",
            sourceUrl = "https://github.com/NatuneGroup/Natune",
            author = "Natune contributors",
        )

    override val fields: List<ModuleField> =
        listOf(
            ModuleField(
                id = FIELD_INSTANCE_URL,
                label = "Instance URL",
                kind = FieldKind.Text,
                hint = "https://qobuz.example.com",
                description = "Base HTTPS URL of your community Qobuz instance",
            ),
        )

    override val choices: List<ModuleChoice> =
        listOf(
            ModuleChoice(
                id = CHOICE_QUALITY,
                label = "Format",
                options =
                    listOf(
                        StreamOption("6", "FLAC 16-bit", "CD lossless"),
                        StreamOption("27", "Hi-Res 24/192", "Studio master FLAC"),
                        StreamOption("7", "Hi-Res 24/96", "24-bit up to 96 kHz"),
                        StreamOption("5", "MP3 320", "320 kbps lossy"),
                    ),
            ),
        )

    private lateinit var host: ModuleHost
    private var values: Map<String, String> = emptyMap()

    override fun attach(host: ModuleHost) {
        this.host = host
    }

    override fun onFields(values: Map<String, String>) {
        this.values = values
    }

    override fun accountState(): AccountState =
        if (normalizeWebBaseUrl(values[FIELD_INSTANCE_URL].orEmpty()) == null) {
            AccountState.SignedOut
        } else {
            AccountState.SignedIn("Instance configured")
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
        val base =
            normalizeWebBaseUrl(values[FIELD_INSTANCE_URL].orEmpty())
                ?: return ResolveResult.Unavailable("Configure a Qobuz instance URL")
        val q = "${track.artists.firstOrNull().orEmpty()} ${TrackMatcher.core(track.title)}".trim()
        report(DiagnosticStep("Search", null, "Searching ${extractHostName(base)}…"))
        val resp =
            runCatching { host.http(HttpRequest(queryUrl(base, "/api/get-music", mapOf("q" to q, "offset" to "0")))) }.getOrElse {
                report(DiagnosticStep("Search", false, "Instance unreachable"))
                return ResolveResult.Unavailable("Instance unreachable")
            }
        if (!resp.ok) {
            report(DiagnosticStep("Search", false, "HTTP ${resp.code}"))
            return ResolveResult.Unavailable("HTTP ${resp.code}")
        }
        val root = runCatching { providerJson.parseToJsonElement(resp.text) as? JsonObject }.getOrNull()
        val items = extractTrackArray(root)
        val candidates =
            items.mapNotNull { item ->
                val id = item.str("id") ?: item.str("track_id") ?: return@mapNotNull null
                val title = item.str("title") ?: item.str("name") ?: return@mapNotNull null
                val artist =
                    (item["performer"] as? JsonObject)?.str("name")
                        ?: (item["artist"] as? JsonObject)?.str("name")
                        ?: item.str("artist")
                        ?: return@mapNotNull null
                val dur = item.numLong("duration")?.let { it * 1000L }
                CatalogTrack(id = id, title = title, artist = artist, durationMs = dur, isrc = item.str("isrc"))
            }
        val best = TrackMatcher.best(track, candidates)
        if (best == null) {
            report(DiagnosticStep("Search", false, "No match found"))
            return ResolveResult.NotFound("No match on Qobuz instance")
        }
        report(DiagnosticStep("Search", true, "${best.track.title} · ${best.track.artist}"))

        val quality = picks[CHOICE_QUALITY] ?: "6"
        report(DiagnosticStep("Stream", null, "Requesting format $quality…"))
        val dlResp =
            runCatching {
                host.http(HttpRequest(queryUrl(base, "/api/download-music", mapOf("track_id" to best.track.id, "quality" to quality))))
            }.getOrElse {
                report(DiagnosticStep("Stream", false, "Download endpoint failed"))
                return ResolveResult.Unavailable("Download endpoint failed")
            }
        val dlRoot = runCatching { providerJson.parseToJsonElement(dlResp.text) as? JsonObject }.getOrNull()
        val data = (dlRoot?.get("data") as? JsonObject) ?: (dlRoot?.get("result") as? JsonObject) ?: dlRoot
        val mediaUrl =
            data?.str("url")
                ?: data?.str("downloadUrl")
                ?: data?.str("download_url")
                ?: data?.str("stream_url")
                ?: data?.str("link")
        if (mediaUrl.isNullOrBlank()) {
            report(DiagnosticStep("Stream", false, "No direct stream URL"))
            return ResolveResult.Unavailable("Instance returned no stream URL")
        }
        val codec = if (quality == "5") "MP3" else "FLAC"
        report(DiagnosticStep("Stream", true, codec))
        return ResolveResult.Found(
            SourceStream(
                url = mediaUrl,
                format = StreamFormat.Progressive,
                sourceLabel = "Qobuz Instance",
                qualityLabel = codec,
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

    companion object {
        const val ID = "qobuz-instance"
        const val FIELD_INSTANCE_URL = "instanceUrl"
        const val CHOICE_QUALITY = "quality"
    }
}

/** Community TIDAL instance module (`/search/` & `/track/`), supporting BTS and unprotected DASH. */
class TidalInstanceModule :
    SourceModule,
    ModuleFields {
    override val info =
        ModuleInfo(
            id = ID,
            name = "TIDAL Instance",
            version = "1.0",
            description = "Connects to a community TIDAL instance using /search/ and /track/ endpoints",
            sourceUrl = "https://github.com/NatuneGroup/Natune",
            author = "Natune contributors",
        )

    override val fields: List<ModuleField> =
        listOf(
            ModuleField(
                id = FIELD_INSTANCE_URL,
                label = "Instance URL",
                kind = FieldKind.Text,
                hint = "https://tidal.example.com",
                description = "Base HTTPS URL of your community TIDAL instance",
            ),
        )

    override val choices: List<ModuleChoice> =
        listOf(
            ModuleChoice(
                id = CHOICE_QUALITY,
                label = "Quality",
                options =
                    listOf(
                        StreamOption("LOSSLESS", "Lossless", "16-bit FLAC"),
                        StreamOption("HI_RES_LOSSLESS", "Hi-Res FLAC", "24-bit lossless"),
                        StreamOption("HIGH", "High", "320 kbps AAC"),
                    ),
            ),
        )

    private lateinit var host: ModuleHost
    private var values: Map<String, String> = emptyMap()

    override fun attach(host: ModuleHost) {
        this.host = host
    }

    override fun onFields(values: Map<String, String>) {
        this.values = values
    }

    override fun accountState(): AccountState =
        if (normalizeWebBaseUrl(values[FIELD_INSTANCE_URL].orEmpty()) == null) {
            AccountState.SignedOut
        } else {
            AccountState.SignedIn("Instance configured")
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
        val base =
            normalizeWebBaseUrl(values[FIELD_INSTANCE_URL].orEmpty())
                ?: return ResolveResult.Unavailable("Configure a TIDAL instance URL")
        val q = "${track.artists.firstOrNull().orEmpty()} ${TrackMatcher.core(track.title)}".trim()
        report(DiagnosticStep("Search", null, "Searching ${extractHostName(base)}…"))
        val resp =
            runCatching {
                host.http(HttpRequest(queryUrl(base, "/search/", mapOf("s" to q, "q" to q, "limit" to "10"))))
            }.getOrElse {
                report(DiagnosticStep("Search", false, "Instance unreachable"))
                return ResolveResult.Unavailable("Instance unreachable")
            }
        if (!resp.ok) {
            report(DiagnosticStep("Search", false, "HTTP ${resp.code}"))
            return ResolveResult.Unavailable("HTTP ${resp.code}")
        }
        val root = runCatching { providerJson.parseToJsonElement(resp.text) as? JsonObject }.getOrNull()
        val items = extractTrackArray(root)
        val candidates =
            items.mapNotNull { v ->
                val id = v.str("id") ?: v.str("trackId") ?: return@mapNotNull null
                val title = v.str("title") ?: return@mapNotNull null
                val artist =
                    (v["artists"] as? JsonArray)
                        ?.mapNotNull { (it as? JsonObject)?.str("name") }
                        ?.joinToString(", ")
                        ?.takeIf { it.isNotBlank() }
                        ?: (v["artist"] as? JsonObject)?.str("name")
                        ?: return@mapNotNull null
                val dur = v.numLong("duration")?.let { it * 1000L }
                CatalogTrack(id = id, title = title, artist = artist, durationMs = dur, isrc = v.str("isrc"))
            }
        val best = TrackMatcher.best(track, candidates)
        if (best == null) {
            report(DiagnosticStep("Search", false, "No match found"))
            return ResolveResult.NotFound("No match on TIDAL instance")
        }
        report(DiagnosticStep("Search", true, "${best.track.title} · ${best.track.artist}"))

        val quality = picks[CHOICE_QUALITY] ?: "LOSSLESS"
        report(DiagnosticStep("Stream", null, "Fetching $quality manifest…"))
        val streamEndpoint = queryUrl(base, "/track/", mapOf("id" to best.track.id, "quality" to quality))
        val trResp =
            runCatching { host.http(HttpRequest(streamEndpoint)) }.getOrElse {
                report(DiagnosticStep("Stream", false, "Track endpoint failed"))
                return ResolveResult.Unavailable("Track endpoint failed")
            }
        val trRoot = runCatching { providerJson.parseToJsonElement(trResp.text) as? JsonObject }.getOrNull()
        val infoObj = (trRoot?.get("data") as? JsonObject) ?: trRoot
        val directUrl = infoObj?.str("url") ?: infoObj?.str("stream_url")
        if (!directUrl.isNullOrBlank()) {
            report(DiagnosticStep("Stream", true, quality))
            return ResolveResult.Found(
                SourceStream(
                    url = directUrl,
                    format = StreamFormat.Progressive,
                    sourceLabel = "TIDAL Instance",
                    qualityLabel = quality,
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
        val manifestB64 = infoObj?.str("manifest") ?: return ResolveResult.Unavailable("No manifest returned")
        val decoded = decodeBase64Flex(manifestB64).decodeToString()
        val mime = infoObj.str("manifestMimeType").orEmpty()
        if (mime == "application/dash+xml") {
            if ("ContentProtection" in decoded) return ResolveResult.Unavailable("Protected DASH not supported")
            report(DiagnosticStep("Stream", true, "$quality DASH"))
            return ResolveResult.Found(
                SourceStream(
                    url = streamEndpoint,
                    format = StreamFormat.Dash,
                    sourceLabel = "TIDAL Instance",
                    qualityLabel = quality,
                    inlineManifest = decoded,
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
        val bts = runCatching { providerJson.parseToJsonElement(decoded) as? JsonObject }.getOrNull()
        val mediaUrl =
            (bts?.get("urls") as? JsonArray)
                ?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { u -> u.startsWith("http") } }
                ?: return ResolveResult.Unavailable("BTS manifest has no stream URL")
        val codec = bts?.str("codecs")?.uppercase() ?: "FLAC"
        report(DiagnosticStep("Stream", true, "$quality ($codec)"))
        return ResolveResult.Found(
            SourceStream(
                url = mediaUrl,
                format = StreamFormat.Progressive,
                sourceLabel = "TIDAL Instance",
                qualityLabel = quality,
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

    companion object {
        const val ID = "tidal-instance"
        const val FIELD_INSTANCE_URL = "instanceUrl"
        const val CHOICE_QUALITY = "quality"
    }
}

/** Deezer public catalog + plaintext instance resolver with bounded byte-range FLAC/MP3 probe. */
class DeezerInstanceModule :
    SourceModule,
    ModuleFields {
    override val info =
        ModuleInfo(
            id = ID,
            name = "Deezer Instance",
            version = "1.0",
            description = "Matches tracks via Deezer public catalog and streams plaintext FLAC/MP3 from your configured instance",
            sourceUrl = "https://github.com/NatuneGroup/Natune",
            author = "Natune contributors",
        )

    override val fields: List<ModuleField> =
        listOf(
            ModuleField(
                id = FIELD_INSTANCE_URL,
                label = "Instance URL",
                kind = FieldKind.Text,
                hint = "https://deezer.example.com",
                description = "Base HTTPS URL of your Deezer streaming instance",
            ),
            ModuleField(
                id = FIELD_INSTANCE_TOKEN,
                label = "Bearer token (optional)",
                kind = FieldKind.Text,
                hint = "Optional Authorization token",
                secret = true,
            ),
        )

    override val choices: List<ModuleChoice> =
        listOf(
            ModuleChoice(
                id = CHOICE_QUALITY,
                label = "Quality",
                options =
                    listOf(
                        StreamOption("FLAC", "FLAC", "Lossless 16-bit FLAC"),
                        StreamOption("MP3", "MP3 320", "320 kbps MP3"),
                    ),
            ),
        )

    private lateinit var host: ModuleHost
    private var values: Map<String, String> = emptyMap()

    override fun attach(host: ModuleHost) {
        this.host = host
    }

    override fun onFields(values: Map<String, String>) {
        this.values = values
    }

    override fun accountState(): AccountState =
        if (normalizeWebBaseUrl(values[FIELD_INSTANCE_URL].orEmpty()) == null) {
            AccountState.SignedOut
        } else {
            AccountState.SignedIn("Instance configured")
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
        val base =
            normalizeWebBaseUrl(values[FIELD_INSTANCE_URL].orEmpty())
                ?: return ResolveResult.Unavailable("Configure a Deezer instance URL")
        val q = "${track.artists.firstOrNull().orEmpty()} ${TrackMatcher.core(track.title)}".trim()
        report(DiagnosticStep("Deezer catalog", null, "Searching api.deezer.com…"))
        val searchResp =
            runCatching {
                host.http(HttpRequest(queryUrl("https://api.deezer.com", "/search/track", mapOf("q" to q, "limit" to "10"))))
            }.getOrElse {
                report(DiagnosticStep("Deezer catalog", false, "Deezer API unreachable"))
                return ResolveResult.Unavailable("Deezer API unreachable")
            }
        val root = runCatching { providerJson.parseToJsonElement(searchResp.text) as? JsonObject }.getOrNull()
        val items = (root?.get("data") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        val candidates =
            items.mapNotNull { item ->
                val id = item.str("id") ?: return@mapNotNull null
                val title = item.str("title") ?: return@mapNotNull null
                val artist = (item["artist"] as? JsonObject)?.str("name") ?: return@mapNotNull null
                val dur = item.numLong("duration")?.let { it * 1000L }
                CatalogTrack(id = id, title = title, artist = artist, durationMs = dur, isrc = item.str("isrc"))
            }
        val best = TrackMatcher.best(track, candidates)
        if (best == null) {
            report(DiagnosticStep("Deezer catalog", false, "No match on Deezer"))
            return ResolveResult.NotFound("No match on Deezer")
        }
        report(DiagnosticStep("Deezer catalog", true, "${best.track.title} · ${best.track.artist}"))

        val token = values[FIELD_INSTANCE_TOKEN]?.trim().orEmpty()
        val probeHeaders =
            buildMap {
                put("Range", "bytes=0-15")
                if (token.isNotEmpty()) put("Authorization", "Bearer $token")
            }
        val paths =
            buildList {
                best.track.isrc?.let { add("/stream/?isrc=${urlEncodeComponent(it)}") }
                add("/stream/${urlEncodeComponent(best.track.id)}")
            }
        val wantFlac = (picks[CHOICE_QUALITY] ?: "FLAC") == "FLAC"
        for (path in paths) {
            val url = "$base$path"
            val probe = runCatching { host.http(HttpRequest(url, headers = probeHeaders)) }.getOrNull() ?: continue
            if (probe.code != 206 || probe.body.size < 4) continue
            val b = probe.body
            val isFlac = b[0] == 102.toByte() && b[1] == 76.toByte() && b[2] == 97.toByte() && b[3] == 67.toByte()
            val isMp3 =
                (b[0] == 73.toByte() && b[1] == 68.toByte() && b[2] == 51.toByte()) ||
                    (b[0] == 0xFF.toByte() && (b[1].toInt() and 0xE0) == 0xE0)
            if (!isFlac && !isMp3) continue
            if (wantFlac && !isFlac) continue
            val codec = if (isFlac) "FLAC" else "MP3"
            report(DiagnosticStep("Stream probe", true, "Verified plaintext $codec"))
            return ResolveResult.Found(
                SourceStream(
                    url = url,
                    format = StreamFormat.Progressive,
                    headers = if (token.isNotEmpty()) mapOf("Authorization" to "Bearer $token") else emptyMap(),
                    sourceLabel = "Deezer Instance",
                    qualityLabel = codec,
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
        report(DiagnosticStep("Stream probe", false, "Instance did not return plaintext FLAC/MP3"))
        return ResolveResult.Unavailable("No plaintext Deezer instance stream")
    }

    companion object {
        const val ID = "deezer-instance"
        const val FIELD_INSTANCE_URL = "instanceUrl"
        const val FIELD_INSTANCE_TOKEN = "instanceToken"
        const val CHOICE_QUALITY = "quality"
    }
}

/** Own-account Qobuz module with MD5-signed `/track/getFileUrl` requests. */
class QobuzDirectModule :
    SourceModule,
    ModuleFields {
    override val info =
        ModuleInfo(
            id = ID,
            name = "Qobuz",
            version = "1.0",
            description = "Direct own-account Qobuz FLAC and MP3 streaming using signed API requests",
            sourceUrl = "https://github.com/NatuneGroup/Natune",
            author = "Natune contributors",
        )

    override val fields: List<ModuleField> =
        listOf(
            ModuleField(id = "appId", label = "App ID", kind = FieldKind.Text, hint = "Qobuz app ID"),
            ModuleField(id = "appSecret", label = "App secret", kind = FieldKind.Text, hint = "Qobuz app secret", secret = true),
            ModuleField(
                id = "userAuthToken",
                label = "User auth token",
                kind = FieldKind.Text,
                hint = "Your Qobuz user_auth_token",
                secret = true,
            ),
        )

    override val choices: List<ModuleChoice> =
        listOf(
            ModuleChoice(
                id = "quality",
                label = "Format",
                options =
                    listOf(
                        StreamOption("6", "FLAC 16-bit", "CD lossless"),
                        StreamOption("27", "Hi-Res 24/192", "Studio master FLAC"),
                        StreamOption("5", "MP3 320", "320 kbps lossy"),
                    ),
            ),
        )

    private lateinit var host: ModuleHost
    private var values: Map<String, String> = emptyMap()

    override fun attach(host: ModuleHost) {
        this.host = host
    }

    override fun onFields(values: Map<String, String>) {
        this.values = values
    }

    override fun accountState(): AccountState {
        val ready =
            !values["appId"].isNullOrBlank() &&
                !values["appSecret"].isNullOrBlank() &&
                !values["userAuthToken"].isNullOrBlank()
        return if (ready) AccountState.SignedIn("Credentials configured") else AccountState.SignedOut
    }

    override suspend fun resolve(
        track: TrackQuery,
        picks: Map<String, String>,
    ): ResolveResult = diagnose(track, picks) {}

    override suspend fun diagnose(
        track: TrackQuery,
        picks: Map<String, String>,
        report: (DiagnosticStep) -> Unit,
    ): ResolveResult =
        QobuzApiHelper.resolveTrack(
            host = host,
            track = track,
            appId = values["appId"].orEmpty().trim(),
            appSecret = values["appSecret"].orEmpty().trim(),
            userAuthToken = values["userAuthToken"].orEmpty().trim(),
            formatId = picks["quality"]?.toIntOrNull() ?: 6,
            sourceLabel = "Qobuz",
            report = report,
        )

    companion object {
        const val ID = "qobuz"
    }
}

/** Own-account TIDAL module with quality fallback (`HI_RES_LOSSLESS` → `LOSSLESS` → `HIGH`). */
class TidalDirectModule :
    SourceModule,
    ModuleFields {
    override val info =
        ModuleInfo(
            id = ID,
            name = "TIDAL",
            version = "1.0",
            description = "Direct own-account TIDAL FLAC and Hi-Res streaming with quality fallback",
            sourceUrl = "https://github.com/NatuneGroup/Natune",
            author = "Natune contributors",
        )

    override val fields: List<ModuleField> =
        listOf(
            ModuleField(
                id = "accessToken",
                label = "Access token",
                kind = FieldKind.Text,
                hint = "Your TIDAL OAuth access token",
                secret = true,
            ),
            ModuleField(
                id = "countryCode",
                label = "Country code",
                kind = FieldKind.Text,
                default = "US",
                hint = "Two-letter region (e.g. US, GB, DE)",
            ),
        )

    override val choices: List<ModuleChoice> =
        listOf(
            ModuleChoice(
                id = "quality",
                label = "Quality",
                options =
                    listOf(
                        StreamOption("LOSSLESS", "Lossless", "16-bit FLAC"),
                        StreamOption("HI_RES_LOSSLESS", "Hi-Res FLAC", "24-bit lossless"),
                        StreamOption("HIGH", "High", "320 kbps AAC"),
                    ),
            ),
        )

    private lateinit var host: ModuleHost
    private var values: Map<String, String> = emptyMap()

    override fun attach(host: ModuleHost) {
        this.host = host
    }

    override fun onFields(values: Map<String, String>) {
        this.values = values
    }

    override fun accountState(): AccountState =
        if (values["accessToken"].isNullOrBlank()) {
            AccountState.SignedOut
        } else {
            AccountState.SignedIn("Token configured")
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
        val rawToken = values["accessToken"].orEmpty().trim()
        val cleanToken = rawToken.removePrefix("Bearer ").trim()
        val rawCountry = values["countryCode"].orEmpty().trim()
        val country = rawCountry.uppercase().ifBlank { "US" }
        return TidalApiHelper.resolveTrack(
            host = host,
            track = track,
            accessToken = cleanToken,
            countryCode = country,
            quality = picks["quality"] ?: "LOSSLESS",
            sourceLabel = "TIDAL",
            report = report,
        )
    }

    companion object {
        const val ID = "tidal"
    }
}

private object TidalApiHelper {
    private const val API = "https://api.tidal.com/v1"

    suspend fun resolveTrack(
        host: ModuleHost,
        track: TrackQuery,
        accessToken: String,
        countryCode: String,
        quality: String,
        sourceLabel: String,
        report: (DiagnosticStep) -> Unit,
    ): ResolveResult {
        if (accessToken.isBlank()) return ResolveResult.Unavailable("Configure a TIDAL access token")
        val headers = mapOf("Authorization" to "Bearer $accessToken", "Accept" to "application/json")
        val q = "${track.artists.firstOrNull().orEmpty()} ${TrackMatcher.core(track.title)}".trim()
        report(DiagnosticStep("$sourceLabel search", null, "Searching catalog…"))
        val searchUrl = queryUrl(API, "/search/tracks", mapOf("query" to q, "limit" to "10", "countryCode" to countryCode))
        val resp =
            runCatching { host.http(HttpRequest(searchUrl, headers = headers)) }.getOrElse {
                report(DiagnosticStep("$sourceLabel search", false, "Request failed"))
                return ResolveResult.Unavailable("TIDAL unreachable")
            }
        if (!resp.ok) {
            report(DiagnosticStep("$sourceLabel search", false, "HTTP ${resp.code}"))
            return ResolveResult.Unavailable("TIDAL HTTP ${resp.code}")
        }
        val root = runCatching { providerJson.parseToJsonElement(resp.text) as? JsonObject }.getOrNull()
        val items = (root?.get("items") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        val candidates =
            items.mapNotNull { item ->
                val id = item.str("id") ?: return@mapNotNull null
                val title = item.str("title") ?: return@mapNotNull null
                val artist =
                    (item["artists"] as? JsonArray)
                        ?.mapNotNull { (it as? JsonObject)?.str("name") }
                        ?.joinToString(", ")
                        ?.takeIf { it.isNotBlank() }
                        ?: (item["artist"] as? JsonObject)?.str("name")
                        ?: return@mapNotNull null
                val dur = item.numLong("duration")?.let { it * 1000L }
                CatalogTrack(id = id, title = title, artist = artist, durationMs = dur, isrc = item.str("isrc"))
            }
        val best = TrackMatcher.best(track, candidates)
        if (best == null) {
            report(DiagnosticStep("$sourceLabel search", false, "No match found"))
            return ResolveResult.NotFound("Not found on $sourceLabel")
        }
        report(DiagnosticStep("$sourceLabel search", true, "${best.track.title} · ${best.track.artist}"))

        val tiers = listOf("HI_RES_LOSSLESS", "LOSSLESS", "HIGH", "LOW")
        val startIdx = tiers.indexOf(quality).coerceAtLeast(0)
        for (tier in tiers.subList(startIdx, tiers.size)) {
            val url =
                queryUrl(
                    API,
                    "/tracks/${urlEncodeComponent(best.track.id)}/playbackinfopostpaywall",
                    mapOf(
                        "audioquality" to tier,
                        "playbackmode" to "STREAM",
                        "assetpresentation" to "FULL",
                        "countryCode" to countryCode,
                    ),
                )
            val pbResp = runCatching { host.http(HttpRequest(url, headers = headers)) }.getOrNull() ?: continue
            if (!pbResp.ok) continue
            val pbObj = runCatching { providerJson.parseToJsonElement(pbResp.text) as? JsonObject }.getOrNull() ?: continue
            val manifestB64 = pbObj.str("manifest") ?: continue
            val decoded = decodeBase64Flex(manifestB64).decodeToString()
            val mime = pbObj.str("manifestMimeType").orEmpty()
            if (mime == "application/dash+xml") {
                if ("ContentProtection" in decoded) continue
                report(DiagnosticStep("$sourceLabel stream", true, "$tier DASH"))
                return ResolveResult.Found(
                    SourceStream(
                        url = url,
                        format = StreamFormat.Dash,
                        sourceLabel = sourceLabel,
                        qualityLabel = tier,
                        inlineManifest = decoded,
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
            val bts = runCatching { providerJson.parseToJsonElement(decoded) as? JsonObject }.getOrNull() ?: continue
            val enc = bts.str("encryptionType")
            if (!enc.isNullOrBlank() && enc != "NONE") continue
            val mediaUrl =
                (bts["urls"] as? JsonArray)
                    ?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { u -> u.startsWith("http") } }
                    ?: continue
            val codec = bts.str("codecs")?.uppercase() ?: "FLAC"
            report(DiagnosticStep("$sourceLabel stream", true, "$tier ($codec)"))
            return ResolveResult.Found(
                SourceStream(
                    url = mediaUrl,
                    format = StreamFormat.Progressive,
                    sourceLabel = sourceLabel,
                    qualityLabel = tier,
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
        report(DiagnosticStep("$sourceLabel stream", false, "No playable tier available"))
        return ResolveResult.Unavailable("No playable TIDAL tier")
    }
}

private object QobuzApiHelper {
    private const val API = "https://www.qobuz.com/api.json/0.2"

    suspend fun resolveTrack(
        host: ModuleHost,
        track: TrackQuery,
        appId: String,
        appSecret: String,
        userAuthToken: String,
        formatId: Int,
        sourceLabel: String,
        report: (DiagnosticStep) -> Unit,
    ): ResolveResult {
        if (appId.isBlank() || appSecret.isBlank() || userAuthToken.isBlank()) {
            return ResolveResult.Unavailable("Configure Qobuz app ID, app secret, and auth token")
        }
        val headers =
            mapOf(
                "X-App-Id" to appId,
                "X-User-Auth-Token" to userAuthToken,
                "Accept" to "application/json",
            )
        val q = "${track.artists.firstOrNull().orEmpty()} ${TrackMatcher.core(track.title)}".trim()
        report(DiagnosticStep("$sourceLabel search", null, "Searching catalog…"))
        val searchUrl = queryUrl(API, "/track/search", mapOf("query" to q, "limit" to "10", "offset" to "0"))
        val resp =
            runCatching { host.http(HttpRequest(searchUrl, headers = headers)) }.getOrElse {
                report(DiagnosticStep("$sourceLabel search", false, "Request failed"))
                return ResolveResult.Unavailable("Qobuz unreachable")
            }
        if (!resp.ok) {
            report(DiagnosticStep("$sourceLabel search", false, "HTTP ${resp.code}"))
            return ResolveResult.Unavailable("Qobuz HTTP ${resp.code}")
        }
        val root = runCatching { providerJson.parseToJsonElement(resp.text) as? JsonObject }.getOrNull()
        val items = ((root?.get("tracks") as? JsonObject)?.get("items") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        val candidates =
            items.mapNotNull { item ->
                if (item.bool("streamable") == false) return@mapNotNull null
                val id = item.str("id") ?: return@mapNotNull null
                val title = item.str("title") ?: return@mapNotNull null
                val artist =
                    (item["performer"] as? JsonObject)?.str("name")
                        ?: ((item["album"] as? JsonObject)?.get("artist") as? JsonObject)?.str("name")
                        ?: return@mapNotNull null
                val dur = item.numLong("duration")?.let { it * 1000L }
                CatalogTrack(id = id, title = title, artist = artist, durationMs = dur, isrc = item.str("isrc"))
            }
        val best = TrackMatcher.best(track, candidates)
        if (best == null) {
            report(DiagnosticStep("$sourceLabel search", false, "No match found"))
            return ResolveResult.NotFound("Not found on $sourceLabel")
        }
        report(DiagnosticStep("$sourceLabel search", true, "${best.track.title} · ${best.track.artist}"))

        val tiers = listOf(27, 7, 6, 5)
        val startIdx = tiers.indexOf(formatId).coerceAtLeast(0)
        for (fmt in tiers.subList(startIdx, tiers.size)) {
            val ts = (Clock.System.now().toEpochMilliseconds() / 1000L).toString()
            val sig = md5Hex("trackgetFileUrlformat_id${fmt}intentstreamtrack_id${best.track.id}$ts$appSecret")
            val url =
                queryUrl(
                    API,
                    "/track/getFileUrl",
                    mapOf(
                        "track_id" to best.track.id,
                        "format_id" to fmt.toString(),
                        "intent" to "stream",
                        "request_ts" to ts,
                        "request_sig" to sig,
                    ),
                )
            val fileResp = runCatching { host.http(HttpRequest(url, headers = headers)) }.getOrNull() ?: continue
            if (!fileResp.ok) continue
            val obj = runCatching { providerJson.parseToJsonElement(fileResp.text) as? JsonObject }.getOrNull() ?: continue
            if (obj.bool("sample") == true) continue
            val mediaUrl = obj.str("url")?.takeIf { it.startsWith("http") } ?: continue
            val rate = obj.numDouble("sampling_rate")?.let { if (it < 1000) (it * 1000).toInt() else it.toInt() }
            val codec = if (fmt == 5) "MP3" else "FLAC"
            val label =
                buildString {
                    append(codec)
                    obj.numInt("bit_depth")?.let { append(" $it-bit") }
                    rate?.let { append(" ${it / 1000} kHz") }
                }
            report(DiagnosticStep("$sourceLabel stream", true, label))
            return ResolveResult.Found(
                SourceStream(
                    url = mediaUrl,
                    format = StreamFormat.Progressive,
                    sourceLabel = sourceLabel,
                    qualityLabel = label,
                    codec = codec,
                    sampleRateHz = rate,
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
        report(DiagnosticStep("$sourceLabel stream", false, "No playable format available"))
        return ResolveResult.Unavailable("No playable Qobuz format")
    }
}

private fun extractTrackArray(root: JsonObject?): List<JsonObject> {
    if (root == null) return emptyList()
    for (key in listOf("tracks", "items", "results", "data")) {
        val value = root[key] ?: continue
        if (value is JsonArray) return value.mapNotNull { it as? JsonObject }
        if (value is JsonObject) {
            (value["items"] as? JsonArray)?.let { return it.mapNotNull { el -> el as? JsonObject } }
            ((value["tracks"] as? JsonObject)?.get("items") as? JsonArray)?.let {
                return it.mapNotNull { el -> el as? JsonObject }
            }
        }
    }
    return emptyList()
}
