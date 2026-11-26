package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WbIncandescent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.ScriptEntity
import com.example.data.model.ScriptMetricsCalculator
import com.example.data.preferences.PrompterSettings
import com.example.data.preferences.RimLightTone
import com.example.ui.components.RimTonePill
import com.example.ui.components.ThemeModeSegmentedRow
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.RimLightCoolColor
import com.example.ui.theme.RimLightNeutralColor
import com.example.ui.theme.RimLightWarmColor
import com.example.ui.viewmodel.ScriptsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptLibraryScreen(
    viewModel: ScriptsViewModel,
    onCreateScript: () -> Unit,
    onEditScript: (Long) -> Unit,
    onRecordScript: (Long) -> Unit,
) {
    val scripts by viewModel.scripts.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val filteredScripts by remember(scripts, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                scripts
            } else {
                val q = searchQuery.trim().lowercase()
                scripts.filter {
                    it.title.lowercase().contains(q) || it.content.lowercase().contains(q)
                }
            }
        }
    }

    var pendingDeleteScript by remember { mutableStateOf<ScriptEntity?>(null) }
    var showStudioSettingsSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateScript,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("new_script_fab"),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.library_new_script),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Brand Studio Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.library_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    IconButton(
                        onClick = { showStudioSettingsSheet = true },
                        modifier = Modifier.testTag("library_settings_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.action_settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Options Pill (plus active Rim Light badge if enabled)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StudioFeatureChip(
                        icon = Icons.Rounded.Tune,
                        label = "${stringResource(R.string.action_options)} · %.1fx".format(settings.scrollSpeedLevel),
                        onClick = { showStudioSettingsSheet = true },
                    )
                    if (settings.rimLightEnabled) {
                        StudioFeatureChip(
                            icon = Icons.Rounded.WbIncandescent,
                            label = stringResource(R.string.adjust_rim_light),
                            onClick = { showStudioSettingsSheet = true },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))

                // Search Bar when user has multiple scripts
                AnimatedVisibility(visible = scripts.size >= 2) {
                    Column {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = viewModel::updateSearchQuery,
                            singleLine = true,
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.library_search_placeholder),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = null,
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("library_search_input"),
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                // Script List or Empty State
                if (scripts.isEmpty()) {
                    LibraryEmptyState(
                        title = stringResource(R.string.library_empty_title),
                        body = stringResource(R.string.library_empty_body),
                        modifier = Modifier.weight(1f),
                    )
                } else if (filteredScripts.isEmpty()) {
                    LibraryEmptyState(
                        title = stringResource(R.string.library_no_results_title),
                        body = stringResource(R.string.library_no_results_body),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("scripts_lazy_list"),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = filteredScripts,
                            key = { it.id },
                        ) { script ->
                            ScriptLibraryCard(
                                script = script,
                                scrollSpeedLevel = settings.scrollSpeedLevel,
                                onEdit = { onEditScript(script.id) },
                                onRecord = { onRecordScript(script.id) },
                                onDuplicate = { viewModel.duplicateScript(script) },
                                onRequestDelete = { pendingDeleteScript = script },
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    val scriptToDelete = pendingDeleteScript
    if (scriptToDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteScript = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = stringResource(R.string.library_delete_script_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.library_delete_script_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteScript(scriptToDelete.id)
                        pendingDeleteScript = null
                    },
                    modifier = Modifier.testTag("confirm_delete_script_button"),
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteScript = null }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // Options Bottom Sheet (Scroll Speed, Theme, Rim Light, Volume Down Shutter)
    if (showStudioSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStudioSettingsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            LibraryStudioSettingsSheet(
                settings = settings,
                onScrollSpeedChange = viewModel::updateScrollSpeed,
                onThemeModeChange = viewModel::setThemeMode,
                onRimLightEnabledChange = viewModel::setRimLightEnabled,
                onRimLightToneChange = viewModel::setRimLightTone,
                onVolumeDownShutterChange = viewModel::setVolumeDownShutterEnabled,
                onCountdownSoundsChange = viewModel::setCountdownSoundsEnabled,
            )
        }
    }
}

@Composable
private fun StudioFeatureChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun ScriptLibraryCard(
    script: ScriptEntity,
    scrollSpeedLevel: Float,
    onEdit: () -> Unit,
    onRecord: () -> Unit,
    onDuplicate: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val metrics = remember(script.content, scrollSpeedLevel) {
        ScriptMetricsCalculator.calculate(script.content, scrollSpeedLevel)
    }
    val dateFormatter = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val updatedLabel = remember(script.updatedAtMillis) {
        dateFormatter.format(Date(script.updatedAtMillis))
    }

    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .clickable(onClick = onEdit)
            .padding(18.dp)
            .testTag("script_card_${script.id}"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = script.title.ifBlank { stringResource(R.string.library_untitled_script) },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("script_menu_${script.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_duplicate)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onDuplicate()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onRequestDelete()
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = script.content.trim().ifEmpty {
                stringResource(R.string.library_empty_script_preview)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.metrics_words_format, metrics.wordCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = stringResource(R.string.metrics_read_time_format, metrics.formattedDuration()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "· $updatedLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }

            Button(
                onClick = onRecord,
                enabled = script.content.isNotBlank(),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.testTag("script_record_button_${script.id}"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Videocam,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.library_record_action),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun LibraryEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private const val KOFI_SUPPORT_URL = "https://ko-fi.com/M4R320UNC5"

@Composable
private fun LibraryStudioSettingsSheet(
    settings: PrompterSettings,
    onScrollSpeedChange: (Float) -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onRimLightEnabledChange: (Boolean) -> Unit,
    onRimLightToneChange: (RimLightTone) -> Unit,
    onVolumeDownShutterChange: (Boolean) -> Unit,
    onCountdownSoundsChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.action_options),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // Default Scroll Speed (always remembers last set value)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.adjust_scroll_speed),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val wpm = ScriptMetricsCalculator.speedLevelToWpm(settings.scrollSpeedLevel)
                Text(
                    text = "%.1fx · %d wpm".format(settings.scrollSpeedLevel, wpm),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = settings.scrollSpeedLevel,
                onValueChange = onScrollSpeedChange,
                valueRange = 1f..10f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Theme Mode (OLED Black / Dark / Light)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.adjust_theme_mode),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ThemeModeSegmentedRow(
                selectedMode = settings.themeMode,
                onSelect = onThemeModeChange,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Screen Rim Light
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.adjust_rim_light),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.adjust_rim_light_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.rimLightEnabled,
                onCheckedChange = onRimLightEnabledChange,
                modifier = Modifier.testTag("settings_switch_rim_light"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }

        AnimatedVisibility(visible = settings.rimLightEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RimTonePill(
                    label = stringResource(R.string.adjust_rim_warm),
                    swatchColor = RimLightWarmColor,
                    selected = settings.rimLightTone == RimLightTone.WARM,
                    onClick = { onRimLightToneChange(RimLightTone.WARM) },
                    modifier = Modifier.weight(1f),
                )
                RimTonePill(
                    label = stringResource(R.string.adjust_rim_neutral),
                    swatchColor = RimLightNeutralColor,
                    selected = settings.rimLightTone == RimLightTone.NEUTRAL,
                    onClick = { onRimLightToneChange(RimLightTone.NEUTRAL) },
                    modifier = Modifier.weight(1f),
                )
                RimTonePill(
                    label = stringResource(R.string.adjust_rim_cool),
                    swatchColor = RimLightCoolColor,
                    selected = settings.rimLightTone == RimLightTone.COOL,
                    onClick = { onRimLightToneChange(RimLightTone.COOL) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Volume Down Hardware Shutter
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.adjust_volume_shutter),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.adjust_volume_shutter_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.volumeDownShutterEnabled,
                onCheckedChange = onVolumeDownShutterChange,
                modifier = Modifier.testTag("settings_switch_volume_shutter"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }

        // Countdown Sounds (3-2-1 audio cues)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.adjust_countdown_sounds),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.adjust_countdown_sounds_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.countdownSoundsEnabled,
                onCheckedChange = onCountdownSoundsChange,
                modifier = Modifier.testTag("settings_switch_countdown_sounds"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Voluntary Creator Support on Ko-fi (opens external browser)
        KofiSupportCard(
            onClick = { openKofiSupportPage(context) },
        )

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun KofiSupportCard(
    onClick: () -> Unit,
) {
    val cardDescription = stringResource(R.string.settings_support_kofi_cd)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .semantics { contentDescription = cardDescription }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("settings_kofi_support_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.LocalCafe,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_support_kofi_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.settings_support_kofi_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun openKofiSupportPage(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(KOFI_SUPPORT_URL)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            context.getString(R.string.settings_support_kofi_browser_error),
            Toast.LENGTH_SHORT,
        ).show()
    }
}
