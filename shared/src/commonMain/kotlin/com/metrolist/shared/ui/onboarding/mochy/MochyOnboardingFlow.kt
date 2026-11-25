// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// Mochy's setup sheet: one page at a time sliding sideways, card moments in between, the sheet rubber-bands when dragged

package com.metrolist.shared.ui.onboarding.mochy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_card_local_member
import com.metrolist.shared.generated.resources.onboarding_migration_failed
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.mochy.ensureMochyCardNumber
import com.metrolist.shared.ui.navigation.NavigationBackHandler
import com.metrolist.shared.utils.isCrashReportingAvailable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private enum class Step { WELCOME, IMPORT_ASK, IMPORT_MODE, MIGRATING, PROFILE, HELLO, LAST_ONE, DONE }

// pages that move on by themselves: the back gesture waits for them, and they fade in rather than slide
private val AutoSteps = setOf(Step.MIGRATING, Step.HELLO, Step.DONE)

/** what the person picked on the way: the app opens sign-in or backup restore after the sheet when asked to */
data class MochyOnboardingResult(
    val sendCrashReports: Boolean,
    val signIn: Boolean,
    val restoreBackup: Boolean,
)

/**
 * Mochy's setup sheet. [importPreviousData] moves the library of the app that came before, when there is one;
 * [rememberPhotoPicker] opens the platform's picture picker for the profile card.
 */
@Composable
fun MochyOnboarding(
    onComplete: (MochyOnboardingResult) -> Unit,
    rememberPhotoPicker: (@Composable (onPicked: (String) -> Unit) -> () -> Unit)? = null,
    importPreviousData: (suspend () -> Result<Unit>)? = null,
    motionEnabled: Boolean = true,
    showHandle: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val history = remember { mutableStateListOf(Step.WELCOME) }
    var forward by remember { mutableStateOf(true) }
    val step = history.last()

    fun go(
        next: Step,
        replaceCurrent: Boolean = false,
    ) {
        forward = true
        if (replaceCurrent && history.size > 1) history.removeAt(history.lastIndex)
        history.add(next)
    }

    fun back() {
        if (history.size <= 1 || step in AutoSteps) return
        forward = false
        history.removeAt(history.lastIndex)
    }

    var isLeavingWelcome by remember { mutableStateOf(false) }
    // signing in and restoring a backup open once the sheet is done, the way Mochy's own importer runs after it
    var signIn by rememberSaveable { mutableStateOf(false) }
    var restoreBackup by rememberSaveable { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }

    // ---- profile ----
    val settings = Settings.shared
    var username by rememberSaveable { mutableStateOf(settings.mochyUsername.ifBlank { settings.accountName }) }
    var photo by rememberSaveable { mutableStateOf(settings.mochyProfilePhotoUri) }
    val cardNumber = remember { ensureMochyCardNumber() }
    val joinedText = stringResource(Res.string.mochy_card_local_member)
    val pickPhoto = rememberPhotoPicker?.invoke { picked -> photo = picked } ?: {}
    val shownPhoto = photo.ifBlank { settings.accountThumbnail }.takeIf { it.isNotBlank() }
    val card = CardInfo(username = username, photo = shownPhoto, cardNumber = cardNumber, joinedText = joinedText)

    // ---- the last page's switches ----
    var crossfade by rememberSaveable { mutableStateOf(settings.crossfadeEnabled) }
    val crashReportingAvailable = remember { isCrashReportingAvailable() }
    var sendCrashReports by rememberSaveable { mutableStateOf(false) }

    // ---- bringing the old library over ----
    val migrationFailed = stringResource(Res.string.onboarding_migration_failed)

    fun importPrevious(run: suspend () -> Result<Unit>) {
        importError = null
        go(Step.MIGRATING)
        scope.launch {
            run()
                .onSuccess { go(Step.PROFILE, replaceCurrent = true) }
                .onFailure {
                    // back to the choices, saying why there
                    forward = false
                    if (history.last() == Step.MIGRATING) history.removeAt(history.lastIndex)
                    importError = migrationFailed
                }
        }
    }

    fun finish() {
        settings.mochyUsername = username.trim()
        settings.mochyProfilePhotoUri = photo
        settings.crossfadeEnabled = crossfade
        onComplete(MochyOnboardingResult(sendCrashReports && crashReportingAvailable, signIn = signIn, restoreBackup = restoreBackup))
    }

    NavigationBackHandler(enabled = history.size > 1 && step !in AutoSteps) { back() }

    // the sheet is dragged by its handle: it only rubber-bands, it never closes
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = topInset + 20.dp)
                    .graphicsLayer { translationY = dragOffset }
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(Onb.Page),
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val auto = targetState in AutoSteps || initialState in AutoSteps
                    when {
                        auto -> {
                            (fadeIn(tween(420)) + scaleIn(tween(520), initialScale = 0.96f)) togetherWith fadeOut(tween(260))
                        }

                        forward -> {
                            slideInHorizontally(tween(380)) { it / 3 } + fadeIn(tween(320)) togetherWith
                                slideOutHorizontally(tween(380)) { -it / 4 } + fadeOut(tween(220))
                        }

                        else -> {
                            slideInHorizontally(tween(380)) { -it / 3 } + fadeIn(tween(320)) togetherWith
                                slideOutHorizontally(tween(380)) { it / 4 } + fadeOut(tween(220))
                        }
                    }.using(SizeTransform(clip = false))
                },
                label = "mochyOnboardingStep",
                modifier = Modifier.fillMaxSize(),
            ) { current ->
                when (current) {
                    Step.WELCOME -> {
                        WelcomeStep(
                            motionEnabled = motionEnabled,
                            isHiding = isLeavingWelcome,
                            onNext = {
                                // a second tap while she waves goodbye would skip a page
                                if (!isLeavingWelcome) {
                                    isLeavingWelcome = true
                                    scope.launch {
                                        delay(400)
                                        go(Step.IMPORT_ASK)
                                        isLeavingWelcome = false
                                    }
                                }
                            },
                        )
                    }

                    Step.IMPORT_ASK -> {
                        ImportAskPage(
                            onYes = {
                                importError = null
                                go(Step.IMPORT_MODE)
                            },
                            onSkip = {
                                signIn = false
                                restoreBackup = false
                                go(Step.PROFILE)
                            },
                        )
                    }

                    Step.IMPORT_MODE -> {
                        ImportModePage(
                            onPreviousData = importPreviousData?.let { run -> { importPrevious(run) } },
                            onSignIn = {
                                signIn = true
                                restoreBackup = false
                                go(Step.PROFILE)
                            },
                            onBackup = {
                                restoreBackup = true
                                signIn = false
                                go(Step.PROFILE)
                            },
                            onBack = ::back,
                            error = importError,
                        )
                    }

                    Step.MIGRATING -> {
                        MigratingPage()
                    }

                    Step.PROFILE -> {
                        ProfilePage(
                            username = username,
                            onUsernameChange = { username = it },
                            photo = shownPhoto,
                            onPhotoClick = pickPhoto,
                            cardNumber = cardNumber,
                            joinedText = joinedText,
                            onNext = { if (username.isNotBlank()) go(Step.HELLO) },
                        )
                    }

                    Step.HELLO -> {
                        HelloCardStep(card = card, onFinished = { go(Step.LAST_ONE, replaceCurrent = true) })
                    }

                    Step.LAST_ONE -> {
                        LastOnePage(
                            crossfade = crossfade,
                            onCrossfadeChange = { crossfade = it },
                            crashReports = if (crashReportingAvailable) sendCrashReports else null,
                            onCrashReportsChange = { sendCrashReports = it },
                            onNext = { go(Step.DONE) },
                        )
                    }

                    Step.DONE -> {
                        DoneStep(card = card, onFinished = ::finish)
                    }
                }
            }

            // the sheet's own handle
            if (showHandle) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onVerticalDrag = { change, amount ->
                                        change.consume()
                                        val resistance = 1f - (dragOffset / 2000f).coerceIn(0f, 0.8f)
                                        dragOffset = (dragOffset + amount * resistance).coerceAtLeast(0f)
                                    },
                                    onDragEnd = {
                                        scope.launch {
                                            animate(
                                                initialValue = dragOffset,
                                                targetValue = 0f,
                                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                                            ) { value, _ ->
                                                dragOffset = value
                                            }
                                        }
                                    },
                                )
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 36.dp, height = 5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.28f)),
                    )
                }
            }
        }
    }
}
