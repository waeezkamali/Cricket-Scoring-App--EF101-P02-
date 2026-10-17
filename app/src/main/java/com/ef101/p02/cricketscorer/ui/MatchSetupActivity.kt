package com.ef101.p02.cricketscorer.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ef101.p02.cricketscorer.CricketApp
import com.ef101.p02.cricketscorer.R
import com.ef101.p02.cricketscorer.data.MatchStore
import com.ef101.p02.cricketscorer.data.Player
import com.ef101.p02.cricketscorer.data.Team
import com.ef101.p02.cricketscorer.databinding.ActivityMatchSetupBinding
import com.ef101.p02.cricketscorer.engine.NativeEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Match setup screen.
 *
 * The striker / non-striker spinners are mutually exclusive: a player chosen in
 * one role is removed from the other's list, so the same person can never be
 * selected to occupy both ends. The opening-bowler spinner is always populated
 * from the *opposing* (bowling) team's roster.
 */
class MatchSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMatchSetupBinding
    private val repo get() = (application as CricketApp).teamRepository

    private var teams: List<Team> = emptyList()
    private var updating = false

    private var teamA: Team? = null
    private var teamB: Team? = null
    private var battingFirst: Team? = null
    private var bowlingTeam: Team? = null

    private var battingPlayers: List<Player> = emptyList()
    private var bowlingPlayers: List<Player> = emptyList()

    // The lists actually backing the two crease spinners. They are kept free of
    // the player chosen in the opposite role.
    private var strikerOptions: List<Player> = emptyList()
    private var nonStrikerOptions: List<Player> = emptyList()
    private var bowlerOptions: List<Player> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMatchSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        teams = repo.getTeams()
        if (teams.size < 2) {
            Toast.makeText(this, "Create at least two teams first", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.etDate.setText(
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        )

        bindSpinner(binding.teamASpinner, teams.map { it.name })
        bindSpinner(binding.teamBSpinner, teams.map { it.name })
        bindSpinner(
            binding.tossChoiceSpinner,
            listOf(getString(R.string.bat_first), getString(R.string.bowl_first))
        )
        bindSpinner(binding.tossWinnerSpinner, emptyList())
        bindSpinner(binding.strikerSpinner, emptyList())
        bindSpinner(binding.nonStrikerSpinner, emptyList())
        bindSpinner(binding.bowlerSpinner, emptyList())

        if (teams.size >= 2) binding.teamBSpinner.setSelection(1)

        val teamListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) =
                rebuildTeamContext()

            override fun onNothingSelected(p: AdapterView<*>?) = rebuildTeamContext()
        }
        binding.teamASpinner.onItemSelectedListener = teamListener
        binding.teamBSpinner.onItemSelectedListener = teamListener
        binding.tossChoiceSpinner.onItemSelectedListener = teamListener
        binding.tossWinnerSpinner.onItemSelectedListener = teamListener

        val creaseListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) =
                refreshCreaseOptions()

            override fun onNothingSelected(p: AdapterView<*>?) = refreshCreaseOptions()
        }
        binding.strikerSpinner.onItemSelectedListener = creaseListener
        binding.nonStrikerSpinner.onItemSelectedListener = creaseListener

        binding.btnStart.setOnClickListener { startMatch() }

        rebuildTeamContext()
    }

    // ----- Spinner plumbing -------------------------------------------------

    private fun bindSpinner(spinner: Spinner, values: List<String>) {
        val adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, values.toMutableList()
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun setSpinnerValues(spinner: Spinner, values: List<String>) {
        val current = spinner.selectedItemPosition
        @Suppress("UNCHECKED_CAST")
        val adapter = spinner.adapter as ArrayAdapter<String>
        adapter.clear()
        adapter.addAll(values)
        adapter.notifyDataSetChanged()
        if (current in values.indices) spinner.setSelection(current)
    }

    /** Replaces a spinner's items with [players], keeping [preferredId] selected. */
    private fun setPlayerSpinner(spinner: Spinner, players: List<Player>, preferredId: Int?) {
        @Suppress("UNCHECKED_CAST")
        val adapter = spinner.adapter as ArrayAdapter<String>
        adapter.clear()
        adapter.addAll(players.map { it.name })
        adapter.notifyDataSetChanged()
        val idx = players.indexOfFirst { it.id == preferredId }
        if (idx >= 0) {
            spinner.setSelection(idx)
        } else if (players.isNotEmpty()) {
            spinner.setSelection(0)
        }
    }

    private fun selectedPlayer(spinner: Spinner, options: List<Player>): Player? =
        options.getOrNull(spinner.selectedItemPosition)

    // ----- Rebuilding -------------------------------------------------------

    /**
     * Recomputes the batting / bowling teams from the team + toss spinners and
     * repopulates every dependent spinner. Called whenever teams or the toss
     * change.
     */
    private fun rebuildTeamContext() {
        if (updating) return
        updating = true
        try {
            teamA = teams.getOrNull(binding.teamASpinner.selectedItemPosition)
            teamB = teams.getOrNull(binding.teamBSpinner.selectedItemPosition)

            setSpinnerValues(
                binding.tossWinnerSpinner,
                listOfNotNull(teamA?.name, teamB?.name).distinct()
            )

            val tossWinnerName =
                binding.tossWinnerSpinner.selectedItem?.toString() ?: teamA?.name.orEmpty()
            val batFirstChoice = binding.tossChoiceSpinner.selectedItemPosition == 0

            val tossWinner: Team? = when (tossWinnerName) {
                teamB?.name -> teamB
                else -> teamA
            }
            val other: Team? = if (tossWinner?.id == teamA?.id) teamB else teamA

            if (teamA == null || teamB == null || teamA!!.id == teamB!!.id) {
                battingFirst = null
                bowlingTeam = null
                battingPlayers = emptyList()
                bowlingPlayers = emptyList()
                binding.battingFirstLabel.text = getString(R.string.select_two_teams)
            } else {
                battingFirst = if (batFirstChoice) tossWinner else other
                // The bowling side is always the "other" team.
                bowlingTeam = if (battingFirst!!.id == teamA!!.id) teamB else teamA
                battingPlayers = battingFirst!!.players
                bowlingPlayers = bowlingTeam!!.players
                binding.battingFirstLabel.text =
                    getString(R.string.batting_first_fmt, battingFirst!!.name)
            }

            strikerOptions = battingPlayers
            nonStrikerOptions = battingPlayers
            bowlerOptions = bowlingPlayers
            setPlayerSpinner(binding.strikerSpinner, strikerOptions, null)
            setPlayerSpinner(binding.nonStrikerSpinner, nonStrikerOptions, null)
            setPlayerSpinner(binding.bowlerSpinner, bowlerOptions, null)
        } finally {
            updating = false
        }
        // Immediately apply the mutual-exclusion rule so the striker and
        // non-striker default to two different players.
        refreshCreaseOptions()
    }

    /**
     * Keeps the striker / non-striker spinners mutually exclusive. The striker
     * list excludes the chosen non-striker and vice-versa.
     */
    private fun refreshCreaseOptions() {
        if (updating) return
        updating = true
        try {
            val striker = selectedPlayer(binding.strikerSpinner, strikerOptions)
            val nonStriker = selectedPlayer(binding.nonStrikerSpinner, nonStrikerOptions)

            nonStrikerOptions = battingPlayers.filter { it.id != striker?.id }
            setPlayerSpinner(binding.nonStrikerSpinner, nonStrikerOptions, nonStriker?.id)

            val chosenNonStriker =
                selectedPlayer(binding.nonStrikerSpinner, nonStrikerOptions)
            strikerOptions = battingPlayers.filter { it.id != chosenNonStriker?.id }
            setPlayerSpinner(binding.strikerSpinner, strikerOptions, striker?.id)
        } finally {
            updating = false
        }
    }

    // ----- Start ------------------------------------------------------------

    private fun startMatch() {
        binding.errorText.visibility = View.GONE

        val a = teamA
        val b = teamB
        if (a == null || b == null || a.id == b.id) {
            return fail("Please select two different teams.")
        }
        val overs = binding.etOvers.text.toString().toIntOrNull()
        if (overs == null || overs <= 0) {
            return fail("Enter a valid number of overs.")
        }
        if (binding.etVenue.text.isNullOrBlank()) {
            return fail("Enter a venue.")
        }
        if (battingPlayers.size < 2) {
            return fail("The batting team needs at least 2 players.")
        }
        if (bowlingPlayers.isEmpty()) {
            return fail("The bowling team needs at least 1 player.")
        }

        val striker = selectedPlayer(binding.strikerSpinner, strikerOptions)
        val nonStriker = selectedPlayer(binding.nonStrikerSpinner, nonStrikerOptions)
        if (striker == null || nonStriker == null) {
            return fail("Select both the striker and the non-striker.")
        }
        if (striker.id == nonStriker.id) {
            return fail("Striker and non-striker must be different players.")
        }
        val bowler = selectedPlayer(binding.bowlerSpinner, bowlerOptions)
        if (bowler == null) {
            return fail("Select the opening bowler.")
        }

        val engine = NativeEngine.create()
        try {
            engine.configure(
                a.toSpec(), b.toSpec(),
                binding.etDate.text.toString().trim(),
                binding.etVenue.text.toString().trim(),
                overs,
                binding.tossWinnerSpinner.selectedItem?.toString().orEmpty(),
                if (binding.tossChoiceSpinner.selectedItemPosition == 0) "bat" else "bowl"
            )
            val validation = engine.validationMessage()
            if (validation.isNotEmpty()) {
                return fail(validation)
            }
            if (!engine.startMatch(striker.id, nonStriker.id, bowler.id)) {
                return fail("Could not start the match. Check your selections.")
            }

            val matchId = MatchStore.newMatchId()
            val saveError = MatchStore.save(this, engine, matchId)
            if (saveError.isNotEmpty()) {
                return fail(saveError)
            }

            startActivity(
                Intent(this, ScoringActivity::class.java)
                    .putExtra(ScoringActivity.EXTRA_MATCH_ID, matchId)
            )
            finish()
        } finally {
            engine.close()
        }
    }

    /** Shows a clean Toast (and inline hint) for an invalid setup. */
    private fun fail(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        binding.errorText.text = message
        binding.errorText.visibility = View.VISIBLE
    }

    private fun Team.toSpec() = NativeEngine.TeamSpec(
        id = id,
        name = name,
        players = players.map { NativeEngine.PlayerSpec(it.id, it.name) }
    )
}
