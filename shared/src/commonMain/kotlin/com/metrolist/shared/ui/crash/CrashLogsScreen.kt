package com.metrolist.shared.ui.crash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.crash.CrashLogsState
import com.metrolist.shared.crash.CrashReport
import com.metrolist.shared.crash.CrashStore
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.back
import com.metrolist.shared.generated.resources.crash_clear
import com.metrolist.shared.generated.resources.crash_close
import com.metrolist.shared.generated.resources.crash_copy_logs
import com.metrolist.shared.generated.resources.crash_logs
import com.metrolist.shared.generated.resources.crash_no_log
import com.metrolist.shared.generated.resources.crash_share_logs
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import com.metrolist.shared.ui.utils.DragScrollableColumn
import com.metrolist.shared.ui.utils.setText
import com.metrolist.shared.utils.logTextChunks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

@Composable
fun CrashLogsScreen(
    store: CrashStore,
    onClose: () -> Unit,
    onShare: (String) -> Unit,
) {
    val state = remember { CrashLogsState(store) }
    val reports by state.reports.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(state) { state.load() }
    var selected by remember { mutableStateOf<CrashReport?>(null) }
    val chunks by produceState(emptyList<String>(), selected) {
        value = withContext(Dispatchers.Default) { selected?.let { logTextChunks(it.text) }.orEmpty() }
    }
    val clipboard = LocalClipboard.current
    val reportToShare = selected ?: reports.firstOrNull()
    CrashScreenshotHint(
        onCopy = if (reportToShare == null) null else ({ scope.launch { clipboard.setText(reportToShare.text) } }),
        onShare = reportToShare?.let { report -> { onShare(report.text) } },
    )
    val report = selected
    MochyCrashPage(
        title = stringResource(Res.string.crash_logs),
        headerIcon = if (report != null) MetrolistIcons.AutoMirrored.Filled.ArrowBack else MetrolistIcons.Default.Close,
        headerIconDescription = stringResource(if (report != null) Res.string.back else Res.string.crash_close),
        onHeaderClick = { if (report != null) selected = null else onClose() },
        headerEnd = {
            if (reports.isNotEmpty()) {
                MochyCrashRoundButton(MetrolistIcons.Outlined.Delete, stringResource(Res.string.crash_clear)) {
                    selected = null
                    scope.launch { state.clear() }
                }
            }
        },
    ) {
        if (report != null) {
            MochyCrashBodyText(reportLabel(report))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MochyPromptButton(
                    label = stringResource(Res.string.crash_copy_logs),
                    onClick = { scope.launch { clipboard.setText(report.text) } },
                    modifier = Modifier.weight(1f),
                )
                MochyPromptButton(
                    label = stringResource(Res.string.crash_share_logs),
                    onClick = { onShare(report.text) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            MochyCrashCard(Modifier.weight(1f)) {
                SelectionContainer {
                    DragScrollableColumn {
                        items(chunks.size) { index -> MochyCrashLogText(chunks[index].removeSuffix("\n")) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        } else {
            CrashScreenshotHintText()
            Spacer(Modifier.height(16.dp))
            if (reports.isEmpty()) {
                MochyCrashBodyText(stringResource(Res.string.crash_no_log))
            } else {
                DragScrollableColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(reports, key = { it.id }) { entry ->
                        CrashReportRow(entry, onClick = { selected = entry })
                    }
                }
            }
        }
    }
}

private fun reportLabel(report: CrashReport): String = "${Instant.fromEpochMilliseconds(report.timestamp)} \u00b7\u00a0${report.version}"

// one saved crash: when it happened and on which version, on the update prompt's card
@Composable
private fun CrashReportRow(
    report: CrashReport,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MochyCrashCardColor)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = reportLabel(report),
            color = Color.White,
            fontFamily = mochyInterFontFamily(),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            MetrolistIcons.Outlined.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp),
        )
    }
}
