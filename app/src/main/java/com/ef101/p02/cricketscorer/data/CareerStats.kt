package com.ef101.p02.cricketscorer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Aggregates batting and bowling records for every player across all locally
 * saved matches. Records are keyed by player name so a player carries their
 * numbers across matches even though the match files are independent.
 *
 * Everything is computed from the engine's JSON scorecards — no network and no
 * additional database tables are required.
 */
object CareerStats {

    data class Batting(
        val name: String,
        var innings: Int = 0,
        var runs: Int = 0,
        var balls: Int = 0,
        var fours: Int = 0,
        var sixes: Int = 0,
        var dismissals: Int = 0,
        var notOuts: Int = 0,
        var highest: Int = 0
    ) {
        /** Runs per dismissal; null when the player has never been dismissed. */
        val average: Double?
            get() = if (dismissals > 0) runs.toDouble() / dismissals else null

        val strikeRate: Double
            get() = if (balls > 0) 100.0 * runs / balls else 0.0
    }

    data class Bowling(
        val name: String,
        var innings: Int = 0,
        var balls: Int = 0,
        var runs: Int = 0,
        var wickets: Int = 0,
        var maidens: Int = 0,
        var bestWickets: Int = 0,
        var bestRuns: Int = Int.MAX_VALUE
    ) {
        val economy: Double
            get() = if (balls > 0) runs.toDouble() / (balls / 6.0) else 0.0

        val best: String?
            get() = if (bestRuns == Int.MAX_VALUE) null else "$bestWickets/$bestRuns"
    }

    data class Result(
        val matches: Int,
        val batting: List<Batting>,
        val bowling: List<Bowling>
    ) {
        val isEmpty: Boolean get() = batting.isEmpty() && bowling.isEmpty()
    }

    fun aggregate(context: Context): Result {
        val battingByName = LinkedHashMap<String, Batting>()
        val bowlingByName = LinkedHashMap<String, Bowling>()
        var matches = 0

        for (match in MatchStore.list(context)) {
            try {
                MatchStore.open(context, match.id).use { engine ->
                    val sc = JSONObject(engine.scorecardJson())
                    val innings = sc.optJSONArray("innings") ?: return@use
                    if (innings.length() == 0) return@use
                    matches++

                    for (i in 0 until innings.length()) {
                        val inn = innings.optJSONObject(i) ?: continue
                        accumulateBatting(inn.optJSONArray("batters"), battingByName)
                        accumulateBowling(inn.optJSONArray("bowlers"), bowlingByName)
                    }
                }
            } catch (_: Throwable) {
                // A corrupt file must never break the aggregate.
            }
        }

        val batting = battingByName.values
            .sortedWith(compareByDescending<Batting> { it.runs }.thenByDescending { it.strikeRate })
        val bowling = bowlingByName.values
            .sortedWith(compareByDescending<Bowling> { it.wickets }.thenBy { it.economy })

        return Result(matches, batting, bowling)
    }

    private fun accumulateBatting(arr: JSONArray?, into: MutableMap<String, Batting>) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            if (name.isEmpty()) continue
            val rec = into.getOrPut(name) { Batting(name) }
            rec.innings++
            rec.runs += o.optInt("runs")
            rec.balls += o.optInt("balls")
            rec.fours += o.optInt("fours")
            rec.sixes += o.optInt("sixes")
            val scored = o.optInt("runs")
            if (scored > rec.highest) rec.highest = scored
            if (o.optBoolean("out")) rec.dismissals++ else rec.notOuts++
        }
    }

    private fun accumulateBowling(arr: JSONArray?, into: MutableMap<String, Bowling>) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            if (name.isEmpty()) continue
            val rec = into.getOrPut(name) { Bowling(name) }
            rec.innings++
            rec.balls += o.optInt("balls")
            rec.runs += o.optInt("runs")
            rec.wickets += o.optInt("wickets")
            rec.maidens += o.optInt("maidens")
            val w = o.optInt("wickets")
            val r = o.optInt("runs")
            if (w > rec.bestWickets || (w == rec.bestWickets && r < rec.bestRuns)) {
                rec.bestWickets = w
                rec.bestRuns = r
            }
        }
    }
}
