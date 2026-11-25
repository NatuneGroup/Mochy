// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.audiolab

import kotlin.math.pow

/** Wall materials: how much sound the walls soak up and how quickly they dull what bounces off them. */
enum class StageWalls(
    val absorption: Float,
    val damping: Float,
) {
    Soft(0.45f, 0.7f),
    Wood(0.22f, 0.45f),
    Concrete(0.08f, 0.15f),
}

/** The kinds of sound a song is split into; each one can be placed in the room up to [StageLayout.COPIES] times. */
enum class StageSource { Vocal, Center, Left, Right, Ambience, Sub }

/**
 * One placed sound. x and z run -1..1 from wall to wall (z is ahead), y runs 0..1 from the floor to the ceiling.
 * [width] spreads the ambience pair apart, 0..1.
 */
data class StageObject(
    val id: Int,
    val source: StageSource,
    val x: Float,
    val y: Float,
    val z: Float,
    val width: Float = 0.6f,
)

/** Where the listener sits, in the same terms as [StageObject]. */
data class StageListener(
    val x: Float,
    val y: Float,
    val z: Float,
)

/** A ready-made room: where the sounds sit, where the listener sits, and the room around them. */
data class StagePreset(
    val id: String,
    val objects: List<StageObject>,
    val listener: StageListener,
    val roomSize: Float,
    val walls: StageWalls,
    val roomAmount: Float,
)

/** The room in metres. */
data class StageBox(
    val width: Float,
    val depth: Float,
    val height: Float,
)

/**
 * Stage (3D room) controls. The sound positions and the listener come from [presetId]; the room size, walls and
 * echo amount are what the presets set and what the sliders change. Off by default.
 */
data class StageSettings(
    val enabled: Boolean = false,
    val presetId: String = StagePresets.DEFAULT_ID,
    /** 0 a small studio .. 1 a concert hall */
    val roomSize: Float = StagePresets.Default.roomSize,
    val walls: StageWalls = StagePresets.Default.walls,
    val roomAmount: Float = StagePresets.Default.roomAmount,
    val levelDb: Float = 0f,
) {
    val box: StageBox
        get() {
            val width = 3.5f * (22f / 3.5f).pow(roomSize)
            return StageBox(width, width * 1.3f, 2.6f + 6.4f * roomSize.pow(1.5f))
        }
}

/** Layout of the float array the native room reads. Must match stageEngine/src/main/cpp/stage/engine.h. */
object StageLayout {
    const val ENABLED = 0
    const val MODE = 1
    const val ROOM_WIDTH = 2
    const val ROOM_DEPTH = 3
    const val ROOM_HEIGHT = 4
    const val ABSORPTION = 5
    const val DAMPING = 6
    const val ROOM_AMOUNT = 7
    const val LISTENER_X = 8
    const val LISTENER_Y = 9
    const val LISTENER_Z = 10
    const val LISTENER_YAW = 11
    const val MASTER = 12
    const val OBJECTS = 13
    const val OBJECT_STRIDE = 5
    const val COPIES = 4
    const val KINDS = 6
    const val OBJECT_COUNT = KINDS * COPIES
    const val PARAM_COUNT = OBJECTS + OBJECT_COUNT * OBJECT_STRIDE

    /** The room always plays the headphone model: speaker output is not detected on Android yet. */
    const val MODE_HEADPHONES = 0f
}

/** The float array the native room reads for these settings, using the preset's sound positions. */
fun StageSettings.toStageParams(): FloatArray {
    val preset = StagePresets.byId(presetId)
    val room = box
    val values = FloatArray(StageLayout.PARAM_COUNT)
    values[StageLayout.ENABLED] = if (enabled) 1f else 0f
    values[StageLayout.MODE] = StageLayout.MODE_HEADPHONES
    values[StageLayout.ROOM_WIDTH] = room.width
    values[StageLayout.ROOM_DEPTH] = room.depth
    values[StageLayout.ROOM_HEIGHT] = room.height
    values[StageLayout.ABSORPTION] = walls.absorption
    values[StageLayout.DAMPING] = walls.damping
    values[StageLayout.ROOM_AMOUNT] = roomAmount
    values[StageLayout.LISTENER_X] = preset.listener.x * room.width / 2
    values[StageLayout.LISTENER_Y] = preset.listener.y * room.height
    values[StageLayout.LISTENER_Z] = preset.listener.z * room.depth / 2
    values[StageLayout.LISTENER_YAW] = 0f
    values[StageLayout.MASTER] = dbToGain(levelDb)

    // every copy of a sound plays the same thing, so the copies together stay as loud as one
    StageSource.entries.forEach { source ->
        val copies = preset.objects.filter { it.source == source }.take(StageLayout.COPIES)
        copies.forEachIndexed { i, o ->
            putObject(values, source.ordinal * StageLayout.COPIES + i, o, room, dbToGain(levelDb) / copies.size)
        }
    }
    return values
}

private fun putObject(
    values: FloatArray,
    slot: Int,
    o: StageObject,
    box: StageBox,
    gain: Float,
) {
    val at = StageLayout.OBJECTS + slot * StageLayout.OBJECT_STRIDE
    values[at] = o.x * box.width / 2
    values[at + 1] = o.y * box.height
    values[at + 2] = o.z * box.depth / 2
    values[at + 3] = gain
    values[at + 4] = o.width
}

private fun dbToGain(db: Float): Float = 10f.pow(db / 20f)

object StagePresets {
    const val DEFAULT_ID = "studio"

    private class Spot(
        val x: Float,
        val y: Float,
        val z: Float,
    )

    private fun at(
        x: Float,
        y: Float,
        z: Float,
    ) = Spot(x, y, z)

    private fun layout(
        vocal: Spot,
        center: Spot,
        left: Spot,
        right: Spot,
        ambience: Spot,
        width: Float,
        subs: List<Spot>,
    ): List<StageObject> {
        val main =
            listOf(
                StageSource.Vocal to vocal,
                StageSource.Center to center,
                StageSource.Left to left,
                StageSource.Right to right,
                StageSource.Ambience to ambience,
            ).mapIndexed { id, (source, spot) -> StageObject(id, source, spot.x, spot.y, spot.z, width) }
        return main + subs.mapIndexed { i, spot -> StageObject(main.size + i, StageSource.Sub, spot.x, spot.y, spot.z) }
    }

    val Default =
        StagePreset(
            id = DEFAULT_ID,
            objects =
                layout(
                    vocal = at(0f, 0.42f, 0.45f),
                    center = at(0f, 0.4f, 0.52f),
                    left = at(-0.62f, 0.4f, 0.42f),
                    right = at(0.62f, 0.4f, 0.42f),
                    ambience = at(0f, 0.75f, -0.7f),
                    width = 0.6f,
                    subs = listOf(at(0f, 0.06f, 0.55f)),
                ),
            listener = StageListener(0f, 0.39f, -0.2f),
            roomSize = 0.18f,
            walls = StageWalls.Wood,
            roomAmount = 0.35f,
        )

    val All: List<StagePreset> =
        listOf(
            Default,
            StagePreset(
                id = "living",
                objects =
                    layout(
                        vocal = at(0f, 0.38f, 0.6f),
                        center = at(0f, 0.3f, 0.7f),
                        left = at(-0.8f, 0.36f, 0.6f),
                        right = at(0.8f, 0.36f, 0.6f),
                        ambience = at(0f, 0.6f, -0.85f),
                        width = 0.9f,
                        subs = listOf(at(-0.85f, 0.05f, 0.85f)),
                    ),
                listener = StageListener(0f, 0.35f, -0.15f),
                roomSize = 0.3f,
                walls = StageWalls.Soft,
                roomAmount = 0.45f,
            ),
            StagePreset(
                id = "front_row",
                objects =
                    layout(
                        vocal = at(0f, 0.3f, 0.55f),
                        center = at(0f, 0.25f, 0.75f),
                        left = at(-0.45f, 0.28f, 0.6f),
                        right = at(0.45f, 0.28f, 0.6f),
                        ambience = at(0f, 0.6f, -0.8f),
                        width = 1f,
                        subs = listOf(at(-0.6f, 0.04f, 0.7f), at(0.6f, 0.04f, 0.7f)),
                    ),
                listener = StageListener(0f, 0.21f, -0.3f),
                roomSize = 0.6f,
                walls = StageWalls.Wood,
                roomAmount = 0.55f,
            ),
            StagePreset(
                id = "on_stage",
                objects =
                    layout(
                        vocal = at(0f, 0.3f, 0.25f),
                        center = at(0f, 0.25f, -0.45f),
                        left = at(-0.7f, 0.28f, 0.05f),
                        right = at(0.7f, 0.28f, 0.05f),
                        ambience = at(0f, 0.5f, 0.9f),
                        width = 1f,
                        subs = listOf(at(0f, 0.04f, -0.5f)),
                    ),
                listener = StageListener(0f, 0.27f, 0f),
                roomSize = 0.45f,
                walls = StageWalls.Wood,
                roomAmount = 0.4f,
            ),
            StagePreset(
                id = "around",
                objects =
                    layout(
                        vocal = at(0f, 0.38f, 0.5f),
                        center = at(0f, 0.9f, 0.1f),
                        left = at(-0.75f, 0.38f, -0.35f),
                        right = at(0.75f, 0.38f, -0.35f),
                        ambience = at(0f, 0.85f, -0.6f),
                        width = 0.8f,
                        subs = listOf(at(0f, 0.05f, 0.6f)),
                    ),
                listener = StageListener(0f, 0.35f, 0f),
                roomSize = 0.3f,
                walls = StageWalls.Soft,
                roomAmount = 0.4f,
            ),
            StagePreset(
                id = "hall",
                objects =
                    layout(
                        vocal = at(0f, 0.2f, 0.55f),
                        center = at(0f, 0.18f, 0.7f),
                        left = at(-0.4f, 0.2f, 0.6f),
                        right = at(0.4f, 0.2f, 0.6f),
                        ambience = at(0f, 0.6f, -0.2f),
                        width = 1f,
                        subs = listOf(at(0f, 0.02f, 0.6f)),
                    ),
                listener = StageListener(0f, 0.15f, -0.35f),
                roomSize = 0.9f,
                walls = StageWalls.Concrete,
                roomAmount = 0.7f,
            ),
        )

    fun byId(id: String): StagePreset = All.firstOrNull { it.id == id } ?: Default

    /** Applies a preset's room to [settings]. The on/off switch and the level are kept. */
    fun apply(
        settings: StageSettings,
        preset: StagePreset,
    ): StageSettings =
        settings.copy(
            presetId = preset.id,
            roomSize = preset.roomSize,
            walls = preset.walls,
            roomAmount = preset.roomAmount,
        )
}
