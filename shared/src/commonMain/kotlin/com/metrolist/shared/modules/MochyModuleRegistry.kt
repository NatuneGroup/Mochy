// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.modules

import com.metrolist.shared.network.createHttpClient
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.utils.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

sealed interface ModuleOrigin {
    data object BuiltIn : ModuleOrigin

    data class Web(
        val base: String,
    ) : ModuleOrigin
}

data class ModuleEntry(
    val module: MochyModule,
    val origin: ModuleOrigin,
    val enabled: Boolean,
    val picks: Map<String, String>,
    val account: AccountState,
    val fields: List<ModuleField> = emptyList(),
    val values: Map<String, String> = emptyMap(),
    val addedAt: Long? = null,
) {
    val id: String get() = module.info.id
    val icon: String? get() = (module as? ModuleArt)?.icon
    val banner: String? get() = (module as? ModuleArt)?.banner
    val active: Boolean get() = enabled && account is AccountState.SignedIn

    fun adds(feature: ModuleFeature): Boolean = feature in module.adds
}

@Serializable
private data class SavedWebModule(
    val base: String,
    val manifestJson: String,
    val addedAt: Long? = null,
)

@Serializable
private data class PersistedModulesState(
    val enabled: Map<String, Boolean> = emptyMap(),
    val picks: Map<String, Map<String, String>> = emptyMap(),
    val fields: Map<String, Map<String, String>> = emptyMap(),
    val storage: Map<String, Map<String, String>> = emptyMap(),
    val webModules: List<SavedWebModule> = emptyList(),
)

/**
 * Multiplatform Mochy ModuleRegistry managing built-in providers (YouTube, ArchivePool,
 * Monochrome, Qobuz, TIDAL, Deezer) and user-added Mochy Web Modules across Android, Desktop, and iOS.
 */
class MochyModuleRegistry(
    private val httpClientFactory: () -> HttpClient = ::createHttpClient,
    private val settings: Settings = Settings.shared,
) {
    private val lock = SynchronizedObject()
    private val httpClient: HttpClient by lazy(httpClientFactory)
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private var lastPersistedJson: String = settings.mochyModulesStateJson
    private var state: PersistedModulesState = decodePersisted(lastPersistedJson)
    private val registered = mutableListOf<Pair<MochyModule, ModuleOrigin>>()

    private val _entries = MutableStateFlow<List<ModuleEntry>>(emptyList())
    val entries: StateFlow<List<ModuleEntry>> = _entries.asStateFlow()

    private val _activeLook = MutableStateFlow<Map<String, String>>(emptyMap())
    val activeLook: StateFlow<Map<String, String>> = _activeLook.asStateFlow()

    init {
        rebuildModules()
        settings.addReloadListener { reloadFromSettingsIfChanged() }
    }

    fun reloadFromSettings() {
        synchronized(lock) {
            val raw = settings.mochyModulesStateJson
            lastPersistedJson = raw
            state = decodePersisted(raw)
        }
        rebuildModules()
    }

    private fun reloadFromSettingsIfChanged() {
        val changed =
            synchronized(lock) {
                syncFromSettingsLocked()
            }
        if (changed) {
            rebuildModules()
        }
    }

    private fun syncFromSettingsLocked(): Boolean {
        val raw = settings.mochyModulesStateJson
        if (raw == lastPersistedJson) return false
        lastPersistedJson = raw
        state = decodePersisted(raw)
        return true
    }

    fun isEnabled(id: String): Boolean {
        reloadFromSettingsIfChanged()
        return synchronized(lock) { state.enabled[id] ?: (id == YouTubeBuiltInModule.ID) }
    }

    fun setEnabled(
        id: String,
        on: Boolean,
    ) {
        val needsRebuild =
            synchronized(lock) {
                val reloaded = syncFromSettingsLocked()
                state = state.copy(enabled = state.enabled + (id to on))
                savePersisted()
                reloaded
            }
        if (needsRebuild) rebuildModules() else refresh()
    }

    fun setPick(
        id: String,
        choiceId: String,
        optionId: String,
    ) {
        val needsRebuild =
            synchronized(lock) {
                val reloaded = syncFromSettingsLocked()
                val existing = state.picks[id].orEmpty()
                state = state.copy(picks = state.picks + (id to (existing + (choiceId to optionId))))
                savePersisted()
                reloaded
            }
        if (needsRebuild) rebuildModules() else refresh()
    }

    fun setField(
        id: String,
        fieldId: String,
        value: String,
    ) {
        val needsRebuild =
            synchronized(lock) {
                val reloaded = syncFromSettingsLocked()
                val existing = state.fields[id].orEmpty()
                val updated = existing + (fieldId to value)
                state = state.copy(fields = state.fields + (id to updated))
                savePersisted()
                if (!reloaded) {
                    val mod = registered.firstOrNull { it.first.info.id == id }?.first as? ModuleFields
                    mod?.onFields(mergedFieldValues(mod, updated))
                }
                reloaded
            }
        if (needsRebuild) rebuildModules() else refresh()
    }

    suspend fun addWebModule(rawLink: String): Result<ModuleEntry> =
        runCatching {
            val base = WebModuleManifest.baseOf(rawLink) ?: error("Invalid module URL")
            val manifestUrl = "$base/manifest.json"
            val response = executeHttp(HttpRequest(manifestUrl, headers = mapOf("Accept" to "application/json")))
            if (!response.ok) error("Could not load manifest.json (HTTP ${response.code})")
            val text = response.text
            if (WebModuleManifest.isWebsite(text)) {
                error("This URL is a regular website manifest, not a Mochy module")
            }
            val manifest = WebModuleManifest.parse(text) ?: error("Invalid Mochy manifest.json")
            val id = WebModuleManifest.idFor(base, manifest)

            synchronized(lock) {
                syncFromSettingsLocked()
                val filtered = state.webModules.filterNot { it.base.equals(base, ignoreCase = true) }
                val saved = SavedWebModule(base = base, manifestJson = text, addedAt = Clock.System.now().toEpochMilliseconds())
                state =
                    state.copy(
                        webModules = filtered + saved,
                        enabled = state.enabled + (id to true),
                    )
                savePersisted()
            }
            rebuildModules()
            _entries.value.first { it.id == id }
        }

    fun removeWebModule(id: String) {
        synchronized(lock) {
            if (syncFromSettingsLocked()) {
                // Rebuild state first so target web module matches restored settings.
                val restoredTarget =
                    state.webModules.firstOrNull { saved ->
                        val manifest = WebModuleManifest.parse(saved.manifestJson) ?: return@firstOrNull false
                        WebModuleManifest.idFor(saved.base, manifest) == id
                    } ?: return
                state =
                    state.copy(
                        webModules = state.webModules.filterNot { it.base.equals(restoredTarget.base, ignoreCase = true) },
                        enabled = state.enabled - id,
                        picks = state.picks - id,
                        fields = state.fields - id,
                        storage = state.storage - id,
                    )
                savePersisted()
            } else {
                val target = registered.firstOrNull { it.first.info.id == id }?.second as? ModuleOrigin.Web ?: return
                state =
                    state.copy(
                        webModules = state.webModules.filterNot { it.base.equals(target.base, ignoreCase = true) },
                        enabled = state.enabled - id,
                        picks = state.picks - id,
                        fields = state.fields - id,
                        storage = state.storage - id,
                    )
                savePersisted()
            }
        }
        rebuildModules()
    }

    suspend fun diagnose(
        id: String,
        track: TrackQuery = DEFAULT_TEST_TRACK,
        report: (DiagnosticStep) -> Unit,
    ): ResolveResult {
        val entry = _entries.value.firstOrNull { it.id == id } ?: return ResolveResult.Unavailable("Module not found")
        val source = entry.module as? SourceModule
        if (source != null) {
            return source.diagnose(track, entry.picks, report)
        }
        val stateCheck = entry.module.verifyAccount()
        val ok = stateCheck is AccountState.SignedIn
        val detail =
            when (stateCheck) {
                is AccountState.SignedIn -> stateCheck.label
                is AccountState.Expired -> stateCheck.reason
                AccountState.SignedOut -> "Not configured"
            }
        report(DiagnosticStep("Verify module", ok, detail))
        return if (ok) ResolveResult.NotFound("Module verified") else ResolveResult.Unavailable(detail)
    }

    /**
     * Resolves an audio stream from enabled non-YouTube [SourceModule]s in priority order.
     * Returns null when no active external source module matches so playback falls back to YouTube.
     */
    suspend fun resolveAudioStream(
        track: TrackQuery,
        accepts: (SourceStream) -> Boolean = { true },
    ): SourceStream? =
        firstModuleResult(track, activeEntries(ModuleFeature.AudioSource)) { entry, query ->
            if (entry.id == YouTubeBuiltInModule.ID) {
                null
            } else {
                val result = (entry.module as? SourceModule)?.resolve(query, entry.picks)
                (result as? ResolveResult.Found)?.stream?.takeIf(accepts)
            }
        }

    internal fun activeEntries(feature: ModuleFeature): List<ModuleEntry> = entries.value.filter { it.active && it.adds(feature) }

    internal suspend fun fetchModuleLyrics(
        track: TrackQuery,
        accepts: (String) -> Boolean,
    ): Pair<String, String>? =
        firstModuleResult(track, activeEntries(ModuleFeature.Lyrics)) { entry, query ->
            (entry.module as? LyricsModule)?.fetchLyrics(query)?.takeIf(accepts)?.let { entry.id to it }
        }

    internal suspend fun fetchModuleMetadata(
        track: TrackQuery,
        modules: List<ModuleEntry>,
    ): TrackMetadata? =
        firstModuleResult(track, modules) { entry, query ->
            (entry.module as? MetadataModule)?.fetchMetadata(query)?.takeIf { metadata ->
                listOf(metadata.title, metadata.artist, metadata.album, metadata.artworkUrl, metadata.isrc).any { !it.isNullOrBlank() }
            }
        }

    private suspend fun <T> firstModuleResult(
        track: TrackQuery,
        modules: List<ModuleEntry>,
        fetch: suspend (ModuleEntry, TrackQuery) -> T?,
    ): T? =
        withTimeoutOrNull(MODULE_LOOKUP_TIMEOUT_MS) {
            for (entry in modules) {
                val result =
                    try {
                        withTimeoutOrNull(MODULE_ATTEMPT_TIMEOUT_MS) { fetch(entry, track) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                currentCoroutineContext().ensureActive()
                if (result != null && entries.value.any { it == entry }) return@withTimeoutOrNull result
            }
            null
        }

    fun refresh() {
        val builtEntries =
            synchronized(lock) {
                val addedMap = state.webModules.associate { it.base to it.addedAt }
                registered.map { (module, origin) ->
                    val id = module.info.id
                    val fields = (module as? ModuleFields)?.fields.orEmpty()
                    val fieldVals = mergedFieldValues(module as? ModuleFields, state.fields[id].orEmpty())
                    ModuleEntry(
                        module = module,
                        origin = origin,
                        enabled = state.enabled[id] ?: (id == YouTubeBuiltInModule.ID),
                        picks = state.picks[id].orEmpty(),
                        account = runCatching { module.accountState() }.getOrDefault(AccountState.SignedOut),
                        fields = fields,
                        values = fieldVals,
                        addedAt = (origin as? ModuleOrigin.Web)?.let { addedMap[it.base] },
                    )
                }
            }
        _entries.value = builtEntries

        val lookMap = LinkedHashMap<String, String>()
        builtEntries
            .filter { it.active && it.module is LookModule && it.adds(ModuleFeature.Look) }
            .forEach { entry ->
                lookMap.putAll((entry.module as LookModule).look)
            }
        _activeLook.value = lookMap
    }

    private fun rebuildModules() {
        synchronized(lock) {
            registered.clear()
            val builtIns: List<MochyModule> =
                listOf(
                    YouTubeBuiltInModule(),
                    ArchivePoolModule(),
                    MonochromeModule(),
                    QobuzInstanceModule(),
                    TidalInstanceModule(),
                    DeezerInstanceModule(),
                    QobuzDirectModule(),
                    TidalDirectModule(),
                )
            builtIns.forEach { attachModule(it, ModuleOrigin.BuiltIn) }

            state.webModules.forEach { saved ->
                val manifest = WebModuleManifest.parse(saved.manifestJson) ?: return@forEach
                attachModule(WebModule(saved.base, manifest), ModuleOrigin.Web(saved.base))
            }
        }
        refresh()
    }

    private fun attachModule(
        module: MochyModule,
        origin: ModuleOrigin,
    ) {
        val id = module.info.id
        module.attach(hostFor(id))
        if (module is ModuleFields) {
            module.onFields(mergedFieldValues(module, state.fields[id].orEmpty()))
        }
        registered += module to origin
    }

    private fun mergedFieldValues(
        module: ModuleFields?,
        saved: Map<String, String>,
    ): Map<String, String> {
        if (module == null) return saved
        val out = LinkedHashMap<String, String>()
        module.fields.forEach { field ->
            out[field.id] = saved[field.id] ?: field.default
        }
        return out
    }

    private fun hostFor(moduleId: String): ModuleHost =
        object : ModuleHost {
            override val storage: ModuleStorage =
                object : ModuleStorage {
                    override fun get(key: String): String? =
                        synchronized(lock) {
                            syncFromSettingsLocked()
                            state.storage[moduleId]?.get(key)
                        }

                    override fun set(
                        key: String,
                        value: String?,
                    ) {
                        synchronized(lock) {
                            syncFromSettingsLocked()
                            val current = state.storage[moduleId].orEmpty()
                            val next = if (value == null) current - key else current + (key to value)
                            state = state.copy(storage = state.storage + (moduleId to next))
                            savePersisted()
                        }
                    }
                }

            override suspend fun http(request: HttpRequest): HttpResponse = executeHttp(request)

            override fun log(message: String) {
                Logger.d("MochyModule:$moduleId", message)
            }
        }

    private suspend fun executeHttp(request: HttpRequest): HttpResponse {
        val response =
            httpClient.request(request.url) {
                method = HttpMethod.parse(request.method)
                headers {
                    request.headers.forEach { (k, v) -> append(k, v) }
                }
                request.body?.let { payload ->
                    request.contentType?.let { ct -> contentType(ContentType.parse(ct)) }
                    setBody(payload)
                }
            }
        val responseHeaders =
            buildMap {
                response.headers.forEach { name, values ->
                    values.firstOrNull()?.let { put(name.lowercase(), it) }
                }
            }
        return HttpResponse(
            code = response.status.value,
            body = response.readRawBytes(),
            headers = responseHeaders,
        )
    }

    private fun decodePersisted(raw: String): PersistedModulesState {
        if (raw.isBlank()) return PersistedModulesState()
        return runCatching { json.decodeFromString(PersistedModulesState.serializer(), raw) }
            .getOrDefault(PersistedModulesState())
    }

    private fun savePersisted() {
        val encoded = json.encodeToString(state)
        lastPersistedJson = encoded
        settings.mochyModulesStateJson = encoded
    }

    companion object {
        private const val MODULE_LOOKUP_TIMEOUT_MS = 6_000L
        private const val MODULE_ATTEMPT_TIMEOUT_MS = 3_000L

        val shared: MochyModuleRegistry by lazy { MochyModuleRegistry() }

        val DEFAULT_TEST_TRACK =
            TrackQuery(
                title = "Blinding Lights",
                artists = listOf("The Weeknd"),
                durationMs = 200_040L,
            )
    }
}
