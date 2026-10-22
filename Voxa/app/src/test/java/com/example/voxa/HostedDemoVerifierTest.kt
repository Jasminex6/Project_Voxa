package com.example.voxa

import com.example.voxa.data.license.*
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Base64

class HostedDemoVerifierTest {
    @Test fun hostedQrTokensUnlockOnlyThePairedInstallationAndChosenPlan() {
        val fixture = JsonParser.parseString(File("../../config/hosted-demo-test-fixture.json").readText()).asJsonObject
        val verifier = CryptoLicenseVerifier(mapOf("hosted-demo-v1" to Base64.getDecoder().decode(fixture["publicKey"].asString)))
        val now = fixture["nowSeconds"].asLong
        for (tier in listOf(LicenseTier.FAMILY_PRO, LicenseTier.CLINICAL_SLP)) {
            val token = fixture["tokens"].asJsonObject[tier.name].asString
            val license = verifier.verify(token, fixture["deviceId"].asString, now)
            assertEquals(tier, license.effectiveTier)
            assertEquals(LicenseStatus.ACTIVE, license.status)
            assertTrue(license.canEnrollIntent(30))
            assertTrue(license.hasFullPractice)
            assertTrue(license.canExportProgress)
            assertThrows(IllegalArgumentException::class.java) { verifier.verify(token, "44444444-4444-4444-8444-444444444444", now) }
            assertThrows(IllegalStateException::class.java) { CryptoLicenseVerifier(emptyMap()).verify(token, fixture["deviceId"].asString, now) }
        }
    }
}
