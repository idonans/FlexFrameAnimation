package io.github.idonans.ffa.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object Debug {

    private val _enable = MutableStateFlow(false)
    val enableFlow = _enable.asStateFlow()

    var enable: Boolean
        get() = _enable.value
        set(value) {
            _enable.value = value
        }

}