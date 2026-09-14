package org.animatedantmo.weightgraph.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.animatedantmo.weightgraph.R

/**
 * Speed-dial style action menu. The main button toggles the stack open; each action is a small
 * button with a label beside it, so the icons never have to carry the meaning alone.
 */
@Composable
fun ActionMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    hasEntries: Boolean,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onBackup: () -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Quarter turn as the stack opens, so the swap from menu to close reads as one motion.
    val iconRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(220),
        label = "fabIcon",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(160)) + expandVertically(tween(200)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MenuAction(
                    label = "Import CSV",
                    icon = R.drawable.ic_import,
                    onClick = {
                        onExpandedChange(false)
                        onImport()
                    },
                )
                if (hasEntries) {
                    MenuAction(
                        label = "Export CSV",
                        icon = R.drawable.ic_export,
                        onClick = {
                            onExpandedChange(false)
                            onExport()
                        },
                    )
                }
                // Always shown, unlike Export and Delete All, so the schedule and the last result
                // can still be checked when there are no entries.
                MenuAction(
                    label = "Drive Backup",
                    icon = R.drawable.ic_backup,
                    onClick = {
                        onExpandedChange(false)
                        onBackup()
                    },
                )
                if (hasEntries) {
                    MenuAction(
                        label = "Delete All",
                        icon = R.drawable.ic_delete,
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = {
                            onExpandedChange(false)
                            onDeleteAll()
                        },
                    )
                }
            }
        }

        // Secondary colour: this is the data-management group, not the primary add action.
        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Icon(
                painter = painterResource(
                    if (expanded) R.drawable.ic_close else R.drawable.ic_more
                ),
                contentDescription = if (expanded) "Close data actions" else "Data actions",
                modifier = Modifier.rotate(iconRotation),
            )
        }
    }
}

@Composable
private fun MenuAction(
    label: String,
    icon: Int,
    onClick: () -> Unit,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // A label pill rather than a bare icon: import and export icons are easy to mix up.
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 3.dp,
            modifier = Modifier.padding(end = 12.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = container,
            contentColor = content,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
        ) {
            Icon(painter = painterResource(icon), contentDescription = label)
        }
    }
}
