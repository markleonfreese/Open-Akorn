package com.openakorn.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrillProfilesTest {
    @Test
    fun codecRoundTripsWifiAndBluetoothProfiles() {
        val profiles = listOf(
            GrillProfile("Back patio", GrillProfileTransport.WIFI_TCP, "192.168.1.50"),
            GrillProfile("Garage smoker", GrillProfileTransport.BLUETOOTH, "Akorn-1234"),
        )

        assertEquals(profiles, GrillProfileCodec.decode(GrillProfileCodec.encode(profiles)))
    }

    @Test
    fun codecSkipsCorruptInvalidAndDuplicateRecords() {
        val duplicate = listOf(
            GrillProfile("First", GrillProfileTransport.WIFI_TCP, "192.168.1.50"),
            GrillProfile("Duplicate", GrillProfileTransport.WIFI_TCP, "192.168.1.50"),
            GrillProfile("Invalid", GrillProfileTransport.WIFI_TCP, "8.8.8.8"),
        )
        val encoded = GrillProfileCodec.encode(duplicate) + "\nnot-valid-base64|row"

        assertEquals(1, GrillProfileCodec.decode(encoded).size)
        assertEquals("First", GrillProfileCodec.decode(encoded).single().name)
    }

    @Test
    fun profileValidationRequiresLocalEndpointAndSafeLabel() {
        assertTrue(GrillProfile("Kitchen", GrillProfileTransport.WIFI_TCP, "10.0.0.12").isValid())
        assertFalse(GrillProfile("Kitchen", GrillProfileTransport.WIFI_TCP, "203.0.113.8").isValid())
        assertTrue(GrillProfile("Deck", GrillProfileTransport.BLUETOOTH, "Akorn-12AB").isValid())
        assertFalse(GrillProfile("Deck", GrillProfileTransport.BLUETOOTH, "Unknown-device").isValid())
        assertFalse(GrillProfile("\n", GrillProfileTransport.WIFI_TCP, "192.168.1.50").isValid())
    }

    @Test
    fun codecCapsProfilesAtTheSupportedLimit() {
        val profiles = (1..20).map { index ->
            GrillProfile("Grill $index", GrillProfileTransport.WIFI_TCP, "192.168.1.$index")
        }

        assertEquals(LocalGrillProfiles.MAX_PROFILES, GrillProfileCodec.decode(GrillProfileCodec.encode(profiles)).size)
    }
}
