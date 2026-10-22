package com.medicapp.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medicapp.ui.theme.BlisterTheme

/**
 * Barra inferior como una lámina de blíster: cada pestaña es una celda, y la activa se llena de
 * tinta como una cápsula. Al pulsar, la celda se hunde un poco.
 *
 * [badges] pone un contador coral sobre una pestaña; Hoy lo usa para las tomas atrasadas, que es
 * lo único de la barra que pide atención.
 */
@Composable
internal fun BlisterNavBar(
    tabs: List<BottomTab>,
    currentRoute: String?,
    onSelect: (BottomTab) -> Unit,
    badges: Map<String, Int> = emptyMap(),
) {
    val colors = BlisterTheme.colors
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(colors.foilLight, colors.foilDark)))
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        tabs.forEach { tab ->
            NavCell(
                tab = tab,
                selected = currentRoute == tab.route,
                badge = badges[tab.route] ?: 0,
                onClick = { onSelect(tab) },
            )
        }
    }
}

@Composable
private fun RowScope.NavCell(tab: BottomTab, selected: Boolean, badge: Int, onClick: () -> Unit) {
    val colors = BlisterTheme.colors
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill by animateColorAsState(if (selected) scheme.primary else Color.Transparent, label = "fill")
    val iconTint by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurfaceVariant, label = "tint")
    val press by animateFloatAsState(if (pressed) 0.92f else 1f, label = "press")
    val description = if (badge > 0) "${tab.label}, $badge sin marcar" else tab.label

    Column(
        modifier = Modifier
            .weight(1f)
            .selectable(selected = selected, onClick = onClick, interactionSource = interaction, indication = null)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Tab
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box {
            Box(
                modifier = Modifier
                    .scale(press)
                    .size(width = 60.dp, height = 34.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(colors.glass, colors.glassEdge)))
                    .background(fill)
                    .border(1.dp, colors.glassEdge, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(tab.icon, contentDescription = null, tint = iconTint)
            }
            if (badge > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                        .clip(CircleShape)
                        .background(scheme.tertiary)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$badge",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onTertiary,
                    )
                }
            }
        }
        Text(
            tab.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
        )
    }
}
