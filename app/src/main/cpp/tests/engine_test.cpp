// engine_test.cpp
// ---------------------------------------------------------------------------
// Host-side test harness for the EF101-P02 scoring engine.
// Not part of the Android build; compile with a desktop C++17 compiler, e.g.:
//
//   g++ -std=c++17 -Wall -I. CricketEngine.cpp Serialization.cpp
//       tests/engine_test.cpp -o engine_test
//
// It exercises every edge case called out in the specification.
// ---------------------------------------------------------------------------
#include "../CricketEngine.h"

#include <cstdio>
#include <iostream>
#include <string>

using namespace ef101;

static int g_failures = 0;
static int g_checks = 0;

#define CHECK(cond, msg)                                                   \
    do {                                                                   \
        ++g_checks;                                                        \
        if (!(cond)) {                                                     \
            ++g_failures;                                                  \
            std::cout << "  [FAIL] " << msg << "\n";                       \
        } else {                                                           \
            std::cout << "  [ok]   " << msg << "\n";                       \
        }                                                                  \
    } while (0)

static Team makeTeam(int id, const std::string& name,
                     std::initializer_list<int> playerIds) {
    Team t;
    t.id = id;
    t.name = name;
    int n = 1;
    for (int pid : playerIds) {
        Player p;
        p.id = pid;
        p.name = name + " P" + std::to_string(n++);
        t.players.push_back(p);
    }
    return t;
}

static std::string r(CricketEngine& e, int batRuns, ExtraType extra = ExtraType::None,
                     int extraRuns = 0, bool wicket = false,
                     DismissalType dis = DismissalType::NotOut,
                     int dismissed = -1, int fielder = -1) {
    BallInput in;
    in.batRuns = batRuns;
    in.extra = extra;
    in.extraRuns = extraRuns;
    in.wicket = wicket;
    in.dismissal = dis;
    in.dismissedPlayerId = dismissed;
    in.fielderId = fielder;
    return e.recordDelivery(in);
}

// ---------------------------------------------------------------------------
static void testOverWithWideNoBallBoundaryWicketStrikeChange() {
    std::cout << "\n== Test: wide, no-ball, boundary, strike change, over end ==\n";
    Team a = makeTeam(1, "TeamA", {1, 2, 3, 4});
    Team b = makeTeam(2, "TeamB", {11, 12, 13, 14});

    CricketEngine e;
    e.configure(a, b, "2026-10-16", "Wankhede", 2, "TeamA", "bat");
    CHECK(e.canStart(), "match can start with valid teams");
    CHECK(e.startMatch(1, 2, 11), "match started");

    // Over 1: ball-by-ball.
    CHECK(r(e, 1) == "", "ball 1: single");
    CHECK(r(e, 0, ExtraType::Wide) == "", "ball 2: wide (illegal)");
    CHECK(r(e, 4) == "", "ball 3: FOUR");
    CHECK(r(e, 1, ExtraType::NoBall) == "", "ball 4: no-ball + 1");
    CHECK(r(e, 0, ExtraType::Bye, 2) == "", "ball 5: 2 byes");
    CHECK(r(e, 0) == "", "ball 6: dot");
    CHECK(r(e, 6) == "", "ball 7: SIX");
    CHECK(r(e, 2) == "", "ball 8: two (ends the over)");

    const Innings& inn = e.match.innings[0];
    CHECK(inn.runs == 18, "team total is 18 (14 bat + 4 extras)");
    CHECK(inn.legalBalls == 6, "exactly 6 legal balls bowled");
    CHECK(inn.oversText() == "1.0", "over notation is 1.0 (not 8 balls)");
    CHECK(inn.extrasWides == 1, "1 wide recorded");
    CHECK(inn.extrasNoBalls == 1, "1 no-ball recorded");
    CHECK(inn.extrasByes == 2, "2 byes recorded");

    // Strike: p1 scored 9 off 5, p2 scored 5 off 2.
    const BatterInnings& p1 = inn.batters[0];
    const BatterInnings& p2 = inn.batters[1];
    CHECK(p1.runs == 9 && p1.ballsFaced == 5, "batter1: 9 off 5");
    CHECK(p1.sixes == 1, "batter1 hit 1 six");
    CHECK(p2.runs == 5 && p2.ballsFaced == 2, "batter2: 5 off 2");
    CHECK(p2.fours == 1, "batter2 hit 1 four");

    // Strike change + over-end flip => p2 on strike.
    CHECK(inn.batters[inn.strikerIdx].playerId == 2,
          "strike changed correctly (p2 on strike after over)");
    CHECK(inn.needsNewBowler, "a new bowler is required for the next over");
    CHECK(inn.lastOverBowlerId == 11, "last over bowler remembered");

    const BowlerSpell& bw = inn.bowlers[0];
    CHECK(bw.runsConceded == 16, "bowler conceded 16 (byes not charged)");
    CHECK(bw.legalBalls == 6 && bw.oversText() == "1.0", "bowler bowled 1.0 overs");
    CHECK(bw.wides == 1 && bw.noBalls == 1, "bowler has 1 wide + 1 no-ball");

    // Consecutive-over rule.
    CHECK(e.setNextBowler(11) != "", "same bowler cannot bowl consecutive overs");
    CHECK(e.setNextBowler(12) == "", "different bowler accepted");

    // Wicket.
    CHECK(r(e, 0, ExtraType::None, 0, true, DismissalType::Bowled, 2) == "",
          "wicket recorded (bowled)");
    CHECK(inn.wickets == 1, "wickets == 1");
    CHECK(inn.needsNewBatter, "new batter required after wicket");
    CHECK(e.setNewBatter(3) == "", "incoming batter accepted");
    CHECK(inn.batters[inn.strikerIdx].playerId == 3,
          "incoming batter takes strike");
    CHECK(inn.bowlers[1].playerId == 12 && inn.bowlers[1].wickets == 1,
          "bowler 12 credited with the wicket");
}

// ---------------------------------------------------------------------------
static void testRunOutNotCreditedToBowler() {
    std::cout << "\n== Test: run out is not credited to the bowler ==\n";
    Team a = makeTeam(1, "TeamA", {1, 2, 3, 4, 5});
    Team b = makeTeam(2, "TeamB", {11, 12, 13, 14, 15});

    CricketEngine e;
    e.configure(a, b, "2026-10-16", "Oval", 5, "TeamA", "bat");
    e.startMatch(1, 2, 11);

    // Bowler 11 takes a bowled wicket.
    r(e, 0, ExtraType::None, 0, true, DismissalType::Bowled, 1);
    e.setNewBatter(3);
    // Now striker = 3, non-striker = 2. Run out the non-striker.
    std::string err = r(e, 0, ExtraType::None, 0, true, DismissalType::RunOut, 2, 11);
    CHECK(err == "", "run out recorded");
    CHECK(e.match.innings[0].wickets == 2, "two wickets down");
    CHECK(e.match.innings[0].bowlers[0].wickets == 1,
          "run out is NOT credited to the bowler");
    e.setNewBatter(4);
}

// ---------------------------------------------------------------------------
static void testAllOutAndTargetChase() {
    std::cout << "\n== Test: successful chase + result text ==\n";
    Team a = makeTeam(1, "TeamA", {1, 2, 3, 4});
    Team b = makeTeam(2, "TeamB", {11, 12, 13, 14});

    CricketEngine e;
    e.configure(a, b, "2026-10-16", "Eden", 1, "TeamA", "bat");
    e.startMatch(1, 2, 11);

    // Innings 1: six singles => 6 runs, then overs exhausted (1 over).
    for (int i = 0; i < 6; ++i) r(e, 1);
    CHECK(e.match.innings[0].complete, "first innings complete (overs exhausted)");
    CHECK(e.match.innings[0].runs == 6, "first innings total is 6");

    // Innings 2: chase 7.
    CHECK(e.startSecondInnings(11, 12, 1), "second innings started");
    CHECK(e.match.innings[1].target == 7, "target is 7");
    CHECK(r(e, 4) == "", "chase: four");
    CHECK(r(e, 3) == "", "chase: three seals it");
    CHECK(e.match.innings[1].complete, "second innings complete");
    CHECK(e.match.matchComplete, "match complete");
    CHECK(e.match.resultText == "TeamB won by 3 wickets",
          "result text correct: " + e.match.resultText);
}

// ---------------------------------------------------------------------------
static void testAllOutInnings() {
    std::cout << "\n== Test: all out ends the innings ==\n";
    Team a = makeTeam(1, "TeamA", {1, 2, 3}); // 3 players => max 2 wickets
    Team b = makeTeam(2, "TeamB", {11, 12, 13});

    CricketEngine e;
    e.configure(a, b, "2026-10-16", "Lord's", 10, "TeamA", "bat");
    e.startMatch(1, 2, 11);

    r(e, 0, ExtraType::None, 0, true, DismissalType::Bowled, 1);
    e.setNewBatter(3);
    r(e, 0, ExtraType::None, 0, true, DismissalType::Lbw, 2);
    CHECK(e.match.innings[0].complete, "innings complete after all out");
    CHECK(e.match.innings[0].wickets == 2, "2 wickets (3-player team)");
}

// ---------------------------------------------------------------------------
static void testPersistenceRoundTrip() {
    std::cout << "\n== Test: serialize / deserialize round trip ==\n";
    Team a = makeTeam(1, "TeamA", {1, 2, 3, 4});
    Team b = makeTeam(2, "TeamB", {11, 12, 13, 14});

    CricketEngine e;
    e.configure(a, b, "2026-10-16", "Wankhede", 2, "TeamA", "bat");
    e.match.id = "match-xyz";
    e.startMatch(1, 2, 11);
    r(e, 1);
    r(e, 0, ExtraType::Wide);
    r(e, 4);
    r(e, 1, ExtraType::NoBall);
    r(e, 0, ExtraType::Bye, 2);
    r(e, 0);
    r(e, 6);
    r(e, 2);

    std::string blob = e.serialize();
    std::string before = e.toJson();

    CricketEngine restored;
    CHECK(restored.deserialize(blob), "deserialize succeeded");
    CHECK(restored.toJson() == before, "restored state matches original");
    CHECK(restored.match.id == "match-xyz", "match id persisted");
    CHECK(restored.match.innings[0].runs == 18, "score persisted");
    CHECK(restored.serialize() == blob, "re-serialization is stable");
}

// ---------------------------------------------------------------------------
int main() {
    std::cout << "EF101-P02 Cricket Engine - host test suite\n";
    testOverWithWideNoBallBoundaryWicketStrikeChange();
    testRunOutNotCreditedToBowler();
    testAllOutAndTargetChase();
    testAllOutInnings();
    testPersistenceRoundTrip();

    std::cout << "\n---------------------------------------------\n";
    std::cout << "Checks: " << g_checks << "  Failures: " << g_failures << "\n";
    return g_failures == 0 ? 0 : 1;
}
