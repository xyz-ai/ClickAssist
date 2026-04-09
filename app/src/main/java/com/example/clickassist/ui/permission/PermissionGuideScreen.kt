package com.example.clickassist.ui.permission

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.clickassist.viewmodel.PermissionGuideViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionGuideScreen(
    viewModel: PermissionGuideViewModel,
    onContinueToTaskList: () -> Unit,
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
                title = { Text(text = "Permission Guide") },
                actions = {
                    TextButton(onClick = onContinueToTaskList) {
                        Text(text = "Tasks")
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
                title = "Overlay Permission",
                description = "Used for the basic floating panel. V1 only shows runner status and does not do dynamic recognition.",
                statusText = if (uiState.hasOverlayPermission) "Granted" else "Missing",
                actionText = "Open Settings",
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
                title = "Accessibility Permission",
                description = "Used to dispatch static gestures. V1 only runs user-configured actions.",
                statusText = if (uiState.hasAccessibilityPermission) "Granted" else "Missing",
                actionText = "Open Settings",
                onAction = {
                    viewModel.markAccessibilitySettingsOpened()
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Local Data",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Tasks, steps, and settings are stored only in local Room and DataStore. No network or cloud sync is used.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = if (uiState.localOnlyNoticeAcknowledged) {
                            "Status: local-only notice acknowledged"
                        } else {
                            "Status: local-only notice not acknowledged"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = viewModel::acknowledgeLocalOnlyNotice) {
                        Text(text = "Acknowledge")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(text = "Overlay guide opens: ${uiState.overlayGuideOpenCount}")
                    Text(text = "Accessibility guide opens: ${uiState.accessibilityGuideOpenCount}")
                    Text(
                        text = if (uiState.allRequiredPermissionsGranted) {
                            "All required permissions for V1 are available."
                        } else {
                            "Some permissions are still missing. Tasks can be saved, but execution will fail."
                        },
                    )
                }
            }

            Button(
                onClick = onContinueToTaskList,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Open Task List")
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    statusText: String,
    actionText: String,
    onAction: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Status: $statusText",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onAction) {
                    Text(text = actionText)
                }
            }
        }
    }
}
