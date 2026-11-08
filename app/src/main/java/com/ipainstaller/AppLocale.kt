package com.ipainstaller

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppLanguage(val code: String, val displayName: String) {
    RU("ru", "Русский"),
    EN("en", "English")
}

object AppLocale {

    private const val PREFS_NAME = "app_locale_prefs"
    private const val KEY_LANG = "selected_lang"

    var currentLanguage by mutableStateOf(AppLanguage.RU)
        private set

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_LANG, AppLanguage.RU.code) ?: AppLanguage.RU.code
        currentLanguage = if (code == "en") AppLanguage.EN else AppLanguage.RU
    }

    fun toggleLanguage(context: Context): AppLanguage {
        val next = if (currentLanguage == AppLanguage.RU) AppLanguage.EN else AppLanguage.RU
        setLanguage(context, next)
        return next
    }

    fun setLanguage(context: Context, lang: AppLanguage) {
        currentLanguage = lang
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, lang.code).apply()
    }

    fun t(ru: String, en: String): String {
        return if (currentLanguage == AppLanguage.RU) ru else en
    }
}
