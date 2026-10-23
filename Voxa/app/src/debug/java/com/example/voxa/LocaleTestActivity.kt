package com.example.voxa

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.example.voxa.utils.AppLanguage

/** Non-exported debug host: exercise real recreation without changing family settings or setup. */
class LocaleTestActivity : ComponentActivity() {
    companion object {
        const val SETTINGS = "voxa_locale_test"
        var content: (@Composable () -> Unit)? = null
    }
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.localizedContext(newBase, newBase.getSharedPreferences(SETTINGS, MODE_PRIVATE)))
    }
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        super.getSharedPreferences(if (name == "voxa_settings") SETTINGS else name, mode)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { content?.invoke() }
    }
}
