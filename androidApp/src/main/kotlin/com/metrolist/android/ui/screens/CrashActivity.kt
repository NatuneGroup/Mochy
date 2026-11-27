/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.android.ui.screens

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.metrolist.android.MainActivity
import com.metrolist.android.R
import com.metrolist.android.ui.screens.settings.UpdateDetailsScreen
import com.metrolist.android.update.AppUpdateManager
import com.metrolist.android.update.AppUpdateState
import com.metrolist.android.update.GitHubRelease
import com.metrolist.android.utils.CrashHandler
import com.metrolist.android.utils.CrashLogUploader
import com.metrolist.android.utils.CrashPaste
import com.metrolist.android.utils.redactCrashLog
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.crash_close
import com.metrolist.shared.generated.resources.crash_copy_logs
import com.metrolist.shared.generated.resources.crash_description
import com.metrolist.shared.generated.resources.crash_logs
import com.metrolist.shared.generated.resources.crash_no_log
import com.metrolist.shared.generated.resources.crash_paste_id
import com.metrolist.shared.generated.resources.crash_paste_url
import com.metrolist.shared.generated.resources.crash_report_subject
import com.metrolist.shared.generated.resources.crash_restart
import com.metrolist.shared.generated.resources.crash_retry
import com.metrolist.shared.generated.resources.crash_share_logs
import com.metrolist.shared.generated.resources.crash_share_title
import com.metrolist.shared.generated.resources.crash_title
import com.metrolist.shared.generated.resources.crash_updates_action
import com.metrolist.shared.generated.resources.crash_updates_check
import com.metrolist.shared.generated.resources.crash_updates_checking
import com.metrolist.shared.generated.resources.crash_updates_error
import com.metrolist.shared.generated.resources.crash_updates_install
import com.metrolist.shared.generated.resources.crash_updates_no_updates
import com.metrolist.shared.generated.resources.crash_updates_prompt_later
import com.metrolist.shared.generated.resources.crash_updates_prompt_text
import com.metrolist.shared.generated.resources.crash_updates_prompt_title
import com.metrolist.shared.generated.resources.crash_upload
import com.metrolist.shared.generated.resources.crash_upload_disclosure
import com.metrolist.shared.generated.resources.crash_upload_failed
import com.metrolist.shared.generated.resources.crash_uploading
import com.metrolist.shared.ui.LocalIsTv
import com.metrolist.shared.ui.components.ContainedLoadingIndicator
import com.metrolist.shared.ui.crash.CrashLogsScreen
import com.metrolist.shared.ui.crash.CrashScreenshotHint
import com.metrolist.shared.ui.crash.CrashScreenshotHintText
import com.metrolist.shared.ui.crash.MochyCrashBodyText
import com.metrolist.shared.ui.crash.MochyCrashCard
import com.metrolist.shared.ui.crash.MochyCrashErrorText
import com.metrolist.shared.ui.crash.MochyCrashLogText
import com.metrolist.shared.ui.crash.MochyCrashMark
import com.metrolist.shared.ui.crash.MochyCrashPage
import com.metrolist.shared.ui.crash.MochyPromptButton
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.tv.tvHorizontalScrollFocus
import com.metrolist.shared.ui.tv.tvVerticalScrollFocus
import com.metrolist.shared.ui.utils.setText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashActivity : ComponentActivity() {
    private val crashLog by lazy {
        CrashHandler
            .store(this)
            .list()
            .firstOrNull { it.id == intent.getStringExtra(CrashHandler.EXTRA_CRASH_ID) }
            ?.text
            ?: getString(R.string.crash_no_log)
    }
    private val crashViewModel: CrashViewModel by viewModels {
        CrashViewModel.Factory(crashLog)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra(CrashHandler.EXTRA_CRASH_ID)?.let { CrashHandler.store(this).acknowledge(it) }
        val failedProcess = intent.getIntExtra(CrashHandler.EXTRA_FAILED_PROCESS, 0)
        intent.removeExtra(CrashHandler.EXTRA_FAILED_PROCESS)
        if (failedProcess > 0 && failedProcess != Process.myPid()) Process.killProcess(failedProcess)
        WindowCompat.enableEdgeToEdge(window)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = true
        }

        setContent {
            // Mochy is a dark app, and its crash page is drawn on its black
            val darkTheme = true
            val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
            SideEffect {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    // Android 7 only supports light navigation icons.
                    val navigationBarColor =
                        if (Build.VERSION.SDK_INT <
                            Build.VERSION_CODES.O
                        ) {
                            darkColorScheme().surface
                        } else {
                            colorScheme.surface
                        }
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = navigationBarColor.toArgb()
                }
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            MaterialTheme(colorScheme = colorScheme) {
                CompositionLocalProvider(
                    LocalIsTv provides
                        (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION),
                ) {
                    var showLogs by remember { mutableStateOf(intent.action == CrashHandler.ACTION_LOGS) }
                    var showUpdate by remember { mutableStateOf(false) }
                    val clipboard = LocalClipboard.current
                    val scope = rememberCoroutineScope()
                    var updatePrompt by remember { mutableStateOf<GitHubRelease?>(null) }
                    LaunchedEffect(Unit) {
                        updatePrompt = AppUpdateManager.check(this@CrashActivity, crashRecovery = true)
                    }
                    updatePrompt?.let { release ->
                        AlertDialog(
                            onDismissRequest = { updatePrompt = null },
                            title = { Text(stringResource(Res.string.crash_updates_prompt_title)) },
                            text = { Text(stringResource(Res.string.crash_updates_prompt_text, release.version)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    updatePrompt = null
                                    showUpdate = true
                                    scope.launch { AppUpdateManager.download(this@CrashActivity, release, crashRecovery = true) }
                                }) { Text(stringResource(Res.string.crash_updates_install)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { updatePrompt = null }) {
                                    Text(stringResource(Res.string.crash_updates_prompt_later))
                                }
                            },
                        )
                    }
                    if (showLogs) {
                        CrashLogsScreen(CrashHandler.store(this), onClose = {
                            if (intent.action == CrashHandler.ACTION_LOGS) finish() else showLogs = false
                        }, onShare = ::shareCrashLog)
                    } else if (showUpdate) {
                        UpdateDetailsScreen(onNavigateBack = { showUpdate = false }, crashRecovery = true)
                    } else {
                        CrashScreen(
                            crashLog = crashLog,
                            uploadState = crashViewModel.uploadState,
                            onClose = { finishAffinity() },
                            onRestart = {
                                startActivity(
                                    Intent(
                                        this,
                                        MainActivity::class.java,
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                                )
                                finishAffinity()
                            },
                            onCopy = { scope.launch { clipboard.setText(crashLog) } },
                            onLogs = { showLogs = true },
                            onUpdate = { showUpdate = true },
                            onRetryUpload = crashViewModel::upload,
                            onShare = { paste ->
                                shareCrashLog(
                                    crashLogWithPaste(
                                        crashLog,
                                        paste,
                                        getString(R.string.crash_paste_id, paste?.id.orEmpty()),
                                        getString(R.string.crash_paste_url, paste?.url.orEmpty()),
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    private fun shareCrashLog(crashLog: String) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "metrolist_crash_$timestamp.txt"
            val crashDir = File(cacheDir, "crashes").apply { mkdirs() }
            val crashFile = File(crashDir, fileName)
            crashFile.writeText(crashLog)

            val uri =
                FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
                    crashFile,
                )

            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, crashLog)
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_report_subject))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            startActivity(Intent.createChooser(shareIntent, getString(R.string.crash_share_title)))
        } catch (_: Exception) {
            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, crashLog)
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_report_subject))
                }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.crash_share_title)))
        }
    }
}

// the crash page in Mochy's look: its mark, what happened, the update prompt's pill buttons and the log on a card
@Composable
private fun CrashScreen(
    crashLog: String,
    uploadState: CrashUploadState,
    onClose: () -> Unit,
    onRestart: () -> Unit,
    onCopy: () -> Unit,
    onLogs: () -> Unit,
    onUpdate: () -> Unit,
    onRetryUpload: () -> Unit,
    onShare: (CrashPaste?) -> Unit,
) {
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val paste = (uploadState as? CrashUploadState.Uploaded)?.paste
    val pasteIdLine = stringResource(Res.string.crash_paste_id, paste?.id.orEmpty())
    val pasteUrlLine = stringResource(Res.string.crash_paste_url, paste?.url.orEmpty())
    CrashScreenshotHint(onCopy = onCopy, onShare = { onShare(paste) })
    MochyCrashPage(
        title = stringResource(Res.string.crash_title),
        headerIcon = MetrolistIcons.Outlined.Close,
        headerIconDescription = stringResource(Res.string.crash_close),
        onHeaderClick = onClose,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState)
                    .tvVerticalScrollFocus(verticalScrollState),
        ) {
            MochyCrashMark()
            Spacer(modifier = Modifier.height(18.dp))
            MochyCrashBodyText(stringResource(Res.string.crash_description))
            Spacer(modifier = Modifier.height(8.dp))
            CrashScreenshotHintText()
            Spacer(modifier = Modifier.height(22.dp))
            MochyPromptButton(label = stringResource(Res.string.crash_restart), onClick = onRestart)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MochyPromptButton(
                    label = stringResource(Res.string.crash_copy_logs),
                    onClick = onCopy,
                    modifier = Modifier.weight(1f),
                )
                MochyPromptButton(
                    label = stringResource(Res.string.crash_share_logs),
                    onClick = { onShare(paste) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            MochyPromptButton(label = stringResource(Res.string.crash_logs), onClick = onLogs)
            Spacer(modifier = Modifier.height(10.dp))
            CrashUpdateButton(onUpdate)
            CrashUploadStatus(state = uploadState, onRetry = onRetryUpload)
            Spacer(modifier = Modifier.height(16.dp))
            MochyCrashCard {
                MochyCrashLogText(
                    text = crashLogWithPaste(crashLog, paste, pasteIdLine, pasteUrlLine),
                    modifier = Modifier.horizontalScroll(horizontalScrollState).tvHorizontalScrollFocus(horizontalScrollState),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CrashUploadStatus(
    state: CrashUploadState,
    onRetry: () -> Unit,
) {
    // The paste ID and URL sit at the top of the log itself, so screenshots of the log carry them.
    if (state is CrashUploadState.Uploaded) return
    Spacer(modifier = Modifier.height(16.dp))
    MochyCrashCard(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        when (state) {
            CrashUploadState.Idle -> {
                MochyCrashBodyText(stringResource(Res.string.crash_upload_disclosure), color = Color.White)
                Spacer(modifier = Modifier.height(12.dp))
                MochyPromptButton(label = stringResource(Res.string.crash_upload), onClick = onRetry)
            }

            CrashUploadState.Uploading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ContainedLoadingIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    MochyCrashBodyText(stringResource(Res.string.crash_uploading), color = Color.White)
                }
            }

            CrashUploadState.Failed -> {
                MochyCrashBodyText(stringResource(Res.string.crash_upload_failed), color = MochyCrashErrorText)
                Spacer(modifier = Modifier.height(12.dp))
                MochyPromptButton(label = stringResource(Res.string.crash_retry), onClick = onRetry)
            }

            is CrashUploadState.Uploaded -> {
                Unit
            }
        }
    }
}

internal fun crashLogWithPaste(
    crashLog: String,
    paste: CrashPaste?,
    pasteIdLine: String,
    pasteUrlLine: String,
): String =
    if (paste == null) {
        redactCrashLog(crashLog)
    } else {
        buildString {
            appendLine(pasteIdLine)
            appendLine(pasteUrlLine)
            appendLine()
            append(redactCrashLog(crashLog))
        }
    }

private sealed interface CrashUploadState {
    data object Idle : CrashUploadState

    data object Uploading : CrashUploadState

    data object Failed : CrashUploadState

    data class Uploaded(
        val paste: CrashPaste,
    ) : CrashUploadState
}

private class CrashViewModel(
    private val crashLog: String,
    private val uploader: CrashLogUploader = CrashLogUploader(),
) : ViewModel() {
    var uploadState by mutableStateOf<CrashUploadState>(CrashUploadState.Idle)
        private set

    private var uploadJob: Job? = null

    fun upload() {
        if (uploadJob?.isActive == true) return
        uploadState = CrashUploadState.Uploading
        uploadJob =
            viewModelScope.launch {
                uploadState =
                    try {
                        CrashUploadState.Uploaded(uploader.upload(crashLog))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "Could not upload crash log")
                        CrashUploadState.Failed
                    }
            }
    }

    class Factory(
        private val crashLog: String,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CrashViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return CrashViewModel(crashLog) as T
        }
    }
}

@Composable
private fun CrashUpdateButton(onUpdate: () -> Unit) {
    val state by AppUpdateManager.state.collectAsState()
    val release =
        when (val value = state) {
            is AppUpdateState.Available -> value.release
            is AppUpdateState.Downloading -> value.release
            is AppUpdateState.Downloaded -> value.release
            else -> null
        }
    MochyPromptButton(
        label =
            when {
                release != null -> "${stringResource(Res.string.crash_updates_action)} \u00b7\u00a0${release.version}"
                state is AppUpdateState.Checking -> stringResource(Res.string.crash_updates_checking)
                state is AppUpdateState.Error -> stringResource(Res.string.crash_updates_error)
                state is AppUpdateState.UpToDate -> stringResource(Res.string.crash_updates_no_updates)
                else -> stringResource(Res.string.crash_updates_check)
            },
        onClick = onUpdate,
        dimmed = release == null && state !is AppUpdateState.Error,
    )
}
