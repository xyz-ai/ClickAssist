package com.TradeRoutine.LZLapp.common.i18n

enum class AppLanguage(
    val storageValue: String,
) {
    FOLLOW_SYSTEM("follow_system"),
    ENGLISH("english"),
    ZH_CN("zh_cn");

    companion object {
        fun fromStorage(value: String?): AppLanguage {
            return entries.firstOrNull { it.storageValue == value } ?: ENGLISH
        }
    }
}
