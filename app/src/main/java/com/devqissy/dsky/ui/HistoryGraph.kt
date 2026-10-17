package com.devqissy.dsky.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader.TileMode
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.devqissy.dsky.R
import com.devqissy.dsky.sys.ActivityReceiver
import com.devqissy.dsky.sys.QueryTracker
import com.devqissy.dsky.sys.VpnController
import java.util.Arrays

class HistoryGraph(context: Context, attrs: AttributeSet) : View(context, attrs), ActivityReceiver {

    companion object {
        private const val WINDOW_MS = 60 * 1000
        private const val RESOLUTION_MS = 100
        private const val PULSE_INTERVAL_MS = 10 * 1000
        private const val DATA_STROKE_WIDTH = 10
        private const val BOTTOM_MARGIN_FRACTION = 0.05f
        private const val RIGHT_MARGIN_FRACTION = 0.1f
    }

    private val tracker: QueryTracker = VpnController.getTracker(context)
    private val dataPaint: Paint
    private val pulsePaint: Paint

    private val range = WINDOW_MS / RESOLUTION_MS
    private val curve = FloatArray(range)
    private val lines = FloatArray((curve.size - 1) * 4)

    private var curveIsEmpty = true
    private var max = 0f
    private var now: Long = 0

    init {
        @Suppress("DEPRECATION")
        val color = resources.getColor(R.color.accent_good)

        dataPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = DATA_STROKE_WIDTH.toFloat()
            style = Paint.Style.STROKE
            this.color = color
        }

        pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 0f
            style = Paint.Style.STROKE
            this.color = color
        }
    }

    fun setColor(color: Int) {
        dataPaint.color = color
        pulsePaint.color = color
        updateDataShader(width)
    }

    private fun gaussian(mu: Float, inverseSigma: Float, x: Int): Float {
        val z = (x - mu) * inverseSigma
        return Math.exp((-z * z).toDouble()).toFloat() * inverseSigma
    }

    override fun receive(activity: Collection<Long>) {
        curveIsEmpty = true
        val scale = 1.0f / RESOLUTION_MS
        for (t in activity) {
            val age = now - t
            if (age < 0) continue
            val e = age * scale
            val sigma = Math.sqrt((e + DATA_STROKE_WIDTH).toDouble()).toFloat()
            val support = 2.7f * sigma
            val left = Math.max(0, (e - support).toInt())
            if (left > range) continue
            if (curveIsEmpty) {
                curveIsEmpty = false
                Arrays.fill(curve, 0.0f)
            }
            val right = Math.min(range, (e + support).toInt())
            val inverseSigma = 1.0f / sigma
            for (i in left until right) {
                curve[i] += gaussian(e, inverseSigma, i)
            }
        }
    }

    private fun updateCurve(): Boolean {
        tracker.showActivity(this)
        return !curveIsEmpty
    }

    private fun updateMax() {
        var total = 0f
        for (v in curve) {
            total += v
            max = Math.max(max, v)
        }
        if (total == 0f) max = 0f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        updateDataShader(w)
    }

    private fun updateDataShader(width: Int) {
        val color = dataPaint.color
        dataPaint.shader = LinearGradient(
            0f, 0f, width.toFloat(), 0f,
            color, color and 0x00FFFFFF, TileMode.CLAMP
        )
    }

    override fun onMeasure(w: Int, h: Int) {
        val wMode = MeasureSpec.getMode(w)
        val width = if (wMode == MeasureSpec.AT_MOST || wMode == MeasureSpec.EXACTLY)
            MeasureSpec.getSize(w) else suggestedMinimumWidth

        val hMode = MeasureSpec.getMode(h)
        val height = if (hMode == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(h)
        } else {
            val screenHeight = resources.displayMetrics.heightPixels
            val preferred = (0.8 * screenHeight).toInt()
            if (hMode == MeasureSpec.AT_MOST) Math.min(preferred, MeasureSpec.getSize(h))
            else preferred
        }
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.rotate(180f, width / 2f, height / 2f)

        val xoffset = width * RIGHT_MARGIN_FRACTION
        val usableWidth = width - xoffset
        val yoffset = height * BOTTOM_MARGIN_FRACTION
        val usableHeight = Math.min(width.toFloat(), height - (DATA_STROKE_WIDTH + yoffset))

        now = SystemClock.elapsedRealtime()
        val rightEndY: Float
        if (updateCurve()) {
            updateMax()
            val xscale = usableWidth / (curve.size - 1)
            val yscale = if (max == 0f) 0f else usableHeight / max
            for (i in 1 until curve.size) {
                val j = (i - 1) * 4
                lines[j] = (i - 1) * xscale + xoffset
                lines[j + 1] = curve[i - 1] * yscale + yoffset
                lines[j + 2] = i * xscale + xoffset
                lines[j + 3] = curve[i] * yscale + yoffset
            }
            canvas.drawLines(lines, dataPaint)
            rightEndY = lines[1]
        } else {
            max = 0f
            canvas.drawLine(xoffset, yoffset, xoffset + usableWidth, yoffset, dataPaint)
            rightEndY = yoffset
        }

        val tagRadius = 2f * DATA_STROKE_WIDTH
        val tagX = xoffset - tagRadius
        canvas.drawCircle(tagX, rightEndY, tagRadius, dataPaint)

        val maxRadius = width - tagX
        var age = now % PULSE_INTERVAL_MS
        while (age < WINDOW_MS) {
            val fraction = age.toFloat() / WINDOW_MS
            val radius = tagRadius + fraction * (maxRadius - tagRadius)
            pulsePaint.alpha = (255 * (1 - fraction)).toInt()
            canvas.drawCircle(tagX, yoffset, radius, pulsePaint)
            age += PULSE_INTERVAL_MS
        }
        postInvalidateOnAnimation()
    }
}