package net.canyonwolf.sparklauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TopMenuBar(
    isHomeSelected: Boolean,
    isLibrarySelected: Boolean,
    onHomeClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val activeTextColor = MaterialTheme.colorScheme.primary
    val inactiveTextColor = MaterialTheme.colorScheme.onSurface

    TopAppBar(
        title = {
            // Ensure we can go below typical minimum interactive height
            CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                val twoPx: Dp = with(LocalDensity.current) { 2f.toDp() }

                @Composable
                fun MenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
                    val interaction = remember { MutableInteractionSource() }
                    var hovered by remember { mutableStateOf(false) }
                    val bgColor = when {
                        hovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                    }
                    val textColor = if (selected) activeTextColor else inactiveTextColor
                    val borderColor = if (selected) activeTextColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f)

                    Box(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 0.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(bgColor)
                            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                            .onPointerEvent(PointerEventType.Enter) { hovered = true }
                            .onPointerEvent(PointerEventType.Exit) { hovered = false }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                role = Role.Button,
                                onClick = onClick
                            )
                            .padding(horizontal = 12.dp, vertical = twoPx + 2.dp)
                    ) {
                        Text(label, color = textColor, style = MaterialTheme.typography.labelLarge)
                    }
                }

                Row {
                    MenuItem("Home", isHomeSelected, onHomeClick)
                    Spacer(Modifier.width(4.dp))
                    MenuItem("Library", isLibrarySelected, onLibraryClick)
                    Spacer(Modifier.width(4.dp))
                    MenuItem("Settings", false, onSettingsClick)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        actions = {}
    )
}
