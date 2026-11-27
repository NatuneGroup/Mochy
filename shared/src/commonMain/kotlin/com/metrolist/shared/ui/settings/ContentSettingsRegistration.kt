/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@file:Suppress("FunctionName", "ktlint:standard:no-wildcard-imports")

package com.metrolist.shared.ui.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import com.metrolist.shared.generated.resources.*
import com.metrolist.shared.generated.resources.audio_quality_auto
import com.metrolist.shared.generated.resources.audio_quality_high
import com.metrolist.shared.generated.resources.audio_quality_low
import com.metrolist.shared.generated.resources.audio_quality_on_mobile_data
import com.metrolist.shared.generated.resources.audio_quality_same_as_wifi
import com.metrolist.shared.generated.resources.dns_over_https
import com.metrolist.shared.generated.resources.dns_over_https_custom
import com.metrolist.shared.generated.resources.dns_over_https_custom_url
import com.metrolist.shared.generated.resources.dns_over_https_desc
import com.metrolist.shared.generated.resources.dns_over_https_off
import com.metrolist.shared.generated.resources.internet
import com.metrolist.shared.generated.resources.mochy_pages_translate
import com.metrolist.shared.generated.resources.proxy
import com.metrolist.shared.generated.resources.proxy_host
import com.metrolist.shared.generated.resources.proxy_off
import com.metrolist.shared.generated.resources.proxy_port
import com.metrolist.shared.settings.AiSlopBlocklist
import com.metrolist.shared.settings.BlockedContent
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.settings.SongShareLink
import com.metrolist.shared.ui.icons.MetrolistIcons
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
internal fun SettingsUiRegistryBuilder.registerContentSettings(host: SharedSettingsHost) {
    val settings = Settings.shared
    val scope = rememberCoroutineScope()
    val blocklistDescription = stringResource(Res.string.block_ai_slop_artists_desc)
    val blocklistLink =
        buildAnnotatedString {
            append(blocklistDescription)
            val sourceName = AiSlopBlocklist.CONTRIBUTION_URL.substringAfterLast('/')
            val sourceIndex = blocklistDescription.indexOf(sourceName)
            val start = if (sourceIndex >= 0) sourceIndex else length.also { append(" ") } + 1
            if (sourceIndex < 0) append(AiSlopBlocklist.CONTRIBUTION_URL)
            addLink(
                LinkAnnotation.Url(
                    AiSlopBlocklist.CONTRIBUTION_URL,
                    TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)),
                ),
                start,
                if (sourceIndex >= 0) start + sourceName.length else length,
            )
        }
    page(
        id = SettingsPageId.CONTENT,
        title = stringResource(Res.string.settings_content_title),
        subtitle = stringResource(Res.string.settings_content_description),
        icon = MetrolistIcons.Outlined.Language,
        tone = SettingsTone.ROSE,
    ) {
        host.onSwitchYouTubeChannel?.let { switchChannel ->
            group("youtubeAccount", stringResource(Res.string.account)) {
                action(
                    id = "switchYouTubeChannel",
                    title = stringResource(Res.string.switch_youtube_channel),
                    description = settings.accountChannelHandle.ifBlank { settings.accountName },
                    onClick = switchChannel,
                )
            }
        }
        // Sora's Internet page: quality on mobile data, DNS over HTTPS and the proxy; iOS's own network stack has neither of the last two
        if (host.platform != SettingsPlatform.IOS) {
            group("internet", stringResource(Res.string.internet)) {
                if (host.platform == SettingsPlatform.ANDROID) {
                    val qualityChoices =
                        listOf(
                            SettingsChoice("", stringResource(Res.string.audio_quality_same_as_wifi)),
                            SettingsChoice("LOW", stringResource(Res.string.audio_quality_low)),
                            SettingsChoice("AUTO", stringResource(Res.string.audio_quality_auto)),
                            SettingsChoice("HIGH", stringResource(Res.string.audio_quality_high)),
                        )
                    choice(
                        id = "audioQualityCellular",
                        title = stringResource(Res.string.audio_quality_on_mobile_data),
                        valueLabel = qualityChoices.firstOrNull { it.id == settings.audioQualityCellular }?.label,
                        choices = qualityChoices,
                        selectedChoiceId = settings.audioQualityCellular,
                    ) { settings.audioQualityCellular = it }
                }
                val dohChoices =
                    listOf(
                        SettingsChoice("OFF", stringResource(Res.string.dns_over_https_off)),
                        SettingsChoice("CLOUDFLARE", "Cloudflare"),
                        SettingsChoice("GOOGLE", "Google"),
                        SettingsChoice("QUAD9", "Quad9"),
                        SettingsChoice("ADGUARD", "AdGuard"),
                        SettingsChoice("CUSTOM", stringResource(Res.string.dns_over_https_custom)),
                    )
                choice(
                    id = "dnsOverHttps",
                    title = stringResource(Res.string.dns_over_https),
                    description = stringResource(Res.string.dns_over_https_desc),
                    valueLabel = dohChoices.firstOrNull { it.id == settings.dnsOverHttpsProvider }?.label,
                    choices = dohChoices,
                    selectedChoiceId = settings.dnsOverHttpsProvider,
                ) { settings.dnsOverHttpsProvider = it }
                if (settings.dnsOverHttpsProvider == "CUSTOM") {
                    text(
                        id = "dnsOverHttpsCustomUrl",
                        title = stringResource(Res.string.dns_over_https_custom_url),
                        value = settings.dnsOverHttpsCustomUrl,
                        placeholder = "https://",
                    ) { settings.dnsOverHttpsCustomUrl = it.trim() }
                }
                val proxyChoices =
                    listOf(
                        SettingsChoice("OFF", stringResource(Res.string.proxy_off)),
                        SettingsChoice("HTTP", "HTTP"),
                        SettingsChoice("SOCKS", "SOCKS5"),
                    )
                choice(
                    id = "proxyType",
                    title = stringResource(Res.string.proxy),
                    valueLabel = proxyChoices.firstOrNull { it.id == settings.proxyType }?.label,
                    choices = proxyChoices,
                    selectedChoiceId = settings.proxyType,
                ) { settings.proxyType = it }
                if (settings.proxyType != "OFF") {
                    text(
                        id = "proxyHost",
                        title = stringResource(Res.string.proxy_host),
                        value = settings.proxyHost,
                    ) { settings.proxyHost = it.trim() }
                    text(
                        id = "proxyPort",
                        title = stringResource(Res.string.proxy_port),
                        value =
                            settings.proxyPort
                                .takeIf { it > 0 }
                                ?.toString()
                                .orEmpty(),
                    ) { settings.proxyPort = it.trim().toIntOrNull()?.takeIf { port -> port in 1..65535 } ?: 0 }
                }
            }
        }
        val contentLanguageChoices = host.hostChoices[SettingsHostChoice.CONTENT_LANGUAGE].orEmpty()
        val contentCountryChoices = host.hostChoices[SettingsHostChoice.CONTENT_COUNTRY].orEmpty()
        val appLanguageChoices = host.hostChoices[SettingsHostChoice.APP_LANGUAGE].orEmpty()
        if (contentLanguageChoices.isNotEmpty() || contentCountryChoices.isNotEmpty() || appLanguageChoices.isNotEmpty()) {
            group("localization", stringResource(Res.string.settings_localization)) {
                if (contentLanguageChoices.isNotEmpty()) {
                    choice(
                        id = "contentLanguage",
                        title = stringResource(Res.string.settings_content_language),
                        valueLabel =
                            contentLanguageChoices.firstOrNull { it.id == settings.contentLanguage }?.label ?: settings.contentLanguage,
                        choices = contentLanguageChoices,
                        selectedChoiceId = settings.contentLanguage,
                        description = stringResource(Res.string.settings_content_language_summary),
                    ) { host.onHostChoiceSelected(SettingsHostChoice.CONTENT_LANGUAGE, it) }
                }
                if (contentCountryChoices.isNotEmpty()) {
                    choice(
                        id = "contentCountry",
                        title = stringResource(Res.string.settings_content_country),
                        valueLabel =
                            contentCountryChoices.firstOrNull { it.id == settings.contentCountry }?.label ?: settings.contentCountry,
                        choices = contentCountryChoices,
                        selectedChoiceId = settings.contentCountry,
                        description = stringResource(Res.string.settings_content_country_summary),
                    ) { host.onHostChoiceSelected(SettingsHostChoice.CONTENT_COUNTRY, it) }
                }
                if (appLanguageChoices.isNotEmpty()) {
                    choice(
                        id = "appLanguage",
                        title = stringResource(Res.string.app_language),
                        icon = Res.drawable.mochy_pages_translate,
                        valueLabel = appLanguageChoices.firstOrNull { it.id == settings.appLanguage }?.label ?: settings.appLanguage,
                        choices = appLanguageChoices,
                        selectedChoiceId = settings.appLanguage,
                    ) { host.onHostChoiceSelected(SettingsHostChoice.APP_LANGUAGE, it) }
                }
            }
        }
        group("filtering", stringResource(Res.string.content_filters_filtering)) {
            switch(
                id = "hideExplicit",
                title = stringResource(Res.string.hide_explicit),
                description = stringResource(Res.string.hide_explicit_desc),
                checked = settings.hideExplicit,
                enabled = !settings.explicitOnly,
            ) {
                settings.hideExplicit = it
                if (it) settings.explicitOnly = false
                BlockedContent.contentFiltersChanged()
                host.onSettingChanged(SettingsChangeEffect.CONTENT, null)
            }
            switch(
                id = "explicitOnly",
                title = stringResource(Res.string.explicit_only),
                description = stringResource(Res.string.explicit_only_desc),
                checked = settings.explicitOnly,
                enabled = !settings.hideExplicit,
            ) {
                settings.explicitOnly = it
                if (it) settings.hideExplicit = false
                BlockedContent.contentFiltersChanged()
                host.onSettingChanged(SettingsChangeEffect.CONTENT, null)
            }
            switch(
                id = "blockAiArtists",
                title = stringResource(Res.string.block_ai_slop_artists),
                description = blocklistDescription,
                annotatedDescription = blocklistLink,
                checked = settings.blockAiSlopArtists,
            ) {
                settings.blockAiSlopArtists = it
                BlockedContent.contentFiltersChanged()
                if (it) scope.launch { BlockedContent.refreshAiSlopBlocklist(force = true) }
                host.onSettingChanged(SettingsChangeEffect.CONTENT, null)
            }
            switch(
                id = "hideVideoSongs",
                title = stringResource(Res.string.hide_video_songs),
                description = stringResource(Res.string.hide_video_songs_desc),
                checked = settings.hideVideoSongs,
            ) {
                settings.hideVideoSongs = it
                BlockedContent.contentFiltersChanged()
                host.onSettingChanged(SettingsChangeEffect.CONTENT, null)
            }
            switch(
                id = "hideNonMusicalVideos",
                title = stringResource(Res.string.hide_non_musical_videos),
                description = stringResource(Res.string.hide_non_musical_videos_desc),
                checked = settings.hideNonMusicalVideos,
            ) {
                settings.hideNonMusicalVideos = it
                BlockedContent.contentFiltersChanged()
                host.onSettingChanged(SettingsChangeEffect.CONTENT, null)
            }
            switch(
                id = "hideShortSongs",
                title = stringResource(Res.string.hide_short_songs),
                description = stringResource(Res.string.hide_short_songs_desc),
                checked = settings.hideShortSongs,
            ) {
                settings.hideShortSongs = it
                BlockedContent.contentFiltersChanged()
                host.onSettingChanged(SettingsChangeEffect.CONTENT, null)
            }
            slider(
                id = "minimumDuration",
                title = stringResource(Res.string.minimum_song_duration),
                description =
                    if (settings.hideShortSongs) {
                        stringResource(Res.string.minimum_song_duration_desc, settings.minimumSongDurationSeconds)
                    } else {
                        stringResource(Res.string.settings_requires_enabled, stringResource(Res.string.hide_short_songs))
                    },
                enabled = settings.hideShortSongs,
                valueLabel = stringResource(Res.string.settings_seconds_value, settings.minimumSongDurationSeconds),
                value = settings.minimumSongDurationSeconds.toFloat(),
                range = 15f..180f,
                steps = 10,
                valueFormatter = { stringResource(Res.string.settings_seconds_value, it.roundToInt()) },
            ) {
                settings.minimumSongDurationSeconds = it.roundToInt()
                BlockedContent.contentFiltersChanged()
                host.onSettingChanged(SettingsChangeEffect.CONTENT, null)
            }
            switch(
                id = "hidePodcasts",
                title = stringResource(Res.string.hide_podcasts),
                description = stringResource(Res.string.hide_podcasts_desc),
                checked = settings.hidePodcasts,
            ) {
                settings.hidePodcasts = it
                BlockedContent.contentFiltersChanged()
                host.onSettingChanged(SettingsChangeEffect.CONTENT, null)
            }
        }
        val artistIconSourceChoices = host.hostChoices[SettingsHostChoice.ARTIST_ICON_SOURCE].orEmpty()
        val artistBannerSourceChoices = host.hostChoices[SettingsHostChoice.ARTIST_BANNER_SOURCE].orEmpty()
        if (artistIconSourceChoices.isNotEmpty() || artistBannerSourceChoices.isNotEmpty()) {
            group("artistSources", stringResource(Res.string.settings_artist_sources)) {
                if (artistIconSourceChoices.isNotEmpty()) {
                    choice(
                        id = "artistIconSource",
                        title = stringResource(Res.string.settings_artist_icon_source),
                        valueLabel = artistIconSourceChoices.firstOrNull { it.id == settings.artistIconSource }?.label,
                        choices = artistIconSourceChoices,
                        selectedChoiceId = settings.artistIconSource,
                        description = stringResource(Res.string.settings_artist_icon_summary),
                    ) { host.onHostChoiceSelected(SettingsHostChoice.ARTIST_ICON_SOURCE, it) }
                }
                if (artistBannerSourceChoices.isNotEmpty()) {
                    choice(
                        id = "artistBannerSource",
                        title = stringResource(Res.string.settings_artist_banner_source),
                        valueLabel = artistBannerSourceChoices.firstOrNull { it.id == settings.artistBannerSource }?.label,
                        choices = artistBannerSourceChoices,
                        selectedChoiceId = settings.artistBannerSource,
                        description = stringResource(Res.string.settings_artist_banner_summary),
                    ) { host.onHostChoiceSelected(SettingsHostChoice.ARTIST_BANNER_SOURCE, it) }
                }
            }
        }
        val songShareLinkChoices =
            listOf(
                SettingsChoice(SongShareLink.YOUTUBE_MUSIC.name, stringResource(Res.string.youtube_music)),
                SettingsChoice(SongShareLink.SONG_LINK.name, stringResource(Res.string.song_link)),
            )
        group("sharing", stringResource(Res.string.share)) {
            choice(
                id = "songShareLink",
                title = stringResource(Res.string.song_share_link),
                valueLabel = songShareLinkChoices.firstOrNull { it.id == settings.songShareLink }?.label,
                choices = songShareLinkChoices,
                selectedChoiceId = settings.songShareLink,
                description = stringResource(Res.string.song_share_link_description),
            ) { settings.songShareLink = it }
        }
        group(
            "autoPlaylists",
            stringResource(Res.string.appearance_auto_playlists),
            footer = stringResource(Res.string.settings_auto_playlist_summary),
        ) {
            switch(
                "showLiked",
                stringResource(Res.string.appearance_show_liked_playlist),
                checked = settings.showLikedPlaylist,
            ) {
                settings.showLikedPlaylist = it
            }
            switch(
                "showDownloaded",
                stringResource(Res.string.appearance_show_downloaded_playlist),
                checked = settings.showDownloadedPlaylist,
            ) {
                settings.showDownloadedPlaylist = it
            }
            switch(
                "showTop",
                stringResource(Res.string.appearance_show_top_playlist),
                checked = settings.showTopPlaylist,
            ) {
                settings.showTopPlaylist = it
            }
            switch(
                "showUploaded",
                stringResource(Res.string.appearance_show_uploaded_playlist),
                checked = settings.showUploadedPlaylist,
            ) {
                settings.showUploadedPlaylist = it
            }
            switch(
                "showCached",
                stringResource(Res.string.appearance_show_cached_playlist),
                checked = settings.showCachedPlaylist,
            ) {
                settings.showCachedPlaylist = it
            }
        }
        if (host.homeSections.isNotEmpty()) {
            group(
                id = "homeSections",
                title = stringResource(Res.string.settings_home_sections),
                description = stringResource(Res.string.settings_home_sections_description),
            ) {
                host.homeSections.forEach { section ->
                    switch(
                        id = "homeSection:${section.id}",
                        title = section.title,
                        description = section.description,
                        checked = section.visible,
                    ) { visible -> host.onHomeSectionVisibilityChanged(section.preferenceKey, visible) }
                }
            }
        }
    }
}
