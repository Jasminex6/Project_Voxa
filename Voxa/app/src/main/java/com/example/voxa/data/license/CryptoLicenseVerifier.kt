package com.example.voxa.data.license

import com.google.gson.JsonParser
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.util.Base64
import java.util.UUID

/** JWS verification is entirely local. No signing secret is ever shipped in the APK. */
class CryptoLicenseVerifier(private val trustedKeys: Map<String, ByteArray>) {
    fun verify(token: String, expectedDeviceId: String, nowSeconds: Long): ActiveLicense {
        require(token.length in 1..8192) { "Invalid activation token" }
        val parts = token.split('.')
        require(parts.size == 3 && parts.all { it.matches(Regex("[A-Za-z0-9_-]+")) }) { "Invalid activation token" }
        val decoder = Base64.getUrlDecoder()
        val header = JsonParser.parseString(String(decoder.decode(parts[0]), Charsets.UTF_8)).asJsonObject
        require(header["alg"]?.asString == "EdDSA" && header["typ"]?.asString == "JWT") { "Unsupported signature" }
        val key = trustedKeys[header["kid"]?.asString] ?: error("Untrusted signing key")
        require(key.size == 32) { "Invalid verification key" }
        val signature = decoder.decode(parts[2])
        require(signature.size == 64) { "Invalid signature" }
        val body = "${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII)
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(key, 0))
        verifier.update(body, 0, body.size)
        require(verifier.verifySignature(signature)) { "Activation signature does not match" }
        val claims = JsonParser.parseString(String(decoder.decode(parts[1]), Charsets.UTF_8)).asJsonObject
        fun text(name: String): String {
            val v = claims[name]
            require(v != null && v.isJsonPrimitive && v.asJsonPrimitive.isString) { "Invalid license field" }
            return v.asString
        }
        fun seconds(name: String): Long {
            val v = claims[name]
            require(v != null && v.isJsonPrimitive && v.asJsonPrimitive.isNumber && v.toString().matches(Regex("[0-9]{1,12}"))) { "Invalid license date" }
            return v.asLong
        }
        require(text("iss") == "voxa-license" && text("aud") == "voxa-android") { "License is for another app" }
        UUID.fromString(text("sub")); UUID.fromString(text("jti"))
        val device = text("device_id")
        require(device == expectedDeviceId) { "This activation belongs to a different installation" }
        val licenseKey = text("key")
        require(licenseKey.matches(Regex("VOXA-[A-F0-9]{4}(-[A-F0-9]{4}){3}"))) { "Invalid license key" }
        val tier = LicenseTier.valueOf(text("tier"))
        val issued = seconds("iat")
        val expires = seconds("exp")
        val grace = seconds("grace_until")
        val devices = seconds("devices_allowed")
        require(devices in 1..100 && expires > issued && grace == expires + 7 * 86400) { "Invalid license validity" }
        require(issued <= nowSeconds + 300) { "Check your device date and time" }
        require(nowSeconds < grace) { "This activation has expired" }
        return ActiveLicense(tier, licenseKey, expires * 1000, grace * 1000, issued * 1000, device,
            devices.toInt(), if (nowSeconds >= expires) LicenseStatus.GRACE else LicenseStatus.ACTIVE)
    }
}
