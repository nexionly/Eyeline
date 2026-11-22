package com.example.capture

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Coordinates hardware Volume Down key presses from MainActivity to the active
 * Teleprompter Studio screen without intercepting system volume controls elsewhere.
 */
object HardwareShutterBus {
    @Volatile
    var isStudioCaptureActive: Boolean = false

    @Volatile
    var isVolumeDownShutterEnabled: Boolean = true

    private val _shutterTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val shutterTrigger: SharedFlow<Unit> = _shutterTrigger.asSharedFlow()

    fun shouldInterceptVolumeDown(): Boolean =
        isStudioCaptureActive && isVolumeDownShutterEnabled

    fun emitShutterTrigger() {
        _shutterTrigger.tryEmit(Unit)
    }
}
