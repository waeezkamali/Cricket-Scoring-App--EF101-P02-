// JniBridge.cpp
// ---------------------------------------------------------------------------
// JNI glue between the Kotlin NativeEngine wrapper and the C++ CricketEngine.
//
// Engines are held in a process-wide registry keyed by an opaque jlong handle.
// String payloads (team rosters, JSON snapshots) are passed as plain strings
// to avoid the overhead of constructing Java collections from native code.
// ---------------------------------------------------------------------------
#include <jni.h>
#include <android/log.h>

#include <string>
#include <vector>
#include <mutex>
#include <unordered_map>
#include <atomic>

#include "CricketEngine.h"

#define LOG_TAG "CricketEngineJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using ef101::CricketEngine;
using ef101::Team;
using ef101::Player;
using ef101::BallInput;
using ef101::ExtraType;
using ef101::DismissalType;

namespace {

std::mutex g_mutex;
std::unordered_map<jlong, CricketEngine*> g_engines;
std::atomic<jlong> g_nextHandle{1};

std::string jstringToStd(JNIEnv* env, jstring s) {
    if (s == nullptr) return std::string();
    const char* chars = env->GetStringUTFChars(s, nullptr);
    std::string result(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(s, chars);
    return result;
}

jstring stdToJstring(JNIEnv* env, const std::string& s) {
    return env->NewStringUTF(s.c_str());
}

CricketEngine* getEngine(jlong handle) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_engines.find(handle);
    if (it == g_engines.end()) return nullptr;
    return it->second;
}

// Parse a roster string of the form "id|name\nid|name\n...".
Team parseTeam(int teamId, const std::string& name, const std::string& roster) {
    Team t;
    t.id = teamId;
    t.name = name;
    size_t start = 0;
    while (start < roster.size()) {
        size_t nl = roster.find('\n', start);
        std::string line = roster.substr(
            start, nl == std::string::npos ? std::string::npos : nl - start);
        if (!line.empty()) {
            size_t bar = line.find('|');
            Player p;
            if (bar == std::string::npos) {
                p.name = line;
            } else {
                try { p.id = std::stoi(line.substr(0, bar)); } catch (...) { p.id = 0; }
                p.name = line.substr(bar + 1);
            }
            t.players.push_back(p);
        }
        if (nl == std::string::npos) break;
        start = nl + 1;
    }
    return t;
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_ef101_p02_cricketscorer_engine_NativeEngine_nativeCreate(JNIEnv*, jobject) {
    auto* engine = new CricketEngine();
    jlong handle = g_nextHandle.fetch_add(1);
    std::lock_guard<std::mutex> lock(g_mutex);
    g_engines[handle] = engine;
    return handle;
}

JNIEXPORT void JNICALL
Java_com_ef101_p02_cricketscorer_engine_NativeEngine_nativeDestroy(JNIEnv*, jobject,
                                                                   jlong handle) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_engines.find(handle);
    if (it != g_engines.end()) {
        delete it->second;
        g_engines.erase(it);
    }
}

JNIEXPORT void JNICALL
Java_com_ef101_p02_cricketscorer_engine_NativeEngine_nativeSetMatchId(
        JNIEnv* env, jobject, jlong handle, jstring id) {
    CricketEngine* e = getEngine(handle);
    if (e) e->match.id = jstringToStd(env, id);
}

JNIEXPORT void JNICALL
Java_com_ef101_p02_cricketscorer_engine_NativeEngine_nativeConfigure(
        JNIEnv* env, jobject, jlong handle,
        jint teamAId, jstring teamAName, jstring teamARoster,
        jint teamBId, jstring teamBName, jstring teamBRoster,
        jstring date, jstring venue, jint overs, jstring tossWinner,
        jstring battingChoice) {
    CricketEngine* e = getEngine(handle);
    if (!e) return;
    Team a = parseTeam(teamAId, jstringToStd(env, teamAName),
                       jstringToStd(env, teamARoster));
    Team b = parseTeam(teamBId, jstringToStd(env, teamBName),
                       jstringToStd(env, teamBRoster));
    e->configure(a, b,
                 jstringToStd(env, date),
                 jstringToStd(env, venue),
                 overs,
                 jstringToStd(env, tossWinner),
                 jstringToStd(env, battingChoice));
}

JNIEXPORT jstring JNICALL
Java_com_ef101_p02_cricketscorer_engine_NativeEngine_nativeValidationMessage(
        JNIEnv* env, jobject, jlong handle) {
    CricketEngine* e = getEngine(handle);
    if (!e) return stdToJstring(env, "Engine not found.");
    return stdToJstring(env, e->validationMessage());
}

JNIEXPORT jboolean JNICALL
Java_com_ef101_p02_cricketscorer_engine_NativeEngine_nativeCanStart(
        JNIEnv*, jobject, jlong handle) {
    CricketEngine* e = getEngine(handle);
    return (e && e->canStart()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_ef101_p02_cricketscorer_engine_NativeEngine_nativeStartMatch(
        JNIEnv*, jobject, jlong handle, jint strikerId, jint nonStrikerId,
        jint bowlerId) {
    CricketEngine* e = getEngine(handle);
    if (!e) return JNI_FALSE;
    return e->startMatch(strikerId, nonStrikerId, bowlerId) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_ef101_p02_cricketscorer_engine_NativeEngine_nativeStartSecondInnings(
        JNIEnv*, jobject, jlong handle, jint strikerId, jint nonStrikerId,
        jint bowlerId) {
    CricketEngine* e = getEngine(handle);
    if (!e) return JNI_FALSE;
    return e->startSecondInnings(strikerId, nonStrikerId, bowlerId) ? JNI_TRUE
                                                                    : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_ef101_p02_cricketscorer_engine_NativeEngine_nativeRecordDelivery(
        JNIEnv* env, jobject, jlong handle, jint batRuns, jstring extraType,
        jint extraRuns, jboolean wicket, jstring dismissal, jint dismissedId,
        jint fielderId) {
    CricketEngine* e = getEngine(handle);
    if (!e) return stdToJstring(env, "Engine not found.");

    BallInput in;
    in.batRuns = batRuns;
    in.extra = ef101::extraTypeFromString(jstringToStd(env, extraType));
    in.extraRuns = extraRuns;
    in.wicket = wicket == JNI_TRUE;
    in.dismissal = ef101::dismissalTypeFromString(jstringToStd(env, dismissal));
    in.dismissedPlayerId = dismissedId;
    in.fielderId = fielderId;

    std::string err = e->recordDelivery(in);
    return stdToJstring(env, err);
}

JNIEXPORT jstring JNICALL
Java_com_ef101_p02_cricketscorer_engine_NativeEngine_nativeSetNextBowler(
        JNIEnv* env, jobject, jlong handle, jint bowlerId) {
    CricketEngine* e = getEngine(handle);
    if (!e) return stdToJstring(env, "Engine not found.");
    return stdToJstring(env, e->setNextBowler(bowlerId));
}

JNIEXPORT jstring JNICALL
Java_com_ef101_p02_cricketscorer_engine_NativeEngine_nativeSetNewBatter(
        JNIEnv* env, jobject, jlong handle, jint playerId) {
    CricketEngine* e = getEngine(handle);
    if (!e) return stdToJstring(env, "Engine not found.");
    return stdToJstring(env, e->setNewBatter(playerId));
}

JNIEXPORT jstring JNICALL
Java_com_ef101_p02_cricketscorer_engine_NativeEngine_nativeGetStateJson(
        JNIEnv* env, jobject, jlong handle) {
    CricketEngine* e = getEngine(handle);
    if (!e) return stdToJstring(env, "{}");
    return stdToJstring(env, e->toJson());
}

JNIEXPORT jstring JNICALL
Java_com_ef101_p02_cricketscorer_engine_NativeEngine_nativeGetScorecardJson(
        JNIEnv* env, jobject, jlong handle) {
    CricketEngine* e = getEngine(handle);
    if (!e) return stdToJstring(env, "{}");
    return stdToJstring(env, e->scorecardJson());
}

JNIEXPORT jstring JNICALL
Java_com_ef101_p02_cricketscorer_engine_NativeEngine_nativeSerialize(
        JNIEnv* env, jobject, jlong handle) {
    CricketEngine* e = getEngine(handle);
    if (!e) return stdToJstring(env, "");
    return stdToJstring(env, e->serialize());
}

JNIEXPORT jstring JNICALL
Java_com_ef101_p02_cricketscorer_engine_NativeEngine_nativeSaveToFile(
        JNIEnv* env, jobject, jlong handle, jstring path) {
    CricketEngine* e = getEngine(handle);
    if (!e) return stdToJstring(env, "Engine not found.");
    return stdToJstring(env, e->saveToFile(jstringToStd(env, path)));
}

JNIEXPORT jstring JNICALL
Java_com_ef101_p02_cricketscorer_engine_NativeEngine_nativeLoadFromFile(
        JNIEnv* env, jobject, jlong handle, jstring path) {
    CricketEngine* e = getEngine(handle);
    if (!e) return stdToJstring(env, "Engine not found.");
    return stdToJstring(env, e->loadFromFile(jstringToStd(env, path)));
}

JNIEXPORT jstring JNICALL
Java_com_ef101_p02_cricketscorer_engine_NativeEngine_nativeBuildScorecard(
        JNIEnv* env, jobject, jlong handle) {
    CricketEngine* e = getEngine(handle);
    if (!e) return stdToJstring(env, "{}");
    return stdToJstring(env, e->scorecardJson());
}

} // extern "C"
