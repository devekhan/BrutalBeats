package com.brutalbeats.player.output

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Feeds the Output Devices sheet, Now Playing and the mini player. */
@HiltViewModel
class OutputViewModel @Inject constructor(
    private val monitor: OutputDeviceMonitor
) : ViewModel() {

    val state: StateFlow<OutputState> = monitor.state

    init {
        monitor.start()
    }

    fun refresh() = monitor.refresh()

    /** Tap on a device: switch if Android allows, otherwise open the system output switcher. */
    fun select(device: OutputDevice) {
        if (!monitor.select(device)) monitor.openSystemSwitcher()
    }

    fun openSystemSwitcher() = monitor.openSystemSwitcher()
}
