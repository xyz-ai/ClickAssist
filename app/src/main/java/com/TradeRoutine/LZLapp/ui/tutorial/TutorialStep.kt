package com.TradeRoutine.LZLapp.ui.tutorial

import androidx.annotation.StringRes

enum class TutorialPlacement {
    ABOVE,
    BELOW,
    LEFT,
    RIGHT,
}

enum class TutorialStepKind {
    INFO,
    ACTION_OPTIONAL,
}

enum class TutorialActionKey {
    ADD_NODE,
    TARGET_MARKER,
    START,
    HANDLE,
}

data class TutorialStep(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val placement: TutorialPlacement,
    val kind: TutorialStepKind = TutorialStepKind.INFO,
    val actionKey: TutorialActionKey? = null,
)

object TutorialAnchorKeys {
    const val TOOLBAR_MAIN = "toolbar_main"
    const val TOOLBAR_ADD_NODE = "toolbar_add_node"
    const val TOOLBAR_START = "toolbar_start"
    const val TOOLBAR_SETTINGS = "toolbar_settings"
    const val TOOLBAR_HANDLE = "toolbar_handle"
    const val TARGET_MARKER = "target_marker"
}
