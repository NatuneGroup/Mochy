// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.audiolab

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement

/** A named snapshot of the Audio Lab effects, applied all at once from the Audio Lab presets row. */
@Serializable
data class FxPreset(
    val id: String,
    val name: String,
    val reverb: ReverbSettings,
    val spatial: SpatialSettings,
    val eightD: EightDSettings,
    val stage: StageSettings,
) {
    internal val isValid: Boolean
        get() = id.isNotBlank() && name.isNotBlank()

    companion object {
        internal fun decode(value: String): List<FxPreset> {
            val entries = runCatching { Json.parseToJsonElement(value) as? JsonArray }.getOrNull() ?: return emptyList()
            return entries
                .mapNotNull { entry ->
                    runCatching { Json.decodeFromJsonElement<FxPreset>(entry) }.getOrNull()?.takeIf { it.isValid }
                }.distinctBy { it.id }
        }
    }
}
