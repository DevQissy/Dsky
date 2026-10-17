package com.devqissy.dsky.ui.fragment

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*
import kotlin.random.Random
import androidx.core.graphics.toColorInt

class ParticleBackground @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private object ColorPalette {
        val BACKGROUND = "#000010".toColorInt()
        val SURFACE = "#08001F".toColorInt()
        val PRIMARY = "#6A20FF".toColorInt()
        val SECONDARY = "#8B4DFF".toColorInt()
        val GLOW = "#A875FF".toColorInt()
        val TEXT = "#F5F0FF".toColorInt()

        val NEBULA_COLOR_PRIMARY_START = "#336A20FF".toColorInt()
        val NEBULA_COLOR_PRIMARY_END = "#006A20FF".toColorInt()
        val NEBULA_COLOR_SECONDARY_START = "#268B4DFF".toColorInt()
        val NEBULA_COLOR_SECONDARY_END = "#008B4DFF".toColorInt()
    }

    private object ParticleConfig {
        const val NUM_PARTICLES = 500
        const val MIN_RADIUS = 0.5f
        const val MAX_RADIUS = 4.5f
        const val BASE_ALPHA = 190f
        const val ALPHA_VARIATION = 60f
        const val MIN_BLINK_SPEED = 0.008f
        const val MAX_BLINK_SPEED_ADDITION = 0.02f
        val COLORS = intArrayOf(
            ColorPalette.TEXT,
            ColorPalette.GLOW,
            "#EBE2FF".toColorInt(),
            "#D8C2FF".toColorInt()
        )
        const val ANGULAR_SPEED = 0.0008f
        const val TWO_PI = (2 * PI).toFloat()
    }

    private object NebulaConfig {
        const val COUNT = 5
        const val MIN_RADIUS_FACTOR = 0.5f
        const val MAX_RADIUS_FACTOR = 1.5f
        const val MAX_SPEED = 0.01f
    }

    private object MeteorConfig {
        const val NUM_METEORS = 1
        const val MIN_SIZE = 3f
        const val MAX_SIZE = 7f
        const val ANGULAR_SPEED = 0.004f
        val COLOR = ColorPalette.GLOW
        const val TRAIL_LENGTH = 20
        const val TRAIL_ALPHA_DECAY = 15
        const val TRAIL_SEGMENT_DISTANCE_FACTOR = 2.0f
        const val SPAWN_INTERVAL_MS = 5000L
    }

    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val nebulaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val meteorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val nebulaMatrix = Matrix()
    private val random = java.util.Random()
    private val backgroundColor = ColorPalette.BACKGROUND

    private val particles = mutableListOf<Particle>()
    private val nebulaBlobs = mutableListOf<NebulaBlob>()
    private val meteors = mutableListOf<MeteorParticle>()

    private var animationRunning = false
    private var lastMeteorSpawnTime = 0L

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animationRunning = true
        lastMeteorSpawnTime = System.currentTimeMillis() - MeteorConfig.SPAWN_INTERVAL_MS
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animationRunning = false
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            if (!animationRunning) {
                animationRunning = true
                postInvalidateOnAnimation()
            }
        } else {
            animationRunning = false
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        particles.clear()
        nebulaBlobs.clear()
        meteors.clear()

        val safeWidth = maxOf(1f, w.toFloat())
        val safeHeight = maxOf(1f, h.toFloat())
        val orbitCenterX = safeWidth
        val orbitCenterY = safeHeight

        repeat(ParticleConfig.NUM_PARTICLES) {
            val initialX = random.nextFloat() * safeWidth
            val initialY = random.nextFloat() * safeHeight
            val starColor = ParticleConfig.COLORS[random.nextInt(ParticleConfig.COLORS.size)]
            particles.add(createParticle(orbitCenterX, orbitCenterY, initialX, initialY, starColor))
        }

        repeat(NebulaConfig.COUNT) { index ->
            val nebulaRadius = (NebulaConfig.MIN_RADIUS_FACTOR + random.nextFloat() * (NebulaConfig.MAX_RADIUS_FACTOR - NebulaConfig.MIN_RADIUS_FACTOR)) * safeWidth
            val nebulaX = random.nextFloat() * safeWidth
            val nebulaY = random.nextFloat() * safeHeight
            val speedX = (random.nextFloat() * 2 - 1) * NebulaConfig.MAX_SPEED
            val speedY = (random.nextFloat() * 2 - 1) * NebulaConfig.MAX_SPEED

            val colorStart = if (index % 2 == 0) ColorPalette.NEBULA_COLOR_PRIMARY_START else ColorPalette.NEBULA_COLOR_SECONDARY_START
            val colorEnd = if (index % 2 == 0) ColorPalette.NEBULA_COLOR_PRIMARY_END else ColorPalette.NEBULA_COLOR_SECONDARY_END

            nebulaBlobs.add(
                NebulaBlob(
                    nebulaX, nebulaY, nebulaRadius,
                    speedX, speedY,
                    colorStart, colorEnd
                )
            )
        }

        if (MeteorConfig.NUM_METEORS > 0) {
            meteors.add(createMeteorParticle(orbitCenterX, orbitCenterY, safeWidth, safeHeight))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(backgroundColor)

        drawNebulas(canvas)
        drawParticles(canvas)
        drawMeteors(canvas)

        if (animationRunning) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawNebulas(canvas: Canvas) {
        for (nebula in nebulaBlobs) {
            nebula.update(width.toFloat(), height.toFloat(), nebulaMatrix)
            nebulaPaint.shader = nebula.shader
            canvas.drawCircle(nebula.x, nebula.y, nebula.radius, nebulaPaint)
        }
        nebulaPaint.shader = null
    }

    private fun drawParticles(canvas: Canvas) {
        for (p in particles) {
            p.update(width.toFloat(), height.toFloat(), random)
            if (p.radius > ParticleConfig.MAX_RADIUS * 0.6f) {
                starPaint.color = p.starColor
                starPaint.alpha = (p.alpha * 0.4f).toInt()
                canvas.drawCircle(p.x, p.y, p.radius * 1.5f, starPaint)
            }
            starPaint.color = p.starColor
            starPaint.alpha = p.alpha.toInt()
            canvas.drawCircle(p.x, p.y, p.radius, starPaint)
        }
    }

    private fun drawMeteors(canvas: Canvas) {
        val currentTime = System.currentTimeMillis()
        if (meteors.isEmpty() && currentTime - lastMeteorSpawnTime >= MeteorConfig.SPAWN_INTERVAL_MS) {
            meteors.add(createMeteorParticle(width.toFloat(), height.toFloat(), width.toFloat(), height.toFloat()))
            lastMeteorSpawnTime = currentTime
        }

        val iterator = meteors.iterator()
        while (iterator.hasNext()) {
            val meteor = iterator.next()
            meteor.update(width.toFloat(), height.toFloat(), iterator)

            val headRadius = meteor.size
            val tailLength = meteor.size * 25f
            val rotationDegrees = (meteor.angle + (PI / 2).toFloat()) * (180f / PI.toFloat())

            canvas.save()
            canvas.rotate(rotationDegrees, meteor.x, meteor.y)

            val dropPath = Path()
            dropPath.moveTo(meteor.x - tailLength, meteor.y)
            dropPath.cubicTo(
                meteor.x - tailLength * 0.7f, meteor.y - headRadius * 0.15f,
                meteor.x - headRadius * 1.4f, meteor.y - headRadius * 0.95f,
                meteor.x, meteor.y - headRadius
            )

            val headRect = RectF(
                meteor.x - headRadius, meteor.y - headRadius,
                meteor.x + headRadius, meteor.y + headRadius
            )
            dropPath.arcTo(headRect, 270f, 180f, false)

            dropPath.cubicTo(
                meteor.x - headRadius * 1.4f, meteor.y + headRadius * 0.95f,
                meteor.x - tailLength * 0.7f, meteor.y + headRadius * 0.15f,
                meteor.x - tailLength, meteor.y
            )
            dropPath.close()

            meteorPaint.shader = LinearGradient(
                meteor.x, meteor.y,
                meteor.x - tailLength, meteor.y,
                intArrayOf(MeteorConfig.COLOR, MeteorConfig.COLOR, Color.TRANSPARENT),
                floatArrayOf(0f, 0.1f, 1f),
                Shader.TileMode.CLAMP
            )
            meteorPaint.alpha = 255
            canvas.drawPath(dropPath, meteorPaint)
            meteorPaint.shader = null

            canvas.restore()
        }
    }

    private inner class Particle {
        var x = 0f
        var y = 0f
        var radius = 0f
        var alpha = 0f
        var orbitRadius = 0f
        var angle = 0f
        var angularSpeed = 0f
        var blinkPhase = 0f
        var blinkSpeed = 0f
        var starColor = 0
        var orbitCenterX = 0f
        var orbitCenterY = 0f

        fun calculateOrbitalParameters() {
            val dx = x - orbitCenterX
            val dy = y - orbitCenterY
            orbitRadius = sqrt(dx * dx + dy * dy)
            angle = atan2(dy, dx)
            angularSpeed = ParticleConfig.ANGULAR_SPEED
        }

        fun update(viewWidth: Float, viewHeight: Float, random: java.util.Random) {
            angle += angularSpeed
            blinkPhase += blinkSpeed
            alpha = ParticleConfig.BASE_ALPHA + ParticleConfig.ALPHA_VARIATION * sin(blinkPhase).toFloat()
            alpha = alpha.coerceIn(0f, 255f)
            x = orbitCenterX + orbitRadius * cos(angle).toFloat()
            y = orbitCenterY + orbitRadius * sin(angle).toFloat()

            if (x < -radius || x > viewWidth + radius || y < -radius || y > viewHeight + radius) {
                respawn(viewWidth, viewHeight, random)
            }
        }

        private fun respawn(viewWidth: Float, viewHeight: Float, random: java.util.Random) {
            if (random.nextFloat() < 0.65f) {
                x = 0f
                y = random.nextFloat() * viewHeight
            } else {
                x = random.nextFloat() * viewWidth
                y = viewHeight
            }
            calculateOrbitalParameters()
            blinkPhase = random.nextFloat() * ParticleConfig.TWO_PI
            alpha = ParticleConfig.BASE_ALPHA
        }
    }

    private fun createParticle(
        ox: Float, oy: Float, ix: Float, iy: Float, color: Int
    ): Particle {
        return Particle().apply {
            orbitCenterX = ox
            orbitCenterY = oy
            x = ix
            y = iy
            starColor = color
            radius = random.nextFloat().pow(2.5f) * (ParticleConfig.MAX_RADIUS - ParticleConfig.MIN_RADIUS) + ParticleConfig.MIN_RADIUS
            blinkSpeed = random.nextFloat() * ParticleConfig.MAX_BLINK_SPEED_ADDITION + ParticleConfig.MIN_BLINK_SPEED
            blinkPhase = random.nextFloat() * ParticleConfig.TWO_PI
            calculateOrbitalParameters()
        }
    }

    private inner class NebulaBlob(
        var x: Float,
        var y: Float,
        var radius: Float,
        var speedX: Float,
        var speedY: Float,
        colorStart: Int,
        colorEnd: Int
    ) {
        var shader: RadialGradient = RadialGradient(0f, 0f, radius, colorStart, colorEnd, Shader.TileMode.CLAMP)

        fun update(vw: Float, vh: Float, matrix: Matrix) {
            x += speedX
            y += speedY
            if (x - radius > vw) x = -radius
            if (x + radius < 0) x = vw + radius
            if (y - radius > vh) y = -radius
            if (y + radius < 0) y = vh + radius
            matrix.reset()
            matrix.setTranslate(x, y)
            shader.setLocalMatrix(matrix)
        }
    }

    private inner class MeteorParticle {
        var x = 0f
        var y = 0f
        var size = 0f
        var orbitRadius = 0f
        var angle = 0f
        var angularSpeed = 0f
        var orbitCenterX = 0f
        var orbitCenterY = 0f

        fun calculateOrbitalParameters() {
            val dx = x - orbitCenterX
            val dy = y - orbitCenterY
            orbitRadius = sqrt(dx * dx + dy * dy)
            angle = atan2(dy, dx)
            angularSpeed = MeteorConfig.ANGULAR_SPEED
        }

        fun update(vw: Float, vh: Float, iterator: MutableIterator<MeteorParticle>) {
            angle += angularSpeed
            x = orbitCenterX + orbitRadius * cos(angle).toFloat()
            y = orbitCenterY + orbitRadius * sin(angle).toFloat()
            if (x > vw + size * 2 || y > vh + size * 2 || x < -size * 2 || y < -size * 2) {
                iterator.remove()
            }
        }
    }

    private fun createMeteorParticle(ox: Float, oy: Float, vw: Float, vh: Float): MeteorParticle {
        return MeteorParticle().apply {
            orbitCenterX = ox
            orbitCenterY = oy
            size = random.nextFloat() * (MeteorConfig.MAX_SIZE - MeteorConfig.MIN_SIZE) + MeteorConfig.MIN_SIZE
            x = random.nextFloat() * vw
            y = random.nextFloat() * vh
            calculateOrbitalParameters()
        }
    }
}