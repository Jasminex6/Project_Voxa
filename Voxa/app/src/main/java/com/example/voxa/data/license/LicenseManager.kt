package com.example.voxa.data.license

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.voxa.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.*
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores a signed, installation-bound license with Android Keystore encryption. */
class LicenseManager private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("voxa_license", Context.MODE_PRIVATE)
    val deviceId: String = runCatching { prefs.getString("device_id", null)?.also { UUID.fromString(it) } }.getOrNull() ?: UUID.randomUUID().toString().also {
        prefs.edit().putString("device_id", it).commit()
    }
    private val verifier = CryptoLicenseVerifier(buildMap {
        if (BuildConfig.LICENSE_PUBLIC_KEY.isNotBlank()) {
            // A bad deployment key must not prevent local communication or SOS from starting.
            runCatching { Base64.decode(BuildConfig.LICENSE_PUBLIC_KEY, Base64.DEFAULT) }.getOrNull()
                ?.takeIf { it.size == 32 }?.let { put(BuildConfig.LICENSE_KEY_ID, it) }
        }
    })
    private val _license = MutableStateFlow(ActiveLicense())
    val license: StateFlow<ActiveLicense> = _license

    init {
        refresh()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            while (isActive) { delay(60_000); refresh() }
        }
    }

    @Synchronized fun activate(token: String): ActiveLicense {
        val now = System.currentTimeMillis()
        val previousMax = runCatching { prefs.getLong("max_seen_time", 0) }.getOrDefault(0)
        require(now >= previousMax - 5 * 60_000) { "Check your device date and time" }
        val verified = verifier.verify(token.trim(), deviceId, now / 1000)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encrypted = cipher.doFinal(token.trim().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("token", Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP))
            .putLong("max_seen_time", maxOf(now, previousMax)).commit()) { "Could not save license" }
        _license.value = verified
        return verified
    }

    @Synchronized fun refresh() {
        val now = System.currentTimeMillis()
        val maxSeen = runCatching { prefs.getLong("max_seen_time", 0) }.getOrDefault(0)
        if (now < maxSeen - 5 * 60_000) {
            _license.value = ActiveLicense(status = LicenseStatus.CLOCK_INVALID)
            return
        }
        prefs.edit().putLong("max_seen_time", maxOf(now, maxSeen)).apply()
        val stored = runCatching { prefs.getString("token", null) }.getOrNull()
        if (stored == null) { _license.value = ActiveLicense(); return }
        _license.value = runCatching {
            require(stored.length <= 16384)
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            require(bytes.size > 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            val token = String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
            verifier.verify(token, deviceId, now / 1000)
        }.getOrElse { ActiveLicense(status = LicenseStatus.EXPIRED) }
    }

    private fun encryptionKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("voxa_license_aes", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("voxa_license_aes", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    companion object {
        @Volatile private var instance: LicenseManager? = null
        fun get(context: Context): LicenseManager = instance ?: synchronized(this) {
            instance ?: LicenseManager(context.applicationContext).also { instance = it }
        }
    }
}
