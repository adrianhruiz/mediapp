package com.medicapp.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Observa solo el índice visible y el total, no `layoutInfo` entero: ese objeto se
 * reevalúa en cada fotograma del scroll y arrastra la lista completa de items visibles.
 */
@Composable
fun LazyListState.OnBottomReached(buffer: Int = 5, onLoadMore: () -> Unit) {
    LaunchedEffect(this) {
        snapshotFlow {
            val info = layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .map { (lastVisible, total) -> total > 0 && lastVisible >= total - 1 - buffer }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }
}
