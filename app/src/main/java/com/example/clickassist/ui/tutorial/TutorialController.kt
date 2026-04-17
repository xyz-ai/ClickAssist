package com.example.clickassist.ui.tutorial

import android.graphics.RectF
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TutorialController {
    private val _anchors = MutableStateFlow<Map<String, RectF>>(emptyMap())
    val anchors: StateFlow<Map<String, RectF>> = _anchors.asStateFlow()

    fun updateAnchor(
        key: String,
        rect: RectF,
    ) {
        _anchors.value = _anchors.value.toMutableMap().apply {
            put(key, RectF(rect))
        }
    }

    fun removeAnchor(key: String) {
        if (!_anchors.value.containsKey(key)) return
        _anchors.value = _anchors.value.toMutableMap().apply {
            remove(key)
        }
    }

    fun clearAnchors() {
        _anchors.value = emptyMap()
    }

    fun getAnchor(key: String): RectF? = _anchors.value[key]?.let(::RectF)
}
