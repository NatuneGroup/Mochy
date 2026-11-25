// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// every page after the welcome screen as stateless composables, MochyOnboardingScreen picks the page and wires it

package com.metrolist.shared.ui.onboarding.mochy

import com.metrolist.shared.generated.resources.mochy_onb_bug_report
import com.metrolist.shared.generated.resources.mochy_onb_tune
import com.metrolist.shared.generated.resources.mochy_onb_person
import com.metrolist.shared.generated.resources.mochy_onb_backup
import com.metrolist.shared.generated.resources.mochy_onb_login
import com.metrolist.shared.generated.resources.mochy_onb_restore
import com.metrolist.shared.generated.resources.mochy_onb_download
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.next
import com.metrolist.shared.generated.resources.onb_auto_crossfade_title
import com.metrolist.shared.generated.resources.onb_bringing_over
import com.metrolist.shared.generated.resources.onb_crash_reports_desc
import com.metrolist.shared.generated.resources.onb_crash_reports_title
import com.metrolist.shared.generated.resources.onb_crossfade_desc
import com.metrolist.shared.generated.resources.onb_go_back
import com.metrolist.shared.generated.resources.onb_import_ask_subtitle
import com.metrolist.shared.generated.resources.onb_import_backup_desc
import com.metrolist.shared.generated.resources.onb_import_backup_title
import com.metrolist.shared.generated.resources.onb_import_mode_subtitle
import com.metrolist.shared.generated.resources.onb_import_previous_desc
import com.metrolist.shared.generated.resources.onb_import_previous_title
import com.metrolist.shared.generated.resources.onb_import_title
import com.metrolist.shared.generated.resources.onb_last_one
import com.metrolist.shared.generated.resources.onb_last_one_subtitle
import com.metrolist.shared.generated.resources.onb_never_store
import com.metrolist.shared.generated.resources.onb_next_lower
import com.metrolist.shared.generated.resources.onb_profile_title
import com.metrolist.shared.generated.resources.onb_set_photo
import com.metrolist.shared.generated.resources.onb_sign_in_desc
import com.metrolist.shared.generated.resources.onb_sign_in_title
import com.metrolist.shared.generated.resources.onb_skip
import com.metrolist.shared.generated.resources.onb_username_hint
import com.metrolist.shared.generated.resources.onb_username_prompt
import com.metrolist.shared.generated.resources.onb_yes
import com.metrolist.shared.ui.mochy.ProfileIdCard
import org.jetbrains.compose.resources.stringResource

// ---- 1. import? ----

@Composable
internal fun ImportAskPage(
    onYes: () -> Unit,
    onSkip: () -> Unit,
) {
    OnboardingPage(
        badge = { PageBadge(painterResource(Res.drawable.mochy_onb_download)) },
        title = stringResource(Res.string.onb_import_title),
        subtitle = stringResource(Res.string.onb_import_ask_subtitle),
        footer = {
            OnbButton(stringResource(Res.string.onb_yes), onClick = onYes)
            OnbButton(stringResource(Res.string.onb_skip), onClick = onSkip, style = OnbButtonStyle.Secondary)
        },
    )
}

// ---- 2. which kind ----

@Composable
internal fun ImportModePage(
    onPreviousData: (() -> Unit)?,
    onSignIn: () -> Unit,
    onBackup: () -> Unit,
    onBack: () -> Unit,
    error: String?,
) {
    OnboardingPage(
        badge = { PageBadge(painterResource(Res.drawable.mochy_onb_download)) },
        title = stringResource(Res.string.onb_import_title),
        subtitle = stringResource(Res.string.onb_import_mode_subtitle),
        footer = { OnbButton(stringResource(Res.string.onb_go_back), onClick = onBack, style = OnbButtonStyle.Secondary) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // only offered when the app that came before left a library on this phone
            if (onPreviousData != null) {
                OnbChoiceCard(
                    icon = painterResource(Res.drawable.mochy_onb_restore),
                    title = stringResource(Res.string.onb_import_previous_title),
                    description = stringResource(Res.string.onb_import_previous_desc),
                    onClick = onPreviousData,
                )
            }
            OnbChoiceCard(
                icon = painterResource(Res.drawable.mochy_onb_login),
                title = stringResource(Res.string.onb_sign_in_title),
                description = stringResource(Res.string.onb_sign_in_desc),
                onClick = onSignIn,
            )
            OnbChoiceCard(
                icon = painterResource(Res.drawable.mochy_onb_backup),
                title = stringResource(Res.string.onb_import_backup_title),
                description = stringResource(Res.string.onb_import_backup_desc),
                onClick = onBackup,
            )
            if (error != null) {
                Text(error, color = Color(0xFFFF8A80), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---- 2b. bringing the previous library over ----

@Composable
internal fun MigratingPage() {
    OnboardingPage(
        badge = { PageBadge(painterResource(Res.drawable.mochy_onb_restore)) },
        title = stringResource(Res.string.onb_bringing_over),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White, trackColor = Onb.Card)
        }
    }
}

// ---- 3. profile ----

@Composable
internal fun ProfilePage(
    username: String,
    onUsernameChange: (String) -> Unit,
    photo: Any?,
    onPhotoClick: () -> Unit,
    cardNumber: String,
    joinedText: String,
    onNext: () -> Unit,
) {
    OnboardingPage(
        badge = { PageBadge(painterResource(Res.drawable.mochy_onb_person)) },
        title = stringResource(Res.string.onb_profile_title),
        footer = {
            Text(
                stringResource(Res.string.onb_never_store),
                color = Onb.Faint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp),
            )
            OnbButton(stringResource(Res.string.next), onClick = onNext, enabled = username.isNotBlank())
        },
    ) {
        Text(stringResource(Res.string.onb_username_prompt), color = Onb.Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        OnbTextField(
            value = username,
            onValueChange = { onUsernameChange(it.take(32)) },
            placeholder = stringResource(Res.string.onb_username_hint),
        )
        Spacer(Modifier.height(24.dp))
        Text(stringResource(Res.string.onb_set_photo), color = Onb.Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        val deviceTilt = rememberDeviceTilt()
        ProfileIdCard(
            username = username,
            photo = photo,
            cardNumber = cardNumber,
            joinedText = joinedText,
            onPhotoClick = onPhotoClick,
            modifier =
                Modifier.graphicsLayer {
                    rotationX = deviceTilt.rotationX
                    rotationY = deviceTilt.rotationY
                    cameraDistance = 14f * density
                },
        )
    }
}

// ---- 4. last one ----

@Composable
internal fun LastOnePage(
    crossfade: Boolean,
    onCrossfadeChange: (Boolean) -> Unit,
    crashReports: Boolean?,
    onCrashReportsChange: (Boolean) -> Unit,
    onNext: () -> Unit,
) {
    OnboardingPage(
        badge = { PageBadge(painterResource(Res.drawable.mochy_onb_tune)) },
        title = stringResource(Res.string.onb_last_one),
        subtitle = stringResource(Res.string.onb_last_one_subtitle),
        footer = { OnbButton(stringResource(Res.string.onb_next_lower), onClick = onNext) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OnbToggleCard(
                title = stringResource(Res.string.onb_auto_crossfade_title),
                description = stringResource(Res.string.onb_crossfade_desc),
                checked = crossfade,
                onCheckedChange = onCrossfadeChange,
                header = { CrossfadeBanner() },
            )
            // null where this build has no crash reporting to offer
            if (crashReports != null) {
                OnbToggleCard(
                    title = stringResource(Res.string.onb_crash_reports_title),
                    description = stringResource(Res.string.onb_crash_reports_desc),
                    checked = crashReports,
                    onCheckedChange = onCrashReportsChange,
                    leading = {
                        PageBadge(painterResource(Res.drawable.mochy_onb_bug_report), size = 32.dp)
                    },
                )
            }
        }
    }
}
