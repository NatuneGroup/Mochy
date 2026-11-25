package com.metrolist.android.onboarding.mochy

import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.metrolist.android.onboarding.LegacyInstallMigration
import com.metrolist.android.onboarding.OnboardingDestination
import com.metrolist.android.ui.component.rememberMochyProfilePhotoPicker
import com.metrolist.shared.ui.onboarding.mochy.MochyOnboarding

/** Mochy's setup sheet on phones and tablets, with the old app's library offered when one is on the phone */
@Composable
internal fun MochyOnboardingScreen(
    migration: LegacyInstallMigration,
    onComplete: (sendCrashReports: Boolean, destination: OnboardingDestination) -> Unit,
    hasLegacyInstallationOverride: Boolean? = null,
) {
    val context = LocalContext.current
    val hasLegacyInstallation = rememberSaveable { hasLegacyInstallationOverride ?: migration.hasLegacyInstallation() }
    // animations switched off in the system settings leave the welcome girl still
    val motionEnabled =
        remember(context) {
            AndroidSettings.Global.getFloat(context.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        }
    MochyOnboarding(
        motionEnabled = motionEnabled,
        rememberPhotoPicker = { onPicked -> rememberMochyProfilePhotoPicker(onPicked) },
        importPreviousData = if (hasLegacyInstallation) ({ migration.migrate().also { migration.clearResult() } }) else null,
        onComplete = { result ->
            onComplete(
                result.sendCrashReports,
                when {
                    result.signIn -> OnboardingDestination.LOGIN
                    result.restoreBackup -> OnboardingDestination.BACKUP_RESTORE
                    else -> OnboardingDestination.HOME
                },
            )
        },
    )
}
