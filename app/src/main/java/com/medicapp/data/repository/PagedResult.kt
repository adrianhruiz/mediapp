package com.medicapp.data.repository

data class PagedResult<T>(
    val items: List<T>,
    val hasMore: Boolean,
    val isFromCache: Boolean,
)

object PagingDefaults {
    const val PAGE_SIZE: Long = 20L
    const val PAGE_INCREMENT: Long = 20L
}
