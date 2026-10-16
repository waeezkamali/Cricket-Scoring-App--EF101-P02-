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
        bindSpinner(binding.tossChoiceSpinner, listOf(
            getString(R.string.bat_first), getString(R.string.bowl_first)
        ))
        bindSpinner(binding.tossWinnerSpinner, emptyList())
        bindSpinner(binding.strikerSpinner, emptyList())
        bindSpinner(binding.nonStrikerSpinner, emptyList())
        bindSpinner(binding.bowlerSpinner, emptyList())

        if (teams.size >= 2) binding.teamBSpinner.setSelection(1)

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                rebuildOpeners()
            }
            override fun onNothingSelected(p: AdapterView<*>?) { rebuildOpeners() }
        }
        binding.teamASpinner.onItemSelectedListener = listener
        binding.teamBSpinner.onItemSelectedListener = listener
        binding.tossChoiceSpinner.onItemSelectedListener = listener
        binding.tossWinnerSpinner.onItemSelectedListener = listener

        binding.btnStart.setOnClickListener { startMatch() }

        rebuildOpeners()
    }

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

    private fun rebuildOpeners() {
        if (updating) return
        updating = true
        try {
            val posA = binding.teamASpinner.selectedItemPosition
            val posB = binding.teamBSpinner.selectedItemPosition
            teamA = teams.getOrNull(posA)
            teamB = teams.getOrNull(posB)

            // Toss winner list always mirrors the two chosen teams.
            setSpinnerValues(
                binding.tossWinnerSpinner,
                listOfNotNull(teamA?.name, teamB?.name).distinct()
            )

            val tossWinnerName =
                binding.tossWinnerSpinner.selectedItem?.toString() ?: teamA?.name.orEmpty()
            val batFirstChoice = binding.tossChoiceSpinner.selectedItemPosition == 0

            val tossWinner: Team? = when (tossWinnerName) {
                teamA?.name -> teamA
                teamB?.name -> teamB
                else -> teamA
            }
            val other: Team? = if (tossWinner === teamA) teamB else teamA

            battingFirst = if (batFirstChoice) tossWinner else other
            bowlingTeam = if (battingFirst === teamA) teamB else teamA

            if (battingFirst == null || bowlingTeam == null ||
                battingFirst === bowlingTeam
            ) {
                binding.battingFirstLabel.text = "Select two different teams"
                battingPlayers = emptyList()
                bowlingPlayers = emptyList()
            } else {
                binding.battingFirstLabel.text =
                    "${battingFirst!!.name} will bat first"
                battingPlayers = battingFirst!!.players
                bowlingPlayers = bowlingTeam!!.players
            }

            setSpinnerValues(binding.strikerSpinner, battingPlayers.map { it.name })
            setSpinnerValues(binding.nonStrikerSpinner, battingPlayers.map { it.name })
            setSpinnerValues(binding.bowlerSpinner, bowlingPlayers.map { it.name })
        } finally {
            updating = false
        }
    }

    private fun startMatch() {
        val a = teamA
        val b = teamB
        if (a == null || b == null || a.id == b.id) {
            showError("Please select two different teams.")
            return
        }
        val overs = binding.etOvers.text.toString().toIntOrNull()
        if (overs == null || overs <= 0) {
            showError("Enter a valid number of overs.")
            return
        }
        if (binding.etVenue.text.isNullOrBlank()) {
            showError("Enter a venue.")
            return
        }
        if (battingPlayers.size < 2) {
            showError("The batting team needs at least 2 players.")
            return
        }
        if (bowlingPlayers.isEmpty()) {
            showError("The bowling team needs at least 1 player.")
            return
        }

        val strikerId = battingPlayers[binding.strikerSpinner.selectedItemPosition].id
        val nonStrikerId = battingPlayers[binding.nonStrikerSpinner.selectedItemPosition].id
        if (strikerId == nonStrikerId) {
            showError("Striker and non-striker must be different players.")
            return
        }
        val bowlerId = bowlingPlayers[binding.bowlerSpinner.selectedItemPosition].id

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
                showError(validation)
                return
            }
            if (!engine.startMatch(strikerId, nonStrikerId, bowlerId)) {
                showError("Could not start the match. Check your selections.")
                return
            }

            val matchId = MatchStore.newMatchId()
            val saveError = MatchStore.save(this, engine, matchId)
            if (saveError.isNotEmpty()) {
                showError(saveError)
                return
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

    private fun showError(message: String) {
        binding.errorText.text = message
        binding.errorText.visibility = View.VISIBLE
    }

    private fun Team.toSpec() = NativeEngine.TeamSpec(
        id = id,
        name = name,
        players = players.map { NativeEngine.PlayerSpec(it.id, it.name) }
    )
}
