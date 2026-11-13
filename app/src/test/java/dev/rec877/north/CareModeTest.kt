package dev.rec877.north

import org.junit.Assert.*
import org.junit.Test

class CareModeTest {
    private val allowed = setOf("自宅 Wi-Fi")

    @Test fun disabledNeverActivates() {
        assertFalse(shouldActivateCare(false, CareConnection(mobile = true), allowed))
    }
    @Test fun onlyKnownWhitelistedWifiExempts() {
        assertFalse(shouldActivateCare(true, CareConnection(wifi = true, ssid = "自宅 Wi-Fi"), allowed))
        assertTrue(shouldActivateCare(true, CareConnection(wifi = true, ssid = "カフェ"), allowed))
        assertTrue(shouldActivateCare(true, CareConnection(wifi = true, ssid = "自宅 Wi-Fi "), allowed))
    }
    @Test fun mobileUnknownOfflineAndVpnStayProtected() {
        listOf(CareConnection(), CareConnection(wifi = true), CareConnection(mobile = true),
            CareConnection(wifi = true, ssid = "自宅 Wi-Fi", mobile = true),
            CareConnection(wifi = true, ssid = "自宅 Wi-Fi", vpn = true)).forEach {
            assertTrue(shouldActivateCare(true, it, allowed))
        }
    }
    @Test fun emptyWhitelistNeverExempts() {
        assertTrue(shouldActivateCare(true, CareConnection(wifi = true, ssid = "自宅 Wi-Fi"), emptySet()))
    }
    @Test fun animatedImageContainersAreNotTreatedAsStatic() {
        assertTrue(OnDeviceMediaClassifier.isAnimatedContainer("GIF89a".toByteArray()))
        val webp = ByteArray(21)
        "WEBPVP8X".toByteArray().copyInto(webp, 8)
        webp[20] = 2
        assertTrue(OnDeviceMediaClassifier.isAnimatedContainer(webp))
        webp[20] = 0
        assertFalse(OnDeviceMediaClassifier.isAnimatedContainer(webp))
        assertFalse(OnDeviceMediaClassifier.isAnimatedContainer(byteArrayOf()))
    }
}
