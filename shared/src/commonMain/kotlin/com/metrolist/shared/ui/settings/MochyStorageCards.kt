// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// Mochy's storage page: how much of the phone Mochy takes next to everything else, what that's made of as one
// colour-split bar with its legend, and the pill buttons that cut the junk.

package com.metrolist.shared.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_storage_breakdown_title
import com.metrolist.shared.generated.resources.mochy_storage_free
import com.metrolist.shared.generated.resources.mochy_storage_junk_title
import com.metrolist.shared.generated.resources.mochy_storage_legend_animated
import com.metrolist.shared.generated.resources.mochy_storage_legend_cache
import com.metrolist.shared.generated.resources.mochy_storage_legend_downloads
import com.metrolist.shared.generated.resources.mochy_storage_legend_images
import com.metrolist.shared.generated.resources.mochy_storage_legend_mochy
import com.metrolist.shared.generated.resources.mochy_storage_legend_yours
import com.metrolist.shared.generated.resources.mochy_storage_measuring
import com.metrolist.shared.generated.resources.mochy_storage_overall
import com.metrolist.shared.generated.resources.mochy_storage_uses
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import com.metrolist.shared.ui.utils.rememberFileSizeFormatter
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private val StorageCard = Color(0xFF1C1C1E)
private val StorageTrack = Color(0xFF3A3A3C)
private val StorageMuted = Color(0xFF8E8E93)
private val StorageDanger = Color(0xFFF07A70)
private val MochyGreen = Color(0xFF86EF7F)
private val OthersGrey = Color(0xFF6E6E73)
private val CacheOrange = Color(0xFFF5B26B)
private val CoversYellow = Color(0xFFE9D86A)
private val AppDataBlue = Color(0xFF6878F0)
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** What Mochy keeps on the phone, and the phone's own room; null while it's measured. */
class MochyStorageUsage(
    val songCache: Long,
    val downloads: Long,
    val imageCache: Long,
    val animatedCache: Long,
    val phoneTotal: Long,
    val phoneFree: Long,
) {
    val mochy: Long get() = songCache + downloads + imageCache + animatedCache
}

/** One of the pill buttons under the breakdown. */
class MochyStorageAction(
    val label: String,
    val onClick: () -> Unit,
    val detail: String? = null,
    val danger: Boolean = false,
    val enabled: Boolean = true,
)

@Composable
fun MochyStorageOverview(
    usage: MochyStorageUsage?,
    actions: List<MochyStorageAction>,
    modifier: Modifier = Modifier,
) {
    val format = rememberFileSizeFormatter()
    Column(modifier.fillMaxWidth()) {
        StorageCardColumn {
            Text(stringResource(Res.string.mochy_storage_overall), style = storageText(17f))
            Spacer(Modifier.height(14.dp))
            val others = usage?.let { (it.phoneTotal - it.phoneFree - it.mochy).coerceAtLeast(0L) } ?: 0L
            UsageBar(listOf(MochyGreen to (usage?.mochy ?: 0L), OthersGrey to others), usage?.phoneTotal ?: 0L)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                LegendSwatch(MochyGreen)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.mochy_storage_legend_mochy), style = storageText(16f, FontWeight.SemiBold))
                Spacer(Modifier.width(16.dp))
                LegendSwatch(OthersGrey)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.mochy_storage_legend_yours),
                    style = storageText(16f, FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (usage != null) {
                    Text(
                        stringResource(Res.string.mochy_storage_free, format(usage.phoneFree)),
                        style = storageText(12f, FontWeight.Medium, StorageMuted, 0f),
                    )
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        SectionText(
            if (usage == null) {
                stringResource(Res.string.mochy_storage_measuring)
            } else {
                stringResource(Res.string.mochy_storage_uses, percentOf(usage.mochy, usage.phoneTotal), format(usage.mochy))
            },
        )
        Spacer(Modifier.height(14.dp))
        StorageCardColumn {
            Text(stringResource(Res.string.mochy_storage_breakdown_title), style = storageText(17f))
            Spacer(Modifier.height(14.dp))
            UsageBar(
                listOf(
                    CacheOrange to (usage?.songCache ?: 0L),
                    MochyGreen to (usage?.downloads ?: 0L),
                    CoversYellow to (usage?.imageCache ?: 0L),
                    AppDataBlue to (usage?.animatedCache ?: 0L),
                ),
                usage?.mochy ?: 0L,
            )
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LegendRow(CacheOrange, stringResource(Res.string.mochy_storage_legend_cache), usage?.let { format(it.songCache) })
                LegendRow(MochyGreen, stringResource(Res.string.mochy_storage_legend_downloads), usage?.let { format(it.downloads) })
                LegendRow(CoversYellow, stringResource(Res.string.mochy_storage_legend_images), usage?.let { format(it.imageCache) })
                LegendRow(AppDataBlue, stringResource(Res.string.mochy_storage_legend_animated), usage?.let { format(it.animatedCache) })
            }
        }
        if (actions.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            SectionText(stringResource(Res.string.mochy_storage_junk_title))
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { actions.forEach { ActionButton(it) } }
        }
        Spacer(Modifier.height(26.dp))
    }
}

@Composable
private fun storageText(
    size: Float,
    weight: FontWeight = FontWeight.Bold,
    color: Color = Color.White,
    tracking: Float = -0.5f,
) = TextStyle(fontFamily = mochyInterFontFamily(), fontWeight = weight, fontSize = size.sp, letterSpacing = tracking.sp, color = color)

@Composable
private fun SectionText(text: String) {
    Text(text, style = storageText(17f, FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 2.dp))
}

// like 1.4%, or <0.1% for a sliver, so a small app never reads as using nothing
private fun percentOf(
    part: Long,
    total: Long,
): String {
    if (total <= 0L) return "0%"
    val p = part * 100.0 / total
    return when {
        p < 0.1 -> "<0.1%"
        p < 10 -> "${(p * 10).roundToInt() / 10.0}%"
        else -> "${p.toInt()}%"
    }
}

@Composable
private fun StorageCardColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(StorageCard)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        content = content,
    )
}

// a rounded bar split into parts over the total, a part too small to see still a sliver so the legend is never missing one
@Composable
private fun UsageBar(
    parts: List<Pair<Color, Long>>,
    total: Long,
) {
    val grow = remember { Animatable(0f) }
    val ready = total > 0
    LaunchedEffect(ready) { if (ready) grow.animateTo(1f, tween(1100, easing = Emphasized)) }
    val fractions =
        parts.map { (_, bytes) ->
            animateFloatAsState(
                targetValue = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 120f),
                label = "usageBarPart",
            )
        }
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(StorageTrack)
            .drawBehind {
                val minSliver = 3.dp.toPx()
                var x = 0f
                parts.forEachIndexed { i, (color, bytes) ->
                    if (bytes <= 0L) return@forEachIndexed
                    val width = (fractions[i].value * size.width).coerceAtLeast(minSliver) * grow.value
                    drawRect(color, Offset(x, 0f), Size(width.coerceAtMost(size.width - x), size.height))
                    x += width
                }
            },
    )
}

@Composable
private fun LegendSwatch(color: Color) {
    Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(color))
}

@Composable
private fun LegendRow(
    color: Color,
    label: String,
    size: String?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        LegendSwatch(color)
        Spacer(Modifier.width(10.dp))
        Text(label, style = storageText(17f, FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1)
        if (size != null) Text(size, style = storageText(12f, FontWeight.Medium, StorageMuted, 0f))
    }
}

@Composable
private fun ActionButton(action: MochyStorageAction) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .graphicsLayer { alpha = if (action.enabled) 1f else 0.45f }
                .clip(CircleShape)
                .background(StorageCard)
                .clickable(enabled = action.enabled, onClick = action.onClick)
                .padding(horizontal = 20.dp, vertical = 9.dp),
    ) {
        Text(
            action.label,
            style = storageText(15f, FontWeight.SemiBold, if (action.danger) StorageDanger else Color.White, -0.4f),
            textAlign = TextAlign.Center,
        )
        if (action.detail != null) {
            Text(action.detail, style = storageText(11f, FontWeight.Medium, StorageMuted, 0f), textAlign = TextAlign.Center)
        }
    }
}
