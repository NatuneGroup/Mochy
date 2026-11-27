package com.metrolist.shared.ui.mochy.lyrics

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

internal actual val agslSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

// only reached behind agslSupported, so RuntimeShader never resolves on an older device
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun createAgslShader(source: String): Any = RuntimeShader(source)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun Any.setAgslUniform(
    name: String,
    value: Float,
) {
    (this as RuntimeShader).setFloatUniform(name, value)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun Any.setAgslUniform(
    name: String,
    first: Float,
    second: Float,
) {
    (this as RuntimeShader).setFloatUniform(name, first, second)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun agslRenderEffect(
    shader: Any,
    inputName: String,
): RenderEffect =
    android.graphics.RenderEffect
        .createRuntimeShaderEffect(shader as RuntimeShader, inputName)
        .asComposeRenderEffect()
