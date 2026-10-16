package com.ef101.p02.cricketscorer.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.ef101.p02.cricketscorer.CricketApp
import com.ef101.p02.cricketscorer.databinding.ActivityTeamsBinding

class TeamsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeamsBinding
    private lateinit var adapter: SimpleListAdapter
    private val repo get() = (application as CricketApp).teamRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeamsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = SimpleListAdapter(
            items = emptyList(),
            onClick = { row ->
                startActivity(
                    Intent(this, TeamDetailActivity::class.java)
                        .putExtra(TeamDetailActivity.EXTRA_TEAM_ID, row.id)
                )
            },
            onLongClick = { row -> confirmDelete(row) }
        )
        binding.teamList.layoutManager = LinearLayoutManager(this)
        binding.teamList.adapter = adapter

        binding.btnAddTeam.setOnClickListener { promptAddTeam() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val teams = repo.getTeams()
        adapter.submit(teams.map {
            SimpleListAdapter.Row(it.id, it.name, "${it.playerCount} players")
        })
        binding.emptyText.visibility =
            if (teams.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun promptAddTeam() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(com.ef101.p02.cricketscorer.R.string.team_name)
        }
        AlertDialog.Builder(this)
            .setTitle(com.ef101.p02.cricketscorer.R.string.add_team)
            .setView(input)
            .setPositiveButton(com.ef101.p02.cricketscorer.R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Team name cannot be empty", Toast.LENGTH_SHORT).show()
                } else {
                    repo.createTeam(name)
                    refresh()
                }
            }
            .setNegativeButton(com.ef101.p02.cricketscorer.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(row: SimpleListAdapter.Row) {
        AlertDialog.Builder(this)
            .setTitle("Delete team?")
            .setMessage("Delete \"${row.primary}\" and all its players?")
            .setPositiveButton(com.ef101.p02.cricketscorer.R.string.delete) { _, _ ->
                repo.deleteTeam(row.id)
                refresh()
            }
            .setNegativeButton(com.ef101.p02.cricketscorer.R.string.cancel, null)
            .show()
    }
}
