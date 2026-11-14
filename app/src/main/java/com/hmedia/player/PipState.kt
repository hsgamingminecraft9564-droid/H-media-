package com.hmedia.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// True while the app is shown in the small Picture-in-Picture window. MainActivity keeps it up to date.
object PipState {
    var inPip by mutableStateOf(false)
}
