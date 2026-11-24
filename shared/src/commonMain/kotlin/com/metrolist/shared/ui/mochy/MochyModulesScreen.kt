// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

@file:Suppress("FunctionName")

package com.metrolist.shared.ui.mochy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.cancel
import com.metrolist.shared.generated.resources.error_unknown
import com.metrolist.shared.generated.resources.loading
import com.metrolist.shared.generated.resources.mochy_module_configuration
import com.metrolist.shared.generated.resources.mochy_module_diagnostics
import com.metrolist.shared.generated.resources.mochy_module_feature_audio
import com.metrolist.shared.generated.resources.mochy_module_feature_look
import com.metrolist.shared.generated.resources.mochy_module_feature_lyrics
import com.metrolist.shared.generated.resources.mochy_module_feature_metadata
import com.metrolist.shared.generated.resources.mochy_module_not_configured
import com.metrolist.shared.generated.resources.mochy_module_origin_builtin
import com.metrolist.shared.generated.resources.mochy_module_origin_web
import com.metrolist.shared.generated.resources.mochy_modules_add_dialog_title
import com.metrolist.shared.generated.resources.mochy_modules_add_url
import com.metrolist.shared.generated.resources.mochy_modules_catalog_empty
import com.metrolist.shared.generated.resources.mochy_modules_install
import com.metrolist.shared.generated.resources.mochy_modules_installed_badge
import com.metrolist.shared.generated.resources.mochy_modules_refresh
import com.metrolist.shared.generated.resources.mochy_modules_remove
import com.metrolist.shared.generated.resources.mochy_modules_tab_installed
import com.metrolist.shared.generated.resources.mochy_modules_tab_store
import com.metrolist.shared.generated.resources.mochy_modules_test
import com.metrolist.shared.generated.resources.mochy_modules_testing
import com.metrolist.shared.generated.resources.mochy_modules_url_label
import com.metrolist.shared.generated.resources.mochy_modules_url_placeholder
import com.metrolist.shared.generated.resources.retry
import com.metrolist.shared.modules.AccountState
import com.metrolist.shared.modules.CatalogModule
import com.metrolist.shared.modules.DiagnosticStep
import com.metrolist.shared.modules.FieldKind
import com.metrolist.shared.modules.MochyModuleRegistry
import com.metrolist.shared.modules.ModuleCatalog
import com.metrolist.shared.modules.ModuleEntry
import com.metrolist.shared.modules.ModuleFeature
import com.metrolist.shared.modules.ModuleField
import com.metrolist.shared.modules.ModuleOrigin
import com.metrolist.shared.modules.WebModuleManifest
import com.metrolist.shared.ui.components.ExpressiveSwitch
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.settings.LocalSettingsPageBottomPadding
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Multiplatform Mochy Modules management screen (Installed modules, built-in providers including
 * ArchivePool, custom web module URL installer, live module diagnostics, and remote catalog browser).
 */
@Composable
fun MochyModulesScreen(
    modifier: Modifier = Modifier,
    registry: MochyModuleRegistry = MochyModuleRegistry.shared,
) {
    val entries by registry.entries.collectAsState()
    val bottomPadding = LocalSettingsPageBottomPadding.current
    val scope = rememberCoroutineScope()
    val catalog = remember { ModuleCatalog() }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    var catalogLoading by remember { mutableStateOf(false) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var catalogItems by remember { mutableStateOf<List<CatalogModule>>(emptyList()) }
    var catalogLoadedOnce by remember { mutableStateOf(false) }
    var installingUrl by remember { mutableStateOf<String?>(null) }
    var installError by remember { mutableStateOf<String?>(null) }

    val diagnosticSteps = remember { mutableStateMapOf<String, List<DiagnosticStep>>() }
    val testingIds = remember { mutableStateListOf<String>() }

    fun loadCatalog() {
        if (catalogLoading) return
        scope.launch {
            catalogLoading = true
            catalogError = null
            catalog.load().fold(
                onSuccess = { items ->
                    catalogItems = items
                    catalogLoadedOnce = true
                },
                onFailure = { error ->
                    catalogError = error.message
                },
            )
            catalogLoading = false
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && !catalogLoadedOnce && !catalogLoading) {
            loadCatalog()
        }
    }

    val installedWebBases =
        remember(entries) {
            entries
                .mapNotNull { (it.origin as? ModuleOrigin.Web)?.base?.lowercase() }
                .toSet()
        }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                label = { Text(stringResource(Res.string.mochy_modules_tab_installed)) },
            )
            FilterChip(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                label = { Text(stringResource(Res.string.mochy_modules_tab_store)) },
            )
            Spacer(Modifier.weight(1f))
            FilledTonalButton(
                onClick = { showAddDialog = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = MetrolistIcons.Outlined.AddLink,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.mochy_modules_add_url))
            }
        }

        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 24.dp + bottomPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    val isTesting = entry.id in testingIds
                    val steps = diagnosticSteps[entry.id].orEmpty()
                    InstalledModuleCard(
                        entry = entry,
                        expanded = expandedId == entry.id,
                        testing = isTesting,
                        steps = steps,
                        onToggleExpand = {
                            expandedId = if (expandedId == entry.id) null else entry.id
                        },
                        onEnabledChange = { enabled ->
                            registry.setEnabled(entry.id, enabled)
                        },
                        onPickChange = { choiceId, optionId ->
                            registry.setPick(entry.id, choiceId, optionId)
                        },
                        onFieldChange = { fieldId, value ->
                            registry.setField(entry.id, fieldId, value)
                        },
                        onTest = {
                            if (entry.id !in testingIds) {
                                testingIds.add(entry.id)
                                diagnosticSteps[entry.id] = emptyList()
                                scope.launch {
                                    registry.diagnose(entry.id) { step ->
                                        val current = diagnosticSteps[entry.id].orEmpty()
                                        val updated = current.filterNot { it.name == step.name } + step
                                        diagnosticSteps[entry.id] = updated
                                    }
                                    testingIds.remove(entry.id)
                                }
                            }
                        },
                        onRemove =
                            if (entry.origin is ModuleOrigin.Web) {
                                {
                                    registry.removeWebModule(entry.id)
                                    if (expandedId == entry.id) expandedId = null
                                }
                            } else {
                                null
                            },
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 24.dp + bottomPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item("catalog_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { loadCatalog() }, enabled = !catalogLoading) {
                            Icon(
                                imageVector = MetrolistIcons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(Res.string.mochy_modules_refresh))
                        }
                    }
                }

                installError?.let { message ->
                    item("install_error") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }

                when {
                    catalogLoading && catalogItems.isEmpty() -> {
                        item("loading") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                    Text(
                                        text = stringResource(Res.string.loading),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    catalogError != null && catalogItems.isEmpty() -> {
                        item("error") {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = catalogError ?: stringResource(Res.string.error_unknown),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    OutlinedButton(onClick = { loadCatalog() }) {
                                        Text(stringResource(Res.string.retry))
                                    }
                                }
                            }
                        }
                    }

                    catalogItems.isEmpty() -> {
                        item("empty") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(Res.string.mochy_modules_catalog_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    else -> {
                        items(catalogItems, key = { it.url }) { item ->
                            val normalizedBase = WebModuleManifest.baseOf(item.url)?.lowercase()
                            val alreadyInstalled = normalizedBase != null && normalizedBase in installedWebBases
                            val isInstalling = installingUrl == item.url
                            CatalogModuleCard(
                                item = item,
                                installed = alreadyInstalled,
                                installing = isInstalling,
                                onInstall = {
                                    if (installingUrl == null) {
                                        installingUrl = item.url
                                        installError = null
                                        scope.launch {
                                            registry.addWebModule(item.url).onFailure { err ->
                                                installError = err.message
                                            }
                                            installingUrl = null
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddWebModuleDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { url -> registry.addWebModule(url) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InstalledModuleCard(
    entry: ModuleEntry,
    expanded: Boolean,
    testing: Boolean,
    steps: List<DiagnosticStep>,
    onToggleExpand: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onPickChange: (String, String) -> Unit,
    onFieldChange: (String, String) -> Unit,
    onTest: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleExpand)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ModuleAvatar(iconUrl = entry.icon)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = entry.module.info.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        val originLabel =
                            when (entry.origin) {
                                ModuleOrigin.BuiltIn -> stringResource(Res.string.mochy_module_origin_builtin)
                                is ModuleOrigin.Web -> stringResource(Res.string.mochy_module_origin_web)
                            }
                        ModuleBadge(text = originLabel)
                    }
                    val statusText =
                        when (val account = entry.account) {
                            is AccountState.SignedIn -> account.label
                            is AccountState.Expired -> account.reason
                            AccountState.SignedOut -> stringResource(Res.string.mochy_module_not_configured)
                        }
                    val statusColor =
                        when (entry.account) {
                            is AccountState.SignedIn -> MaterialTheme.colorScheme.primary
                            is AccountState.Expired -> MaterialTheme.colorScheme.error
                            AccountState.SignedOut -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (entry.module.adds.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            entry.module.adds.forEach { feature ->
                                ModuleBadge(text = featureLabel(feature), subtle = true)
                            }
                        }
                    }
                }
                ExpressiveSwitch(
                    checked = entry.enabled,
                    onCheckedChange = onEnabledChange,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text(
                        text = entry.module.info.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    entry.module.choices.forEach { choice ->
                        val selectedOption = entry.picks[choice.id] ?: choice.options.firstOrNull()?.id
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = choice.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                choice.options.forEach { option ->
                                    FilterChip(
                                        selected = option.id == selectedOption,
                                        onClick = { onPickChange(choice.id, option.id) },
                                        label = { Text(option.label) },
                                    )
                                }
                            }
                        }
                    }

                    if (entry.fields.isNotEmpty()) {
                        Text(
                            text = stringResource(Res.string.mochy_module_configuration),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                        entry.fields.forEach { field ->
                            ModuleFieldEditor(
                                field = field,
                                value = entry.values[field.id] ?: field.default,
                                onValueChange = { onFieldChange(field.id, it) },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(
                            onClick = onTest,
                            enabled = !testing,
                        ) {
                            if (testing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(Res.string.mochy_modules_testing))
                            } else {
                                Icon(
                                    imageVector = MetrolistIcons.Outlined.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(Res.string.mochy_modules_test))
                            }
                        }
                        if (onRemove != null) {
                            OutlinedButton(onClick = onRemove) {
                                Icon(
                                    imageVector = MetrolistIcons.Outlined.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(Res.string.mochy_modules_remove))
                            }
                        }
                    }

                    if (steps.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(Res.string.mochy_module_diagnostics),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                steps.forEach { step ->
                                    DiagnosticStepRow(step)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleFieldEditor(
    field: ModuleField,
    value: String,
    onValueChange: (String) -> Unit,
) {
    when (field.kind) {
        FieldKind.Toggle -> {
            val checked = value.equals("true", ignoreCase = true)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = field.label,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (field.description.isNotBlank()) {
                        Text(
                            text = field.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ExpressiveSwitch(
                    checked = checked,
                    onCheckedChange = { onValueChange(it.toString()) },
                )
            }
        }

        FieldKind.Select -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = field.label,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (field.description.isNotBlank()) {
                    Text(
                        text = field.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    field.options.forEach { option ->
                        FilterChip(
                            selected = option.id == value,
                            onClick = { onValueChange(option.id) },
                            label = { Text(option.label) },
                        )
                    }
                }
            }
        }

        FieldKind.Text -> {
            var draft by remember(field.id, value) { mutableStateOf(value) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { updated ->
                        draft = updated
                        onValueChange(updated)
                    },
                    label = { Text(field.label) },
                    placeholder =
                        if (field.hint.isNotBlank()) {
                            { Text(field.hint) }
                        } else {
                            null
                        },
                    singleLine = true,
                    visualTransformation =
                        if (field.secret) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (field.description.isNotBlank()) {
                    Text(
                        text = field.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticStepRow(step: DiagnosticStep) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (step.ok) {
            true -> {
                Icon(
                    imageVector = MetrolistIcons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }

            false -> {
                Icon(
                    imageVector = MetrolistIcons.Filled.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }

            null -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = step.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = step.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogModuleCard(
    item: CatalogModule,
    installed: Boolean,
    installing: Boolean,
    onInstall: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModuleAvatar(iconUrl = item.icon)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.author?.takeIf { it.isNotBlank() }?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.adds.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        item.adds.forEach { feature ->
                            ModuleBadge(text = featureLabel(feature), subtle = true)
                        }
                    }
                }
            }
            if (installed) {
                ModuleBadge(text = stringResource(Res.string.mochy_modules_installed_badge))
            } else {
                Button(
                    onClick = onInstall,
                    enabled = !installing,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    if (installing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(Res.string.mochy_modules_install))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddWebModuleDialog(
    onDismiss: () -> Unit,
    onAdd: suspend (String) -> Result<ModuleEntry>,
) {
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(Res.string.mochy_modules_add_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        error = null
                    },
                    label = { Text(stringResource(Res.string.mochy_modules_url_label)) },
                    placeholder = { Text(stringResource(Res.string.mochy_modules_url_placeholder)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = url.trim()
                    if (trimmed.isEmpty()) return@Button
                    busy = true
                    error = null
                    scope.launch {
                        onAdd(trimmed).fold(
                            onSuccess = {
                                busy = false
                                onDismiss()
                            },
                            onFailure = { err ->
                                busy = false
                                error = err.message
                            },
                        )
                    }
                },
                enabled = !busy && url.isNotBlank(),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(Res.string.mochy_modules_install))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun ModuleAvatar(iconUrl: String?) {
    Box(
        modifier =
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (!iconUrl.isNullOrBlank()) {
            AsyncImage(
                model = iconUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = MetrolistIcons.Outlined.Extension,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ModuleBadge(
    text: String,
    subtle: Boolean = false,
) {
    val container =
        if (subtle) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    val content =
        if (subtle) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = container,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun featureLabel(feature: ModuleFeature): String =
    when (feature) {
        ModuleFeature.AudioSource -> stringResource(Res.string.mochy_module_feature_audio)
        ModuleFeature.Lyrics -> stringResource(Res.string.mochy_module_feature_lyrics)
        ModuleFeature.Look -> stringResource(Res.string.mochy_module_feature_look)
        ModuleFeature.Metadata -> stringResource(Res.string.mochy_module_feature_metadata)
    }
