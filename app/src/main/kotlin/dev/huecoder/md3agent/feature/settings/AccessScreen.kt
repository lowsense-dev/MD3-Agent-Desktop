package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Check_circle
import com.composables.icons.materialsymbols.rounded.Error
import dev.huecoder.md3agent.LocalAppContainer
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.FileScope
import dev.huecoder.md3agent.data.settings.ShellMode
import dev.huecoder.md3agent.system.environment.Component
import dev.huecoder.md3agent.system.environment.ComponentStatus
import dev.huecoder.md3agent.system.environment.Distro
import dev.huecoder.md3agent.system.environment.EnvironmentProbe
import dev.huecoder.md3agent.system.environment.packageManager
import dev.huecoder.md3agent.ui.components.LabeledSwitch
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.text.stringResource
import kotlinx.coroutines.launch

val ShellMode.title: Int
    get() = when (this) {
        ShellMode.SYSTEM -> R.string.access_mode_system
        ShellMode.SANDBOX -> R.string.access_mode_sandbox
    }

val FileScope.title: Int
    get() = when (this) {
        FileScope.APP -> R.string.access_files_app
        FileScope.HOME -> R.string.access_files_home
    }

val Component.title: Int
    get() = when (this) {
        Component.BWRAP -> R.string.env_bwrap
        Component.PYTHON -> R.string.env_python
        Component.PYTHON_VENV -> R.string.env_python_venv
        Component.WL_PASTE -> R.string.env_wl_paste
        Component.XCLIP -> R.string.env_xclip
        Component.PORTAL -> R.string.env_portal
        Component.SECRET_SERVICE -> R.string.env_secret_service
        Component.NOTIFICATIONS -> R.string.env_notifications
        Component.SYSTEMD_USER -> R.string.env_systemd_user
        Component.SPEECH -> R.string.env_speech
    }

/** Shell and file access for the assistant, and what this computer has for the optional features. */
@Composable
fun AccessScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val distro = remember { Distro.read() }
    var statuses by remember { mutableStateOf<List<ComponentStatus>?>(null) }
    var checks by remember { mutableIntStateOf(0) }
    LaunchedEffect(checks) {
        container.shellAccess.recheck()
        statuses = EnvironmentProbe(container.bus, container.shellAccess).collect()
    }
    SettingsScaffold(stringResource(R.string.settings_access), onBack = onBack) { padding ->
        val access = settings?.tools?.access ?: return@SettingsScaffold
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader(stringResource(R.string.access_section_shell), topPadding = 0.dp)
            ChoiceRow(
                title = stringResource(R.string.access_shell_hint),
                choices = ShellMode.entries.map { Choice(it, it.title) },
                selected = access.shell,
                onSelect = { mode -> scope.launch { container.settings.updateTools { it.copy(access = it.access.copy(shell = mode)) } } },
                hint = stringResource(if (access.shell == ShellMode.SYSTEM) R.string.access_mode_system_summary else R.string.access_mode_sandbox_summary),
            )
            container.shellAccess.unavailableReason(ShellMode.SANDBOX)?.takeIf { access.shell == ShellMode.SANDBOX }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            LabeledSwitch(
                title = stringResource(R.string.access_sandbox_network),
                summary = stringResource(R.string.access_sandbox_network_summary),
                checked = access.sandboxNetwork,
                onCheckedChange = { on -> scope.launch { container.settings.updateTools { it.copy(access = it.access.copy(sandboxNetwork = on)) } } },
            )
            SectionHeader(stringResource(R.string.access_section_files))
            ChoiceRow(
                title = stringResource(R.string.access_files_hint, container.paths.workspace.path),
                choices = FileScope.entries.map { Choice(it, it.title) },
                selected = access.files,
                onSelect = { scopeChoice -> scope.launch { container.settings.updateTools { it.copy(access = it.access.copy(files = scopeChoice)) } } },
                hint = stringResource(if (access.files == FileScope.HOME) R.string.access_files_home_summary else R.string.access_files_app_summary),
            )
            SectionHeader(stringResource(R.string.access_section_shortcut))
            Text(stringResource(R.string.access_shortcut_hint), style = MaterialTheme.typography.bodyMedium)
            CommandBox("md3-agent --overlay")
            SectionHeader(stringResource(R.string.access_section_environment))
            Text(stringResource(R.string.access_environment_hint, distro.pretty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            statuses?.forEach { status -> ComponentRow(status, distro) }
            TextButton(onClick = { checks++ }) { Text(stringResource(R.string.access_recheck)) }
        }
    }
}

@Composable
private fun ComponentRow(status: ComponentStatus, distro: Distro) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(
                    if (status.found) MaterialSymbols.Rounded.Check_circle else MaterialSymbols.Rounded.Error,
                    contentDescription = stringResource(if (status.found) R.string.access_component_found else R.string.access_component_missing),
                    tint = if (status.found) colors.primary else colors.error,
                )
                Text(stringResource(status.component.title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
            status.detail?.takeIf { status.found }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            if (!status.found) {
                status.component.packageName(distro.packageManager())?.let { pkg ->
                    Text(stringResource(R.string.access_install_hint, ""), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    CommandBox(distro.packageManager().installCommand(pkg))
                }
            }
        }
    }
}

@Composable
private fun CommandBox(command: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
        }
    }
}
