package com.magicsmart.core.oem

import com.magicsmart.core.DeviceSnapshot
import com.magicsmart.core.SettingReading
import com.magicsmart.core.SettingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OemProfileTest {
    private val off = SettingReading(SettingState.DISABLED)
    private fun snap(man: String, brand: String = "", model: String = "M", hw: String = "", tv: Boolean = true, sdk: Int = 34) =
        DeviceSnapshot(man, brand, model, "14", sdk, tv, off, off, off, hardware = hw)

    private fun profile(id: String, man: List<String> = emptyList(), brands: List<String> = emptyList(),
                        hw: Regex? = null, tvOnly: Boolean = false) =
        OemProfile(id, id, "", man, brands, null, hw, tvOnly, 1, 999, emptyList(), emptyList(), emptyList())

    @Test fun manufacturer_match_ignores_case() {
        assertNotNull(profile("g", man = listOf("GDYM")).matchReason(snap("gdym")))
    }

    @Test fun brand_and_hardware_matching() {
        assertNotNull(profile("b", brands = listOf("MAGIC")).matchReason(snap("X", brand = "magic")))
        assertNotNull(profile("h", hw = Regex("(?i)amlogic"), tvOnly = true).matchReason(snap("X", hw = "Amlogic")))
        assertNull(profile("h", hw = Regex("(?i)amlogic"), tvOnly = true).matchReason(snap("X", hw = "amlogic", tv = false)))
    }

    @Test fun profile_without_matchers_never_matches() {
        assertNull(profile("generic").matchReason(snap("GDYM")))
    }

    @Test fun select_falls_back_to_generic() {
        val load = OemLoad(listOf(profile("generic"), profile("gdym", man = listOf("GDYM"))), emptyList(), emptyList())
        assertEquals("gdym", OemProfiles.select(load, snap("GDYM")).single().first.id)
        assertEquals("generic", OemProfiles.select(load, snap("Other")).single().first.id)
    }
}
