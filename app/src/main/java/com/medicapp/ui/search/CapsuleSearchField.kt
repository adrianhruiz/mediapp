package com.medicapp.ui.search

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Hueco de la cápsula: un gris algo más oscuro que el fondo y un borde, como si el campo
// estuviera hundido en el plástico.
private val wellFill = Color(0xFFE3E6EA)
private val wellEdge = Color(0xFFB9C0C9)

/**
 * Campo de búsqueda en cápsula hundida. El ejemplo va en el [placeholder] y la ayuda larga, si
 * hace falta, en [trailingIcon] (el botón (i)): no hay texto fijo debajo.
 */
@Composable
fun CapsuleSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().border(1.dp, wellEdge, CircleShape),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = trailingIcon,
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = wellFill,
            unfocusedContainerColor = wellFill,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}
