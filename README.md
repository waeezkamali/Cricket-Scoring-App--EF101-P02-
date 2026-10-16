# EF101-P02 — Offline Cricket Match Scoring (Android + C++/NDK)

A production-oriented, **fully offline** cricket match scoring application. The
Android UI (Kotlin) delegates *all* cricket rules, match state and statistics to
a **C++17 core engine** that is called over **JNI/NDK**. Nothing in the app ever
touches the network.

```
┌──────────────────────────────┐        JNI (NativeEngine)        ┌─────────────────────────┐
│  Kotlin UI (Activities)      │  ─────────────────────────────►  │  C++ Core (CricketEngine)│
│  Teams / Setup / Scoring /   │                                  │  rules, state, stats,    │
│  Scorecard / Saved matches   │  ◄───── JSON snapshots ────────  │  serialization           │
└──────────────┬───────────────┘                                  └────────────┬────────────┘
               │ SQLite (teams, players)                                        │ files (match state)
               └──────────────── local, on-device, offline ────────────────────┘
```

## Repository layout

```
app/src/main/cpp/                 C++ core (no Android dependency in the engine)
  ├─ MatchState.h                 Entities: Team, Player, Match, Innings,
  │                               BatterInnings, BowlerSpell, Delivery, enums
  ├─ CricketEngine.h              Engine interface + BallInput
  ├─ CricketEngine.cpp            Ball-by-ball rules, strike rotation, over logic
  ├─ Serialization.cpp            JSON snapshots + line-based file persistence
  ├─ JniBridge.cpp                JNI exports (handle registry)
  ├─ CMakeLists.txt               Builds libcricketengine.so
  └─ tests/engine_test.cpp        Host-side unit tests (not part of the APK)

app/src/main/java/com/ef101/p02/cricketscorer/
  ├─ CricketApp.kt                Application + shared TeamRepository
  ├─ engine/NativeEngine.kt       Kotlin JNI wrapper (AutoCloseable)
  ├─ data/                        Models, SQLite (DbHelper/TeamRepository), MatchStore
  └─ ui/                          Main, Teams, TeamDetail, MatchSetup, Scoring,
                                  Scorecard, SavedMatches, SelfTest activities
```

## Features

### Team & player management
* Create/save teams and add/remove players, stored in a local **SQLite** database.
* **Minimum team size is enforced** (`CricketEngine::validationMessage`): both
  teams need at least 2 players before a match can start. Squads are capped at 11.

### Match setup
* Select two teams, enter date, venue and total overs, choose the toss winner and
  whether they bat or bowl, then select **striker, non-striker and opening bowler**.

### Ball-by-ball scoring engine (C++)
* Runs `0,1,2,3,4,6`.
* Extras: **Wide, No-ball, Bye, Leg-bye**.
* Wides and no-balls **do not count as legal balls**; overs render in cricket
  notation (e.g. `3.4`).
* **Strike changes automatically** on odd runs and at the end of an over.
* **Prompts for the next bowler** at the start of each over (and blocks the same
  bowler from bowling consecutive overs).

### Statistics & scorecard
* Batters: runs, balls faced, fours, sixes, strike rate, dismissal.
* Bowlers: overs, legal balls, runs conceded, wickets, maidens, wides, no-balls,
  economy.
* Dismissals: **Bowled, Caught, LBW, Stumped, Run out** (run out is not credited
  to the bowler).
* Innings end automatically on **overs limit / all out / target chased**; final
  scores and the result string are produced by the engine.

### Persistence & offline
* Teams/players in SQLite. Match state is serialized by C++ to a file in the app's
  private storage, so **in-progress and completed matches survive a restart**.
* No network permission is declared.

### Test hooks for the required edge cases
* `cpp/tests/engine_test.cpp` — host test suite (see below).
* **In-app** “Run Engine Self-Test” screen executes the same edge cases against the
  native library and prints a pass/fail report: an over with a wide, a no-ball, a
  boundary, a wicket, a strike change, an over ending, a successful chase and a
  save/restore round trip.

## Building

### Prerequisites
* Android Studio (Koala/2024.1 or newer) **or** the command line tools.
* **JDK 17** (AGP 8.5 targets 17).
* Android SDK: **compileSdk 34**, **build-tools 34.x**.
* **NDK 26.1.10909125** and **CMake 3.22.1** installed via the SDK Manager
  (Settings ▸ SDK Manager ▸ SDK Tools ▸ “NDK (Side by side)” and “CMake”).

### Build the APK
Open the project in Android Studio (it will generate the Gradle wrapper), or with
a local Gradle 8.7+:

```bash
# point the build at your SDK (Android Studio usually writes this for you)
echo "sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk" > local.properties

gradle wrapper            # once, if ./gradlew is missing
./gradlew :app:assembleDebug
```

The native library `libcricketengine.so` is built automatically for
`arm64-v8a`, `armeabi-v7a` and `x86_64`.

### Run the C++ unit tests (desktop, no Android required)
The engine is deliberately free of Android/JNI dependencies, so it can be tested
with any C++17 compiler:

```bash
cd app/src/main/cpp
g++ -std=c++17 -Wall -Wextra -I. CricketEngine.cpp Serialization.cpp \
    tests/engine_test.cpp -o engine_test
./engine_test
```

Expected result:

```
Checks: 53  Failures: 0
```

## Notable design decisions
* **Engine returns data, not objects.** Snapshots are emitted as JSON strings over
  JNI (built in C++), keeping the bridge small and allocation-light. Kotlin parses
  them with the platform `org.json`.
* **Handles, not globals.** Each `NativeEngine` owns a heap `CricketEngine`
  registered under an opaque `jlong`; `close()` releases it.
* **Idempotent persistence.** The UI writes the match file after every delivery so
  a crash can lose at most one ball.
