package dev.huecoder.md3agent.feature.files

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import dev.huecoder.md3agent.ui.text.stringResource

@Composable
internal fun BarAction(icon: ImageVector, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) { Icon(icon, contentDescription = stringResource(label)) }
}
