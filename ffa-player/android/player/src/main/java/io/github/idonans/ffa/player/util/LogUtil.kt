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

    fun v(lazyMessage: () -> String?) {
        this.println(level = Log.VERBOSE, lazyMessage = lazyMessage)
    }

    fun d(lazyMessage: () -> String?) {
        this.println(level = Log.DEBUG, lazyMessage = lazyMessage)
    }

    fun i(lazyMessage: () -> String?) {
        this.println(level = Log.INFO, lazyMessage = lazyMessage)
    }

    fun w(lazyMessage: () -> String?) {
        this.println(level = Log.WARN, lazyMessage = lazyMessage)
    }

    fun e(lazyMessage: () -> String?) {
        this.println(level = Log.ERROR, lazyMessage = lazyMessage)
    }

    private fun println(tag: String = TAG, level: Int, lazyMessage: () -> String?) {
        if (Log.isLoggable(tag, level).not()) {
            return
        }

        Log.println(level, tag, lazyMessage() ?: "")
    }

}