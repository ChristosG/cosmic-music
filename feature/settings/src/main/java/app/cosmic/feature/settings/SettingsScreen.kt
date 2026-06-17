package app.cosmic.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cosmic.core.prefs.EqPreset
import app.cosmic.core.prefs.ReplayGainMode
import app.cosmic.core.prefs.ThemeMode
import app.cosmic.feature.common.CosmicSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text("Settings", style = MaterialTheme.typography.displaySmall)
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        .copy(alpha = 0.85f),
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            Section("Appearance") {
                LabeledRow("Theme") {
                    SegmentedRow(
                        // SYSTEM is hidden from the picker — the curated
                        // Cosmic themes have an opinionated visual identity
                        // we want users to pick deliberately, and "follow
                        // system" rarely matches what we want here.
                        options = ThemeMode.entries.filter { it != ThemeMode.SYSTEM },
                        selected = state.theme,
                        onSelect = viewModel::setTheme,
                        label = { it.uiLabel() },
                    )
                }
                SwitchRow(
                    title = "Dynamic color",
                    subtitle = "Use system Material You palette (Android 12+)",
                    checked = state.dynamicColor,
                    onChange = viewModel::setDynamicColor,
                )
            }

            Section("Equalizer") {
                SwitchRow(
                    title = "Enable equalizer",
                    subtitle = "Hardware Equalizer + BassBoost; effects apply to Cosmic only",
                    checked = state.eqEnabled,
                    onChange = viewModel::setEqEnabled,
                )
                if (state.eqEnabled) {
                    LabeledRow("Preset") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            EqPreset.entries.filter { it != EqPreset.CUSTOM }.forEach { preset ->
                                FilterChip(
                                    selected = state.eqPreset == preset,
                                    onClick = { viewModel.setEqPreset(preset) },
                                    label = { Text(preset.label) },
                                )
                            }
                        }
                    }
                    LabeledRow("Bass boost") {
                        CosmicSlider(
                            value = state.bassBoostStrength.toFloat(),
                            onValueChange = { viewModel.setBassStrength(it.toInt()) },
                            valueRange = 0f..1000f,
                            steps = 9,
                        )
                        Text(
                            "${(state.bassBoostStrength / 10)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Section("Playback") {
                LabeledRow("Crossfade") {
                    CosmicSlider(
                        value = state.crossfadeMs.toFloat(),
                        onValueChange = { viewModel.setCrossfadeMs(it.toInt()) },
                        valueRange = 0f..12_000f,
                        steps = 11,
                    )
                    Text(
                        if (state.crossfadeMs == 0) "Off" else "${state.crossfadeMs / 1000}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Engine wiring lands next slice — UI saves preference now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LabeledRow("Replay-gain normalization") {
                    SegmentedRow(
                        options = ReplayGainMode.entries,
                        selected = state.replayGain,
                        onSelect = viewModel::setReplayGain,
                        label = { it.uiLabel() },
                    )
                }
            }

            Section("Library") {
                SwitchRow(
                    title = "Scan all of Music/",
                    subtitle = "When off, only Music/Cosmic/ is scanned",
                    checked = state.scanWholeMusicDir,
                    onChange = viewModel::setScanWholeMusic,
                )
                SwitchRow(
                    title = "Smart shuffle",
                    subtitle = "Weight upcoming queue by play history + tags (lands next slice)",
                    checked = state.smartShuffleEnabled,
                    onChange = viewModel::setSmartShuffle,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = { content() })
        HorizontalDivider()
    }
}

@Composable
private fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SegmentedRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, opt ->
            SegmentedButton(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label(opt)) }
        }
    }
}

private fun ThemeMode.uiLabel(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.AMOLED -> "AMOLED"
    ThemeMode.SOLAR -> "Solar"
    ThemeMode.ELECTRIC -> "Electric"
}

private fun ReplayGainMode.uiLabel(): String = when (this) {
    ReplayGainMode.OFF -> "Off"
    ReplayGainMode.TRACK -> "Track"
    ReplayGainMode.ALBUM -> "Album"
}

// Forward-only ColumnScope alias so the Section composable accepts column content.
private typealias ColumnScope = androidx.compose.foundation.layout.ColumnScope
