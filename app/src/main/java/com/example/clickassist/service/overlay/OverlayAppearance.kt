package com.example.clickassist.service.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import com.example.clickassist.domain.repository.AppSettings
import kotlin.math.roundToInt

data class OverlayAppearance(
    val toolbarBackgroundColor: Int,
    val toolbarButtonTextColor: Int,
    val toolbarStatusTextColor: Int,
    val panelBackgroundColor: Int,
    val panelBorderColor: Int,
    val surfaceColor: Int,
    val surfaceVariantColor: Int,
    val textPrimaryColor: Int,
    val textSecondaryColor: Int,
    val primaryActionColor: Int,
    val warningActionColor: Int,
    val dangerActionColor: Int,
    val neutralActionColor: Int,
    val markerTapColor: Int,
    val markerLongPressColor: Int,
    val markerSwipeStartColor: Int,
    val markerSwipeEndColor: Int,
    val markerSelectedColor: Int,
    val markerCenterOuterColor: Int,
    val markerCenterInnerColor: Int,
    val swipeLineColor: Int,
    val swipeSelectedLineColor: Int,
    val markerSizePx: Int,
    val swipeLineWidthPx: Float,
    val swipeSelectedLineWidthPx: Float,
    val showMarkerNumbers: Boolean,
    val showMarkerCenterCross: Boolean,
) {
    companion object {
        fun fromSettings(
            context: Context,
            settings: AppSettings,
        ): OverlayAppearance {
            val density = context.resources.displayMetrics.density
            val isDark = when (settings.themeMode) {
                com.example.clickassist.ui.theme.AppThemeMode.FOLLOW_SYSTEM -> {
                    val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    uiMode == Configuration.UI_MODE_NIGHT_YES
                }
                com.example.clickassist.ui.theme.AppThemeMode.DARK -> true
                com.example.clickassist.ui.theme.AppThemeMode.LIGHT -> false
            }

            return if (isDark) {
                OverlayAppearance(
                    toolbarBackgroundColor = Color.parseColor("#E6101A2B"),
                    toolbarButtonTextColor = Color.WHITE,
                    toolbarStatusTextColor = Color.parseColor("#D6E3F5"),
                    panelBackgroundColor = Color.parseColor("#F2101A2B"),
                    panelBorderColor = Color.parseColor("#304155"),
                    surfaceColor = Color.parseColor("#182235"),
                    surfaceVariantColor = Color.parseColor("#223149"),
                    textPrimaryColor = Color.parseColor("#E5EDF8"),
                    textSecondaryColor = Color.parseColor("#B4C1D3"),
                    primaryActionColor = Color.parseColor("#2563EB"),
                    warningActionColor = Color.parseColor("#D97706"),
                    dangerActionColor = Color.parseColor("#B91C1C"),
                    neutralActionColor = Color.parseColor("#0F766E"),
                    markerTapColor = Color.parseColor("#F87171"),
                    markerLongPressColor = Color.parseColor("#C084FC"),
                    markerSwipeStartColor = Color.parseColor("#60A5FA"),
                    markerSwipeEndColor = Color.parseColor("#22D3EE"),
                    markerSelectedColor = Color.parseColor("#FBBF24"),
                    markerCenterOuterColor = Color.parseColor("#0F172A"),
                    markerCenterInnerColor = Color.parseColor("#FDE047"),
                    swipeLineColor = Color.parseColor("#60A5FA"),
                    swipeSelectedLineColor = Color.parseColor("#FBBF24"),
                    markerSizePx = (settings.markerSizeDp * density).roundToInt(),
                    swipeLineWidthPx = settings.swipeLineWidthDp * density,
                    swipeSelectedLineWidthPx = (settings.swipeLineWidthDp * density * 1.4f),
                    showMarkerNumbers = settings.showMarkerNumbers,
                    showMarkerCenterCross = settings.showMarkerCenterCross,
                )
            } else {
                OverlayAppearance(
                    toolbarBackgroundColor = Color.parseColor("#EAFFFFFF"),
                    toolbarButtonTextColor = Color.WHITE,
                    toolbarStatusTextColor = Color.parseColor("#334155"),
                    panelBackgroundColor = Color.parseColor("#FFFFFFFF"),
                    panelBorderColor = Color.parseColor("#D8E1EC"),
                    surfaceColor = Color.parseColor("#FFFFFF"),
                    surfaceVariantColor = Color.parseColor("#F5F7FB"),
                    textPrimaryColor = Color.parseColor("#0F172A"),
                    textSecondaryColor = Color.parseColor("#475569"),
                    primaryActionColor = Color.parseColor("#2563EB"),
                    warningActionColor = Color.parseColor("#D97706"),
                    dangerActionColor = Color.parseColor("#B91C1C"),
                    neutralActionColor = Color.parseColor("#0F766E"),
                    markerTapColor = Color.parseColor("#E53935"),
                    markerLongPressColor = Color.parseColor("#8E24AA"),
                    markerSwipeStartColor = Color.parseColor("#2563EB"),
                    markerSwipeEndColor = Color.parseColor("#0891B2"),
                    markerSelectedColor = Color.parseColor("#F59E0B"),
                    markerCenterOuterColor = Color.parseColor("#111827"),
                    markerCenterInnerColor = Color.parseColor("#FDE047"),
                    swipeLineColor = Color.parseColor("#2563EB"),
                    swipeSelectedLineColor = Color.parseColor("#F59E0B"),
                    markerSizePx = (settings.markerSizeDp * density).roundToInt(),
                    swipeLineWidthPx = settings.swipeLineWidthDp * density,
                    swipeSelectedLineWidthPx = (settings.swipeLineWidthDp * density * 1.4f),
                    showMarkerNumbers = settings.showMarkerNumbers,
                    showMarkerCenterCross = settings.showMarkerCenterCross,
                )
            }
        }
    }
}

interface OverlayStylable {
    fun applyAppearance(appearance: OverlayAppearance)
}
