package com.ef101.p02.cricketscorer.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ef101.p02.cricketscorer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cardNewMatch.setOnClickListener {
            startActivity(Intent(this, MatchSetupActivity::class.java))
        }
        binding.cardTeams.setOnClickListener {
            startActivity(Intent(this, TeamsActivity::class.java))
        }
        binding.cardSavedMatches.setOnClickListener {
            startActivity(Intent(this, SavedMatchesActivity::class.java))
        }
        binding.cardCareerStats.setOnClickListener {
            startActivity(Intent(this, CareerStatsActivity::class.java))
        }
        binding.cardSelfTest.setOnClickListener {
            startActivity(Intent(this, SelfTestActivity::class.java))
        }
    }
}
