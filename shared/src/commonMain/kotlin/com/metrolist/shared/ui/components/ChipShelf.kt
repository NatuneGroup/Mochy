package com.metrolist.shared.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.chip_content_description
import com.metrolist.shared.ui.LocalIsTv
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import com.metrolist.shared.ui.tv.tvFocusHighlight
import com.metrolist.shared.ui.utils.carouselScroll
import org.jetbrains.compose.resources.stringResource

private val ChipShelfHeight = 48.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ChipShelf(
    options: List<T>,
    selectedOption: T?,
    onSelectionChange: (T?) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    allLabel: String? = null,
    pinnedLabel: String? = null,
    onPinnedClick: () -> Unit = {},
    selectionRequired: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp),
    leadingContent: (@Composable () -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(ChipShelfHeight)
                .clipToBounds()
                .focusGroup()
                .selectableGroup()
                .carouselScroll(scrollState)
                .horizontalScroll(scrollState)
                .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingContent?.invoke()
        pinnedLabel?.let { text ->
            ShelfChip(
                text = text,
                selected = true,
                onClick = onPinnedClick,
            )
        }
        allLabel?.let { text ->
            ShelfChip(
                text = text,
                selected = selectedOption == null,
                onClick = { onSelectionChange(null) },
            )
        }
        options.forEach { option ->
            val selected = selectedOption == option
            ShelfChip(
                text = label(option),
                selected = selected,
                onClick = {
                    onSelectionChange(
                        if (selected && !selectionRequired) null else option,
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShelfChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerHeight = ButtonDefaults.ExtraSmallContainerHeight
    val chipDescription = stringResource(Res.string.chip_content_description, text)
    // the dark theme draws Mochy's chips: a dark pill, the picked one white with black text
    if (!LocalIsTv.current && MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Color.White else Color(0xFF121214))
                    .clickable(onClick = onClick)
                    .clearAndSetSemantics {
                        contentDescription = chipDescription
                        role = Role.Button
                        this.selected = selected
                    }.padding(horizontal = 16.dp),
        ) {
            Text(
                text = text,
                fontFamily = mochyInterFontFamily(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (selected) Color.Black else Color.White,
                maxLines = 1,
            )
        }
        return
    }
    ExpressiveFilterChip(
        selected = selected,
        onClick = onClick,
        modifier =
            Modifier.height(containerHeight).clearAndSetSemantics {
                contentDescription = chipDescription
                role = Role.Button
                this.selected = selected
                onClick {
                    onClick()
                    true
                }
            },
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        enabled = enabled,
        shapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.ExtraSmallContainerHeight),
        colors =
            ToggleButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        contentPadding = ButtonDefaults.ExtraSmallContentPadding,
        modifier =
            modifier
                .height(ButtonDefaults.ExtraSmallContainerHeight)
                .tvFocusHighlight(ToggleButtonDefaults.shape)
                .semantics { this.selected = selected },
    ) {
        label()
    }
}
