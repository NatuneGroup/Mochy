// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.modules

private const val MOCHY_LYRICS_PREFIX = "mochy-module/"

internal fun mochyLyricsProvider(id: String): String = MOCHY_LYRICS_PREFIX + id

internal fun mochyLyricsModuleId(provider: String): String? =
    provider.takeIf { it.startsWith(MOCHY_LYRICS_PREFIX) }?.removePrefix(MOCHY_LYRICS_PREFIX)

internal fun mochyLyricsProviderLabel(provider: String): String? =
    mochyLyricsModuleId(provider)?.let { id ->
        MochyModuleRegistry.shared.entries.value
            .firstOrNull { it.id == id }
            ?.module
            ?.info
            ?.name
    }
