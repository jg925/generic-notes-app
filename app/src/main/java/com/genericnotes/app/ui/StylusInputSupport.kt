package com.genericnotes.app.ui

import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun rememberSupportsTrueStylusInput(): Boolean {
    val context = LocalContext.current
    var supportsTrueStylusInput by remember(context) { mutableStateOf(context.supportsTrueStylusInput()) }

    DisposableEffect(context) {
        val inputManager = context.getSystemService(InputManager::class.java)
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                supportsTrueStylusInput = context.supportsTrueStylusInput()
            }

            override fun onInputDeviceChanged(deviceId: Int) {
                supportsTrueStylusInput = context.supportsTrueStylusInput()
            }

            override fun onInputDeviceRemoved(deviceId: Int) {
                supportsTrueStylusInput = context.supportsTrueStylusInput()
            }
        }

        inputManager?.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
        onDispose {
            inputManager?.unregisterInputDeviceListener(listener)
        }
    }

    return supportsTrueStylusInput
}

private fun android.content.Context.supportsTrueStylusInput(): Boolean =
    InputDevice.getDeviceIds().any { deviceId ->
        InputDevice.getDevice(deviceId)?.let { inputDevice ->
            inputDevice.supportsSource(InputDevice.SOURCE_STYLUS) ||
                inputDevice.supportsSource(InputDevice.SOURCE_BLUETOOTH_STYLUS)
        } == true
    }
