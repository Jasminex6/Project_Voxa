package com.example.voxa.data.license

enum class LicenseTier { FREE, FAMILY_PRO, CLINICAL_SLP }
enum class LicenseStatus { FREE, ACTIVE, GRACE, EXPIRED, CLOCK_INVALID }

data class ActiveLicense(
    val tier: LicenseTier = LicenseTier.FREE,
    val licenseKey: String = "",
    val expiresAt: Long = 0,
    val graceUntil: Long = 0,
    val issuedAt: Long = 0,
    val deviceId: String = "",
    val allowedDevices: Int = 1,
    val status: LicenseStatus = LicenseStatus.FREE
) {
    val effectiveTier: LicenseTier
        get() = if (status == LicenseStatus.ACTIVE || status == LicenseStatus.GRACE) tier else LicenseTier.FREE
    val canSendProfiles: Boolean get() = effectiveTier != LicenseTier.FREE
    // Core voice features retain the unrestricted behavior of justinbeiber.
    val hasFullPractice: Boolean get() = true
    val canExportProgress: Boolean get() = effectiveTier != LicenseTier.FREE
    fun canCreateProfile(count: Int): Boolean = count < when (effectiveTier) {
        LicenseTier.FREE -> 1
        LicenseTier.FAMILY_PRO -> Int.MAX_VALUE
        LicenseTier.CLINICAL_SLP -> Int.MAX_VALUE
    }
    fun canEnrollIntent(@Suppress("UNUSED_PARAMETER") count: Int): Boolean = true
}
