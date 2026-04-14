package com.example.clickassist.common.i18n

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

    fun applyLanguage(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(toLocaleList(language))
    }
}
