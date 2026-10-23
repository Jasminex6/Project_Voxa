package com.example.voxa.data.license

import com.example.voxa.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

object ActivationClient {
    private val demoCode = Regex("VOXA-[FC]-[0-9]{3}-[A-F0-9]{8}", RegexOption.IGNORE_CASE)
    /** Paid exchange codes are single-use; demo pool codes are reusable across test devices. */
    fun parse(input: String): String {
        val value = input.trim()
        if (BuildConfig.DEBUG && demoCode.matches(value)) return value.uppercase(java.util.Locale.ROOT)
        if (value.length <= 8192 && value.matches(Regex("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))) return value
        if (value.matches(Regex("[A-Za-z0-9_-]{32,128}"))) return value
        val uri = URI(value)
        val webHost = runCatching { URI(BuildConfig.VOXA_WEB_URL).host }.getOrNull()
        require((uri.scheme == "voxa" && uri.host == "activate") ||
            (uri.scheme == "https" && uri.host == webHost && uri.path == "/activate")) { "Not a Voxa activation link" }
        val pairs = uri.rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }
        if (pairs.count { it.firstOrNull() == "token" } == 1 && pairs.none { it.firstOrNull() == "code" }) {
            val token = pairs.first { it[0] == "token" }.getOrNull(1).orEmpty()
            require(token.length <= 8192 && token.matches(Regex("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))) { "Invalid activation token" }
            return token
        }
        require(pairs.count { it.firstOrNull() == "code" } == 1) { "Invalid activation link" }
        val code = pairs.first { it[0] == "code" }.getOrNull(1).orEmpty()
        require(code.matches(Regex("[A-Za-z0-9_-]{32,128}")) || (BuildConfig.DEBUG && demoCode.matches(code))) { "Invalid activation code" }
        return if (demoCode.matches(code)) code.uppercase(java.util.Locale.ROOT) else code
    }

    suspend fun redeem(codeOrToken: String, manager: LicenseManager): ActiveLicense = withContext(Dispatchers.IO) {
        val input = parse(codeOrToken)
        // Signed tokens can be pasted and verified fully offline after device pairing.
        if (input.count { it == '.' } == 2 && !input.contains("://")) return@withContext manager.activate(input)
        val code = input
        val base = URI(BuildConfig.VOXA_WEB_URL)
        require(base.host != "voxa.invalid" && (base.scheme == "https" || (BuildConfig.DEBUG && base.host in listOf("10.0.2.2", "127.0.0.1", "localhost")))) {
            "Activation server is not configured"
        }
        val connection = base.resolve("/api/license/activate").toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000; connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(JSONObject().put("code", code).put("deviceId", manager.deviceId).toString().toByteArray()) }
            require(connection.responseCode == 200) { "Activation could not be verified. Check the code and connection." }
            val bytes = connection.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    require(output.size() + read <= 16384) { "Invalid activation response" }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            require(bytes.size <= 16384) { "Invalid activation response" }
            manager.activate(JSONObject(String(bytes, Charsets.UTF_8)).getString("token"))
        } finally { connection.disconnect() }
    }
}
