// CricketEngine.h
// ---------------------------------------------------------------------------
// The ball-by-ball scoring engine. All cricket rules, state transitions and
// statistics live here. This class has no JNI dependency.
// ---------------------------------------------------------------------------
#ifndef EF101_CRICKET_ENGINE_H
#define EF101_CRICKET_ENGINE_H

#include "MatchState.h"
#include <string>

namespace ef101 {

// Parameters for a single delivery, as supplied by the scoring UI.
struct BallInput {
    int           batRuns = 0;                 // runs off the bat (0,1,2,3,4,6)
    ExtraType     extra = ExtraType::None;
    int           extraRuns = 0;               // additional runs (bye/legbye/run)
    bool          wicket = false;
    DismissalType dismissal = DismissalType::NotOut;
    int           dismissedPlayerId = -1;
    int           fielderId = -1;
};

class CricketEngine {
public:
    MatchState match;

    CricketEngine() = default;

    // ---- Setup -------------------------------------------------------------
    // Populate teams and metadata. Does not start play.
    void configure(const Team& teamA, const Team& teamB,
                   const std::string& date, const std::string& venue,
                   int overs, const std::string& tossWinner,
                   const std::string& battingChoice);

    // Validate team sizes etc. Returns true when a match may begin.
    bool canStart() const;
    std::string validationMessage() const;

    // Begin the first innings, assigning the chosen openers.
    bool startMatch(int strikerId, int nonStrikerId, int bowlerId);

    // Begin the second innings (after the first is complete).
    bool startSecondInnings(int strikerId, int nonStrikerId, int bowlerId);

    // ---- In-play -----------------------------------------------------------
    // Record one delivery. Returns a human readable error string ("" == ok).
    std::string recordDelivery(const BallInput& input);

    // Assign the bowler for the new over.
    std::string setNextBowler(int bowlerId);

    // Assign the incoming batter after a wicket.
    std::string setNewBatter(int playerId);

    // ---- Queries -----------------------------------------------------------
    Innings&      current();
    const Innings& current() const;
    bool          matchComplete() const { return match.matchComplete; }

    // ---- Serialization -----------------------------------------------------
    std::string toJson() const;        // live state snapshot for the UI
    std::string scorecardJson() const; // full scorecard (both innings)

    // Compact custom text format used for on-device persistence.
    std::string serialize() const;
    bool        deserialize(const std::string& data);

    // Convenience file helpers (return "" on success, error text otherwise).
    std::string saveToFile(const std::string& path) const;
    std::string loadFromFile(const std::string& path);

private:
    // helpers
    int  findBatterIdx(const Innings& inn, int playerId) const;
    int  findOrCreateBowlerIdx(Innings& inn, int playerId);
    int  findBowlerIdx(const Innings& inn, int playerId) const;
    void ensureOpeners(Innings& inn, int strikerId, int nonStrikerId);
    void swapStrike(Innings& inn);
    void checkInningsCompletion(Innings& inn);
    void finalizeMatchIfDone();
    std::string buildResult() const;
    std::string teamNameById(int teamId) const;
    const Team& teamById(int teamId) const;
};

} // namespace ef101

#endif // EF101_CRICKET_ENGINE_H
