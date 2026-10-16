package com.ef101.p02.cricketscorer.data

import android.content.Context
import com.ef101.p02.cricketscorer.engine.NativeEngine
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Offline persistence for match state.
 *
 * Each match is stored as a single text file inside the app's private
 * `files/matches` directory. Serialization / deserialization is performed by
 * the native C++ engine; this object only manages files and lightweight
 * metadata used to populate the "saved matches" list.
 */
object MatchStore {

    private const val DIR_NAME = "matches"
    private const val EXT = ".match"

    fun directory(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun newMatchId(): String = UUID.randomUUID().toString()

    fun fileFor(context: Context, matchId: String): File =
        File(directory(context), matchId + EXT)

    /** Persists [engine] to disk. @return "" on success, else an error string. */
    fun save(context: Context, engine: NativeEngine, matchId: String): String {
        engine.setMatchId(matchId)
        return engine.saveToFile(fileFor(context, matchId).absolutePath)
    }

    fun delete(context: Context, matchId: String): Boolean =
        fileFor(context, matchId).delete()

    fun exists(context: Context, matchId: String): Boolean =
        fileFor(context, matchId).exists()

    /** Loads a saved match into a new engine. Caller must [NativeEngine.close] it. */
    fun open(context: Context, matchId: String): NativeEngine =
        NativeEngine.fromFile(fileFor(context, matchId).absolutePath)

    /**
     * Lists saved matches, newest first. Files that cannot be parsed are
     * skipped rather than crashing the UI.
     */
    fun list(context: Context): List<SavedMatch> {
        val files = directory(context).listFiles { f -> f.isFile && f.name.endsWith(EXT) }
            ?: return emptyList()

        return files.sortedByDescending { it.lastModified() }.mapNotNull { file ->
            val id = file.nameWithoutExtension
            try {
                NativeEngine.fromFile(file.absolutePath).use { engine ->
                    val sc = JSONObject(engine.scorecardJson())
                    val state = JSONObject(engine.stateJson())
                    val teamA = sc.optString("teamA")
                    val teamB = sc.optString("teamB")
                    val title = if (teamA.isNotEmpty() || teamB.isNotEmpty())
                        "$teamA vs $teamB" else "Saved match"

                    val result = state.optString("result")
                    val batting = state.optString("battingTeam")
                    val runs = state.optInt("runs")
                    val wkts = state.optInt("wickets")
                    val overs = state.optString("overs")
                    val complete = state.optBoolean("matchComplete")

                    val subtitle = when {
                        result.isNotEmpty() -> result
                        batting.isNotEmpty() -> "$batting $runs/$wkts ($overs)"
                        else -> "In progress"
                    }
                    SavedMatch(id, title, subtitle, file.absolutePath, complete)
                }
            } catch (t: Throwable) {
                null
            }
        }
    }
}
