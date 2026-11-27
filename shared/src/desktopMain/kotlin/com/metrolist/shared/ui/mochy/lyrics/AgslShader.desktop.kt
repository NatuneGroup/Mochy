package com.metrolist.shared.ui.mochy.lyrics

import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

internal actual val agslSupported: Boolean = true

// AGSL is SkSL with Android's names, so the same source compiles in Skia
internal actual fun createAgslShader(source: String): Any = RuntimeShaderBuilder(RuntimeEffect.makeForShader(source))

internal actual fun Any.setAgslUniform(
    name: String,
    value: Float,
) {
    (this as RuntimeShaderBuilder).uniform(name, value)
}

internal actual fun Any.setAgslUniform(
    name: String,
    first: Float,
    second: Float,
) {
    (this as RuntimeShaderBuilder).uniform(name, first, second)
}

internal actual fun agslRenderEffect(
    shader: Any,
    inputName: String,
): RenderEffect =
    ImageFilter
        .makeRuntimeShader(shader as RuntimeShaderBuilder, arrayOf(inputName), arrayOf(null))
        .asComposeRenderEffect()
