package com.example.clickassist.ui.permission

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.clickassist.R
import com.example.clickassist.viewmodel.PermissionGuideViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionGuideScreen(
    viewModel: PermissionGuideViewModel,
    onContinueToTaskList: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionStatus()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.permission_guide_title)) },
                actions = {
                    TextButton(onClick = onContinueToTaskList) {
                        Text(text = stringResource(R.string.permission_guide_action_tasks))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PermissionCard(
                titleRes = R.string.permission_overlay_title,
                descriptionRes = R.string.permission_overlay_description,
                statusTextRes = if (uiState.hasOverlayPermission) {
                    R.string.common_status_granted
                } else {
                    R.string.common_status_missing
                },
                actionTextRes = R.string.common_open_settings,
                onAction = {
                    viewModel.markOverlaySettingsOpened()
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                },
            )

            PermissionCard(
                titleRes = R.string.permission_accessibility_title,
                descriptionRes = R.string.permission_accessibility_description,
                statusTextRes = if (uiState.hasAccessibilityPermission) {
                    R.string.common_status_granted
                } else {
                    R.string.common_status_missing
                },
                actionTextRes = R.string.common_open_settings,
                onAction = {
                    viewModel.markAccessibilitySettingsOpened()
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
            )

            PolishedCard {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.permission_local_data_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.permission_local_data_description),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(
                            if (uiState.localOnlyNoticeAcknowledged) {
                                R.string.permission_local_data_acknowledged
                            } else {
                                R.string.permission_local_data_not_acknowledged
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = viewModel::acknowledgeLocalOnlyNotice) {
                        Text(text = stringResource(R.string.common_acknowledge))
                    }
                }
            }

            PolishedCard {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.permission_summary_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(text = stringResource(R.string.permission_overlay_open_count, uiState.overlayGuideOpenCount))
                    Text(text = stringResource(R.string.permission_accessibility_open_count, uiState.accessibilityGuideOpenCount))
                    Text(
                        text = stringResource(
                            if (uiState.allRequiredPermissionsGranted) {
                                R.string.permission_summary_ready
                            } else {
                                R.string.permission_summary_missing
                            },
                        ),
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.permission_open_settings))
                }

                Button(
                    onClick = onContinueToTaskList,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.permission_open_task_list))
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    @StringRes statusTextRes: Int,
    @StringRes actionTextRes: Int,
    onAction: () -> Unit,
) {
    PolishedCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(
                        R.string.permission_status_format,
                        stringResource(statusTextRes),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = onAction) {
                    Text(text = stringResource(actionTextRes))
                }
            }
        }
    }
}

@Composable
private fun PolishedCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = content,
    )
}
