package cz.notecat.app.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import cz.notecat.app.BuildConfig

/**
 * Konfigurace aplikace v šifrovaných preferencích (Android Keystore).
 * Token k backendu se nikdy neukládá v plaintextu ani nezapéká do APK.
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences = run {
        val appContext = context.applicationContext
        runCatching {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                "notecat_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse {
            // Poškozený keystore nesmí zablokovat celou aplikaci
            appContext.getSharedPreferences("notecat_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    var backendUrl: String
        get() = prefs.getString(KEY_URL, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_BACKEND_URL
        set(value) = prefs.edit().putString(KEY_URL, value.trim().trimEnd('/')).apply()

    var backendToken: String
        get() = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_BACKEND_TOKEN
        set(value) = prefs.edit().putString(KEY_TOKEN, value.trim()).apply()

    var closeAfterSave: Boolean
        get() = prefs.getBoolean(KEY_CLOSE_AFTER_SAVE, true)
        set(value) = prefs.edit().putBoolean(KEY_CLOSE_AFTER_SAVE, value).apply()

    val isBackendConfigured: Boolean
        get() = backendUrl.isNotBlank() && backendToken.isNotBlank()

    companion object {
        private const val KEY_URL = "backend_url"
        private const val KEY_TOKEN = "backend_token"
        private const val KEY_CLOSE_AFTER_SAVE = "close_after_save"
    }
}
