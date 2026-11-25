// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// Home while its first load is still out: the hero's bones over a faint fade, then two shelves of card bones

package com.metrolist.shared.ui.mochy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val BoneTint = Color(0xFFD9D9D9).copy(alpha = 0.22f)
private val CardSize = 137.dp
private val CardSpacing = 14.dp
private val CardCorner = 16.dp

/** sized like the phone hero (60% of the window) so nothing jumps when the real one lands */
@Composable
fun MochyHomeBones(modifier: Modifier = Modifier) {
    val windowHeight =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height
                .toDp()
        }
    Column(modifier = modifier.fillMaxWidth()) {
        BonesHost(modifier = Modifier.fillMaxWidth().height(windowHeight * 0.6f)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Brush.verticalGradient(listOf(BoneTint.copy(alpha = 0.12f), Color.Transparent))),
            ) {
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = MochyHomeContentInset, bottom = 64.dp),
                ) {
                    Bone(width = 72.dp, height = 14.dp)
                    Spacer(Modifier.height(8.dp))
                    Bone(width = 220.dp, height = 22.dp)
                    Spacer(Modifier.height(13.dp))
                    Bone(width = 93.dp, height = 33.dp, shape = RoundedCornerShape(50))
                }
            }
        }
        BonesHost(modifier = Modifier.fillMaxWidth()) {
            repeat(2) {
                SectionBones(CardSize)
                Spacer(Modifier.height(34.dp))
            }
        }
    }
}

@Composable
private fun SectionBones(size: Dp) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Bone(width = 128.dp, height = 15.dp, modifier = Modifier.padding(start = MochyHomeContentInset))
        Spacer(Modifier.height(14.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(CardSpacing),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .padding(start = MochyHomeContentInset),
        ) {
            repeat(3) {
                Column {
                    Bone(width = size, height = size, shape = RoundedCornerShape(CardCorner))
                    Spacer(Modifier.height(8.dp))
                    Bone(width = size * 0.62f, height = 15.dp)
                    Spacer(Modifier.height(3.dp))
                    Bone(width = size * 0.28f, height = 11.dp)
                }
            }
        }
    }
}
