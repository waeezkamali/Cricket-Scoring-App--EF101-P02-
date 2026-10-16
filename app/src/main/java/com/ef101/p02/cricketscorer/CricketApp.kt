package com.ef101.p02.cricketscorer

import android.app.Application
import com.ef101.p02.cricketscorer.data.TeamRepository

class CricketApp : Application() {

    /** Single shared repository; SQLite handles its own thread-safety. */
    val teamRepository: TeamRepository by lazy { TeamRepository(this) }
}
