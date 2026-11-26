package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.WbIncandescent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ScriptMetricsCalculator
import com.example.data.preferences.PrompterSettings
import com.example.data.preferences.RimLightTone
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.RimLightCoolColor
import com.example.ui.theme.RimLightNeutralColor
import com.example.ui.theme.RimLightWarmColor
import kotlin.math.roundToInt

/**
 * One-handed studio settings panel positioned in the lower thumb zone.
 * Controls teleprompter font size, scroll speed, backdrop darkness, screen rim light,
 * Volume Down hardware shutter, and Dark/Light theme mode.
 */
@Composable
fun QuickAdjustDrawer(
    settings: PrompterSettings,
    onFontSizeChange: (Float) -> Unit,
    onScrollSpeedChange: (Float) -> Unit,
    onBackgroundOpacityChange: (Float) -> Unit,
    onRimLightEnabledChange: (Boolean) -> Unit,
    onRimLightToneChange: (RimLightTone) -> Unit,
    onRimLightIntensityChange: (Float) -> Unit,
    onVolumeDownShutterChange: (Boolean) -> Unit,
    onCountdownSoundsChange: (Boolean) -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val surfaceBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    val borderCol = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(surfaceBg)
            .border(1.dp, borderCol, RoundedCornerShape(24.dp))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .testTag("quick_adjust_drawer"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Font Size
        StudioSliderRow(
            icon = Icons.Rounded.FormatSize,
            label = stringResource(R.string.adjust_font_size),
            valueLabel = "${settings.fontSizeSp.roundToInt()}sp",
            value = settings.fontSizeSp,
            valueRange = 18f..44f,
            onValueChange = onFontSizeChange,
            testTag = "slider_font_size",
        )

        // 2. Scroll Speed
        val wpm = ScriptMetricsCalculator.speedLevelToWpm(settings.scrollSpeedLevel)
        StudioSliderRow(
            icon = Icons.Rounded.Speed,
            label = stringResource(R.string.adjust_scroll_speed),
            valueLabel = "%.1fx · %d wpm".format(settings.scrollSpeedLevel, wpm),
            value = settings.scrollSpeedLevel,
            valueRange = 1f..10f,
            onValueChange = onScrollSpeedChange,
            testTag = "slider_scroll_speed",
        )

        // 3. Backdrop Darkness
        StudioSliderRow(
            icon = Icons.Rounded.Contrast,
            label = stringResource(R.string.adjust_backdrop_darkness),
            valueLabel = "${(settings.backgroundOpacity * 100f).roundToInt()}%",
            value = settings.backgroundOpacity,
            valueRange = 0f..0.90f,
            onValueChange = onBackgroundOpacityChange,
            testTag = "slider_backdrop_darkness",
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // 4. Screen Rim Light (works in both Dark & Light modes!)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.WbIncandescent,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Column {
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
            }
            Switch(
                checked = settings.rimLightEnabled,
                onCheckedChange = onRimLightEnabledChange,
                modifier = Modifier.testTag("switch_rim_light"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }

        AnimatedVisibility(
            visible = settings.rimLightEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RimTonePill(
                        label = stringResource(R.string.adjust_rim_warm),
                        swatchColor = RimLightWarmColor,
                        selected = settings.rimLightTone == RimLightTone.WARM,
                        onClick = { onRimLightToneChange(RimLightTone.WARM) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rim_tone_warm"),
                    )
                    RimTonePill(
                        label = stringResource(R.string.adjust_rim_neutral),
                        swatchColor = RimLightNeutralColor,
                        selected = settings.rimLightTone == RimLightTone.NEUTRAL,
                        onClick = { onRimLightToneChange(RimLightTone.NEUTRAL) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rim_tone_neutral"),
                    )
                    RimTonePill(
                        label = stringResource(R.string.adjust_rim_cool),
                        swatchColor = RimLightCoolColor,
                        selected = settings.rimLightTone == RimLightTone.COOL,
                        onClick = { onRimLightToneChange(RimLightTone.COOL) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rim_tone_cool"),
                    )
                }

                StudioSliderRow(
                    icon = Icons.Rounded.WbIncandescent,
                    label = stringResource(R.string.adjust_rim_intensity),
                    valueLabel = "${(settings.rimLightIntensity * 100f).roundToInt()}%",
                    value = settings.rimLightIntensity,
                    valueRange = 0.35f..1f,
                    onValueChange = onRimLightIntensityChange,
                    testTag = "slider_rim_intensity",
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // 5. Volume Down Hardware Shutter Option
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.VolumeDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Column {
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
            }
            Switch(
                checked = settings.volumeDownShutterEnabled,
                onCheckedChange = onVolumeDownShutterChange,
                modifier = Modifier.testTag("switch_volume_shutter"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }

        // 5b. Countdown Sounds (3-2-1 audio cues)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Column {
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
            }
            Switch(
                checked = settings.countdownSoundsEnabled,
                onCheckedChange = onCountdownSoundsChange,
                modifier = Modifier.testTag("switch_countdown_sounds"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // 6. Appearance (OLED Black / Dark / Light)
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
    }
}

@Composable
private fun StudioSliderRow(
    icon: ImageVector,
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    testTag: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
    }
}

@Composable
fun RimTonePill(
    label: String,
    swatchColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(swatchColor)
                .border(1.dp, Color.Black.copy(alpha = 0.25f), CircleShape),
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor,
        )
    }
}

@Composable
fun ThemeModeSegmentedRow(
    selectedMode: AppThemeMode,
    onSelect: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        AppThemeMode.OLED_BLACK to stringResource(R.string.theme_oled_black),
        AppThemeMode.DARK to stringResource(R.string.theme_dark),
        AppThemeMode.LIGHT to stringResource(R.string.theme_light),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (mode, label) ->
            val isSelected = selectedMode == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 4.dp, vertical = 10.dp)
                    .testTag("theme_option_${mode.name.lowercase()}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
