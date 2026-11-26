// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.audiolab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.metrolist.shared.audiolab.FxPreset
import com.metrolist.shared.audiolab.eightDSettings
import com.metrolist.shared.audiolab.reverbSettings
import com.metrolist.shared.audiolab.spatialSettings
import com.metrolist.shared.audiolab.stageSettings
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_lab_cancel
import com.metrolist.shared.generated.resources.mochy_lab_preset_add
import com.metrolist.shared.generated.resources.mochy_lab_preset_delete
import com.metrolist.shared.generated.resources.mochy_lab_preset_edit
import com.metrolist.shared.generated.resources.mochy_lab_preset_edit_title
import com.metrolist.shared.generated.resources.mochy_lab_preset_name_hint
import com.metrolist.shared.generated.resources.mochy_lab_preset_new
import com.metrolist.shared.generated.resources.mochy_lab_preset_save
import com.metrolist.shared.generated.resources.mochy_lab_preset_taken
import com.metrolist.shared.generated.resources.mochy_lab_presets
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.theme.mochyInterFontFamily

// long enough for a real name, short enough that its chip still fits on a phone
private const val MaxNameLength = 32

/** the presets row on the Audio Lab page: saved effect snapshots, a tap applies one, a long press renames it */
@Composable
internal fun FxPresetsSection() {
    val presets = Settings.shared.fxPresets
    val pickedId = Settings.shared.fxPresetId
    val pickedPreset = presets.firstOrNull { it.id == pickedId }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FxPreset?>(null) }

    Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(Res.string.mochy_lab_presets),
            fontFamily = mochyInterFontFamily(),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            letterSpacing = (-0.02).em,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        if (pickedPreset != null) {
            LabTextAction(stringResource(Res.string.mochy_lab_preset_edit)) { editing = pickedPreset }
        }
    }
    Spacer(Modifier.height(10.dp))
    LabChips(
        chips = presets.map { LabChip(it.id, it.name) },
        selectedKey = pickedId,
        // tapping the picked one again lets go of it, so you can try things without changing it
        onSelect = { id ->
            if (id == pickedId) {
                Settings.shared.fxPresetId = ""
            } else {
                presets.firstOrNull { it.id == id }?.let { applyFxPreset(it) }
            }
        },
        onLongPress = { id -> editing = presets.firstOrNull { it.id == id } },
        leading = { AddPresetButton { creating = true } },
    )

    if (creating) {
        FxPresetNameDialog(
            title = stringResource(Res.string.mochy_lab_preset_new),
            initialName = "",
            takenNames = presets.map { it.name },
            onDone = { name ->
                Settings.shared.saveFxPreset(
                    name = name,
                    reverb = Settings.shared.reverbSettings(),
                    spatial = Settings.shared.spatialSettings(),
                    eightD = Settings.shared.eightDSettings(),
                    stage = Settings.shared.stageSettings(),
                )
                creating = false
            },
            onDelete = null,
            onDismiss = { creating = false },
        )
    }
    editing?.let { preset ->
        FxPresetNameDialog(
            title = stringResource(Res.string.mochy_lab_preset_edit_title),
            initialName = preset.name,
            takenNames = presets.filter { it.id != preset.id }.map { it.name },
            onDone = { name ->
                Settings.shared.renameFxPreset(preset.id, name)
                editing = null
            },
            onDelete = {
                Settings.shared.deleteFxPreset(preset.id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** writes a preset's effects back into the settings every card reads, and marks it as the picked one */
private fun applyFxPreset(preset: FxPreset) {
    saveReverb(preset.reverb)
    saveSpatial(preset.spatial)
    saveEightD(preset.eightD)
    saveStage(preset.stage)
    Settings.shared.fxPresetId = preset.id
}

// the round plus in front of the chips, the same height so the row reads as one
@Composable
private fun AddPresetButton(onClick: () -> Unit) {
    val label = stringResource(Res.string.mochy_lab_preset_add)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(LabColors.Chip)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.size(18.dp)) {
            val stroke = 2.dp.toPx()
            drawLine(Color.White, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), stroke, StrokeCap.Round)
            drawLine(Color.White, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), stroke, StrokeCap.Round)
        }
    }
}

/** names a new preset or renames one; a taken name is refused, and the bin only shows while renaming */
@Composable
private fun FxPresetNameDialog(
    title: String,
    initialName: String,
    takenNames: List<String>,
    onDone: (String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var field by remember { mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length))) }
    val typed = field.text.trim()
    // keeping its own name while editing is fine, wearing another preset's isn't
    val taken = typed.isNotEmpty() && !typed.equals(initialName.trim(), ignoreCase = true) &&
        takenNames.any { it.trim().equals(typed, ignoreCase = true) }
    val canSave = typed.isNotEmpty() && !taken

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = mochyInterFontFamily(), fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = field,
                    onValueChange = { if (it.text.length <= MaxNameLength) field = it },
                    singleLine = true,
                    isError = taken,
                    placeholder = { Text(stringResource(Res.string.mochy_lab_preset_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (taken) {
                    Text(
                        stringResource(Res.string.mochy_lab_preset_taken),
                        fontFamily = mochyInterFontFamily(),
                        fontSize = 13.sp,
                        color = Color(0xFFFF8A80),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(typed) }, enabled = canSave) {
                Text(stringResource(Res.string.mochy_lab_preset_save))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(Res.string.mochy_lab_preset_delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.mochy_lab_cancel)) }
            }
        },
    )
}
