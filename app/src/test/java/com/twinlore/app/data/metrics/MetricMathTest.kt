package com.twinlore.app.data.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricMathTest {

    // ---- CPU clock -------------------------------------------------------

    @Test
    fun `cpu clock is the mean current in GHz`() {
        // 2.0 GHz and 3.0 GHz cores -> mean 2.5 GHz; max 3.0 -> fill 2.5/3.0.
        val s = MetricMath.cpuClock(currentKHz = listOf(2_000_000L, 3_000_000L), maxKHz = listOf(3_000_000L, 3_000_000L))
        assertEquals("2.5", s.display)
        assertEquals(2.5f / 3.0f, s.fraction!!, 1e-3f)
    }

    @Test
    fun `cpu clock unavailable when no cores read`() {
        assertFalse(MetricMath.cpuClock(emptyList(), emptyList()).available)
        assertFalse(MetricMath.cpuClock(listOf(0L), listOf(0L)).available)
    }

    // ---- GPU clock -------------------------------------------------------

    @Test
    fun `gpu clock is MHz with fill over max`() {
        val s = MetricMath.gpuClock(currentHz = 450_000_000L, maxHz = 900_000_000L)
        assertEquals("450", s.display)
        assertEquals(0.5f, s.fraction!!, 1e-3f)
    }

    @Test
    fun `gpu clock still shows a value without a max`() {
        val s = MetricMath.gpuClock(currentHz = 600_000_000L, maxHz = null)
        assertEquals("600", s.display)
        assertTrue(s.available)
    }

    @Test
    fun `gpu clock unavailable when zero`() {
        assertFalse(MetricMath.gpuClock(0L, 900_000_000L).available)
        assertFalse(MetricMath.gpuClock(null, null).available)
    }

    // ---- RAM -------------------------------------------------------------

    @Test
    fun `ram used is GB with fill over total`() {
        // 8 GB total, 3 GB free -> 5 GB used.
        val s = MetricMath.ramUsed(totalBytes = 8_000_000_000L, availableBytes = 3_000_000_000L)
        assertEquals("5.0", s.display)
        assertEquals(5f / 8f, s.fraction!!, 1e-3f)
    }

    @Test
    fun `ram unavailable for nonsense`() {
        assertFalse(MetricMath.ramUsed(0L, 0L).available)
    }

    // ---- power (signed) --------------------------------------------------

    @Test
    fun `discharging power is negative`() {
        // -1.0 A at 3.7 V = -3.7 W.
        val s = MetricMath.powerDraw(currentMicroAmps = -1_000_000L, voltageMilliVolts = 3700)
        assertEquals("-3.7", s.display)
        assertEquals(3.7f / MetricMath.POWER_CEILING_W, s.fraction!!, 1e-3f)
    }

    @Test
    fun `charging power is positive`() {
        val s = MetricMath.powerDraw(currentMicroAmps = 2_000_000L, voltageMilliVolts = 5000)
        assertEquals("10", s.display) // 10 W formats without decimals
        assertTrue(s.available)
    }

    @Test
    fun `implausible power magnitude is rejected`() {
        // milliamp-reporting OEM: 5_000_000 "microamps" that are really mA at 5 V.
        assertFalse(MetricMath.powerDraw(currentMicroAmps = 50_000_000L, voltageMilliVolts = 5000).available)
        assertFalse(MetricMath.powerDraw(currentMicroAmps = 1_000_000L, voltageMilliVolts = 0).available)
    }

    // ---- temperature -----------------------------------------------------

    @Test
    fun `thermal zone millidegrees normalise to celsius`() {
        val s = MetricMath.temperatureFromThermalZone(48_000L)
        assertEquals("48", s.display)
        assertEquals(48f, s.rawCelsius!!, 1e-3f)
        // Fill maps 45..95 -> 0..1, so 48 is near the bottom.
        assertEquals((48f - 45f) / (95f - 45f), s.fraction!!, 1e-3f)
    }

    @Test
    fun `thermal zone decidegrees normalise to celsius`() {
        assertEquals("52", MetricMath.temperatureFromThermalZone(520L).display)
    }

    @Test
    fun `thermal zone plain celsius passes through`() {
        assertEquals("47", MetricMath.temperatureFromThermalZone(47L).display)
    }

    @Test
    fun `implausible temperature is rejected`() {
        assertFalse(MetricMath.temperatureFromThermalZone(45_000_000L).available)
        assertFalse(MetricMath.temperatureFromThermalZone(null).available)
    }

    @Test
    fun `battery temperature is tenths of a degree and flagged estimated`() {
        val s = MetricMath.temperatureFromBattery(480)
        assertEquals("48", s.display)
        assertTrue(s.estimated)
    }

    // ---- thermal zone matching -------------------------------------------

    @Test
    fun `soc thermal zones match, battery and charger do not`() {
        assertTrue(MetricMath.isCpuThermalZone("cpu-1-0"))
        assertTrue(MetricMath.isCpuThermalZone("tsens_tz_sensor0"))
        assertTrue(MetricMath.isCpuThermalZone("soc-thermal"))
        assertFalse(MetricMath.isCpuThermalZone("battery"))
        assertFalse(MetricMath.isCpuThermalZone("pm8998_charger"))
        assertFalse(MetricMath.isCpuThermalZone(null))
    }
}
