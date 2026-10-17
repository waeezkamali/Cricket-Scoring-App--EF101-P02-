package com.ef101.p02.cricketscorer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.ef101.p02.cricketscorer.R
import kotlin.math.max

/**
 * A lightweight canvas line chart plotting over-by-over run progression.
 *
 * The x-axis is the over number and the y-axis is the cumulative score at the
 * end of each over. No external charting dependency is used; drawing is done
 * directly onto the canvas.
 */
class RunRateChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var cumulative: List<Int> = emptyList()

    private val accent = context.getColor(R.color.brand_primary)
    private val muted = context.getColor(R.color.text_muted)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = accent
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = (accent and 0x00FFFFFF) or 0x33000000
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = accent
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = (muted and 0x00FFFFFF) or 0x33000000
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = (muted and 0x00FFFFFF) or 0x66000000
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted
        textSize = sp(10f)
    }

    fun setData(perOverRuns: List<Int>) {
        var acc = 0
        cumulative = perOverRuns.map { acc += it; acc }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val height = resolveSize(dp(180f).toInt(), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = cumulative.size
        val padLeft = dp(30f)
        val padRight = dp(10f)
        val padTop = dp(12f)
        val padBottom = dp(22f)
        val chartW = width - padLeft - padRight
        val chartH = height - padTop - padBottom
        if (n == 0 || chartW <= 0 || chartH <= 0) {
            return
        }

        val maxRuns = max(1, cumulative.maxOrNull() ?: 0)
        val lineCount = 4
        for (i in 0..lineCount) {
            val y = padTop + chartH * i / lineCount
            canvas.drawLine(padLeft, y, padLeft + chartW, y, gridPaint)
            val value = maxRuns * (lineCount - i) / lineCount
            canvas.drawText(value.toString(), dp(4f), y + sp(3.5f), labelPaint)
        }
        canvas.drawLine(padLeft, padTop + chartH, padLeft + chartW, padTop + chartH, axisPaint)

        val stepX = if (n == 1) 0f else chartW.toFloat() / (n - 1)
        fun xAt(i: Int): Float =
            if (n == 1) padLeft + chartW / 2f else padLeft + stepX * i
        fun yAt(v: Int): Float = padTop + chartH - chartH.toFloat() * v / maxRuns

        // Filled area under the curve.
        val fill = Path().apply {
            moveTo(xAt(0), padTop + chartH.toFloat())
            for (i in 0 until n) lineTo(xAt(i), yAt(cumulative[i]))
            lineTo(xAt(n - 1), padTop + chartH.toFloat())
            close()
        }
        canvas.drawPath(fill, fillPaint)

        // The progression line.
        val line = Path().apply {
            moveTo(xAt(0), yAt(cumulative[0]))
            for (i in 1 until n) lineTo(xAt(i), yAt(cumulative[i]))
        }
        canvas.drawPath(line, linePaint)

        // Data points + x-axis labels (thinned out for long innings).
        val labelEvery = if (n <= 12) 1 else (n + 9) / 10
        for (i in 0 until n) {
            canvas.drawCircle(xAt(i), yAt(cumulative[i]), dp(2.6f), pointPaint)
            if (i % labelEvery == 0 || i == n - 1) {
                val label = (i + 1).toString()
                val tw = labelPaint.measureText(label)
                canvas.drawText(label, xAt(i) - tw / 2f, height - dp(6f), labelPaint)
            }
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    private fun sp(v: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics
    )
}
