package com.pulsestock.app.data.poarvault

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SplitwiseAuthBus {
    private val _code = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val code: SharedFlow<String> = _code.asSharedFlow()

    fun deliver(code: String) { _code.tryEmit(code) }
}
