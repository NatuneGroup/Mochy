package com.metrolist.desktop.ui.screens

import androidx.compose.runtime.Composable
import com.metrolist.desktop.state.app.UiState
import com.metrolist.desktop.ui.components.rememberMochyProfilePhotoPicker
import com.metrolist.shared.ui.onboarding.mochy.MochyOnboarding
import com.metrolist.shared.ui.settings.SettingsPageId

/** Mochy's setup sheet in the desktop window; a backup restore opens its settings page once the sheet is done */
@Composable
fun DesktopOnboarding(onComplete: (sendCrashReports: Boolean, openLogin: Boolean) -> Unit) {
    MochyOnboarding(
        showHandle = false,
        rememberPhotoPicker = { onPicked -> rememberMochyProfilePhotoPicker(onPicked) },
        onComplete = { result ->
            if (result.restoreBackup) {
                UiState.selectedSettingsPage = SettingsPageId.BACKUP_RESTORE
                UiState.showSettings = true
            }
            onComplete(result.sendCrashReports, result.signIn)
        },
    )
}
