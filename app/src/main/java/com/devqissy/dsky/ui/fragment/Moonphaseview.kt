package com.devqissy.dsky.ui.fragment

import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView


@Composable
fun MoonPhaseView(
    phase: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                val drawable = MoonPhaseDrawable(context)
                setImageDrawable(drawable)
            }
        },
        update = { imageView ->
            (imageView.drawable as? MoonPhaseDrawable)?.let {
                it.moonPhase = phase
                it.moonColor = color.toArgb()
            }
        },
        modifier = modifier
    )
}