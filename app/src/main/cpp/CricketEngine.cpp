// CricketEngine.cpp
// ---------------------------------------------------------------------------
// Implementation of the ball-by-ball scoring engine.
// ---------------------------------------------------------------------------
#include "CricketEngine.h"

#include <algorithm>

namespace ef101 {

// Minimum number of players a team must field before a match can start.
static const size_t kMinTeamSize = 2;

// ---------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------
void CricketEngine::configure(const Team& teamA, const Team& teamB,
                              const std::string& date, const std::string& venue,
                              int overs, const std::string& tossWinner,
                              const std::string& battingChoice) {
    match = MatchState();
    match.teamA = teamA;
    match.teamB = teamB;
    match.date = date;
    match.venue = venue;
    match.totalOvers = overs > 0 ? overs : 20;
    match.tossWinner = tossWinner;
    match.battingChoice = (battingChoice == "bowl") ? "bowl" : "bat";
}

bool CricketEngine::canStart() const {
    return validationMessage().empty();
}

std::string CricketEngine::validationMessage() const {
    if (match.teamA.name.empty() || match.teamB.name.empty()) {
        return "Both teams must be selected.";
    }
    if (match.teamA.players.size() < kMinTeamSize) {
        return "Team " + match.teamA.name + " needs at least " +
               std::to_string(kMinTeamSize) + " players.";
    }
    if (match.teamB.players.size() < kMinTeamSize) {
        return "Team " + match.teamB.name + " needs at least " +
               std::to_string(kMinTeamSize) + " players.";
    }
    if (match.totalOvers <= 0) {
        return "Total overs must be greater than zero.";
    }
    return "";
}

bool CricketEngine::startMatch(int strikerId, int nonStrikerId, int bowlerId) {
    if (!canStart()) return false;
    if (strikerId == nonStrikerId) return false;

    // Decide who bats first from the toss result.
    const Team* batFirst = nullptr;
    const Team* bowlFirst = nullptr;
    if (match.battingChoice == "bat") {
        batFirst = (match.tossWinner == match.teamA.name) ? &match.teamA : &match.teamB;
    } else {
        batFirst = (match.tossWinner == match.teamA.name) ? &match.teamB : &match.teamA;
    }
    bowlFirst = (batFirst == &match.teamA) ? &match.teamB : &match.teamA;

    match.battingFirst = *batFirst;
    match.bowlingFirst = *bowlFirst;

    Innings& inn = match.innings[0];
    inn.battingTeamId = batFirst->id;
    inn.bowlingTeamId = bowlFirst->id;
    inn.battingTeamName = batFirst->name;
    inn.bowlingTeamName = bowlFirst->name;
    inn.maxOvers = match.totalOvers;
    inn.maxWickets = static_cast<int>(batFirst->players.size()) - 1;
    if (inn.maxWickets < 1) inn.maxWickets = 1;

    ensureOpeners(inn, strikerId, nonStrikerId);
    inn.currentBowlerIdx = findOrCreateBowlerIdx(inn, bowlerId);
    inn.needsNewBowler = false;
    inn.needsNewBatter = false;

    match.currentInnings = 0;
    match.inningsCount = 1;
    match.started = true;
    match.matchComplete = false;
    match.resultText.clear();
    return true;
}

bool CricketEngine::startSecondInnings(int strikerId, int nonStrikerId, int bowlerId) {
    if (match.inningsCount != 1) return false;
    if (!match.innings[0].complete) return false;
    if (strikerId == nonStrikerId) return false;

    Innings& inn = match.innings[1];
    inn.battingTeamId = match.bowlingFirst.id;
    inn.bowlingTeamId = match.battingFirst.id;
    inn.battingTeamName = match.bowlingFirst.name;
    inn.bowlingTeamName = match.battingFirst.name;
    inn.maxOvers = match.totalOvers;
    inn.maxWickets = static_cast<int>(match.bowlingFirst.players.size()) - 1;
    if (inn.maxWickets < 1) inn.maxWickets = 1;
    inn.target = match.innings[0].runs + 1;

    ensureOpeners(inn, strikerId, nonStrikerId);
    inn.currentBowlerIdx = findOrCreateBowlerIdx(inn, bowlerId);
    inn.needsNewBowler = false;
    inn.needsNewBatter = false;

    match.currentInnings = 1;
    match.inningsCount = 2;
    return true;
}

// ---------------------------------------------------------------------------
// Crease helpers
// ---------------------------------------------------------------------------
int CricketEngine::findBatterIdx(const Innings& inn, int playerId) const {
    for (size_t i = 0; i < inn.batters.size(); ++i) {
        if (inn.batters[i].playerId == playerId) return static_cast<int>(i);
    }
    return -1;
}

int CricketEngine::findBowlerIdx(const Innings& inn, int playerId) const {
    for (size_t i = 0; i < inn.bowlers.size(); ++i) {
        if (inn.bowlers[i].playerId == playerId) return static_cast<int>(i);
    }
    return -1;
}

int CricketEngine::findOrCreateBowlerIdx(Innings& inn, int playerId) {
    int idx = findBowlerIdx(inn, playerId);
    if (idx >= 0) return idx;
    const Player* p = teamById(inn.bowlingTeamId).findPlayer(playerId);
    if (!p) return -1;
    BowlerSpell bs;
    bs.playerId = playerId;
    bs.name = p->name;
    inn.bowlers.push_back(bs);
    return static_cast<int>(inn.bowlers.size()) - 1;
}

void CricketEngine::ensureOpeners(Innings& inn, int strikerId, int nonStrikerId) {
    auto addBatter = [&](int playerId) -> int {
        int existing = findBatterIdx(inn, playerId);
        if (existing >= 0) return existing;
        const Player* p = teamById(inn.battingTeamId).findPlayer(playerId);
        BatterInnings bi;
        bi.playerId = playerId;
        bi.name = p ? p->name : std::string("Unknown");
        bi.atCrease = true;
        inn.batters.push_back(bi);
        return static_cast<int>(inn.batters.size()) - 1;
    };
    inn.strikerIdx = addBatter(strikerId);
    inn.nonStrikerIdx = addBatter(nonStrikerId);
    if (inn.strikerIdx >= 0) inn.batters[inn.strikerIdx].atCrease = true;
    if (inn.nonStrikerIdx >= 0) inn.batters[inn.nonStrikerIdx].atCrease = true;
}

void CricketEngine::swapStrike(Innings& inn) {
    std::swap(inn.strikerIdx, inn.nonStrikerIdx);
}

// ---------------------------------------------------------------------------
// Delivery recording
// ---------------------------------------------------------------------------
std::string CricketEngine::recordDelivery(const BallInput& in) {
    if (match.matchComplete) return "Match is already complete.";
    if (!match.started)      return "Match has not started.";
    if (match.currentInnings < 0 || match.currentInnings > 1)
        return "Invalid innings index.";

    Innings& inn = current();
    if (inn.complete)         return "Innings is complete.";
    if (inn.needsNewBowler)   return "Select the bowler for the next over.";
    if (inn.needsNewBatter)   return "Select the next batter.";
    if (inn.strikerIdx < 0 || inn.nonStrikerIdx < 0) return "Batters are not set.";
    if (inn.currentBowlerIdx < 0) return "Bowler is not set.";

    // Validate the dismissal target before mutating any state.
    int dismissedId = in.dismissedPlayerId;
    if (in.wicket) {
        if (dismissedId < 0) dismissedId = inn.batters[inn.strikerIdx].playerId;
        if (inn.batters[inn.strikerIdx].playerId != dismissedId &&
            inn.batters[inn.nonStrikerIdx].playerId != dismissedId) {
            return "Dismissed player is not at the crease.";
        }
    }

    const bool legal = !(in.extra == ExtraType::Wide || in.extra == ExtraType::NoBall);

    BatterInnings& striker = inn.batters[inn.strikerIdx];
    BowlerSpell&   bowler  = inn.bowlers[inn.currentBowlerIdx];

    Delivery d;
    d.inningIndex = match.currentInnings;
    d.strikerId = striker.playerId;
    d.bowlerId = bowler.playerId;
    d.batRuns = in.batRuns;
    d.extra = in.extra;
    d.extraRuns = in.extraRuns;

    int addedToTeam = 0;      // runs added to the team total
    int runsRunByBatters = 0; // runs physically run (drives strike changes)

    switch (in.extra) {
        case ExtraType::None: {
            striker.runs += in.batRuns;
            striker.ballsFaced += 1;
            if (in.batRuns == 4) striker.fours += 1;
            if (in.batRuns == 6) striker.sixes += 1;
            addedToTeam = in.batRuns;
            bowler.runsConceded += in.batRuns;
            bowler.runsThisOver += in.batRuns;
            runsRunByBatters = in.batRuns;
            break;
        }
        case ExtraType::Wide: {
            int total = 1 + in.extraRuns;         // penalty + runs run
            inn.extrasWides += total;
            addedToTeam = total;
            bowler.runsConceded += total;
            bowler.runsThisOver += total;
            bowler.wides += 1;
            runsRunByBatters = in.extraRuns;       // penalty never changes strike
            break;
        }
        case ExtraType::NoBall: {
            striker.runs += in.batRuns;            // runs off the bat on a no-ball
            striker.ballsFaced += 1;               // a no-ball counts as faced
            if (in.batRuns == 4) striker.fours += 1;
            if (in.batRuns == 6) striker.sixes += 1;
            int penalty = 1 + in.extraRuns;        // extraRuns here are byes off the no-ball
            inn.extrasNoBalls += penalty;
            addedToTeam = penalty + in.batRuns;
            bowler.runsConceded += penalty + in.batRuns;
            bowler.runsThisOver += penalty + in.batRuns;
            bowler.noBalls += 1;
            runsRunByBatters = in.batRuns + in.extraRuns;
            break;
        }
        case ExtraType::Bye: {
            striker.ballsFaced += 1;
            inn.extrasByes += in.extraRuns;
            addedToTeam = in.extraRuns;            // not charged to the bowler
            runsRunByBatters = in.extraRuns;
            break;
        }
        case ExtraType::LegBye: {
            striker.ballsFaced += 1;
            inn.extrasLegByes += in.extraRuns;
            addedToTeam = in.extraRuns;
            runsRunByBatters = in.extraRuns;
            break;
        }
    }

    inn.runs += addedToTeam;
    d.totalRuns = addedToTeam;
    d.legalBall = legal;

    // over.ball label at the moment of delivery.
    d.overLabel = std::to_string(inn.legalBalls / 6) + "." +
                  std::to_string(inn.ballsThisOver + 1);

    // Human readable display token.
    switch (in.extra) {
        case ExtraType::None:    d.display = std::to_string(in.batRuns); break;
        case ExtraType::Wide:    d.display = (in.extraRuns > 0) ? ("wd+" + std::to_string(in.extraRuns)) : "wd"; break;
        case ExtraType::NoBall:  d.display = "nb" + (in.batRuns > 0 ? ("+" + std::to_string(in.batRuns)) : std::string()); break;
        case ExtraType::Bye:     d.display = std::to_string(in.extraRuns) + "b"; break;
        case ExtraType::LegBye:  d.display = std::to_string(in.extraRuns) + "lb"; break;
    }

    // ---- Wicket handling ---------------------------------------------------
    if (in.wicket) {
        int outIdx = (inn.batters[inn.strikerIdx].playerId == dismissedId)
                         ? inn.strikerIdx
                         : inn.nonStrikerIdx;
        BatterInnings& out = inn.batters[outIdx];
        out.out = true;
        out.atCrease = false;
        out.howOut = in.dismissal;

        if (in.dismissal != DismissalType::RunOut &&
            in.dismissal != DismissalType::Retired) {
            // Credited to the bowler.
            out.bowlerId = bowler.playerId;
            out.bowlerName = bowler.name;
            bowler.wickets += 1;
        }
        if (in.fielderId >= 0) {
            out.fielderId = in.fielderId;
            const Player* f = teamById(inn.bowlingTeamId).findPlayer(in.fielderId);
            out.fielderName = f ? f->name : std::string();
        }
        inn.wickets += 1;

        // Empty the crease slot. Strike swap below still proceeds so that the
        // incoming batter is placed at the correct end.
        if (outIdx == inn.strikerIdx) inn.strikerIdx = -1;
        else                          inn.nonStrikerIdx = -1;

        inn.needsNewBatter = true;

        d.wicket = true;
        d.dismissal = in.dismissal;
        d.dismissedPlayerId = dismissedId;
        d.fielderId = in.fielderId;
        d.display = "W";
    }

    // ---- Legal delivery accounting ----------------------------------------
    if (legal) {
        inn.legalBalls += 1;
        bowler.legalBalls += 1;
        bowler.legalThisOver += 1;
    }
    inn.ballsThisOver += 1;

    // ---- Strike change on odd runs ----------------------------------------
    if (runsRunByBatters % 2 == 1) {
        swapStrike(inn);
    }

    inn.timeline.push_back(d);

    // ---- End of over -------------------------------------------------------
    if (legal && inn.legalBalls % 6 == 0 && !inn.complete) {
        if (bowler.runsThisOver == 0) bowler.maidens += 1;
        bowler.runsThisOver = 0;
        bowler.legalThisOver = 0;
        inn.lastOverBowlerId = bowler.playerId;
        inn.ballsThisOver = 0;
        swapStrike(inn);                 // ends are changed at the end of an over
        inn.needsNewBowler = true;
        inn.currentBowlerIdx = -1;
    }

    checkInningsCompletion(inn);
    finalizeMatchIfDone();
    return "";
}

std::string CricketEngine::setNextBowler(int bowlerId) {
    if (match.matchComplete) return "Match is already complete.";
    Innings& inn = current();
    if (inn.complete) return "Innings is complete.";
    if (!inn.needsNewBowler) return "A bowler is already set for this over.";
    if (bowlerId == inn.lastOverBowlerId)
        return "A bowler cannot bowl two consecutive overs.";
    int idx = findOrCreateBowlerIdx(inn, bowlerId);
    if (idx < 0) return "Bowler is not part of the bowling team.";
    inn.currentBowlerIdx = idx;
    inn.needsNewBowler = false;
    return "";
}

std::string CricketEngine::setNewBatter(int playerId) {
    if (match.matchComplete) return "Match is already complete.";
    Innings& inn = current();
    if (!inn.needsNewBatter) return "No new batter is required.";
    if (inn.strikerIdx >= 0 && inn.nonStrikerIdx >= 0) return "The crease is full.";
    if (findBatterIdx(inn, playerId) >= 0) return "That player has already batted.";

    const Player* p = teamById(inn.battingTeamId).findPlayer(playerId);
    if (!p) return "Player is not part of the batting team.";

    BatterInnings bi;
    bi.playerId = playerId;
    bi.name = p->name;
    bi.atCrease = true;
    inn.batters.push_back(bi);
    int idx = static_cast<int>(inn.batters.size()) - 1;

    if (inn.strikerIdx < 0)       inn.strikerIdx = idx;
    else if (inn.nonStrikerIdx < 0) inn.nonStrikerIdx = idx;
    else                          return "The crease is full.";

    inn.needsNewBatter = false;
    return "";
}

// ---------------------------------------------------------------------------
// Innings / match completion
// ---------------------------------------------------------------------------
void CricketEngine::checkInningsCompletion(Innings& inn) {
    if (inn.complete) return;
    if (inn.wickets >= inn.maxWickets) inn.complete = true;
    if (inn.legalBalls >= inn.maxOvers * 6) inn.complete = true;
    if (inn.target > 0 && inn.runs >= inn.target) inn.complete = true;
}

void CricketEngine::finalizeMatchIfDone() {
    if (match.matchComplete) return;
    if (match.inningsCount == 2 && match.innings[1].complete) {
        match.matchComplete = true;
        match.resultText = buildResult();
    } else if (match.inningsCount == 1 && match.innings[0].complete) {
        // First innings finished; second innings not yet started.
        if (match.resultText.empty()) {
            match.resultText = match.innings[0].battingTeamName +
                               " scored " + std::to_string(match.innings[0].runs) +
                               "/" + std::to_string(match.innings[0].wickets);
        }
    }
}

std::string CricketEngine::buildResult() const {
    const Innings& first  = match.innings[0];
    const Innings& second = match.innings[1];
    if (second.runs >= second.target) {
        int wktsLeft = second.maxWickets - second.wickets;
        return second.battingTeamName + " won by " + std::to_string(wktsLeft) +
               " wicket" + (wktsLeft == 1 ? "" : "s");
    }
    if (second.runs == first.runs) {
        return "Match tied";
    }
    int margin = first.runs - second.runs;
    return first.battingTeamName + " won by " + std::to_string(margin) +
           " run" + (margin == 1 ? "" : "s");
}

// ---------------------------------------------------------------------------
// Accessors
// ---------------------------------------------------------------------------
Innings& CricketEngine::current() {
    if (match.currentInnings < 0) match.currentInnings = 0;
    if (match.currentInnings > 1) match.currentInnings = 1;
    return match.innings[match.currentInnings];
}

const Innings& CricketEngine::current() const {
    int idx = match.currentInnings;
    if (idx < 0) idx = 0;
    if (idx > 1) idx = 1;
    return match.innings[idx];
}

const Team& CricketEngine::teamById(int teamId) const {
    if (match.teamA.id == teamId) return match.teamA;
    if (match.teamB.id == teamId) return match.teamB;
    if (match.battingFirst.id == teamId) return match.battingFirst;
    if (match.bowlingFirst.id == teamId) return match.bowlingFirst;
    return match.teamA;
}

std::string CricketEngine::teamNameById(int teamId) const {
    return teamById(teamId).name;
}

} // namespace ef101
