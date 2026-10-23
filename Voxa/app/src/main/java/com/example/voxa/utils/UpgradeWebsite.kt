package com.example.voxa.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.voxa.BuildConfig
import com.example.voxa.R

/** Open the plans page in the app's selected language. Checkout stays on the website. */
fun openUpgradeWebsite(context: Context) {
    val language = if (context.resources.configuration.locales[0].language == "ar") "ar" else "en"
    val url = "${BuildConfig.VOXA_WEB_URL.trimEnd('/')}/$language/pricing"
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { Toast.makeText(context, R.string.license_browser_error, Toast.LENGTH_SHORT).show() }
}
