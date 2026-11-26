// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.audiolab

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.metrolist.shared.audiolab.EightDSettings
import com.metrolist.shared.audiolab.ReverbSettings
import com.metrolist.shared.audiolab.SpatialSettings
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_lab_effect_eight_d
import com.metrolist.shared.generated.resources.mochy_lab_effect_reverb
import com.metrolist.shared.generated.resources.mochy_lab_effect_spatial
import com.metrolist.shared.generated.resources.mochy_lab_effect_stage
import com.metrolist.shared.generated.resources.mochy_lab_fx_and_other
import com.metrolist.shared.generated.resources.mochy_lab_fx_on
import com.metrolist.shared.generated.resources.mochy_lab_fx_position
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** an effect that's on, as the header's cards name it */
class ActiveEffect(val key: String, val label: String)

/** the effects on in KMP's Audio Lab; the Stage counts only where the native room can run ([stageOn]) */
internal fun activeEffects(
    reverb: ReverbSettings,
    spatial: SpatialSettings,
    eightD: EightDSettings,
    stageOn: Boolean,
): List<ActiveEffect> = buildList {
    if (stageOn) add(ActiveEffect("stage", "3D Sims"))
    if (reverb.enabled) add(ActiveEffect("reverb", "Reverb"))
    if (spatial.enabled) add(ActiveEffect("spatial", "Spatial"))
    if (eightD.enabled) add(ActiveEffect("eight_d", "8D Audio"))
}

private fun effectLabelRes(key: String): StringResource = when (key) {
    "reverb" -> Res.string.mochy_lab_effect_reverb
    "spatial" -> Res.string.mochy_lab_effect_spatial
    "eight_d" -> Res.string.mochy_lab_effect_eight_d
    else -> Res.string.mochy_lab_effect_stage
}

private const val ShownCards = 3
private const val FrontSeconds = 3_200L

/** the plain card shown while no effect is on */
private const val IdleKey = "idle"

/** one card in the stack, it stays after its effect goes off until it has slid away */
private class StackCard(val key: String, val label: String?) {
    var leaving by mutableStateOf(false)

    /** where it last stood in the fan, so it slides out from there */
    var lastDepth = 0
}

@Composable
fun BoxScope.FxCardStack(effects: List<ActiveEffect>, hidden: State<Boolean>) {
    val cards = remember { mutableStateListOf<StackCard>() }
    val wanted = if (effects.isEmpty()) listOf(IdleKey) else effects.map { it.key }
    LaunchedEffect(wanted) {
        val labels = effects.associate { it.key to it.label }
        val staying = wanted.map { key ->
            cards.firstOrNull { it.key == key }?.also { it.leaving = false } ?: StackCard(key, labels[key])
        }
        val going = cards.filter { it.key !in wanted }.onEach { it.leaving = true }
        cards.clear()
        cards.addAll(staying + going)
    }

    val staying = cards.filterNot { it.leaving }
    val count = staying.size
    var front by remember { mutableIntStateOf(0) }
    LaunchedEffect(count) {
        while (count > 1) {
            delay(FrontSeconds)
            // not while the page is scrolled past it: nobody would see it turn
            snapshotFlow { hidden.value }.first { !it }
            front++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = count > 1) { front++ },
    ) {
        if (count > ShownCards) {
            Text(
                stringResource(Res.string.mochy_lab_fx_and_other, count - ShownCards),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = LabCardInk,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 26.dp, bottom = LabCardHeight + 46.dp),
            )
        }
        cards.forEach { card ->
            key(card.key) {
                val index = staying.indexOf(card)
                val depth = if (index >= 0) (index - front).mod(count) else card.lastDepth
                card.lastDepth = depth
                StackedCard(
                    card = card,
                    depth = depth,
                    position = index + 1,
                    count = count,
                    onGone = { cards.remove(card) },
                )
            }
        }
    }
}

@Composable
private fun BoxScope.StackedCard(
    card: StackCard,
    depth: Int,
    position: Int,
    count: Int,
    onGone: () -> Unit,
) {
    // where it stands in the fan, sprung, so a turn reads as a shuffle
    val place by animateFloatAsState(depth.toFloat(), spring(dampingRatio = 0.82f, stiffness = 170f), label = "fan")
    // 1 is down behind the page: a card rises from there when its effect goes on, and goes back
    val rise = remember { Animatable(1f) }
    LaunchedEffect(card.leaving) {
        rise.animateTo(if (card.leaving) 1f else 0f, spring(dampingRatio = 0.86f, stiffness = 120f))
        if (card.leaving) onGone()
    }

    LabHeaderCardFrame(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = LabCardStart, bottom = LabCardBottom)
            .zIndex(-place)
            .graphicsLayer {
                translationX = 20.dp.toPx() * place
                translationY = -10.dp.toPx() * place + rise.value * (LabCardHeight + 80.dp).toPx()
                rotationZ = -10f + 5f * place
                val scale = 1f - 0.04f * place
                scaleX = scale
                scaleY = scale
                // past the third it slips away behind the others
                alpha = (ShownCards - place).coerceIn(0f, 1f)
            },
    ) {
        if (depth >= ShownCards && place >= ShownCards - 0.01f) return@LabHeaderCardFrame
        val label = card.label?.let { stringResource(effectLabelRes(card.key)) }
        LabCardFace(
            // the front card shows everything; further back it fades to its name down the edge
            modifier = Modifier.graphicsLayer { alpha = 1f - place.coerceIn(0f, 1f) },
            topStart = label?.let { stringResource(Res.string.mochy_lab_fx_on) },
            topEnd = label?.let { stringResource(Res.string.mochy_lab_fx_position, position, count) },
            label = label,
            bars = {},
        )
        if (label != null) {
            Text(
                label.uppercase(),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 0.08.em,
                color = LabCardInk,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 18.dp, end = 6.dp)
                    .graphicsLayer { alpha = place.coerceIn(0f, 1f) }
                    .readingDown(),
            )
        }
    }
}

/** turns a line of text a quarter turn to read top to bottom and lays it out that way round */
private fun Modifier.readingDown(): Modifier = layout { measurable, _ ->
    val text = measurable.measure(Constraints())
    layout(text.height, text.width) {
        text.placeWithLayer(
            x = -(text.width - text.height) / 2,
            y = (text.width - text.height) / 2,
        ) { rotationZ = 90f }
    }
}
