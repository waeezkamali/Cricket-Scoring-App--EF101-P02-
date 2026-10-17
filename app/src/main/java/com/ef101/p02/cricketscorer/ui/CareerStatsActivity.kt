package com.ef101.p02.cricketscorer.ui

import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ef101.p02.cricketscorer.R
import com.ef101.p02.cricketscorer.data.CareerStats
import com.ef101.p02.cricketscorer.databinding.ActivityCareerStatsBinding
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Player career statistics aggregated across every saved match.
 */
class CareerStatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCareerStatsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCareerStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.summaryText.text = "Loading…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { CareerStats.aggregate(this@CareerStatsActivity) }
            render(result)
        }
    }

    private fun render(result: CareerStats.Result) {
        binding.statsContainer.removeAllViews()
        if (result.isEmpty) {
            binding.emptyText.visibility = View.VISIBLE
            binding.summaryText.text = ""
            return
        }
        binding.emptyText.visibility = View.GONE
        binding.summaryText.text =
            "${result.matches} match${if (result.matches == 1) "" else "es"} aggregated"

        if (result.batting.isNotEmpty()) {
            addCard(
                getString(R.string.career_tab_batting),
                listOf("Player", "Inn", "Runs", "HS", "Avg", "SR", "4s", "6s"),
                listOf(3.2f, 0.9f, 1.0f, 0.9f, 0.9f, 1.1f, 0.8f, 0.8f),
                result.batting.map { b ->
                    listOf(
                        b.name,
                        b.innings.toString(),
                        b.runs.toString(),
                        b.highest.toString(),
                        b.average?.let { "%.2f".format(it) } ?: "—",
                        "%.1f".format(b.strikeRate),
                        b.fours.toString(),
                        b.sixes.toString()
                    )
                }
            )
        }

        if (result.bowling.isNotEmpty()) {
            addCard(
                getString(R.string.career_tab_bowling),
                listOf("Player", "Inn", "Wkts", "Econ", "Best"),
                listOf(3.2f, 0.9f, 0.9f, 1.1f, 1.2f),
                result.bowling.map { b ->
                    listOf(
                        b.name,
                        b.innings.toString(),
                        b.wickets.toString(),
                        "%.2f".format(b.economy),
                        b.best ?: "—"
                    )
                }
            )
        }
    }

    // ----- Table rendering --------------------------------------------------

    private fun addCard(
        title: String,
        headers: List<String>,
        weights: List<Float>,
        rows: List<List<String>>
    ) {
        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = dp(2).toFloat()
            setCardBackgroundColor(color(R.color.card_surface))
            setStrokeColor((colorMuted() and 0x00FFFFFF) or 0x22000000)
            strokeWidth = dp(1)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        card.addView(col, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        col.addView(text(title, 14f, bold = true, color = color(R.color.brand_primary))
            .apply { setPadding(0, 0, 0, dp(4)) })
        col.addView(headerRow(headers, weights))

        rows.forEachIndexed { index, cells ->
            col.addView(bodyRow(cells, weights))
            if (index < rows.size - 1) {
                col.addView(View(this).apply { setBackgroundColor(color(R.color.table_divider)) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
            }
        }

        binding.statsContainer.addView(card)
    }

    private fun headerRow(headers: List<String>, weights: List<Float>): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(color(R.color.table_header_bg))
            setPadding(0, dp(8), 0, dp(8))
        }
        headers.forEachIndexed { i, h ->
            row.addView(cell(h, weights[i], end = i > 0, header = true))
        }
        return row
    }

    private fun bodyRow(cells: List<String>, weights: List<Float>): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(9), 0, dp(9))
        }
        cells.forEachIndexed { i, c ->
            row.addView(cell(c, weights[i], end = i > 0, header = false))
        }
        return row
    }

    private fun cell(value: String, weight: Float, end: Boolean, header: Boolean): View {
        val gravity = (if (end) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
        return TextView(this).apply {
            text = value
            textSize = if (header) 11f else 14f
            setTextColor(if (header) colorMuted() else colorText())
            typeface = Typeface.create(Typeface.DEFAULT, if (header) Typeface.BOLD else Typeface.NORMAL)
            this.gravity = gravity
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
        }
    }

    // ----- Helpers ----------------------------------------------------------

    private fun text(value: String, size: Float, bold: Boolean = false, color: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun color(id: Int): Int = ContextCompat.getColor(this, id)
    private fun colorText(): Int = color(R.color.text_primary)
    private fun colorMuted(): Int = color(R.color.text_muted)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
