// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

@file:OptIn(ExperimentalMaterial3Api::class)

package com.metrolist.shared.ui.settings

import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_pages_radio_button_checked
import com.metrolist.shared.generated.resources.mochy_pages_radio_button_unchecked
import com.metrolist.shared.generated.resources.search
import com.metrolist.shared.ui.components.ModalBottomSheet
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// Mochy's sheet surfaces: the sheet itself, then each option row on it
private val MochySheetSurface = Color(0xFF0B0B0C)
private val MochySheetRowSurface = Color(0xFF161619)

// a pick-one list longer than this gets the search field, as Mochy's language sheet does
private const val SHEET_SEARCH_THRESHOLD = 12

// Mochy's pick-one sheet for a choice row: the title, an optional search, then one radio row per choice
@Composable
internal fun MochyChoiceSheet(
    title: String,
    choices: List<SettingsChoice>,
    selectedId: String?,
    isVisible: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val needle = query.trim()
    val shown =
        if (needle.isBlank()) {
            choices
        } else {
            choices.filter { it.label.contains(needle, ignoreCase = true) }
        }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        isVisible = isVisible,
        containerColor = MochySheetSurface,
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp)) {
            Text(
                text = title,
                fontFamily = mochyInterFontFamily(),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(start = 4.dp, bottom = 14.dp),
            )
            if (choices.size > SHEET_SEARCH_THRESHOLD) {
                MochySheetSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(Res.string.search),
                )
                Spacer(Modifier.height(10.dp))
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            ) {
                items(shown, key = { it.id }, contentType = { "choice" }) { choice ->
                    MochySheetOptionRow(
                        label = choice.label,
                        selected = choice.id == selectedId,
                        preview = choice.preview,
                        onClick = {
                            onSelect(choice.id)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

// one choice on Mochy's sheet: a radio mark, the label and the choice's own preview at its end when it has one
@Composable
private fun MochySheetOptionRow(
    label: String,
    selected: Boolean,
    preview: (@Composable () -> Unit)?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MochySheetRowSurface)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(
            painter =
                painterResource(
                    if (selected) Res.drawable.mochy_pages_radio_button_checked else Res.drawable.mochy_pages_radio_button_unchecked,
                ),
            contentDescription = null,
            tint = if (selected) Color.White else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            fontFamily = mochyInterFontFamily(),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        preview?.invoke()
    }
}

// Mochy's sheet search: a rounded field on the row colour, its placeholder dimmed until typed into
@Composable
private fun MochySheetSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontFamily = mochyInterFontFamily()),
        cursorBrush = SolidColor(Color.White),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MochySheetRowSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontFamily = mochyInterFontFamily(),
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.4f),
                    )
                }
                inner()
            }
        },
    )
}
