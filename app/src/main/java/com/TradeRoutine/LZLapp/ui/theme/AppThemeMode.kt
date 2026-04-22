package com.TradeRoutine.LZLapp.ui.theme

enum class AppThemeMode(
    val storageValue: String,
) {
    FOLLOW_SYSTEM("follow_system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorage(value: String?): AppThemeMode {
            return entries.firstOrNull { it.storageValue == value } ?: FOLLOW_SYSTEM
        }
    }
}
