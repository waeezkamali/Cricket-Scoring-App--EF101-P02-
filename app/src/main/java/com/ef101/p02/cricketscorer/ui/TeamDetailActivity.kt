package com.ef101.p02.cricketscorer.ui

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.ef101.p02.cricketscorer.CricketApp
import com.ef101.p02.cricketscorer.R
import com.ef101.p02.cricketscorer.databinding.ActivityTeamDetailBinding

class TeamDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEAM_ID = "team_id"
        const val MAX_PLAYERS = 11
    }

    private lateinit var binding: ActivityTeamDetailBinding
    private lateinit var adapter: SimpleListAdapter
    private val repo get() = (application as CricketApp).teamRepository
    private var teamId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeamDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        teamId = intent.getIntExtra(EXTRA_TEAM_ID, -1)
        if (teamId < 0) {
            finish()
            return
        }

        adapter = SimpleListAdapter(
            items = emptyList(),
            onLongClick = { row ->
                AlertDialog.Builder(this)
                    .setTitle("Remove player?")
                    .setMessage("Remove \"${row.primary}\" from the squad?")
                    .setPositiveButton(R.string.delete) { _, _ ->
                        repo.deletePlayer(row.id)
                        refresh()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        )
        binding.playerList.layoutManager = LinearLayoutManager(this)
        binding.playerList.adapter = adapter
        binding.btnAddPlayer.setOnClickListener { promptAddPlayer() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val team = repo.getTeam(teamId) ?: run { finish(); return }
        binding.teamName.text = team.name
        binding.playerCount.text =
            "${team.playerCount} / $MAX_PLAYERS players" +
                if (team.playerCount < 2) "  •  at least 2 required to play" else ""

        adapter.submit(
            team.players.mapIndexed { index, p ->
                SimpleListAdapter.Row(p.id, p.name, "Player ${index + 1}")
            }
        )
    }

    private fun promptAddPlayer() {
        val team = repo.getTeam(teamId) ?: return
        if (team.playerCount >= MAX_PLAYERS) {
            Toast.makeText(this, "A squad may have at most $MAX_PLAYERS players",
                Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.player_name)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.add_player)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Player name cannot be empty", Toast.LENGTH_SHORT).show()
                } else {
                    repo.addPlayer(teamId, name)
                    refresh()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
