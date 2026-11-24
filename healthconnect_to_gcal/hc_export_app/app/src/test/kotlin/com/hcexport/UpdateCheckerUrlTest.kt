package com.hcexport

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateCheckerUrlTest {

    /**
     * Pinned: every APK URL must carry the version, or Cloudflare will keep
     * serving the previous build after a publish and the app will "update" to
     * the version it already has, forever.
     */
    @Test
    fun apkUrlCarriesTheVersion() {
        assertEquals(
            "https://downloads.nvlf.uk/hc-sync/app-release.apk?v=42",
            UpdateChecker.apkUrl(42),
        )
    }
}
