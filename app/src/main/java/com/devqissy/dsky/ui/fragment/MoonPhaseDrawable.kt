package com.devqissy.dsky.ui.fragment

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.devqissy.dsky.R
import androidx.core.graphics.toColorInt

class MoonPhaseDrawable(context: Context) : Drawable() {

    private val moonPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val transparentGreyBasePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#80808080".toColorInt()
        style = Paint.Style.FILL
    }

    private var _moonPhase: Float = 0f
    var moonPhase: Float
        get() = _moonPhase
        set(value) {
            _moonPhase = value.coerceIn(0f, 1f)
            powerIconAlpha = (_moonPhase * 255).toInt()
            invalidateSelf()
        }

    private var _moonColor: Int = Color.WHITE
    var moonColor: Int
        get() = _moonColor
        set(value) {
            if (_moonColor != value) {
                _moonColor = value
                moonPaint.color = _moonColor
                invalidateSelf()
            }
        }

    private var _shadowColor: Int = Color.BLACK
    var shadowColor: Int
        get() = _shadowColor
        set(value) {
            if (_shadowColor != value) {
                _shadowColor = value
                invalidateSelf()
            }
        }

    private var powerIconDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_power)
    private var powerIconAlpha: Int = 0

    fun setPowerIconDrawable(drawable: Drawable?) {
        powerIconDrawable = drawable
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val centerX = bounds.centerX().toFloat()
        val centerY = bounds.centerY().toFloat()
        val radius = (minOf(bounds.width(), bounds.height()) / 2f) * 0.95f

        canvas.drawCircle(centerX, centerY, radius, transparentGreyBasePaint)

        val fullMoonPath = Path().apply {
            addCircle(centerX, centerY, radius, Path.Direction.CW)
        }

        val cutterCircleX = centerX + radius * (0.6f - 2.7f * _moonPhase)
        val cutterCircleY = centerY + radius * (-0.6f + 2.7f * _moonPhase)

        val cutterPath = Path().apply {
            addCircle(cutterCircleX, cutterCircleY, radius, Path.Direction.CW)
        }

        val finalMoonShapePath = Path().apply {
            op(fullMoonPath, cutterPath, Path.Op.DIFFERENCE)
        }

        val constantGlowRadius = 20f
        val constantGlowAlpha = 100
        val constantGlowColor = Color.argb(
            constantGlowAlpha,
            Color.red(Color.WHITE),
            Color.green(Color.WHITE),
            Color.blue(Color.WHITE)
        )

        moonPaint.setShadowLayer(constantGlowRadius, 0f, 0f, constantGlowColor)
        canvas.drawPath(finalMoonShapePath, moonPaint)
        moonPaint.clearShadowLayer()

        powerIconDrawable?.let { drawable ->
            val alpha = powerIconAlpha.coerceIn(0, 255)
            drawable.alpha = alpha

            val size = (radius * 1.0f).toInt()
            val left = (centerX - size / 2).toInt()
            val top = (centerY - size / 2).toInt()
            val right = left + size
            val bottom = top + size

            drawable.setBounds(left, top, right, bottom)
            drawable.draw(canvas)
        }
    }

    override fun setAlpha(alpha: Int) {
        moonPaint.alpha = alpha
        transparentGreyBasePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        moonPaint.colorFilter = colorFilter
        transparentGreyBasePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}