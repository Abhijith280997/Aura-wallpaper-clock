package com.aura.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

class AuraWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = AuraEngine()

    inner class AuraEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val startTime = System.currentTimeMillis()
        private val loopSeconds = 20f
        private val tau = (2 * PI).toFloat()

        private var w = 0
        private var h = 0
        private var visible = false

        private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val dateFmt = SimpleDateFormat("EEEE, dd MMMM", Locale("en", "IN"))

        private val bgPaint = Paint()
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F2E5C8")
            typeface = Typeface.SERIF
            textAlign = Paint.Align.CENTER
            letterSpacing = -0.03f
            setShadowLayer(40f, 0f, 0f, Color.parseColor("#30D4AF62"))
        }
        private val greetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B49A65")
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.35f
        }
        private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B6AA8E")
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.25f
        }

        // Particle data, fixed seed so the layout is the same every time
        private val count = 55
        private val px = FloatArray(count)
        private val py = FloatArray(count)
        private val pr = FloatArray(count)
        private val laps = IntArray(count)
        private val swayN = IntArray(count)
        private val twinkleN = IntArray(count)
        private val phase = FloatArray(count)

        private val drawRunnable = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) handler.postDelayed(this, 33)
            }
        }

        private fun seeded(n: Int): Float {
            val x = sin(n * 127.1) * 43758.5453
            return (x - Math.floor(x)).toFloat()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width
            h = height

            bgPaint.shader = RadialGradient(
                w / 2f, h * 0.47f, h * 0.72f,
                intArrayOf(Color.parseColor("#21190C"), Color.parseColor("#100E09"), Color.parseColor("#080808")),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )

            val d = resources.displayMetrics.density
            for (i in 0 until count) {
                px[i] = seeded(i + 1) * w
                py[i] = seeded(i + 101) * h
                pr[i] = (seeded(i + 201) * 1.8f + 0.5f) * d
                laps[i] = 1 + (seeded(i + 301) * 3).toInt()
                swayN[i] = 1 + (seeded(i + 401) * 3).toInt()
                phase[i] = seeded(i + 501) * tau
                twinkleN[i] = 1 + (seeded(i + 601) * 3).toInt()
            }

            clockPaint.textSize = w * 0.24f
            greetPaint.textSize = 11f * d
            datePaint.textSize = 12f * d
            ringPaint.strokeWidth = 1f * d
            drawFrame()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            handler.removeCallbacks(drawRunnable)
            if (isVisible) handler.post(drawRunnable)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunnable)
        }

        private fun drawFrame() {
            if (w == 0 || h == 0) return
            val holder = surfaceHolder
            val canvas = holder.lockCanvas() ?: return
            try {
                render(canvas)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }

        private fun render(c: Canvas) {
            val elapsed = ((System.currentTimeMillis() - startTime) / 1000f) % loopSeconds
            val u = elapsed / loopSeconds
            val d = resources.displayMetrics.density

            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

            // Breathing rings, 5 second cycle
            val breathe = (sin(u * tau * 4) + 1f) / 2f
            val scale = 1f + 0.06f * breathe
            val alpha = 0.7f + 0.3f * breathe
            val cx = w / 2f
            val cy = h * 0.62f
            val base = w * 0.24f

            val rings = arrayOf(floatArrayOf(1f, 0.31f), floatArrayOf(0.86f, 0.19f), floatArrayOf(0.68f, 0.19f))
            for (r in rings) {
                ringPaint.color = Color.argb((r[1] * alpha * 255).toInt(), 212, 175, 98)
                c.drawCircle(cx, cy, base * r[0] * scale, ringPaint)
            }

            glowPaint.shader = RadialGradient(
                cx, cy, 46f * d,
                Color.argb((0.55f * alpha * 255).toInt(), 242, 213, 139),
                Color.argb(0, 212, 175, 98),
                Shader.TileMode.CLAMP
            )
            c.drawCircle(cx, cy, 46f * d, glowPaint)
            dotPaint.color = Color.rgb(242, 213, 139)
            c.drawCircle(cx, cy, 3f * d, dotPaint)

            // Particles drifting upward
            for (i in 0 until count) {
                var y = (py[i] - laps[i] * h * u) % h
                if (y < 0) y += h
                val x = px[i] + sin(u * tau * swayN[i] + phase[i]) * 6f * d
                val a = 0.25f + (sin(u * tau * twinkleN[i] + phase[i]) + 1f) * 0.3f
                dotPaint.color = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), 212, 175, 98)
                c.drawCircle(x, y, pr[i], dotPaint)
            }

            // Live clock
            val now = Date()
            val clockY = h * 0.27f
            c.drawText("AURA", cx, h * 0.17f - 28f * d, greetPaint)
            c.drawText(greeting(), cx, clockY - clockPaint.textSize - 6f * d, greetPaint)
            c.drawText(timeFmt.format(now), cx, clockY, clockPaint)
            c.drawText(dateFmt.format(now).uppercase(Locale.ROOT), cx, clockY + 36f * d, datePaint)
        }

        private fun greeting(): String {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            return when {
                hour < 12 -> "GOLDEN MORNING"
                hour < 17 -> "QUIET LUXURY"
                hour < 20 -> "GOLDEN HOUR"
                else -> "AFTER DARK"
            }
        }
    }
}
