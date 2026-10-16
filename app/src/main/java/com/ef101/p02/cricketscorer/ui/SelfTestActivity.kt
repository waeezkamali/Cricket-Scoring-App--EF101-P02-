package com.ef101.p02.cricketscorer.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ef101.p02.cricketscorer.databinding.ActivitySelfTestBinding
import com.ef101.p02.cricketscorer.engine.NativeEngine
import org.json.JSONObject
import java.io.File

/**
 * Runs the specification's required edge cases directly against the native
 * C++ engine and prints a pass/fail report. This is the on-device test hook:
 *
 *  - an over containing a wide
 *  - a no-ball
 *  - a boundary
 *  - a wicket
 *  - a strike change
 *  - an over ending
 *  - a successful chase
 *  - save / restore across a restart
 */
class SelfTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySelfTestBinding
    private val log = StringBuilder()
    private var checks = 0
    private var failures = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelfTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        runAll()

        val summary = "Checks: $checks    Failures: $failures"
        binding.summaryText.text = summary
        binding.resultText.text = if (failures == 0) "ALL TESTS PASSED" else "FAILURES DETECTED"
        binding.resultText.setTextColor(
            getColor(if (failures == 0) com.ef101.p02.cricketscorer.R.color.success
            else com.ef101.p02.cricketscorer.R.color.danger)
        )
        binding.testOutput.text = log.toString()
    }

    private fun check(cond: Boolean, msg: String) {
        checks++
        if (!cond) failures++
        log.append(if (cond) "  [ok]   " else "  [FAIL] ").append(msg).append('\n')
    }

    private fun section(title: String) {
        log.append("\n").append(title).append('\n')
    }

    private fun spec(id: Int, name: String, ids: IntArray) = NativeEngine.TeamSpec(
        id, name, ids.mapIndexed { i, pid -> NativeEngine.PlayerSpec(pid, "$name P${i + 1}") }
    )

    private fun runAll() {
        log.append("EF101-P02 Cricket Engine — on-device self-test\n")
        testOverEdgeCases()
        testRunOut()
        testChase()
        testAllOut()
        testPersistence()
    }

    private fun testOverEdgeCases() {
        section("Over with wide + no-ball + boundary + strike change + over end")
        val a = spec(1, "TeamA", intArrayOf(1, 2, 3, 4))
        val b = spec(2, "TeamB", intArrayOf(11, 12, 13, 14))

        NativeEngine.create().use { e ->
            e.configure(a, b, "2026-10-16", "Wankhede", 2, "TeamA", "bat")
            check(e.canStart(), "match can start with valid teams")
            check(e.startMatch(1, 2, 11), "match started")

            check(e.recordDelivery(1) == "", "ball 1: single")
            check(e.recordDelivery(0, "Wide") == "", "ball 2: wide (illegal)")
            check(e.recordDelivery(4) == "", "ball 3: FOUR")
            check(e.recordDelivery(1, "NoBall") == "", "ball 4: no-ball + 1")
            check(e.recordDelivery(0, "Bye", 2) == "", "ball 5: 2 byes")
            check(e.recordDelivery(0) == "", "ball 6: dot")
            check(e.recordDelivery(6) == "", "ball 7: SIX")
            check(e.recordDelivery(2) == "", "ball 8: two (ends the over)")

            val s = JSONObject(e.stateJson())
            check(s.optInt("runs") == 18, "team total is 18")
            check(s.optInt("legalBalls") == 6, "6 legal balls only")
            check(s.optString("overs") == "1.0", "over notation is 1.0 (not 8 balls)")
            check(s.optBoolean("needsNewBowler"), "new bowler required after the over")

            check(e.setNextBowler(11) != "", "same bowler blocked consecutively")
            check(e.setNextBowler(12) == "", "different bowler accepted")

            val err = e.recordDelivery(0, "None", 0, true, "Bowled", 2)
            check(err == "", "wicket recorded (bowled)")
            val s2 = JSONObject(e.stateJson())
            check(s2.optInt("wickets") == 1, "one wicket down")
            check(s2.optBoolean("needsNewBatter"), "new batter required")
            check(e.setNewBatter(3) == "", "incoming batter accepted")
        }
    }

    private fun testRunOut() {
        section("Run out is not credited to the bowler")
        val a = spec(1, "TeamA", intArrayOf(1, 2, 3, 4, 5))
        val b = spec(2, "TeamB", intArrayOf(11, 12, 13, 14, 15))
        NativeEngine.create().use { e ->
            e.configure(a, b, "2026-10-16", "Oval", 5, "TeamA", "bat")
            e.startMatch(1, 2, 11)
            e.recordDelivery(0, "None", 0, true, "Bowled", 1)
            e.setNewBatter(3)
            val err = e.recordDelivery(0, "None", 0, true, "Run out", 2, 11)
            check(err == "", "run out recorded")
            val sc = JSONObject(e.scorecardJson())
            val inn = sc.getJSONArray("innings").getJSONObject(0)
            check(inn.optInt("wickets") == 2, "two wickets down")
            val bowlers = inn.getJSONArray("bowlers")
            check(bowlers.getJSONObject(0).optInt("wickets") == 1,
                "run out not credited to bowler")
        }
    }

    private fun testChase() {
        section("Successful chase + result text")
        val a = spec(1, "TeamA", intArrayOf(1, 2, 3, 4))
        val b = spec(2, "TeamB", intArrayOf(11, 12, 13, 14))
        NativeEngine.create().use { e ->
            e.configure(a, b, "2026-10-16", "Eden", 1, "TeamA", "bat")
            e.startMatch(1, 2, 11)
            repeat(6) { e.recordDelivery(1) }
            val s0 = JSONObject(e.stateJson())
            check(s0.optBoolean("inningsComplete"), "first innings complete")
            check(s0.optInt("runs") == 6, "first innings total 6")

            check(e.startSecondInnings(11, 12, 1), "second innings started")
            check(JSONObject(e.stateJson()).optInt("target") == 7, "target is 7")
            e.recordDelivery(4)
            e.recordDelivery(3)
            val s1 = JSONObject(e.stateJson())
            check(s1.optBoolean("matchComplete"), "match complete")
            check(s1.optString("result") == "TeamB won by 3 wickets",
                "result: ${s1.optString("result")}")
        }
    }

    private fun testAllOut() {
        section("All out ends the innings")
        val a = spec(1, "TeamA", intArrayOf(1, 2, 3))
        val b = spec(2, "TeamB", intArrayOf(11, 12, 13))
        NativeEngine.create().use { e ->
            e.configure(a, b, "2026-10-16", "Lord's", 10, "TeamA", "bat")
            e.startMatch(1, 2, 11)
            e.recordDelivery(0, "None", 0, true, "Bowled", 1)
            e.setNewBatter(3)
            e.recordDelivery(0, "None", 0, true, "LBW", 2)
            val s = JSONObject(e.stateJson())
            check(s.optBoolean("inningsComplete"), "innings complete after all out")
            check(s.optInt("wickets") == 2, "2 wickets (3-player team)")
        }
    }

    private fun testPersistence() {
        section("Save / restore across restart")
        val a = spec(1, "TeamA", intArrayOf(1, 2, 3, 4))
        val b = spec(2, "TeamB", intArrayOf(11, 12, 13, 14))
        val file = File(cacheDir, "selftest.match")
        val original = JSONObject("{}")

        NativeEngine.create().use { e ->
            e.configure(a, b, "2026-10-16", "Wankhede", 2, "TeamA", "bat")
            e.startMatch(1, 2, 11)
            e.recordDelivery(1)
            e.recordDelivery(0, "Wide")
            e.recordDelivery(4)
            val before = e.stateJson()
            val err = e.saveToFile(file.absolutePath)
            check(err == "", "save succeeded")
            check(file.exists(), "match file exists on disk")
            original.put("json", before)
        }

        try {
            NativeEngine.fromFile(file.absolutePath).use { restored ->
                val after = restored.stateJson()
                check(after == original.optString("json"),
                    "restored state matches original")
                check(JSONObject(after).optInt("runs") == 6, "score persisted (6)")
            }
        } catch (t: Throwable) {
            check(false, "restore threw: ${t.message}")
        } finally {
            file.delete()
        }
    }
}
