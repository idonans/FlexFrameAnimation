package io.github.idonans.ffa.player.util

import io.github.idonans.ffa.player.Debug
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.system.measureNanoTime

@OptIn(ExperimentalContracts::class)
fun measureTimeIfDebug(description: String? = null, block: () -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    if (Debug.enable) {
        measureNanoTime { block() }.also { time ->
            LogUtil.d {
                "$description duration:${time}ns(${time / 1_000_000}ms)"
            }
        }
    } else {
        block()
    }
}