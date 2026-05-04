package com.pulsestock.app.data.poarvault

import com.pulsestock.app.PulseLog
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SplitwiseAuthBus {
    private val _code = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val code: SharedFlow<String> = _code.asSharedFlow()

    fun deliver(code: String) {
        PulseLog.d("SplitwiseAuthBus", "deliver: emitting code (${code.length} chars), activeSubscribers=${_code.subscriptionCount.value}")
        val emitted = _code.tryEmit(code)
        PulseLog.d("SplitwiseAuthBus", "deliver: tryEmit result=$emitted")
    }
}
