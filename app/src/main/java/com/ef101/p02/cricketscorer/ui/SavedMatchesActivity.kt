package com.ef101.p02.cricketscorer.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.ef101.p02.cricketscorer.R
import com.ef101.p02.cricketscorer.data.MatchStore
import com.ef101.p02.cricketscorer.data.SavedMatch
import com.ef101.p02.cricketscorer.databinding.ActivitySavedMatchesBinding

class SavedMatchesActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySavedMatchesBinding
    private lateinit var adapter: SimpleListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySavedMatchesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = SimpleListAdapter(
            items = emptyList(),
            onClick = { row -> openMatch(row.tag as SavedMatch) },
            onLongClick = { row -> confirmDelete(row.tag as SavedMatch) }
        )
        binding.matchList.layoutManager = LinearLayoutManager(this)
        binding.matchList.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val matches = MatchStore.list(this)
        adapter.submit(
            matches.map { m ->
                SimpleListAdapter.Row(
                    id = m.id.hashCode(),
                    primary = m.title,
                    secondary = m.subtitle,
                    tag = m
                )
            }
        )
        binding.emptyText.visibility =
            if (matches.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openMatch(match: SavedMatch) {
        val target = if (match.complete) ScorecardActivity::class.java
        else ScoringActivity::class.java
        startActivity(
            Intent(this, target).putExtra(ScoringActivity.EXTRA_MATCH_ID, match.id)
        )
    }

    private fun confirmDelete(match: SavedMatch) {
        AlertDialog.Builder(this)
            .setTitle("Delete match?")
            .setMessage("This removes the saved match permanently.")
            .setPositiveButton(R.string.delete) { _, _ ->
                MatchStore.delete(this, match.id)
                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
