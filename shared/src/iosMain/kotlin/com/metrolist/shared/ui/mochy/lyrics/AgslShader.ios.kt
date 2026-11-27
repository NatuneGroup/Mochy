package com.metrolist.shared.ui.mochy.lyrics

import androidx.compose.ui.graphics.RenderEffect

// iOS keeps the lyrics' no-shader path, the one Mochy uses on phones without runtime shaders
internal actual val agslSupported: Boolean = false

internal actual fun createAgslShader(source: String): Any = error("Runtime shaders are not used on iOS")

internal actual fun Any.setAgslUniform(
    name: String,
    value: Float,
) = Unit

internal actual fun Any.setAgslUniform(
    name: String,
    first: Float,
    second: Float,
) = Unit

internal actual fun agslRenderEffect(
    shader: Any,
    inputName: String,
): RenderEffect = error("Runtime shaders are not used on iOS")
