package com.ef101.p02.cricketscorer.data

/** A cricket player belonging to exactly one team. */
data class Player(
    val id: Int,
    val name: String
)

/** A cricket team with its roster. */
data class Team(
    val id: Int,
    val name: String,
    val players: List<Player> = emptyList()
) {
    val playerCount: Int get() = players.size
}

/** Lightweight handle used to open a saved match. */
data class SavedMatch(
    val id: String,
    val title: String,
    val subtitle: String,
    val filePath: String,
    val complete: Boolean
)
