package com.magicsmart.core.engine

import com.magicsmart.core.DeviceSnapshot
import com.magicsmart.core.ResultStatus
import com.magicsmart.core.SettingReading
import com.magicsmart.core.SettingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestDefinitionTest {
    private val off = SettingReading(SettingState.DISABLED)
    private fun snap(tv: Boolean, sdk: Int) = DeviceSnapshot("M", "B", "X", "14", sdk, tv, off, off, off)
    private fun def(target: DeviceTarget = DeviceTarget.ANY, min: Int = 1, max: Int = Int.MAX_VALUE,
                    cond: (DeviceSnapshot) -> Boolean = { true }) =
        TestDefinition(
            id = "T", name = "n", description = "d", engine = Engine.ADB, method = "m", expected = "e",
            minApi = min, maxApi = max, target = target, condition = cond
        ) { TestOutcome(ResultStatus.SUCCESS, "ok") }

    @Test fun target_filtering() {
        assertTrue(def(DeviceTarget.TV).appliesTo(snap(true, 34)))
        assertFalse(def(DeviceTarget.TV).appliesTo(snap(false, 34)))
        assertTrue(def(DeviceTarget.PHONE).appliesTo(snap(false, 34)))
    }

    @Test fun api_range_and_condition() {
        assertFalse(def(min = 30).appliesTo(snap(true, 29)))
        assertTrue(def(min = 30, max = 34).appliesTo(snap(true, 34)))
        assertFalse(def(cond = { false }).appliesTo(snap(true, 34)))
    }

    @Test fun engine_never_runs_same_id_twice_in_registry() {
        val a = def(); val b = def()
        val engine = TestEngine(listOf(a, b))
        assertEquals(1, engine.automatic(snap(true, 34)).size)
    }
}
