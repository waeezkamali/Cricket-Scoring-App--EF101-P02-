package com.ef101.p02.cricketscorer.engine

/**
 * Kotlin wrapper around the native C++ scoring engine.
 *
 * Each instance owns a native [CricketEngine] identified by an opaque handle.
 * Call [close] (or use [use]) when finished so the native memory is released.
 *
 * The method names and signatures mirror the JNI exports in `JniBridge.cpp`.
 */
class NativeEngine private constructor(var handle: Long) : AutoCloseable {

    // ----- Lifecycle --------------------------------------------------------
    @Volatile
    private var closed = false

    override fun close() {
        if (!closed && handle != 0L) {
            nativeDestroy(handle)
            closed = true
            handle = 0L
        }
    }

    val isOpen: Boolean get() = !closed && handle != 0L

    // ----- Configuration ----------------------------------------------------
    fun setMatchId(id: String) = nativeSetMatchId(handle, id)

    fun configure(
        teamA: TeamSpec,
        teamB: TeamSpec,
        date: String,
        venue: String,
        overs: Int,
        tossWinner: String,
        battingChoice: String
    ) = nativeConfigure(
        handle,
        teamA.id, teamA.name, teamA.rosterString(),
        teamB.id, teamB.name, teamB.rosterString(),
        date, venue, overs, tossWinner, battingChoice
    )

    fun validationMessage(): String = nativeValidationMessage(handle)
    fun canStart(): Boolean = nativeCanStart(handle)

    fun startMatch(strikerId: Int, nonStrikerId: Int, bowlerId: Int): Boolean =
        nativeStartMatch(handle, strikerId, nonStrikerId, bowlerId)

    fun startSecondInnings(strikerId: Int, nonStrikerId: Int, bowlerId: Int): Boolean =
        nativeStartSecondInnings(handle, strikerId, nonStrikerId, bowlerId)

    // ----- In-play ----------------------------------------------------------
    /** @return "" on success, otherwise a human readable error. */
    fun recordDelivery(
        batRuns: Int,
        extraType: String = "None",
        extraRuns: Int = 0,
        wicket: Boolean = false,
        dismissal: String = "Not out",
        dismissedPlayerId: Int = -1,
        fielderId: Int = -1
    ): String = nativeRecordDelivery(
        handle, batRuns, extraType, extraRuns, wicket, dismissal,
        dismissedPlayerId, fielderId
    )

    fun setNextBowler(bowlerId: Int): String = nativeSetNextBowler(handle, bowlerId)
    fun setNewBatter(playerId: Int): String = nativeSetNewBatter(handle, playerId)

    // ----- Snapshots --------------------------------------------------------
    fun stateJson(): String = nativeGetStateJson(handle)
    fun scorecardJson(): String = nativeGetScorecardJson(handle)
    fun serialize(): String = nativeSerialize(handle)

    // ----- Persistence ------------------------------------------------------
    /** @return "" on success, otherwise a human readable error. */
    fun saveToFile(path: String): String = nativeSaveToFile(handle, path)

    /** @return "" on success, otherwise a human readable error. */
    fun loadFromFile(path: String): String = nativeLoadFromFile(handle, path)

    // ----- Value types ------------------------------------------------------
    data class PlayerSpec(val id: Int, val name: String)

    data class TeamSpec(val id: Int, val name: String, val players: List<PlayerSpec>) {
        fun rosterString(): String = players.joinToString("\n") { "${it.id}|${it.name}" }
    }

    // ----- Native declarations ---------------------------------------------
    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)

    private external fun nativeSetMatchId(handle: Long, id: String)
    private external fun nativeConfigure(
        handle: Long,
        teamAId: Int, teamAName: String, teamARoster: String,
        teamBId: Int, teamBName: String, teamBRoster: String,
        date: String, venue: String, overs: Int,
        tossWinner: String, battingChoice: String
    )

    private external fun nativeValidationMessage(handle: Long): String
    private external fun nativeCanStart(handle: Long): Boolean
    private external fun nativeStartMatch(
        handle: Long, strikerId: Int, nonStrikerId: Int, bowlerId: Int
    ): Boolean

    private external fun nativeStartSecondInnings(
        handle: Long, strikerId: Int, nonStrikerId: Int, bowlerId: Int
    ): Boolean

    private external fun nativeRecordDelivery(
        handle: Long, batRuns: Int, extraType: String, extraRuns: Int,
        wicket: Boolean, dismissal: String, dismissedPlayerId: Int, fielderId: Int
    ): String

    private external fun nativeSetNextBowler(handle: Long, bowlerId: Int): String
    private external fun nativeSetNewBatter(handle: Long, playerId: Int): String

    private external fun nativeGetStateJson(handle: Long): String
    private external fun nativeGetScorecardJson(handle: Long): String
    private external fun nativeSerialize(handle: Long): String

    private external fun nativeSaveToFile(handle: Long, path: String): String
    private external fun nativeLoadFromFile(handle: Long, path: String): String

    companion object {
        init {
            System.loadLibrary("cricketengine")
        }

        /** Creates a fresh, empty engine bound to a native handle. */
        fun create(): NativeEngine {
            val e = NativeEngine(0L)
            e.handle = e.nativeCreate()
            return e
        }

        /** Creates an engine and loads a previously saved match file into it. */
        fun fromFile(path: String): NativeEngine {
            val e = create()
            val err = e.loadFromFile(path)
            if (err.isNotEmpty()) {
                e.close()
                throw IllegalStateException(err)
            }
            return e
        }
    }
}
