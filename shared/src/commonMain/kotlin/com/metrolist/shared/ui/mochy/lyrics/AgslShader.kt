// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// Mochy's lyric glow and wave run as runtime shaders. They travel as Any so no platform shader type leaks
// into shared code, and only run where [agslSupported] says the platform has runtime shaders.

package com.metrolist.shared.ui.mochy.lyrics

import androidx.compose.ui.graphics.RenderEffect

internal expect val agslSupported: Boolean

internal expect fun createAgslShader(source: String): Any

internal expect fun Any.setAgslUniform(
    name: String,
    value: Float,
)

internal expect fun Any.setAgslUniform(
    name: String,
    first: Float,
    second: Float,
)

/** The shader as a layer effect, reading what the layer draws through its `uniform shader` named [inputName]. */
internal expect fun agslRenderEffect(
    shader: Any,
    inputName: String,
): RenderEffect
