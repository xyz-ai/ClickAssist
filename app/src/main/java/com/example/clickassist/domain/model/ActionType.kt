package com.example.clickassist.domain.model

enum class ActionType(
    val storageValue: String,
) {
    TAP("TAP"),
    LONG_PRESS("LONG_PRESS"),
    SWIPE("SWIPE"),
    WAIT("WAIT");

    companion object {
        fun fromStorage(value: String): ActionType {
            return entries.firstOrNull { it.storageValue == value } ?: TAP
        }
    }
}
