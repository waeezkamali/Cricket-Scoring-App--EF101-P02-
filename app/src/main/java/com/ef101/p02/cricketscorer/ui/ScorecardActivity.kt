package com.ef101.p02.cricketscorer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ef101.p02.cricketscorer.R
import com.ef101.p02.cricketscorer.data.MatchStore
import com.ef101.p02.cricketscorer.databinding.ActivityScorecardBinding
import com.ef101.p02.cricketscorer.engine.NativeEngine
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject

/**
 * Renders a full, print-quality scorecard: Material 3 cards per innings, strict
 * column alignment (names left, numbers right), clear typographic hierarchy for
 * primary scores vs. secondary metrics, a run-progression chart and one-tap
 * text sharing.
 */
class ScorecardActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MATCH_ID = "match_id"
    }

    private lateinit var binding: ActivityScorecardBinding
    private var engine: NativeEngine? = null
    private var scorecard: JSONObject = JSONObject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScorecardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val matchId = intent.getStringExtra(EXTRA_MATCH_ID) ?: run { finish(); return }
        try {
            engine = MatchStore.open(this, matchId)
        } catch (t: Throwable) {
            Toast.makeText(this, "Could not open scorecard", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        scorecard = JSONObject(engine!!.scorecardJson())
        render(scorecard)
        binding.btnShare.setOnClickListener { shareScorecard() }
    }

    // ----- Rendering --------------------------------------------------------

    private fun render(sc: JSONObject) {
        binding.scorecardContainer.removeAllViews()
        addHeaderCard(sc)

        val innings = sc.optJSONArray("innings") ?: return
        for (i in 0 until innings.length()) {
            addInningsCard(innings.optJSONObject(i))
        }
    }

    private fun addHeaderCard(sc: JSONObject) {
        val col = addCard()
        col.addView(text(
            "${sc.optString("teamA")}  vs  ${sc.optString("teamB")}",
            size = 21f, bold = true, color = colorText()
        ))

        val result = sc.optString("result")
        if (result.isNotEmpty()) {
            col.addView(text(result, size = 15f, bold = true, color = colorSuccess())
                .apply { setPadding(0, dp(6), 0, 0) })
        }

        val venue = sc.optString("venue")
        val date = sc.optString("date")
        val meta = listOf(venue, date).filter { it.isNotEmpty() }.joinToString("  •  ")
        if (meta.isNotEmpty()) {
            col.addView(text(meta, size = 13f, color = colorMuted())
                .apply { setPadding(0, dp(6), 0, 0) })
        }
    }

    private fun addInningsCard(inn: JSONObject?) {
        if (inn == null) return
        val col = addCard()

        col.addView(inningsHeader(inn))
        col.addView(sectionTitle(getString(R.string.scorecard_batting)))
        col.addView(battingTable(inn))
        col.addView(extrasRow(inn))
        col.addView(totalRow(inn))

        val bowlers = inn.optJSONArray("bowlers")
        if (bowlers != null && bowlers.length() > 0) {
            col.addView(sectionTitle(getString(R.string.scorecard_bowling)))
            col.addView(bowlingTable(inn))
        }

        val perOver = intList(inn.optJSONArray("perOverRuns"))
        if (perOver.isNotEmpty()) {
            col.addView(sectionTitle(getString(R.string.scorecard_runrate)))
            val chart = RunRateChartView(this)
            chart.setData(perOver)
            col.addView(chart, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(180)
            ))
        }
    }

    private fun inningsHeader(inn: JSONObject): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(text(inn.optString("battingTeam"), size = 18f, bold = true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val scoreCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        scoreCol.addView(text(
            "${inn.optInt("runs")}/${inn.optInt("wickets")}",
            size = 20f, bold = true, color = colorPrimary()
        ).apply { gravity = Gravity.END })
        scoreCol.addView(text(
            "(${inn.optString("overs")} ov)",
            size = 12f, color = colorMuted()
        ).apply { gravity = Gravity.END })
        row.addView(scoreCol)
        return row
    }

    // ----- Batting ----------------------------------------------------------

    private fun battingTable(inn: JSONObject): View {
        val table = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        table.addView(tableRow(
            listOf(
                Cell("Batter", weight = 3.6f, role = Role.Header),
                Cell("R", weight = 0.9f, end = true, role = Role.Header),
                Cell("B", weight = 0.9f, end = true, role = Role.Header),
                Cell("4s", weight = 0.8f, end = true, role = Role.Header),
                Cell("6s", weight = 0.8f, end = true, role = Role.Header),
                Cell("SR", weight = 1.3f, end = true, role = Role.Header)
            ), header = true
        ))

        val batters = inn.optJSONArray("batters") ?: JSONArray()
        for (i in 0 until batters.length()) {
            val b = batters.optJSONObject(i) ?: continue
            table.addView(tableRow(
                listOf(
                    Cell(b.optString("name"), dismissalLabel(b), 3.6f),
                    Cell(b.optInt("runs").toString(), weight = 0.9f, end = true),
                    Cell(b.optInt("balls").toString(), weight = 0.9f, end = true),
                    Cell(b.optInt("fours").toString(), weight = 0.8f, end = true),
                    Cell(b.optInt("sixes").toString(), weight = 0.8f, end = true),
                    Cell(b.optString("strikeRate"), weight = 1.3f, end = true, role = Role.Metric)
                ), header = false
            ))
        }
        return table
    }

    private fun extrasRow(inn: JSONObject): View {
        val extras = inn.optJSONObject("extras") ?: return View(this)
        val detail = "(wd %d, nb %d, b %d, lb %d)".format(
            extras.optInt("wides"), extras.optInt("noBalls"),
            extras.optInt("byes"), extras.optInt("legByes")
        )
        return summaryRow(
            getString(R.string.scorecard_extras),
            "${extras.optInt("total")}  $detail",
            muted = true
        )
    }

    private fun totalRow(inn: JSONObject): View = summaryRow(
        getString(R.string.scorecard_total),
        "${inn.optInt("runs")}/${inn.optInt("wickets")}  (${inn.optString("overs")} ov)",
        muted = false
    )

    private fun summaryRow(label: String, value: String, muted: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(2))
        }
        row.addView(
            text(label, size = 14f, bold = !muted, color = if (muted) colorMuted() else colorText()),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(
            text(value, size = if (muted) 13f else 15f, bold = !muted,
                color = if (muted) colorMuted() else colorPrimary())
                .apply { gravity = Gravity.END }
        )
        return row
    }

    // ----- Bowling ----------------------------------------------------------

    private fun bowlingTable(inn: JSONObject): View {
        val table = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        table.addView(tableRow(
            listOf(
                Cell("Bowler", weight = 3.6f, role = Role.Header),
                Cell("O", weight = 1.0f, end = true, role = Role.Header),
                Cell("M", weight = 0.8f, end = true, role = Role.Header),
                Cell("R", weight = 0.9f, end = true, role = Role.Header),
                Cell("W", weight = 0.8f, end = true, role = Role.Header),
                Cell("Econ", weight = 1.3f, end = true, role = Role.Header)
            ), header = true
        ))

        val bowlers = inn.optJSONArray("bowlers") ?: JSONArray()
        for (i in 0 until bowlers.length()) {
            val b = bowlers.optJSONObject(i) ?: continue
            val extras = buildList {
                if (b.optInt("wides") > 0) add("${b.optInt("wides")}w")
                if (b.optInt("noBalls") > 0) add("${b.optInt("noBalls")}nb")
            }.joinToString(", ")
            table.addView(tableRow(
                listOf(
                    Cell(b.optString("name"), extras.ifEmpty { null }, 3.6f),
                    Cell(b.optString("overs"), weight = 1.0f, end = true),
                    Cell(b.optInt("maidens").toString(), weight = 0.8f, end = true),
                    Cell(b.optInt("runs").toString(), weight = 0.9f, end = true),
                    Cell(b.optInt("wickets").toString(), weight = 0.8f, end = true),
                    Cell(b.optString("economy"), weight = 1.3f, end = true, role = Role.Metric)
                ), header = false
            ))
        }
        return table
    }

    // ----- Table building blocks -------------------------------------------

    private enum class Role { Header, Body, Metric, Total }

    private class Cell(
        val primary: String,
        val secondary: String? = null,
        val weight: Float = 1f,
        val end: Boolean = false,
        val role: Role = Role.Body
    )

    private fun addCard(): LinearLayout {
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
        binding.scorecardContainer.addView(card)
        return col
    }

    private fun tableRow(cells: List<Cell>, header: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(7), 0, dp(7))
            if (header) setBackgroundColor(color(R.color.table_header_bg))
        }
        cells.forEach { row.addView(cellView(it)) }

        if (header) {
            return row.apply {
                setPadding(0, dp(8), 0, dp(8))
            }
        }
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(row, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        wrap.addView(View(this).apply { setBackgroundColor(color(R.color.table_divider)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        return wrap
    }

    private fun cellView(cell: Cell): View {
        val textColor = when (cell.role) {
            Role.Header, Role.Metric -> colorMuted()
            else -> colorText()
        }
        val size = when (cell.role) {
            Role.Header -> 11f
            Role.Metric -> 12f
            else -> 14f
        }
        val style =
            if (cell.role == Role.Header || cell.role == Role.Total) Typeface.BOLD
            else Typeface.NORMAL
        val gravity = (if (cell.end) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
        val lp = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, cell.weight
        )

        if (cell.secondary == null) {
            return TextView(this).apply {
                text = cell.primary
                textSize = size
                setTextColor(textColor)
                typeface = Typeface.create(Typeface.DEFAULT, style)
                this.gravity = gravity
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = lp
            }
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            this.gravity = if (cell.end) Gravity.END else Gravity.START
            layoutParams = lp
        }
        col.addView(TextView(this).apply {
            text = cell.primary
            textSize = size
            setTextColor(colorText())
            typeface = Typeface.create(Typeface.DEFAULT, style)
            this.gravity = gravity
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        col.addView(TextView(this).apply {
            text = cell.secondary
            textSize = 11f
            setTextColor(colorMuted())
            this.gravity = gravity
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        return col
    }

    private fun sectionTitle(title: String): TextView = text(
        title, size = 14f, bold = true, color = colorPrimary()
    ).apply { setPadding(0, dp(16), 0, dp(2)) }

    private fun text(
        value: String,
        size: Float,
        bold: Boolean = false,
        color: Int = colorText()
    ): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun dismissalLabel(b: JSONObject): String {
        if (!b.optBoolean("out")) return "not out"
        val how = b.optString("howOut")
        val bowler = b.optString("bowler")
        val fielder = b.optString("fielder")
        return when (how) {
            "Run out" -> "run out" + if (fielder.isNotEmpty()) " ($fielder)" else ""
            "Caught" -> buildString {
                append("c ")
                if (fielder.isNotEmpty()) append("$fielder ")
                append("b $bowler")
            }
            "Stumped" -> buildString {
                append("st ")
                if (fielder.isNotEmpty()) append("$fielder ")
                append("b $bowler")
            }
            "LBW" -> "lbw b $bowler"
            "Bowled" -> "b $bowler"
            "Retired" -> "retired"
            else -> how.lowercase()
        }
    }

    // ----- Sharing ----------------------------------------------------------

    private fun shareScorecard() {
        if (scorecard.length() == 0) return
        val text = buildShareText(scorecard)

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Scorecard", text))
        Toast.makeText(this, R.string.share_copied, Toast.LENGTH_SHORT).show()

        val teamA = scorecard.optString("teamA")
        val teamB = scorecard.optString("teamB")
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "$teamA vs $teamB — scorecard")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_chooser_title)))
    }

    private fun buildShareText(sc: JSONObject): String {
        val sb = StringBuilder()
        sb.append("CRICKET SCORECARD\n")
        sb.append("${sc.optString("teamA")} vs ${sc.optString("teamB")}\n")

        val venue = sc.optString("venue")
        val date = sc.optString("date")
        val meta = listOf(venue, date).filter { it.isNotEmpty() }.joinToString("  •  ")
        if (meta.isNotEmpty()) sb.append("$meta\n")

        val result = sc.optString("result")
        if (result.isNotEmpty()) sb.append("Result: $result\n")

        val innings = sc.optJSONArray("innings") ?: JSONArray()
        for (i in 0 until innings.length()) {
            val inn = innings.optJSONObject(i) ?: continue
            sb.append("\n------------------------------\n")
            sb.append(
                "${inn.optString("battingTeam")} " +
                    "${inn.optInt("runs")}/${inn.optInt("wickets")} " +
                    "(${inn.optString("overs")} ov)\n"
            )

            val batters = inn.optJSONArray("batters") ?: JSONArray()
            for (b in 0 until batters.length()) {
                val bj = batters.optJSONObject(b) ?: continue
                val dis = dismissalLabel(bj)
                sb.append(
                    "  %-18s %3d (%3d)  %s\n".format(
                        bj.optString("name").take(18), bj.optInt("runs"),
                        bj.optInt("balls"), dis
                    )
                )
            }
            val extras = inn.optJSONObject("extras")
            if (extras != null) {
                sb.append("  Extras ${extras.optInt("total")}\n")
            }
            sb.append("  TOTAL ${inn.optInt("runs")}/${inn.optInt("wickets")}\n")

            val bowlers = inn.optJSONArray("bowlers") ?: JSONArray()
            if (bowlers.length() > 0) {
                sb.append("  -- Bowling --\n")
                for (b in 0 until bowlers.length()) {
                    val bj = bowlers.optJSONObject(b) ?: continue
                    sb.append(
                        "  %-18s %s-%-2d-%-2d-%d\n".format(
                            bj.optString("name").take(18),
                            bj.optString("overs"), bj.optInt("maidens"),
                            bj.optInt("runs"), bj.optInt("wickets")
                        )
                    )
                }
            }
        }
        sb.append("\nShared from Cricket Scorer (offline)")
        return sb.toString()
    }

    // ----- Helpers ----------------------------------------------------------

    private fun intList(arr: JSONArray?): List<Int> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.optInt(it) }
    }

    private fun color(id: Int): Int = ContextCompat.getColor(this, id)
    private fun colorText(): Int = color(R.color.text_primary)
    private fun colorMuted(): Int = color(R.color.text_muted)
    private fun colorPrimary(): Int = color(R.color.brand_primary)
    private fun colorSuccess(): Int = color(R.color.success)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        engine?.close()
    }
}
