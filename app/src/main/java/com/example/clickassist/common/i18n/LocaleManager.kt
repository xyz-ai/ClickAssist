package com.example.clickassist.common.i18n

import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LocaleManager {
    fun toLocaleList(language: AppLanguage): LocaleListCompat {
        return when (language) {
            AppLanguage.FOLLOW_SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.ENGLISH -> LocaleListCompat.create(Locale.ENGLISH)
            AppLanguage.ZH_CN -> LocaleListCompat.create(Locale.SIMPLIFIED_CHINESE)
        }
    }

    fun applyLanguage(language: AppLanguage): Boolean {
        val targetLocales = toLocaleList(language)
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        Log.i(
            TAG,
            "applyLanguage currentTags=${currentLocales.toLanguageTags()} targetTags=${targetLocales.toLanguageTags()} language=$language",
        )
        if (currentLocales.toLanguageTags() == targetLocales.toLanguageTags()) {
            Log.i(TAG, "skip same language language=$language")
            return false
        }
        AppCompatDelegate.setApplicationLocales(targetLocales)
        Log.i(TAG, "applyLanguage executed language=$language")
        return true
    }

    private const val TAG = "LocaleManager"
}
