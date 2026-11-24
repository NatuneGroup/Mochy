// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.modules

import kotlin.math.abs

const val MODULE_API_VERSION = 3

/** Default status label for modules that require no user account. */
const val NO_ACCOUNT_LABEL = "No account needed"

data class ModuleInfo(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val apiVersion: Int = MODULE_API_VERSION,
    val sourceUrl: String? = null,
    val author: String? = null,
)

/** Capabilities a Mochy module can provide. One module can advertise several. */
enum class ModuleFeature {
    AudioSource,
    Lyrics,
    Look,
    Metadata,
}

/** Input control type for a module setting field. */
enum class FieldKind {
    Text,
    Select,
    Toggle,
}

data class StreamOption(
    val id: String,
    val label: String,
    val description: String = "",
)

data class ModuleChoice(
    val id: String,
    val label: String,
    val options: List<StreamOption>,
)

data class ModuleField(
    val id: String,
    val label: String,
    val kind: FieldKind,
    val options: List<StreamOption> = emptyList(),
    val default: String = "",
    val hint: String = "",
    val description: String = "",
    val secret: Boolean = false,
)

interface ModuleArt : MochyModule {
    val icon: String? get() = null
    val banner: String? get() = null
}

interface ModuleFields : MochyModule {
    val fields: List<ModuleField>

    fun onFields(values: Map<String, String>) {}
}

interface ModuleStorage {
    fun get(key: String): String?

    fun set(
        key: String,
        value: String?,
    )
}

class HttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val contentType: String? = null,
)

class HttpResponse(
    val code: Int,
    val body: ByteArray,
    val headers: Map<String, String> = emptyMap(),
) {
    val ok: Boolean get() = code in 200..299
    val text: String get() = body.decodeToString()

    fun header(name: String): String? = headers[name.lowercase()]
}

/** Platform-neutral host bridge exposed to every Mochy module across Android, Desktop, and iOS. */
interface ModuleHost {
    val storage: ModuleStorage

    suspend fun http(request: HttpRequest): HttpResponse

    fun log(message: String)
}

data class TrackQuery(
    val title: String,
    val artists: List<String>,
    val album: String? = null,
    val durationMs: Long? = null,
    val isrc: String? = null,
)

data class CatalogTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    val isrc: String? = null,
)

data class MatchedTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    val confidence: Float,
)

enum class StreamFormat {
    Progressive,
    Hls,
    Dash,
}

data class SourceStream(
    val url: String,
    val format: StreamFormat,
    val headers: Map<String, String> = emptyMap(),
    val sourceLabel: String,
    val qualityLabel: String,
    val matched: MatchedTrack,
    val codec: String? = null,
    val sampleRateHz: Int? = null,
    val inlineManifest: String? = null,
)

sealed interface ResolveResult {
    data class Found(
        val stream: SourceStream,
    ) : ResolveResult

    data class NotFound(
        val reason: String,
    ) : ResolveResult

    data class Unavailable(
        val reason: String,
    ) : ResolveResult
}

sealed interface AccountState {
    data object SignedOut : AccountState

    data class SignedIn(
        val label: String,
    ) : AccountState

    data class Expired(
        val reason: String,
    ) : AccountState
}

data class DiagnosticStep(
    val name: String,
    val ok: Boolean?,
    val detail: String,
)

data class TrackMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artworkUrl: String? = null,
    val isrc: String? = null,
)

interface MochyModule {
    val info: ModuleInfo
    val adds: Set<ModuleFeature>

    fun attach(host: ModuleHost)

    fun accountState(): AccountState = AccountState.SignedIn(NO_ACCOUNT_LABEL)

    suspend fun verifyAccount(): AccountState = accountState()

    fun signOut() {}

    val choices: List<ModuleChoice> get() = emptyList()
}

interface SourceModule : MochyModule {
    override val adds: Set<ModuleFeature> get() = setOf(ModuleFeature.AudioSource)

    suspend fun resolve(
        track: TrackQuery,
        picks: Map<String, String>,
    ): ResolveResult

    suspend fun diagnose(
        track: TrackQuery,
        picks: Map<String, String>,
        report: (DiagnosticStep) -> Unit,
    ): ResolveResult
}

interface LyricsModule : MochyModule {
    override val adds: Set<ModuleFeature> get() = setOf(ModuleFeature.Lyrics)

    suspend fun fetchLyrics(track: TrackQuery): String?
}

interface LookModule : MochyModule {
    override val adds: Set<ModuleFeature> get() = setOf(ModuleFeature.Look)

    val look: Map<String, String>
}

interface MetadataModule : MochyModule {
    override val adds: Set<ModuleFeature> get() = setOf(ModuleFeature.Metadata)

    suspend fun fetchMetadata(track: TrackQuery): TrackMetadata?
}

/**
 * Shared track matching scorer ported from Mochy Music so every source module ranks recordings
 * consistently (ISRC exact match, cleaned title similarity, artist overlap, duration match, and
 * version-word mismatch penalty).
 */
object TrackMatcher {
    private val versionWords =
        listOf(
            "remix",
            "live",
            "acoustic",
            "instrumental",
            "karaoke",
            "sped up",
            "slowed",
            "nightcore",
            "demo",
            "cover",
            "edit",
            "mix",
            "version",
            "unplugged",
            "orchestral",
            "piano",
        )
    private val versionPatterns = versionWords.map { Regex("""\b${Regex.escape(it)}\b""") }

    class Scored(
        val track: CatalogTrack,
        val score: Float,
    )

    fun best(
        query: TrackQuery,
        candidates: List<CatalogTrack>,
    ): Scored? = ranked(query, candidates).firstOrNull()

    fun ranked(
        query: TrackQuery,
        candidates: List<CatalogTrack>,
    ): List<Scored> {
        val isrc = query.isrc
        val scored =
            candidates.map { song ->
                Scored(song, if (isrc != null && song.isrc.equals(isrc, ignoreCase = true)) 1f else score(query, song))
            }
        return scored.filter { it.score >= 0.7f }.sortedByDescending { it.score }
    }

    fun score(
        query: TrackQuery,
        song: CatalogTrack,
    ): Float {
        val qTitle = core(query.title)
        val sTitle = core(song.title)
        val title = similarity(qTitle, sTitle)
        if (title < 0.55f) return 0f

        val songArtists = words(song.artist)
        val artist =
            query.artists.maxOfOrNull { a ->
                val w = words(a)
                if (w.isEmpty()) 0f else w.count { it in songArtists }.toFloat() / w.size
            } ?: 0f
        if (artist < 0.5f) return 0f

        val wantMs = query.durationMs
        val haveMs = song.durationMs
        val duration =
            if (wantMs != null && haveMs != null) {
                val diff = abs(wantMs - haveMs) / 1000f
                when {
                    diff <= 2f -> 1f
                    diff >= 15f -> 0f
                    else -> 1f - (diff - 2f) / 13f
                }
            } else {
                0.6f
            }

        val qRaw = query.title.lowercase()
        val sRaw = song.title.lowercase()
        val mismatch = versionPatterns.count { it.containsMatchIn(qRaw) != it.containsMatchIn(sRaw) }
        val penalty = 0.25f * mismatch

        return (0.5f * title + 0.3f * artist + 0.2f * duration - penalty).coerceIn(0f, 1f)
    }

    fun core(title: String): String {
        var t = title.lowercase()
        t = t.replace(Regex("""[(\[](?:feat|ft|with|prod)\.?\s[^)\]]*[)\]]"""), " ")
        t =
            t.replace(
                Regex("""\s[-–]\s.*\b(?:remaster(?:ed)?|official|video|audio|lyrics?|visuali[sz]er|mv)\b.*$"""),
                " ",
            )
        t =
            t.replace(
                Regex("""[(\[][^)\]]*\b(?:remaster(?:ed)?|official|video|audio|lyrics?|visuali[sz]er|mv|explicit|clean)\b[^)\]]*[)\]]"""),
                " ",
            )
        t = t.replace(Regex("""\b(?:feat|ft)\.?\s.*$"""), " ")
        return words(t).joinToString(" ")
    }

    private fun words(text: String): List<String> =
        text
            .lowercase()
            .split(Regex("""[^\p{L}\p{N}]+"""))
            .filter { it.isNotEmpty() && it != "the" && it != "and" }

    private fun similarity(
        a: String,
        b: String,
    ): Float {
        if (a == b) return 1f
        val wa = a.split(' ').filter { it.isNotEmpty() }.toSet()
        val wb = b.split(' ').filter { it.isNotEmpty() }.toSet()
        if (wa.isEmpty() || wb.isEmpty()) return 0f
        return (wa intersect wb).size.toFloat() / (wa union wb).size
    }
}
