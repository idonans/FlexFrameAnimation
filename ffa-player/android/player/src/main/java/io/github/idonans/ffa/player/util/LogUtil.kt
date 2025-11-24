package io.github.idonans.ffa.player.util

import android.util.Log

object LogUtil {

    private const val TAG = "ffa.player"

    fun getStackTraceString(throwable: Throwable?): String {
        if (throwable == null) {
            return ""
        }
        return Log.getStackTraceString(throwable)
    }

    fun v(lazyMessage: () -> Any?) {
        this.println(level = Log.VERBOSE, lazyMessage = lazyMessage)
    }

    fun d(lazyMessage: () -> Any?) {
        this.println(level = Log.DEBUG, lazyMessage = lazyMessage)
    }

    fun i(lazyMessage: () -> Any?) {
        this.println(level = Log.INFO, lazyMessage = lazyMessage)
    }

    fun w(lazyMessage: () -> Any?) {
        this.println(level = Log.WARN, lazyMessage = lazyMessage)
    }

    fun e(lazyMessage: () -> Any?) {
        this.println(level = Log.ERROR, lazyMessage = lazyMessage)
    }

    private fun println(tag: String = TAG, level: Int, lazyMessage: () -> Any?) {
        if (Log.isLoggable(tag, level).not()) {
            return
        }

        Log.println(level, tag, lazyMessage()?.toString() ?: "")
    }

}