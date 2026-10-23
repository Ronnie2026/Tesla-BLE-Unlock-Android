package com.ronnie.teslakey.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class TeslaProtocolTest {
    @Test
    fun closureStatesDoNotMisrepresentMovingOrUnknownAsClosed() {
        assertEquals("已关闭", VehicleObservation.closureLabel(0))
        assertEquals("已打开", VehicleObservation.closureLabel(1))
        assertEquals("未关严", VehicleObservation.closureLabel(2))
        assertEquals("正在打开", VehicleObservation.closureLabel(5))
        assertEquals("正在关闭", VehicleObservation.closureLabel(6))
        assertEquals("状态未知", VehicleObservation.closureLabel(null))
        assertEquals("状态未知", VehicleObservation.closureLabel(3))
        assertEquals("部分解锁", VehicleObservation.lockLabel(3))
        assertEquals("状态未知", VehicleObservation.lockLabel(null))
    }
    @Test
    fun advertisementNameMatchesOfficialVector() {
        assertEquals(
            "S1a87a5a75f3df858C",
            TeslaProtocol.advertisementName("5YJS0000000000000")
        )
    }
}
