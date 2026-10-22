package com.example.voxa

import com.example.voxa.data.license.*
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Base64

class LicenseVerifierTest {
    private val fixture = JsonParser.parseString(File("../../config/license-test-fixture.json").readText()).asJsonObject
    private val token = fixture["token"].asString
    private val device = fixture["deviceId"].asString
    private val now = fixture["nowSeconds"].asLong
    private val verifier = CryptoLicenseVerifier(mapOf("demo-v1" to Base64.getDecoder().decode(fixture["publicKey"].asString)))

    @Test fun acceptsServerSignedDeviceBoundLicense() {
        val license = verifier.verify(token, device, now)
        assertEquals(LicenseTier.FAMILY_PRO, license.effectiveTier)
        assertEquals(LicenseStatus.ACTIVE, license.status)
        assertTrue(license.canCreateProfile(1))
    }
    @Test fun rejectsTamperedPayload() {
        val parts = token.split('.').toMutableList()
        val payload = String(Base64.getUrlDecoder().decode(parts[1])).replace("FAMILY_PRO", "CLINICAL_SLP")
        parts[1] = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
        assertThrows(IllegalArgumentException::class.java) { verifier.verify(parts.joinToString("."), device, now) }
    }
    @Test fun rejectsDifferentInstallation() {
        assertThrows(IllegalArgumentException::class.java) { verifier.verify(token, "44444444-4444-4444-8444-444444444444", now) }
    }
    @Test fun graceAndExpiryUseSignedDates() {
        val active = verifier.verify(token, device, now)
        assertEquals(LicenseStatus.GRACE, verifier.verify(token, device, active.expiresAt / 1000 + 1).status)
        assertEquals(LicenseStatus.GRACE, verifier.verify(token, device, active.expiresAt / 1000).status)
        assertThrows(IllegalArgumentException::class.java) { verifier.verify(token, device, active.graceUntil / 1000) }
        assertThrows(IllegalArgumentException::class.java) { verifier.verify(token, device, active.graceUntil / 1000 + 1) }
    }
    @Test fun rejectsFutureIssueDate() {
        assertThrows(IllegalArgumentException::class.java) { verifier.verify(token, device, now - 1000) }
    }
    @Test fun rejectsUntrustedSigner() {
        assertThrows(IllegalStateException::class.java) { CryptoLicenseVerifier(emptyMap()).verify(token, device, now) }
    }
    @Test fun freeLimitsApplyOnlyToNewPremiumActions() {
        val free = ActiveLicense()
        assertTrue(free.canCreateProfile(0)); assertFalse(free.canCreateProfile(1))
        assertTrue(free.canEnrollIntent(2)); assertFalse(free.canEnrollIntent(3))
        assertFalse(free.canEnrollIntent(5))
        assertFalse(free.canSendProfiles)
        val expired = ActiveLicense(tier = LicenseTier.CLINICAL_SLP, status = LicenseStatus.EXPIRED)
        assertEquals(LicenseTier.FREE, expired.effectiveTier)
    }
}
