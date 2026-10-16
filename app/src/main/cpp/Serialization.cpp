// Serialization.cpp
// ---------------------------------------------------------------------------
// JSON output for the UI + compact line-based persistence for offline storage.
// ---------------------------------------------------------------------------
#include "CricketEngine.h"

#include <fstream>
#include <sstream>
#include <cstdio>
#include <algorithm>

namespace ef101 {

namespace {

int toInt(const std::string& s, int def = 0) {
    try { return std::stoi(s); } catch (...) { return def; }
}

std::string jsonEscape(const std::string& s) {
    std::string o;
    o.reserve(s.size() + 8);
    for (char c : s) {
        switch (c) {
            case '"':  o += "\\\""; break;
            case '\\': o += "\\\\"; break;
            case '\n': o += "\\n";  break;
            case '\r': o += "\\r";  break;
            case '\t': o += "\\t";  break;
            default:
                if (static_cast<unsigned char>(c) < 0x20) {
                    char buf[8];
                    std::snprintf(buf, sizeof(buf), "\\u%04x",
                                  static_cast<unsigned char>(c));
                    o += buf;
                } else {
                    o += c;
                }
        }
    }
    return o;
}

std::string jstr(const std::string& s) { return "\"" + jsonEscape(s) + "\""; }

std::string esc(const std::string& s) {
    std::string o;
    o.reserve(s.size() + 8);
    for (char c : s) {
        switch (c) {
            case '\\': o += "\\\\"; break;
            case '|':  o += "\\p";  break;
            case '\n': o += "\\n";  break;
            case '\r': o += "\\r";  break;
            default:   o += c;
        }
    }
    return o;
}

std::string unesc(const std::string& s) {
    std::string o;
    o.reserve(s.size());
    for (size_t i = 0; i < s.size(); ++i) {
        if (s[i] == '\\' && i + 1 < s.size()) {
            char n = s[++i];
            if (n == 'n')       o += '\n';
            else if (n == 'r')  o += '\r';
            else if (n == 'p')  o += '|';
            else                o += n;
        } else {
            o += s[i];
        }
    }
    return o;
}

std::vector<std::string> splitPipe(const std::string& s) {
    std::vector<std::string> out;
    std::string cur;
    for (char c : s) {
        if (c == '|') { out.push_back(cur); cur.clear(); }
        else cur += c;
    }
    out.push_back(cur);
    return out;
}

std::string fmt1(double v) {
    char buf[32];
    std::snprintf(buf, sizeof(buf), "%.2f", v);
    return std::string(buf);
}

std::string extrasJson(int w, int nb, int b, int lb) {
    std::ostringstream os;
    os << "{\"wides\":" << w << ",\"noBalls\":" << nb
       << ",\"byes\":" << b << ",\"legByes\":" << lb
       << ",\"total\":" << (w + nb + b + lb) << "}";
    return os.str();
}

} // namespace

// ---------------------------------------------------------------------------
// JSON: live state snapshot
// ---------------------------------------------------------------------------
std::string CricketEngine::toJson() const {
    const Innings& inn = current();
    std::ostringstream os;

    os << "{";
    os << "\"ok\":true,";
    os << "\"id\":" << jstr(match.id) << ",";
    os << "\"date\":" << jstr(match.date) << ",";
    os << "\"venue\":" << jstr(match.venue) << ",";
    os << "\"totalOvers\":" << match.totalOvers << ",";
    os << "\"battingTeam\":" << jstr(inn.battingTeamName) << ",";
    os << "\"bowlingTeam\":" << jstr(inn.bowlingTeamName) << ",";
    os << "\"runs\":" << inn.runs << ",";
    os << "\"wickets\":" << inn.wickets << ",";
    os << "\"overs\":" << jstr(inn.oversText()) << ",";
    os << "\"legalBalls\":" << inn.legalBalls << ",";
    os << "\"extras\":" << extrasJson(inn.extrasWides, inn.extrasNoBalls,
                                      inn.extrasByes, inn.extrasLegByes) << ",";
    os << "\"target\":" << inn.target << ",";
    os << "\"currentInnings\":" << match.currentInnings << ",";
    os << "\"inningsCount\":" << match.inningsCount << ",";
    os << "\"started\":" << (match.started ? "true" : "false") << ",";
    os << "\"needsNewBowler\":" << (inn.needsNewBowler ? "true" : "false") << ",";
    os << "\"needsNewBatter\":" << (inn.needsNewBatter ? "true" : "false") << ",";
    os << "\"inningsComplete\":" << (inn.complete ? "true" : "false") << ",";
    os << "\"matchComplete\":" << (match.matchComplete ? "true" : "false") << ",";
    os << "\"result\":" << jstr(match.resultText) << ",";

    auto batterJson = [&](int idx) -> std::string {
        if (idx < 0 || idx >= static_cast<int>(inn.batters.size())) {
            return "null";
        }
        const BatterInnings& b = inn.batters[idx];
        std::ostringstream bs;
        bs << "{\"id\":" << b.playerId
           << ",\"name\":" << jstr(b.name)
           << ",\"runs\":" << b.runs
           << ",\"balls\":" << b.ballsFaced
           << ",\"fours\":" << b.fours
           << ",\"sixes\":" << b.sixes
           << ",\"out\":" << (b.out ? "true" : "false")
           << ",\"howOut\":" << jstr(dismissalTypeToString(b.howOut)) << "}";
        return bs.str();
    };

    os << "\"striker\":" << batterJson(inn.strikerIdx) << ",";
    os << "\"nonStriker\":" << batterJson(inn.nonStrikerIdx) << ",";

    if (inn.currentBowlerIdx >= 0 &&
        inn.currentBowlerIdx < static_cast<int>(inn.bowlers.size())) {
        const BowlerSpell& bw = inn.bowlers[inn.currentBowlerIdx];
        os << "\"bowler\":{\"id\":" << bw.playerId
           << ",\"name\":" << jstr(bw.name)
           << ",\"overs\":" << jstr(bw.oversText())
           << ",\"runs\":" << bw.runsConceded
           << ",\"wickets\":" << bw.wickets
           << ",\"maidens\":" << bw.maidens << "},";
    } else {
        os << "\"bowler\":null,";
    }

    os << "\"lastOverBowlerId\":" << inn.lastOverBowlerId << ",";

    // Deliveries in the current over, oldest first.
    int curOver = inn.legalBalls / 6;
    std::vector<std::string> thisOver;
    for (int i = static_cast<int>(inn.timeline.size()) - 1; i >= 0; --i) {
        const Delivery& d = inn.timeline[i];
        std::string label = d.overLabel;
        size_t dot = label.find('.');
        int overNo = toInt(dot == std::string::npos ? label : label.substr(0, dot));
        if (overNo != curOver) break;
        thisOver.push_back(d.display);
    }
    std::reverse(thisOver.begin(), thisOver.end());
    os << "\"thisOver\":[";
    for (size_t i = 0; i < thisOver.size(); ++i) {
        if (i) os << ",";
        os << jstr(thisOver[i]);
    }
    os << "]";

    // Players who have already batted (used to exclude them from the
    // incoming-batter chooser).
    os << ",\"battedIds\":[";
    for (size_t i = 0; i < inn.batters.size(); ++i) {
        if (i) os << ",";
        os << inn.batters[i].playerId;
    }
    os << "]";

    // Rosters so the UI can offer batter / bowler selection (also for restored
    // matches, which do not otherwise consult the local database).
    auto teamJson = [&](const Team& t) -> std::string {
        std::ostringstream ts;
        ts << "{\"id\":" << t.id << ",\"name\":" << jstr(t.name) << ",\"players\":[";
        for (size_t i = 0; i < t.players.size(); ++i) {
            if (i) ts << ",";
            ts << "{\"id\":" << t.players[i].id
               << ",\"name\":" << jstr(t.players[i].name) << "}";
        }
        ts << "]}";
        return ts.str();
    };
    os << ",\"battingTeamId\":" << inn.battingTeamId;
    os << ",\"bowlingTeamId\":" << inn.bowlingTeamId;
    os << ",\"teamA\":" << teamJson(match.teamA);
    os << ",\"teamB\":" << teamJson(match.teamB);

    os << "}";
    return os.str();
}

// ---------------------------------------------------------------------------
// JSON: full scorecard
// ---------------------------------------------------------------------------
std::string CricketEngine::scorecardJson() const {
    std::ostringstream os;
    os << "{";
    os << "\"id\":" << jstr(match.id) << ",";
    os << "\"teamA\":" << jstr(match.teamA.name) << ",";
    os << "\"teamB\":" << jstr(match.teamB.name) << ",";
    os << "\"result\":" << jstr(match.resultText) << ",";
    os << "\"matchComplete\":" << (match.matchComplete ? "true" : "false") << ",";
    os << "\"venue\":" << jstr(match.venue) << ",";
    os << "\"date\":" << jstr(match.date) << ",";
    os << "\"innings\":[";

    for (int i = 0; i < match.inningsCount; ++i) {
        const Innings& inn = match.innings[i];
        if (i) os << ",";
        os << "{";
        os << "\"battingTeam\":" << jstr(inn.battingTeamName) << ",";
        os << "\"bowlingTeam\":" << jstr(inn.bowlingTeamName) << ",";
        os << "\"runs\":" << inn.runs << ",";
        os << "\"wickets\":" << inn.wickets << ",";
        os << "\"overs\":" << jstr(inn.oversText()) << ",";
        os << "\"maxOvers\":" << inn.maxOvers << ",";
        os << "\"target\":" << inn.target << ",";
        os << "\"complete\":" << (inn.complete ? "true" : "false") << ",";
        os << "\"extras\":" << extrasJson(inn.extrasWides, inn.extrasNoBalls,
                                          inn.extrasByes, inn.extrasLegByes) << ",";

        os << "\"batters\":[";
        for (size_t b = 0; b < inn.batters.size(); ++b) {
            const BatterInnings& bi = inn.batters[b];
            if (b) os << ",";
            double sr = bi.ballsFaced > 0 ? (100.0 * bi.runs / bi.ballsFaced) : 0.0;
            os << "{"
               << "\"name\":" << jstr(bi.name) << ","
               << "\"runs\":" << bi.runs << ","
               << "\"balls\":" << bi.ballsFaced << ","
               << "\"fours\":" << bi.fours << ","
               << "\"sixes\":" << bi.sixes << ","
               << "\"strikeRate\":" << fmt1(sr) << ","
               << "\"out\":" << (bi.out ? "true" : "false") << ","
               << "\"howOut\":" << jstr(dismissalTypeToString(bi.howOut)) << ","
               << "\"bowler\":" << jstr(bi.bowlerName) << ","
               << "\"fielder\":" << jstr(bi.fielderName)
               << "}";
        }
        os << "],";

        os << "\"bowlers\":[";
        for (size_t b = 0; b < inn.bowlers.size(); ++b) {
            const BowlerSpell& bs = inn.bowlers[b];
            if (b) os << ",";
            double econ = bs.legalBalls > 0 ? (bs.runsConceded / bs.oversDecimal()) : 0.0;
            os << "{"
               << "\"name\":" << jstr(bs.name) << ","
               << "\"overs\":" << jstr(bs.oversText()) << ","
               << "\"balls\":" << bs.legalBalls << ","
               << "\"runs\":" << bs.runsConceded << ","
               << "\"wickets\":" << bs.wickets << ","
               << "\"maidens\":" << bs.maidens << ","
               << "\"wides\":" << bs.wides << ","
               << "\"noBalls\":" << bs.noBalls << ","
               << "\"economy\":" << fmt1(econ)
               << "}";
        }
        os << "]";

        os << "}";
    }

    os << "]";
    os << "}";
    return os.str();
}

// ---------------------------------------------------------------------------
// Persistence
// ---------------------------------------------------------------------------
std::string CricketEngine::serialize() const {
    std::ostringstream os;
    os << "EF101-P02|1\n";

    os << "S|" << esc(match.id) << "|" << esc(match.date) << "|" << esc(match.venue)
       << "|" << match.totalOvers << "|" << esc(match.tossWinner)
       << "|" << esc(match.battingChoice) << "|" << match.currentInnings
       << "|" << match.inningsCount << "|" << (match.matchComplete ? 1 : 0)
       << "|" << (match.started ? 1 : 0) << "|" << esc(match.resultText) << "\n";

    auto writeTeam = [&](const char* slot, const Team& t) {
        os << "T|" << slot << "|" << t.id << "|" << esc(t.name) << "\n";
        for (const auto& p : t.players) {
            os << "P|" << slot << "|" << p.id << "|" << esc(p.name) << "\n";
        }
    };
    writeTeam("A", match.teamA);
    writeTeam("B", match.teamB);
    writeTeam("F", match.battingFirst);
    writeTeam("G", match.bowlingFirst);

    for (int i = 0; i < 2; ++i) {
        const Innings& inn = match.innings[i];
        os << "I|" << i << "|" << inn.battingTeamId << "|" << inn.bowlingTeamId
           << "|" << esc(inn.battingTeamName) << "|" << esc(inn.bowlingTeamName)
           << "|" << inn.runs << "|" << inn.wickets << "|" << inn.legalBalls
           << "|" << inn.extrasWides << "|" << inn.extrasNoBalls
           << "|" << inn.extrasByes << "|" << inn.extrasLegByes
           << "|" << inn.maxOvers << "|" << inn.maxWickets
           << "|" << (inn.complete ? 1 : 0)
           << "|" << inn.strikerIdx << "|" << inn.nonStrikerIdx
           << "|" << inn.currentBowlerIdx << "|" << inn.lastOverBowlerId
           << "|" << (inn.needsNewBowler ? 1 : 0)
           << "|" << (inn.needsNewBatter ? 1 : 0)
           << "|" << inn.target << "|" << inn.ballsThisOver << "\n";

        for (size_t b = 0; b < inn.batters.size(); ++b) {
            const BatterInnings& bi = inn.batters[b];
            os << "B|" << i << "|" << b << "|" << bi.playerId << "|" << esc(bi.name)
               << "|" << bi.runs << "|" << bi.ballsFaced << "|" << bi.fours
               << "|" << bi.sixes << "|" << (bi.out ? 1 : 0)
               << "|" << static_cast<int>(bi.howOut)
               << "|" << bi.bowlerId << "|" << esc(bi.bowlerName)
               << "|" << bi.fielderId << "|" << esc(bi.fielderName)
               << "|" << (bi.atCrease ? 1 : 0) << "\n";
        }
        for (size_t b = 0; b < inn.bowlers.size(); ++b) {
            const BowlerSpell& bs = inn.bowlers[b];
            os << "O|" << i << "|" << b << "|" << bs.playerId << "|" << esc(bs.name)
               << "|" << bs.legalBalls << "|" << bs.runsConceded << "|" << bs.wickets
               << "|" << bs.maidens << "|" << bs.wides << "|" << bs.noBalls
               << "|" << bs.runsThisOver << "|" << bs.legalThisOver << "\n";
        }
        for (size_t d = 0; d < inn.timeline.size(); ++d) {
            const Delivery& dv = inn.timeline[d];
            os << "D|" << i << "|" << d << "|" << dv.inningIndex << "|" << dv.strikerId
               << "|" << dv.bowlerId << "|" << dv.batRuns
               << "|" << static_cast<int>(dv.extra) << "|" << dv.extraRuns
               << "|" << (dv.wicket ? 1 : 0) << "|" << static_cast<int>(dv.dismissal)
               << "|" << dv.dismissedPlayerId << "|" << dv.fielderId
               << "|" << dv.totalRuns << "|" << (dv.legalBall ? 1 : 0)
               << "|" << esc(dv.overLabel) << "|" << esc(dv.display) << "\n";
        }
    }
    return os.str();
}

bool CricketEngine::deserialize(const std::string& data) {
    MatchState fresh;
    Innings& in0 = fresh.innings[0];
    Innings& in1 = fresh.innings[1];
    (void)in0; (void)in1;

    std::istringstream ss(data);
    std::string line;
    bool first = true;

    auto resetInnings = [&](Innings& inn) {
        inn = Innings();
    };
    resetInnings(fresh.innings[0]);
    resetInnings(fresh.innings[1]);

    while (std::getline(ss, line)) {
        if (!line.empty() && line.back() == '\r') line.pop_back();
        if (line.empty()) continue;
        if (first) {
            // Header line, e.g. "EF101-P02|1"
            first = false;
            if (line.rfind("EF101-P02", 0) != 0) return false;
            continue;
        }
        std::vector<std::string> f = splitPipe(line);
        if (f.empty()) continue;
        const std::string& tag = f[0];

        if (tag == "S" && f.size() >= 12) {
            fresh.id = unesc(f[1]);
            fresh.date = unesc(f[2]);
            fresh.venue = unesc(f[3]);
            fresh.totalOvers = toInt(f[4]);
            fresh.tossWinner = unesc(f[5]);
            fresh.battingChoice = unesc(f[6]);
            fresh.currentInnings = toInt(f[7]);
            fresh.inningsCount = toInt(f[8]);
            fresh.matchComplete = toInt(f[9]) != 0;
            fresh.started = toInt(f[10]) != 0;
            fresh.resultText = unesc(f[11]);
        } else if ((tag == "T" || tag == "P") && f.size() >= 4) {
            const std::string& slot = f[1];
            Team* t = nullptr;
            if (slot == "A") t = &fresh.teamA;
            else if (slot == "B") t = &fresh.teamB;
            else if (slot == "F") t = &fresh.battingFirst;
            else if (slot == "G") t = &fresh.bowlingFirst;
            if (!t) continue;
            if (tag == "T") {
                t->id = toInt(f[2]);
                t->name = unesc(f[3]);
            } else {
                Player p;
                p.id = toInt(f[2]);
                p.name = unesc(f[3]);
                t->players.push_back(p);
            }
        } else if (tag == "I" && f.size() >= 24) {
            int idx = toInt(f[1]);
            if (idx < 0 || idx > 1) continue;
            Innings& inn = fresh.innings[idx];
            inn.battingTeamId = toInt(f[2]);
            inn.bowlingTeamId = toInt(f[3]);
            inn.battingTeamName = unesc(f[4]);
            inn.bowlingTeamName = unesc(f[5]);
            inn.runs = toInt(f[6]);
            inn.wickets = toInt(f[7]);
            inn.legalBalls = toInt(f[8]);
            inn.extrasWides = toInt(f[9]);
            inn.extrasNoBalls = toInt(f[10]);
            inn.extrasByes = toInt(f[11]);
            inn.extrasLegByes = toInt(f[12]);
            inn.maxOvers = toInt(f[13]);
            inn.maxWickets = toInt(f[14]);
            inn.complete = toInt(f[15]) != 0;
            inn.strikerIdx = toInt(f[16], -1);
            inn.nonStrikerIdx = toInt(f[17], -1);
            inn.currentBowlerIdx = toInt(f[18], -1);
            inn.lastOverBowlerId = toInt(f[19], -1);
            inn.needsNewBowler = toInt(f[20]) != 0;
            inn.needsNewBatter = toInt(f[21]) != 0;
            inn.target = toInt(f[22]);
            inn.ballsThisOver = toInt(f[23]);
        } else if (tag == "B" && f.size() >= 16) {
            int idx = toInt(f[1]);
            if (idx < 0 || idx > 1) continue;
            Innings& inn = fresh.innings[idx];
            BatterInnings bi;
            bi.playerId = toInt(f[3]);
            bi.name = unesc(f[4]);
            bi.runs = toInt(f[5]);
            bi.ballsFaced = toInt(f[6]);
            bi.fours = toInt(f[7]);
            bi.sixes = toInt(f[8]);
            bi.out = toInt(f[9]) != 0;
            bi.howOut = static_cast<DismissalType>(toInt(f[10]));
            bi.bowlerId = toInt(f[11], -1);
            bi.bowlerName = unesc(f[12]);
            bi.fielderId = toInt(f[13], -1);
            bi.fielderName = unesc(f[14]);
            bi.atCrease = toInt(f[15]) != 0;
            inn.batters.push_back(bi);
        } else if (tag == "O" && f.size() >= 13) {
            int idx = toInt(f[1]);
            if (idx < 0 || idx > 1) continue;
            Innings& inn = fresh.innings[idx];
            BowlerSpell bs;
            bs.playerId = toInt(f[3]);
            bs.name = unesc(f[4]);
            bs.legalBalls = toInt(f[5]);
            bs.runsConceded = toInt(f[6]);
            bs.wickets = toInt(f[7]);
            bs.maidens = toInt(f[8]);
            bs.wides = toInt(f[9]);
            bs.noBalls = toInt(f[10]);
            bs.runsThisOver = toInt(f[11]);
            bs.legalThisOver = toInt(f[12]);
            inn.bowlers.push_back(bs);
        } else if (tag == "D" && f.size() >= 17) {
            int idx = toInt(f[1]);
            if (idx < 0 || idx > 1) continue;
            Innings& inn = fresh.innings[idx];
            Delivery d;
            d.inningIndex = toInt(f[3]);
            d.strikerId = toInt(f[4]);
            d.bowlerId = toInt(f[5]);
            d.batRuns = toInt(f[6]);
            d.extra = static_cast<ExtraType>(toInt(f[7]));
            d.extraRuns = toInt(f[8]);
            d.wicket = toInt(f[9]) != 0;
            d.dismissal = static_cast<DismissalType>(toInt(f[10]));
            d.dismissedPlayerId = toInt(f[11], -1);
            d.fielderId = toInt(f[12], -1);
            d.totalRuns = toInt(f[13]);
            d.legalBall = toInt(f[14]) != 0;
            d.overLabel = unesc(f[15]);
            d.display = unesc(f[16]);
            inn.timeline.push_back(d);
        }
    }

    match = fresh;
    return true;
}

std::string CricketEngine::saveToFile(const std::string& path) const {
    std::ofstream out(path, std::ios::binary | std::ios::trunc);
    if (!out.is_open()) return "Could not open file for writing: " + path;
    out << serialize();
    if (!out.good()) return "Failed while writing file: " + path;
    out.close();
    return "";
}

std::string CricketEngine::loadFromFile(const std::string& path) {
    std::ifstream in(path, std::ios::binary);
    if (!in.is_open()) return "Could not open file for reading: " + path;
    std::ostringstream ss;
    ss << in.rdbuf();
    if (!deserialize(ss.str())) return "File is corrupt or not a match file.";
    return "";
}

} // namespace ef101
