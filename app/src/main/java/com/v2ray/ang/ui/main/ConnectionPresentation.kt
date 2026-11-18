package com.v2ray.ang.ui.main

/** Service/launch request state, never the result of a server test or an animation. */
internal fun MainUiState.connectionPresentation(): ConnectionStatus = when {
    isRunning -> ConnectionStatus.Connected
    isConnecting -> ConnectionStatus.Connecting
    else -> ConnectionStatus.Disconnected
}

internal fun MainUiState.connectionActionAvailable(pending: Boolean): Boolean =
    !pending && !isConnecting && (isRunning || !selectedGuid.isNullOrBlank())
