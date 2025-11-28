package io.github.idonans.ffa.player.util

import android.annotation.SuppressLint
import android.content.res.Configuration
import java.lang.reflect.Field

@SuppressLint("DiscouragedPrivateApi")
object ReflectExt {

    private val mConfigurationSeq: Field? by lazy {
        var field: Field? = null
        try {
            field = Configuration::class.java.getDeclaredField("seq")
            field.isAccessible = true
        } catch (e: Throwable) {
            LogUtil.e {
                LogUtil.getStackTraceString(e)
            }
        }
        field
    }

    fun getConfigurationSeq(configuration: Configuration): Int {
        var seq: Int? = null
        try {
            seq = mConfigurationSeq?.get(configuration) as Int?
        } catch (e: Throwable) {
            LogUtil.e {
                LogUtil.getStackTraceString(e)
            }
        }
        return seq ?: 0
    }

}