// MatchState.h
// ---------------------------------------------------------------------------
// EF101-P02 Cricket Scoring Application - Core domain model.
//
// This header is intentionally free of any JNI / Android dependency so that
// the rules engine can be unit-tested on a normal desktop toolchain.
// ---------------------------------------------------------------------------
#ifndef EF101_MATCH_STATE_H
#define EF101_MATCH_STATE_H

#include <string>
#include <vector>
#include <cstdint>

namespace ef101 {

// ---------------------------------------------------------------------------
// Enumerations
// ---------------------------------------------------------------------------

// Kind of extra applied to a delivery.
enum class ExtraType : int {
    None = 0,
    Wide = 1,
    NoBall = 2,
    Bye = 3,
    LegBye = 4
};

// How a batter was dismissed.
enum class DismissalType : int {
    NotOut = 0,
    Bowled = 1,
    Caught = 2,
    Lbw = 3,
    Stumped = 4,
    RunOut = 5,
    Retired = 6
};

inline const char* extraTypeToString(ExtraType e) {
    switch (e) {
        case ExtraType::Wide:    return "Wide";
        case ExtraType::NoBall:  return "NoBall";
        case ExtraType::Bye:     return "Bye";
        case ExtraType::LegBye:  return "LegBye";
        default:                 return "None";
    }
}

inline ExtraType extraTypeFromString(const std::string& s) {
    if (s == "Wide")   return ExtraType::Wide;
    if (s == "NoBall") return ExtraType::NoBall;
    if (s == "Bye")    return ExtraType::Bye;
    if (s == "LegBye") return ExtraType::LegBye;
    return ExtraType::None;
}

inline const char* dismissalTypeToString(DismissalType d) {
    switch (d) {
        case DismissalType::Bowled:  return "Bowled";
        case DismissalType::Caught:  return "Caught";
        case DismissalType::Lbw:     return "LBW";
        case DismissalType::Stumped: return "Stumped";
        case DismissalType::RunOut:  return "Run out";
        case DismissalType::Retired: return "Retired";
        default:                     return "Not out";
    }
}

inline DismissalType dismissalTypeFromString(const std::string& s) {
    if (s == "Bowled")  return DismissalType::Bowled;
    if (s == "Caught")  return DismissalType::Caught;
    if (s == "LBW")     return DismissalType::Lbw;
    if (s == "Stumped") return DismissalType::Stumped;
    if (s == "Run out") return DismissalType::RunOut;
    if (s == "Retired") return DismissalType::Retired;
    return DismissalType::NotOut;
}

// ---------------------------------------------------------------------------
// Entities
// ---------------------------------------------------------------------------

struct Player {
    int         id = 0;
    std::string name;
};

struct Team {
    int                 id = 0;
    std::string         name;
    std::vector<Player> players;

    const Player* findPlayer(int playerId) const {
        for (const auto& p : players) {
            if (p.id == playerId) return &p;
        }
        return nullptr;
    }
};

// A single batter's contribution within one innings.
struct BatterInnings {
    int            playerId = 0;
    std::string    name;
    int            runs = 0;
    int            ballsFaced = 0;
    int            fours = 0;
    int            sixes = 0;
    bool           out = false;
    DismissalType  howOut = DismissalType::NotOut;
    int            bowlerId = -1;
    std::string    bowlerName;
    int            fielderId = -1;
    std::string    fielderName;

    // True while this batter occupies a crease slot.
    bool           atCrease = false;

    bool isStrikerSlot = false; // presentation helper only
};

// A single bowler's spell within one innings.
struct BowlerSpell {
    int         playerId = 0;
    std::string name;
    int         legalBalls = 0;
    int         runsConceded = 0;
    int         wickets = 0;
    int         maidens = 0;
    int         wides = 0;
    int         noBalls = 0;

    // Per-over accumulators (reset when the over completes).
    int         runsThisOver = 0;
    int         legalThisOver = 0;

    std::string oversText() const {
        return std::to_string(legalBalls / 6) + "." + std::to_string(legalBalls % 6);
    }

    double oversDecimal() const {
        return static_cast<double>(legalBalls) / 6.0;
    }
};

// A single ball / event.
struct Delivery {
    int           inningIndex = 0;
    int           strikerId = 0;
    int           bowlerId = 0;
    int           batRuns = 0;      // runs scored off the bat
    ExtraType     extra = ExtraType::None;
    int           extraRuns = 0;    // etc: additional runs (byes/legbyes/run wides)
    bool          wicket = false;
    DismissalType dismissal = DismissalType::NotOut;
    int           dismissedPlayerId = -1;
    int           fielderId = -1;

    // Derived / presentation values.
    int           totalRuns = 0;    // total added to the team score
    bool          legalBall = true;
    std::string   overLabel;        // e.g. "3.4" (over.ball at delivery time)
    std::string   display;          // e.g. "4", "W", "wd", "nb+1", "2b"
};

// One innings (an innings per side).
struct Innings {
    int                     battingTeamId = 0;
    int                     bowlingTeamId = 0;
    std::string             battingTeamName;
    std::string             bowlingTeamName;

    int                     runs = 0;
    int                     wickets = 0;
    int                     legalBalls = 0;

    int                     extrasWides = 0;
    int                     extrasNoBalls = 0;
    int                     extrasByes = 0;
    int                     extrasLegByes = 0;

    int                     maxOvers = 20;
    int                     maxWickets = 10;

    bool                    complete = false;

    std::vector<BatterInnings> batters;
    std::vector<BowlerSpell>   bowlers;
    std::vector<Delivery>      timeline;

    // Crease state: indices into `batters`. -1 means an empty slot.
    int                     strikerIdx = -1;
    int                     nonStrikerIdx = -1;
    int                     currentBowlerIdx = -1;

    // The bowler who delivered the most recently completed over. A bowler may
    // not bowl two consecutive overs.
    int                     lastOverBowlerId = -1;

    // Number of deliveries (legal + illegal) sent down in the current over.
    // Used purely for over.ball display notation.
    int                     ballsThisOver = 0;

    // Set true when a new over has started and a bowler must be supplied.
    bool                    needsNewBowler = true;

    // Set true when a wicket fell and the incoming batter must be supplied.
    bool                    needsNewBatter = false;

    // Target for the chasing side (0 for the first innings).
    int                     target = 0;

    std::string oversText() const {
        return std::to_string(legalBalls / 6) + "." + std::to_string(legalBalls % 6);
    }

    int totalExtras() const {
        return extrasWides + extrasNoBalls + extrasByes + extrasLegByes;
    }
};

// The complete persisted state of a match.
struct MatchState {
    int         schemaVersion = 1;
    std::string id;
    std::string date;
    std::string venue;
    int         totalOvers = 20;

    std::string tossWinner;    // team name
    std::string battingChoice; // "bat" or "bowl"

    Team        teamA;
    Team        teamB;
    Team        battingFirst;
    Team        bowlingFirst;

    Innings     innings[2];
    int         currentInnings = 0;   // 0 or 1
    int         inningsCount = 0;     // how many innings have started (1 or 2)
    bool        matchComplete = false;
    std::string resultText;
    bool        started = false;
};

} // namespace ef101

#endif // EF101_MATCH_STATE_H
