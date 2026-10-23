package com.catmap.dubai.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Explicit foreground/reconnect refresh. No timer polls hidden documents. */
object ForegroundRefresh {
    private val state = MutableStateFlow(0L)
    val version = state.asStateFlow()
    fun refresh() { state.value += 1 }
}
