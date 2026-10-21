package dev.huecoder.md3agent.feature.chat

import dev.huecoder.md3agent.LocalAppContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.huecoder.md3agent.ui.text.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.conversation.ApprovalDecision
import dev.huecoder.md3agent.conversation.ToolApprovals
import dev.huecoder.md3agent.tools.system.RunPythonTool
import dev.huecoder.md3agent.ui.components.ShapeBadge
import dev.huecoder.md3agent.ui.components.ToolCatalog
import dev.huecoder.md3agent.ui.text.asString
import kotlinx.coroutines.flow.map

@Composable
fun ApprovalCard(call: ToolCallUi, onDecision: (ApprovalDecision) -> Unit, modifier: Modifier = Modifier) {
    val info = ToolCatalog.of(call.name)
    val arguments = readableArguments(call.arguments)
    val settings = LocalAppContainer.current.settings
    val tools by remember(settings) { settings.settings.map { it.tools } }.collectAsState(initial = null)
    val fullAccess = !ToolApprovals.offersSession(call.name)
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShapeBadge(info.icon, size = BADGE_SIZE)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.approval_title, info.title.asString()), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.approval_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (fullAccess) Warning(stringResource(R.string.approval_full_access))
            if (arguments.isNotEmpty()) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Text(
                        arguments,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = ARGUMENTS_MAX_HEIGHT)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onDecision(ApprovalDecision.DENY) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.approval_deny)) }
                Button(
                    onClick = { onDecision(ApprovalDecision.ALLOW) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.approval_allow)) }
            }
            if (!fullAccess) {
                TextButton(
                    onClick = { onDecision(ApprovalDecision.ALLOW_FOR_SESSION) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.approval_allow_session)) }
            }
        }
    }
}

@Composable
private fun Warning(text: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(12.dp))
    }
}

private val BADGE_SIZE = 40.dp
private val ARGUMENTS_MAX_HEIGHT = 160.dp
