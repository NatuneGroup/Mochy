// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.audiolab

/** Reverb controls. Every value is 0..1 except preDelayMs and lowCutHz. Off until the user turns it on. */
data class ReverbSettings(
    val enabled: Boolean = false,
    val mix: Float = 0.5f,
    val roomSize: Float = 0.55f,
    val damping: Float = 0.45f,
    val wet: Float = 0.6f,
    val dry: Float = 1f,
    val width: Float = 1f,
    val preDelayMs: Float = 12f,
    val lowCutHz: Float = 180f,
)

/** A starting room. The UI resolves its display name from a string resource keyed by [id]. */
data class ReverbPreset(
    val id: String,
    val mix: Float,
    val roomSize: Float,
    val damping: Float,
    val wet: Float,
    val width: Float,
    val preDelayMs: Float,
    val lowCutHz: Float,
)

object ReverbPresets {
    val Default =
        ReverbPreset(
            id = "room",
            mix = 0.5f,
            roomSize = 0.55f,
            damping = 0.45f,
            wet = 0.6f,
            width = 1f,
            preDelayMs = 12f,
            lowCutHz = 180f,
        )

    val All: List<ReverbPreset> =
        listOf(
            Default,
            ReverbPreset("studio", 0.4f, 0.3f, 0.65f, 0.45f, 0.8f, 5f, 250f),
            ReverbPreset("bathroom", 0.5f, 0.42f, 0.05f, 0.7f, 0.6f, 2f, 120f),
            ReverbPreset("plate", 0.45f, 0.65f, 0.1f, 0.55f, 1f, 0f, 300f),
            ReverbPreset("hall", 0.5f, 0.78f, 0.35f, 0.6f, 1f, 25f, 150f),
            ReverbPreset("cathedral", 0.55f, 0.95f, 0.2f, 0.7f, 1f, 40f, 120f),
            ReverbPreset("stadium", 0.6f, 0.9f, 0.5f, 0.65f, 1f, 60f, 200f),
            ReverbPreset("dream", 0.7f, 0.93f, 0.6f, 0.85f, 1f, 30f, 300f),
        )
}

/** Applies a preset's room shape. Keeps [ReverbSettings.enabled] and resets dry to 1, as the reference does. */
fun ReverbSettings.withPreset(preset: ReverbPreset): ReverbSettings =
    copy(
        mix = preset.mix,
        roomSize = preset.roomSize,
        damping = preset.damping,
        wet = preset.wet,
        dry = 1f,
        width = preset.width,
        preDelayMs = preset.preDelayMs,
        lowCutHz = preset.lowCutHz,
    )
