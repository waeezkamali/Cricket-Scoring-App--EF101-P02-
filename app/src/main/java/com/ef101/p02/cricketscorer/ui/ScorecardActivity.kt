package com.ef101.p02.cricketscorer.ui

import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ef101.p02.cricketscorer.R
import com.ef101.p02.cricketscorer.data.MatchStore
import com.ef101.p02.cricketscorer.databinding.ActivityScorecardBinding
import com.ef101.p02.cricketscorer.engine.NativeEngine
import org.json.JSONObject

class ScorecardActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MATCH_ID = "match_id"
    }

    private lateinit var binding: ActivityScorecardBinding
    private var engine: NativeEngine? = null

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

        render(JSONObject(engine!!.scorecardJson()))
    }

    private fun render(sc: JSONObject) {
        addHeading("${sc.optString("teamA")}  vs  ${sc.optString("teamB")}")
        val venue = sc.optString("venue")
        val date = sc.optString("date")
        if (venue.isNotEmpty() || date.isNotEmpty()) {
            addSub("$venue   $date".trim())
        }

        val result = sc.optString("result")
        if (result.isNotEmpty()) {
            addResult(result)
        }

        val innings = sc.optJSONArray("innings") ?: return
        for (i in 0 until innings.length()) {
            val inn = innings.optJSONObject(i)
            addInningsHeader(inn)
            addBlock(battingBlock(inn))
            addBlock(bowlingBlock(inn))
        }
    }

    private fun battingBlock(inn: JSONObject): String {
        val sb = StringBuilder()
        val extras = inn.optJSONObject("extras")
        sb.append(
            String.format(
                "%-22s %4s %4s %3s %3s %7s%n",
                "BATTING", "R", "B", "4s", "6s", "SR"
            )
        )
        val batters = inn.optJSONArray("batters")
        for (i in 0 until batters.length()) {
            val b = batters.optJSONObject(i)
            val outLabel = dismissalLabel(b)
            val name = b.optString("name")
            val label = if (outLabel.isNotEmpty()) "$name $outLabel" else name
            sb.append(
                String.format(
                    "%-22s %4d %4d %3d %3d %7s%n",
                    label.take(22),
                    b.optInt("runs"), b.optInt("balls"),
                    b.optInt("fours"), b.optInt("sixes"),
                    b.optString("strikeRate")
                )
            )
        }
        if (extras != null) {
            sb.append(
                String.format(
                    "%-22s %4d%n",
                    "Extras", extras.optInt("total")
                )
            )
            sb.append(
                String.format(
                    "  (wd %d, nb %d, b %d, lb %d)%n",
                    extras.optInt("wides"), extras.optInt("noBalls"),
                    extras.optInt("byes"), extras.optInt("legByes")
                )
            )
        }
        return sb.toString()
    }

    private fun bowlingBlock(inn: JSONObject): String {
        val sb = StringBuilder()
        sb.append(
            String.format(
                "%-22s %4s %3s %4s %3s %6s%n",
                "BOWLING", "O", "M", "R", "W", "Econ"
            )
        )
        val bowlers = inn.optJSONArray("bowlers")
        for (i in 0 until bowlers.length()) {
            val b = bowlers.optJSONObject(i)
            sb.append(
                String.format(
                    "%-22s %4s %3d %4d %3d %6s%n",
                    b.optString("name").take(22),
                    b.optString("overs"), b.optInt("maidens"),
                    b.optInt("runs"), b.optInt("wickets"),
                    b.optString("economy")
                )
            )
        }
        return sb.toString()
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

    private fun addInningsHeader(inn: JSONObject) {
        val title = "${inn.optString("battingTeam")} " +
            "${inn.optInt("runs")}/${inn.optInt("wickets")} " +
            "(${inn.optString("overs")} ov)"
        val tv = TextView(this).apply {
            text = title
            textSize = 18f
            setPadding(0, dp(20), 0, dp(4))
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(getColor(R.color.brand_primary))
        }
        binding.scorecardContainer.addView(tv)
    }

    private fun addBlock(content: String) {
        val tv = TextView(this).apply {
            text = content
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(0, 0, 0, dp(8))
            setTextIsSelectable(true)
        }
        binding.scorecardContainer.addView(
            tv,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun addHeading(text: String) {
        binding.scorecardContainer.addView(TextView(this).apply {
            this.text = text
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        })
    }

    private fun addSub(text: String) {
        binding.scorecardContainer.addView(TextView(this).apply {
            this.text = text
            textSize = 14f
            setPadding(0, dp(2), 0, 0)
        })
    }

    private fun addResult(text: String) {
        binding.scorecardContainer.addView(TextView(this).apply {
            this.text = text
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(getColor(R.color.success))
            setPadding(0, dp(10), 0, 0)
        })
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        engine?.close()
    }
}
