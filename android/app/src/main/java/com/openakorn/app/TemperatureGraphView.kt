package com.openakorn.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class TemperatureGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var samples: List<TemperatureSample> = emptyList()
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD8D8D0.toInt(); strokeWidth = dp(1f) }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF596159.toInt(); textSize = dp(11f) }
    private val series = listOf(
        Series(0xFF245638.toInt(), { it.pit }, false),
        Series(0xFFA56619.toInt(), { it.probe1 }, false),
        Series(0xFF356A86.toInt(), { it.probe2 }, false),
        Series(0xFF245638.toInt(), { it.pit }, true),
        Series(0xFFA56619.toInt(), { it.probe1 }, true),
        Series(0xFF356A86.toInt(), { it.probe2 }, true),
    )

    fun submit(value: List<TemperatureSample>) {
        samples = value
        contentDescription = accessibleSummary(value)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = dp(38f)
        val right = width - dp(8f)
        val top = dp(10f)
        val bottom = height - dp(23f)
        if (right <= left || bottom <= top) return

        for (tick in 0..3) {
            val y = top + (bottom - top) * tick / 3f
            canvas.drawLine(left, y, right, y, gridPaint)
            canvas.drawText("${400 - tick * 100}°", dp(2f), y + dp(4f), labelPaint)
        }
        canvas.drawText("older", left, height - dp(4f), labelPaint)
        canvas.drawText("now", right - dp(24f), height - dp(4f), labelPaint)
        if (samples.size < 2) {
            canvas.drawText("Waiting for fresh samples", left + dp(8f), top + dp(24f), labelPaint)
            return
        }

        val first = samples.first().elapsedMillis
        val last = max(first + 1L, samples.last().elapsedMillis)
        series.forEach { item ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = item.color
                style = Paint.Style.STROKE
                strokeWidth = if (item.target) dp(1.5f) else dp(2.5f)
                strokeCap = Paint.Cap.ROUND
                if (item.target) pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f)
            }
            var path = Path()
            var started = false
            var previousTime: Long? = null
            samples.forEach { point ->
                val probe = item.value(point)
                val value = if (item.target) probe?.targetC else probe?.currentC
                if (value == null || !value.isFinite() || value !in 0.0..400.0 ||
                    (previousTime != null && point.elapsedMillis - previousTime!! > 15_000L)) {
                    if (started) canvas.drawPath(path, paint)
                    path = Path()
                    started = false
                } else {
                    val x = left + (right - left) * ((point.elapsedMillis - first).toFloat() / (last - first))
                    val y = bottom - (bottom - top) * (value.toFloat() / 400f)
                    if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                }
                previousTime = point.elapsedMillis
            }
            if (started) canvas.drawPath(path, paint)
        }
    }

    private fun accessibleSummary(value: List<TemperatureSample>): String {
        val latest = value.lastOrNull() ?: return "Temperature graph. No samples yet."
        val readings = listOf("pit" to latest.pit, "probe 1" to latest.probe1, "probe 2" to latest.probe2)
            .mapNotNull { (name, probe) -> probe?.currentC?.let { "$name ${"%.1f".format(it)} degrees Celsius" } }
        return if (readings.isEmpty()) "Temperature graph. Latest sample has no available probes."
        else "Temperature graph, current session. " + readings.joinToString(", ")
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private data class Series(
        val color: Int,
        val value: (TemperatureSample) -> ProbeReading?,
        val target: Boolean,
    )
}
