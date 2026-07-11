package com.medicapp.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Composable
fun LazyListState.OnBottomReached(buffer: Int = 5, onLoadMore: () -> Unit) {
    LaunchedEffect(this) {
        snapshotFlow { layoutInfo }
            .map { info ->
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 1 - buffer
            }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }
}
