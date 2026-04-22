package com.TradeRoutine.LZLapp.ui.settings

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.TradeRoutine.LZLapp.R
import com.TradeRoutine.LZLapp.common.i18n.AppLanguage
import com.TradeRoutine.LZLapp.common.i18n.LocaleManager
import com.TradeRoutine.LZLapp.ui.theme.AppThemeMode
import com.TradeRoutine.LZLapp.viewmodel.LanguageChangeDecision
import com.TradeRoutine.LZLapp.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onReopenFloatingTutorial: () -> Boolean,
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.settings
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val versionName = remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    val floatingTutorialDeferredMessage = stringResource(R.string.tutorial_reopen_next_floating_message)
    val onLanguageSelected: (AppLanguage) -> Unit = remember(
        viewModel,
        settings.languageMode,
        scope,
    ) {
        { targetLanguage ->
            scope.launch {
                Log.i(TAG, "language option clicked current=${settings.languageMode} target=$targetLanguage")
                when (val decision = viewModel.requestLanguageChange(targetLanguage)) {
                    LanguageChangeDecision.SkipSameLanguage -> {
                        Log.i(TAG, "skip same language current=${settings.languageMode} target=$targetLanguage")
                    }

                    is LanguageChangeDecision.ApplyLanguage -> {
                        val applied = LocaleManager.applyLanguage(decision.target)
                        Log.i(
                            TAG,
                            "apply language requested target=${decision.target} applied=$applied manualRecreate=false",
                        )
                        Log.i(TAG, "manual recreate skipped; rely on AppCompat locale recreation")
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text(text = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 16.dp,
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsSection(title = stringResource(R.string.settings_section_general)) {
                    ChoiceSettingRow(
                        title = stringResource(R.string.settings_language_title),
                        options = listOf(
                            ChoiceOption(
                                label = stringResource(R.string.settings_language_follow_system),
                                selected = settings.languageMode == AppLanguage.FOLLOW_SYSTEM,
                                onClick = { onLanguageSelected(AppLanguage.FOLLOW_SYSTEM) },
                            ),
                            ChoiceOption(
                                label = stringResource(R.string.settings_language_english),
                                selected = settings.languageMode == AppLanguage.ENGLISH,
                                onClick = { onLanguageSelected(AppLanguage.ENGLISH) },
                            ),
                            ChoiceOption(
                                label = stringResource(R.string.settings_language_simplified_chinese),
                                selected = settings.languageMode == AppLanguage.ZH_CN,
                                onClick = { onLanguageSelected(AppLanguage.ZH_CN) },
                            ),
                        ),
                    )
                    ChoiceSettingRow(
                        title = stringResource(R.string.settings_theme_title),
                        options = listOf(
                            ChoiceOption(
                                label = stringResource(R.string.settings_theme_follow_system),
                                selected = settings.themeMode == AppThemeMode.FOLLOW_SYSTEM,
                                onClick = { viewModel.setThemeMode(AppThemeMode.FOLLOW_SYSTEM) },
                            ),
                            ChoiceOption(
                                label = stringResource(R.string.settings_theme_light),
                                selected = settings.themeMode == AppThemeMode.LIGHT,
                                onClick = { viewModel.setThemeMode(AppThemeMode.LIGHT) },
                            ),
                            ChoiceOption(
                                label = stringResource(R.string.settings_theme_dark),
                                selected = settings.themeMode == AppThemeMode.DARK,
                                onClick = { viewModel.setThemeMode(AppThemeMode.DARK) },
                            ),
                        ),
                    )
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.settings_section_floating_toolbar)) {
                    SwitchSettingRow(
                        title = stringResource(R.string.settings_toolbar_default_expanded),
                        checked = settings.toolbarDefaultExpanded,
                        onCheckedChange = viewModel::setToolbarDefaultExpanded,
                    )
                    SwitchSettingRow(
                        title = stringResource(R.string.settings_toolbar_show_handle_when_hidden),
                        checked = settings.showHandleWhenToolbarHidden,
                        onCheckedChange = viewModel::setShowHandleWhenToolbarHidden,
                    )
                    StepperSettingRow(
                        title = stringResource(R.string.settings_toolbar_marker_size),
                        valueText = stringResource(R.string.settings_value_dp, settings.markerSizeDp),
                        onDecrease = { viewModel.setMarkerSizeDp((settings.markerSizeDp - 4).coerceAtLeast(40)) },
                        onIncrease = { viewModel.setMarkerSizeDp((settings.markerSizeDp + 4).coerceAtMost(96)) },
                    )
                    StepperSettingRow(
                        title = stringResource(R.string.settings_toolbar_line_width),
                        valueText = stringResource(R.string.settings_value_dp, settings.swipeLineWidthDp),
                        onDecrease = { viewModel.setSwipeLineWidthDp((settings.swipeLineWidthDp - 1).coerceAtLeast(4)) },
                        onIncrease = { viewModel.setSwipeLineWidthDp((settings.swipeLineWidthDp + 1).coerceAtMost(20)) },
                    )
                    SwitchSettingRow(
                        title = stringResource(R.string.settings_toolbar_show_marker_numbers),
                        checked = settings.showMarkerNumbers,
                        onCheckedChange = viewModel::setShowMarkerNumbers,
                    )
                    SwitchSettingRow(
                        title = stringResource(R.string.settings_toolbar_show_center_cross),
                        checked = settings.showMarkerCenterCross,
                        onCheckedChange = viewModel::setShowMarkerCenterCross,
                    )
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.settings_section_execution_defaults)) {
                    StepperSettingRow(
                        title = stringResource(R.string.settings_default_tap_duration),
                        valueText = stringResource(R.string.settings_value_ms, settings.defaultTapDurationMs),
                        onDecrease = { viewModel.setDefaultTapDurationMs((settings.defaultTapDurationMs - 10L).coerceAtLeast(10L)) },
                        onIncrease = { viewModel.setDefaultTapDurationMs(settings.defaultTapDurationMs + 10L) },
                    )
                    StepperSettingRow(
                        title = stringResource(R.string.settings_default_long_press_duration),
                        valueText = stringResource(R.string.settings_value_ms, settings.defaultLongPressDurationMs),
                        onDecrease = {
                            viewModel.setDefaultLongPressDurationMs(
                                (settings.defaultLongPressDurationMs - 50L).coerceAtLeast(100L),
                            )
                        },
                        onIncrease = { viewModel.setDefaultLongPressDurationMs(settings.defaultLongPressDurationMs + 50L) },
                    )
                    StepperSettingRow(
                        title = stringResource(R.string.settings_default_swipe_duration),
                        valueText = stringResource(R.string.settings_value_ms, settings.defaultSwipeDurationMs),
                        onDecrease = { viewModel.setDefaultSwipeDurationMs((settings.defaultSwipeDurationMs - 50L).coerceAtLeast(50L)) },
                        onIncrease = { viewModel.setDefaultSwipeDurationMs(settings.defaultSwipeDurationMs + 50L) },
                    )
                    StepperSettingRow(
                        title = stringResource(R.string.settings_default_step_interval),
                        valueText = stringResource(R.string.settings_value_ms, settings.defaultStepIntervalMs),
                        onDecrease = { viewModel.setDefaultStepIntervalMs((settings.defaultStepIntervalMs - 50L).coerceAtLeast(1L)) },
                        onIncrease = { viewModel.setDefaultStepIntervalMs(settings.defaultStepIntervalMs + 50L) },
                    )
                    StepperSettingRow(
                        title = stringResource(R.string.settings_default_total_rounds),
                        valueText = settings.defaultTotalRounds.toString(),
                        onDecrease = { viewModel.setDefaultTotalRounds((settings.defaultTotalRounds - 1).coerceAtLeast(1)) },
                        onIncrease = { viewModel.setDefaultTotalRounds(settings.defaultTotalRounds + 1) },
                    )
                    StepperSettingRow(
                        title = stringResource(R.string.settings_default_new_step_repeat_count),
                        valueText = settings.defaultNewStepRepeatCount.toString(),
                        onDecrease = {
                            viewModel.setDefaultNewStepRepeatCount((settings.defaultNewStepRepeatCount - 1).coerceAtLeast(1))
                        },
                        onIncrease = { viewModel.setDefaultNewStepRepeatCount(settings.defaultNewStepRepeatCount + 1) },
                    )
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.settings_section_about_help)) {
                    InfoSettingRow(
                        title = stringResource(R.string.settings_about_version),
                        value = versionName,
                    )
                    DescriptionBlock(
                        title = stringResource(R.string.settings_about_local_data_only),
                        description = stringResource(R.string.settings_about_local_data_only_summary),
                    )
                    TextButton(
                        onClick = {
                            scope.launch {
                                viewModel.setHasSeenOnboarding(false)
                                onOpenOnboarding()
                            }
                        },
                    ) {
                        Text(text = stringResource(R.string.reopen_onboarding))
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                viewModel.setHasSeenFloatingTutorial(false)
                                val shownImmediately = onReopenFloatingTutorial()
                                if (!shownImmediately) {
                                    snackbarHostState.showSnackbar(message = floatingTutorialDeferredMessage)
                                }
                            }
                        },
                    ) {
                        Text(text = stringResource(R.string.reopen_floating_tutorial))
                    }
                    DescriptionBlock(
                        title = stringResource(R.string.settings_about_privacy),
                        description = stringResource(R.string.settings_about_privacy_summary),
                    )
                }
            }
        }
    }
}

private const val TAG = "SettingsLanguage"

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

private data class ChoiceOption(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun ChoiceSettingRow(
    title: String,
    options: List<ChoiceOption>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option.selected,
                    onClick = option.onClick,
                    label = { Text(text = option.label) },
                )
            }
        }
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StepperSettingRow(
    title: String,
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onDecrease) {
                Text(text = stringResource(R.string.common_decrease))
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedButton(onClick = onIncrease) {
                Text(text = stringResource(R.string.common_increase))
            }
        }
    }
}

@Composable
private fun InfoSettingRow(
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DescriptionBlock(
    title: String,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
