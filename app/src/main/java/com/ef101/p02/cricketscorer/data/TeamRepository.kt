package com.ef101.p02.cricketscorer.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Offline SQLite storage for teams and players.
 *
 * The database is entirely local - the application never requires network
 * access. Match state itself is stored as files on disk (see [MatchStore]).
 */
class DbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_TEAMS (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_PLAYERS (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                team_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                FOREIGN KEY(team_id) REFERENCES $TABLE_TEAMS(_id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_players_team ON $TABLE_PLAYERS(team_id)")
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PLAYERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TEAMS")
        onCreate(db)
    }

    companion object {
        const val DB_NAME = "cricket_scorer.db"
        const val DB_VERSION = 1
        const val TABLE_TEAMS = "teams"
        const val TABLE_PLAYERS = "players"
    }
}

/** Repository wrapping CRUD operations for teams and players. */
class TeamRepository(context: Context) {

    private val helper = DbHelper(context)

    // ----- Teams ------------------------------------------------------------
    fun createTeam(name: String): Int {
        val db = helper.writableDatabase
        val values = ContentValues().apply { put("name", name.trim()) }
        val id = db.insert(DbHelper.TABLE_TEAMS, null, values)
        return id.toInt()
    }

    fun deleteTeam(teamId: Int) {
        val db = helper.writableDatabase
        db.delete(DbHelper.TABLE_TEAMS, "_id = ?", arrayOf(teamId.toString()))
    }

    fun getTeams(): List<Team> {
        val db = helper.readableDatabase
        val result = mutableListOf<Team>()
        db.query(
            DbHelper.TABLE_TEAMS, arrayOf("_id", "name"),
            null, null, null, null, "name COLLATE NOCASE ASC"
        ).use { c ->
            val idIdx = c.getColumnIndexOrThrow("_id")
            val nameIdx = c.getColumnIndexOrThrow("name")
            while (c.moveToNext()) {
                val id = c.getInt(idIdx)
                result.add(Team(id, c.getString(nameIdx), getPlayers(id)))
            }
        }
        return result
    }

    fun getTeam(teamId: Int): Team? {
        val db = helper.readableDatabase
        db.query(
            DbHelper.TABLE_TEAMS, arrayOf("_id", "name"),
            "_id = ?", arrayOf(teamId.toString()), null, null, null
        ).use { c ->
            if (c.moveToFirst()) {
                val id = c.getInt(c.getColumnIndexOrThrow("_id"))
                val name = c.getString(c.getColumnIndexOrThrow("name"))
                return Team(id, name, getPlayers(id))
            }
        }
        return null
    }

    // ----- Players ----------------------------------------------------------
    fun addPlayer(teamId: Int, name: String): Int {
        val db = helper.writableDatabase
        val values = ContentValues().apply {
            put("team_id", teamId)
            put("name", name.trim())
        }
        return db.insert(DbHelper.TABLE_PLAYERS, null, values).toInt()
    }

    fun deletePlayer(playerId: Int) {
        val db = helper.writableDatabase
        db.delete(DbHelper.TABLE_PLAYERS, "_id = ?", arrayOf(playerId.toString()))
    }

    fun getPlayers(teamId: Int): List<Player> {
        val db = helper.readableDatabase
        val result = mutableListOf<Player>()
        db.query(
            DbHelper.TABLE_PLAYERS, arrayOf("_id", "name"),
            "team_id = ?", arrayOf(teamId.toString()), null, null, "_id ASC"
        ).use { c ->
            val idIdx = c.getColumnIndexOrThrow("_id")
            val nameIdx = c.getColumnIndexOrThrow("name")
            while (c.moveToNext()) {
                result.add(Player(c.getInt(idIdx), c.getString(nameIdx)))
            }
        }
        return result
    }
}
