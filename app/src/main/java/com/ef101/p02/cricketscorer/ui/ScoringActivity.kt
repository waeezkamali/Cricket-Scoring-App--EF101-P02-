package com.ef101.p02.cricketscorer.ui

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ef101.p02.cricketscorer.R
import com.ef101.p02.cricketscorer.data.MatchStore
import com.ef101.p02.cricketscorer.databinding.ActivityScoringBinding
import com.ef101.p02.cricketscorer.engine.NativeEngine
import org.json.JSONArray
import org.json.JSONObject

class ScoringActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MATCH_ID = "match_id"
    }

    private lateinit var binding: ActivityScoringBinding
    private lateinit var engine: NativeEngine
    private lateinit var matchId: String

    private var promptInFlight = false

    private var scoreAnimator: ValueAnimator? = null
    private var displayedRuns = -1
    private var displayedTeam: String? = null

    private data class Person(val id: Int, val name: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScoringBinding.inflate(layoutInflater)
        setContentView(binding.root)

        matchId = intent.getStringExtra(EXTRA_MATCH_ID) ?: run { finish(); return }
        engine = try {
            MatchStore.open(this, matchId)
        } catch (t: Throwable) {
            Toast.makeText(this, "Could not open match: ${t.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        wireButtons()
        refreshState()
    }

    private fun wireButtons() {
        binding.btn0.setOnClickListener { tap(it); record(0) }
        binding.btn1.setOnClickListener { tap(it); record(1) }
        binding.btn2.setOnClickListener { tap(it); record(2) }
        binding.btn3.setOnClickListener { tap(it); record(3) }
        binding.btn4.setOnClickListener { tap(it); record(4) }
        binding.btn6.setOnClickListener { tap(it); record(6) }

        binding.btnWide.setOnClickListener { tap(it); promptWide() }
        binding.btnNoBall.setOnClickListener { tap(it); promptNoBall() }
        binding.btnBye.setOnClickListener { tap(it); promptBye(false) }
        binding.btnLegBye.setOnClickListener { tap(it); promptBye(true) }
        binding.btnWicket.setOnClickListener { tap(it, strong = true); promptWicket() }

        binding.btnSecondInnings.setOnClickListener { tap(it); promptSecondInnings() }
        binding.btnViewScorecard.setOnClickListener { openScorecard() }
    }

    /** Light tactile feedback for scoring buttons. */
    private fun tap(view: View, strong: Boolean = false) {
        view.performHapticFeedback(
            if (strong) HapticFeedbackConstants.LONG_PRESS
            else HapticFeedbackConstants.VIRTUAL_KEY
        )
    }

    // ----- Recording --------------------------------------------------------

    private fun record(batRuns: Int) {
        val err = engine.recordDelivery(batRuns)
        handleResult(err)
    }

    private fun promptWide() {
        chooseNumber("Wide — additional runs run", 0..4) { n ->
            handleResult(engine.recordDelivery(0, "Wide", n))
        }
    }

    private fun promptNoBall() {
        chooseNumber("No-ball — runs off the bat", 0..6) { n ->
            handleResult(engine.recordDelivery(n, "NoBall", 0))
        }
    }

    private fun promptBye(legBye: Boolean) {
        chooseNumber(if (legBye) "Leg byes" else "Byes", 1..4) { n ->
            handleResult(
                engine.recordDelivery(0, if (legBye) "LegBye" else "Bye", n)
            )
        }
    }

    private fun promptWicket() {
        val types = arrayOf("Bowled", "Caught", "LBW", "Stumped", "Run out")
        AlertDialog.Builder(this)
            .setTitle("How was the batter out?")
            .setItems(types) { _, which ->
                when (which) {
                    0 -> recordWicket("Bowled")
                    1 -> promptFielder("Caught")
                    2 -> recordWicket("LBW")
                    3 -> promptFielder("Stumped")
                    4 -> promptRunOut()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun recordWicket(dismissal: String) {
        // Bowled / LBW / Caught / Stumped always dismiss the striker.
        val striker = striker() ?: return
        handleResult(
            engine.recordDelivery(
                0, "None", 0, wicket = true,
                dismissal = dismissal, dismissedPlayerId = striker.id
            )
        )
    }

    private fun promptFielder(dismissal: String) {
        val bowlers = battingContext()?.second ?: return
        if (bowlers.isEmpty()) { recordWicket(dismissal); return }
        val names = bowlers.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Fielder")
            .setItems(names) { _, which ->
                val striker = striker() ?: return@setItems
                handleResult(
                    engine.recordDelivery(
                        0, "None", 0, wicket = true, dismissal = dismissal,
                        dismissedPlayerId = striker.id,
                        fielderId = bowlers[which].id
                    )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun promptRunOut() {
        val s = striker() ?: return
        val ns = nonStriker() ?: return
        val labels = arrayOf("Striker: ${s.name}", "Non-striker: ${ns.name}")
        AlertDialog.Builder(this)
            .setTitle("Who was run out?")
            .setItems(labels) { _, which ->
                val out = if (which == 0) s else ns
                val bowlers = battingContext()?.second ?: emptyList()
                if (bowlers.isEmpty()) {
                    handleResult(
                        engine.recordDelivery(
                            0, "None", 0, wicket = true,
                            dismissal = "Run out", dismissedPlayerId = out.id
                        )
                    )
                } else {
                    val names = bowlers.map { it.name }.toTypedArray()
                    AlertDialog.Builder(this)
                        .setTitle("Fielder")
                        .setItems(names) { _, f ->
                            handleResult(
                                engine.recordDelivery(
                                    0, "None", 0, wicket = true,
                                    dismissal = "Run out",
                                    dismissedPlayerId = out.id,
                                    fielderId = bowlers[f].id
                                )
                            )
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun chooseNumber(title: String, range: IntRange, onPick: (Int) -> Unit) {
        val values = range.toList()
        val labels = values.map { it.toString() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(labels) { _, which -> onPick(values[which]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun handleResult(err: String) {
        if (err.isNotEmpty()) {
            Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
        } else {
            // Persist immediately so an in-progress match survives a restart.
            MatchStore.save(this, engine, matchId)
        }
        refreshState()
    }

    // ----- Rendering --------------------------------------------------------

    private var state: JSONObject = JSONObject()

    private fun refreshState() {
        state = JSONObject(engine.stateJson())
        bindState()
        maybePrompt()
    }

    private val runButtons get() = listOf(
        binding.btn0, binding.btn1, binding.btn2, binding.btn3,
        binding.btn4, binding.btn6, binding.btnWide, binding.btnNoBall,
        binding.btnBye, binding.btnLegBye, binding.btnWicket
    )

    private fun bindState() {
        val runs = state.optInt("runs")
        val wickets = state.optInt("wickets")
        val batting = state.optString("battingTeam")
        val overs = state.optString("overs")
        val legalBalls = state.optInt("legalBalls")
        val totalOvers = state.optInt("totalOvers")
        val target = state.optInt("target")
        val complete = state.optBoolean("matchComplete")
        val inningsComplete = state.optBoolean("inningsComplete")

        animateScore(batting, runs, wickets)
        binding.oversText.text = "$overs / $totalOvers overs"

        if (target > 0 && !complete) {
            val need = target - runs
            val ballsLeft = totalOvers * 6 - legalBalls
            binding.targetText.text = "Target $target — need $need off $ballsLeft balls"
            binding.targetText.visibility = View.VISIBLE
        } else {
            binding.targetText.visibility = View.GONE
        }

        val striker = striker()
        val nonStriker = nonStriker()
        binding.battersText.text = buildString {
            append("Striker: ")
            append(striker?.let { "${it.name} ${it.runsLabel()}" } ?: "-")
            append("\nNon-striker: ")
            append(nonStriker?.let { "${it.name} ${it.runsLabel()}" } ?: "-")
        }

        val bowler = state.optJSONObject("bowler")
        binding.bowlerText.text = if (bowler != null) {
            "Bowler: ${bowler.optString("name")} " +
                "${bowler.optString("overs")}-${bowler.optInt("maidens")}-" +
                "${bowler.optInt("runs")}-${bowler.optInt("wickets")}"
        } else "Bowler: -"

        val over = state.optJSONArray("thisOver") ?: JSONArray()
        binding.thisOverText.text = "This over: " + (0 until over.length())
            .joinToString("  ") { over.optString(it) }

        // Status / prompts.
        val status: String = when {
            complete -> state.optString("result")
            inningsComplete -> "Innings complete."
            state.optBoolean("needsNewBatter") -> "Select the next batter."
            state.optBoolean("needsNewBowler") -> "Select the bowler for the next over."
            else -> ""
        }
        binding.statusText.text = status
        binding.statusText.visibility = if (status.isEmpty()) View.GONE else View.VISIBLE

        val ready = !complete && !inningsComplete && striker != null &&
            nonStriker != null && bowler != null &&
            !state.optBoolean("needsNewBowler") && !state.optBoolean("needsNewBatter")
        runButtons.forEach { it.isEnabled = ready }

        binding.btnSecondInnings.visibility =
            if (inningsComplete && !complete) View.VISIBLE else View.GONE
    }

    /** Animates the run counter between deliveries while keeping the team and
     *  wicket suffix static. */
    private fun animateScore(batting: String, runs: Int, wickets: Int) {
        if (batting != displayedTeam) {
            scoreAnimator?.cancel()
            displayedTeam = batting
            displayedRuns = 0
        }
        val from = if (displayedRuns < 0) 0 else displayedRuns
        if (from == runs) {
            displayedRuns = runs
            binding.scoreText.text = "$batting $runs/$wickets"
            return
        }
        scoreAnimator?.cancel()
        displayedRuns = runs
        scoreAnimator = ValueAnimator.ofInt(from, runs).apply {
            duration = 420L
            addUpdateListener {
                val value = it.animatedValue as Int
                binding.scoreText.text = "$batting $value/$wickets"
            }
            start()
        }
    }

    private fun Person.runsLabel(): String {
        val bat = state.optJSONObject("striker")
        val ns = state.optJSONObject("nonStriker")
        val obj = if (bat?.optInt("id") == id) bat else ns
        val r = obj?.optInt("runs") ?: 0
        val b = obj?.optInt("balls") ?: 0
        return "($r off $b)"
    }

    // ----- Prompts ----------------------------------------------------------

    private fun maybePrompt() {
        if (promptInFlight) return
        if (state.optBoolean("matchComplete") || state.optBoolean("inningsComplete")) return

        when {
            state.optBoolean("needsNewBatter") -> {
                promptInFlight = true
                showBatterDialog()
            }
            state.optBoolean("needsNewBowler") -> {
                promptInFlight = true
                showBowlerDialog()
            }
        }
    }

    private fun showBatterDialog() {
        val ctx = battingContext() ?: return releasePrompt()
        val batted = state.optJSONArray("battedIds")?.let { arr ->
            (0 until arr.length()).map { arr.optInt(it) }.toSet()
        } ?: emptySet()
        val available = ctx.first.filter { it.id !in batted }
        if (available.isEmpty()) {
            releasePrompt()
            return
        }
        val labels = available.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select next batter")
            .setItems(labels) { _, which ->
                val err = engine.setNewBatter(available[which].id)
                if (err.isNotEmpty()) Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
                releasePrompt()
                refreshState()
            }
            .setOnCancelListener { releasePrompt() }
            .setNegativeButton(R.string.cancel) { _, _ -> releasePrompt() }
            .show()
    }

    private fun showBowlerDialog() {
        val ctx = battingContext() ?: return releasePrompt()
        val lastBowler = state.optInt("lastOverBowlerId", -1)
        val available = ctx.second.filter { it.id != lastBowler }
        if (available.isEmpty()) {
            releasePrompt()
            return
        }
        val labels = available.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select bowler")
            .setItems(labels) { _, which ->
                val err = engine.setNextBowler(available[which].id)
                if (err.isNotEmpty()) Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
                releasePrompt()
                refreshState()
            }
            .setOnCancelListener { releasePrompt() }
            .setNegativeButton(R.string.cancel) { _, _ -> releasePrompt() }
            .show()
    }

    private fun releasePrompt() {
        promptInFlight = false
    }

    // ----- Second innings ---------------------------------------------------

    private fun promptSecondInnings() {
        val ctx = battingContext() ?: return
        // In the first innings the chasing team is the bowling team.
        val chasing = ctx.second // bowling team of innings 1 == chasing side
        val defending = ctx.first
        if (chasing.size < 2 || defending.isEmpty()) return

        val strikerLabels = chasing.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("${chasingTeamName()} — select striker")
            .setItems(strikerLabels) { _, sIdx ->
                AlertDialog.Builder(this)
                    .setTitle("Select non-striker")
                    .setItems(strikerLabels) nsHandler@{ _, nsIdx ->
                        if (nsIdx == sIdx) {
                            Toast.makeText(this, "Striker and non-striker must differ",
                                Toast.LENGTH_SHORT).show()
                            promptInFlight = false
                            return@nsHandler
                        }
                        val bowlerLabels = defending.map { it.name }.toTypedArray()
                        AlertDialog.Builder(this)
                            .setTitle("Select opening bowler")
                            .setItems(bowlerLabels) { _, bIdx ->
                                val ok = engine.startSecondInnings(
                                    chasing[sIdx].id, chasing[nsIdx].id,
                                    defending[bIdx].id
                                )
                                if (!ok) {
                                    Toast.makeText(this, "Could not start innings",
                                        Toast.LENGTH_SHORT).show()
                                }
                                MatchStore.save(this, engine, matchId)
                                promptInFlight = false
                                refreshState()
                            }
                            .setNegativeButton(R.string.cancel) { _, _ ->
                                promptInFlight = false
                            }
                            .show()
                    }
                    .setNegativeButton(R.string.cancel) { _, _ -> promptInFlight = false }
                    .show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun chasingTeamName(): String {
        val bowlingId = state.optInt("bowlingTeamId")
        return teamRoster(bowlingId)?.name ?: "Chasing team"
    }

    // ----- Helpers ----------------------------------------------------------

    private fun striker(): Person? = personFrom(state.optJSONObject("striker"))
    private fun nonStriker(): Person? = personFrom(state.optJSONObject("nonStriker"))

    private fun personFrom(o: JSONObject?): Person? {
        if (o == null) return null
        return Person(o.optInt("id"), o.optString("name"))
    }

    /** @return Pair(batting team roster, bowling team roster). */
    private fun battingContext(): Pair<List<Person>, List<Person>>? {
        val battingId = state.optInt("battingTeamId")
        val bowlingId = state.optInt("bowlingTeamId")
        val batting = teamRoster(battingId) ?: return null
        val bowling = teamRoster(bowlingId) ?: return null
        return batting.players to bowling.players
    }

    private data class Roster(val id: Int, val name: String, val players: List<Person>)

    private fun teamRoster(teamId: Int): Roster? {
        for (key in listOf("teamA", "teamB")) {
            val t = state.optJSONObject(key) ?: continue
            if (t.optInt("id") == teamId) {
                val arr = t.optJSONArray("players") ?: JSONArray()
                val players = (0 until arr.length()).map {
                    val p = arr.optJSONObject(it)
                    Person(p.optInt("id"), p.optString("name"))
                }
                return Roster(t.optInt("id"), t.optString("name"), players)
            }
        }
        return null
    }

    private fun openScorecard() {
        startActivity(
            Intent(this, ScorecardActivity::class.java)
                .putExtra(ScorecardActivity.EXTRA_MATCH_ID, matchId)
        )
    }

    override fun onPause() {
        super.onPause()
        if (::engine.isInitialized && engine.isOpen) {
            MatchStore.save(this, engine, matchId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scoreAnimator?.cancel()
        if (::engine.isInitialized) engine.close()
    }
}
