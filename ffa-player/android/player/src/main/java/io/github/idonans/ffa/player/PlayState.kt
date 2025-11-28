package io.github.idonans.ffa.player

import android.os.SystemClock
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * 帧动画的播放状态
 */
class PlayState {

    private val mObjectTag by lazy {
        "${javaClass.simpleName}@${System.identityHashCode(this)}"
    }

    // 动画是否正在运行
    private var mRunning = false

    // 动画开始时间，调用 [start] 方法时的时间，单位毫秒（相对时间）
    private var mStartTime: Long = -1L

    // 动画开始时间偏移量。如果动画需要从中间的某个时刻开始执行，则可以通过设置此时间偏移量。
    // 允许传递负值，即表达了此动画需要延迟执行
    // 注意：此值不会改变动画的执行顺序，仅相当于调整按下 `动画开始键` 的时间。
    private var mStartTimeOffset: Long = 0L

    // 动画累计的休眠时间，单位毫秒
    private var mSleepTimeTotal: Long = 0L

    // 如果动画正在休眠，记录最近一次休眠的开始时间，调用 [pause]方法时的时间，单位毫秒（相对时间）
    private var mSleepTimeStart: Long = -1L

    /**
     * 开始执行动画
     *
     * @param reset 是否重置动画时间。重置动画时间的含义是指：将动画执行的开始时间设置为当前时刻。
     * @param startTimeOffset 动画开始时间偏移量。如果动画需要从中间的某个时刻开始执行，则可以通
     * 过设置此时间偏移量。允许传递负值，即表达了此动画需要延迟执行。
     */
    fun start(reset: Boolean = true, startTimeOffset: Long = mStartTimeOffset) = doWithLock {
        mStartTimeOffset = startTimeOffset

        if (reset || mStartTime <= 0L) {
            // 重置动画，或者动画尚未开始
            mStartTime = SystemClock.uptimeMillis()
            mSleepTimeTotal = 0L
            mSleepTimeStart = -1L
        } else {
            // 累计 sleep 时间
            if (mSleepTimeStart > 0L) {
                val sleepTimeDuration = SystemClock.uptimeMillis() - mSleepTimeStart
                if (sleepTimeDuration > 0) {
                    mSleepTimeTotal += sleepTimeDuration
                }
                mSleepTimeStart = -1L
            }
        }

        mRunning = true
    }

    /**
     * 停止执行动画
     *
     * @param reset 是否重置动画时间
     */
    fun stop(reset: Boolean = true) = doWithLock {
        if (reset || mStartTime <= 0L) {
            // 停止动画，或者动画尚未开始
            mStartTime = -1L
            mSleepTimeTotal = 0L
            mSleepTimeStart = -1L
        } else {
            // sleep 动画
            // 动画正在运行，进入 sleep 状态
            if (mSleepTimeStart <= 0L) {
                mSleepTimeStart = SystemClock.uptimeMillis()
            }
        }

        mRunning = false
    }

    /**
     * 恢复动画的执行
     */
    fun resume() {
        start(false)
    }

    /**
     * 暂停动画执行
     */
    fun pause() {
        stop(false)
    }

    /**
     * 动画是否正在运行（处于 start 或 resume 状态）
     */
    fun isRunning(): Boolean = doWithLock {
        mRunning
    }

    /**
     * 动画的实际执行时长（不包括 sleep 时间），单位毫秒
     */
    fun uptimeRunning(): Long = doWithLock {
        if (mStartTime <= 0L) {
            // 动画尚未开始
            return 0L
        }

        var uptimeRunning = SystemClock.uptimeMillis() - mStartTime + mStartTimeOffset
        if (uptimeRunning < 0L) {
            // 动画尚未到达开始时间，这通常是因为设置了值为负数的 mStartTimeOffset
            return 0L
        }

        // 减去 sleep 时间
        if (mSleepTimeTotal > 0L) {
            uptimeRunning -= mSleepTimeTotal
        }
        if (mSleepTimeStart > 0L) {
            // 当前正在 sleep
            val sleepTimeDuration = SystemClock.uptimeMillis() - mSleepTimeStart
            if (sleepTimeDuration > 0) {
                uptimeRunning -= sleepTimeDuration
            }
        }

        require(uptimeRunning >= 0L) {
            "$mObjectTag uptimeRunning:$uptimeRunning, mStartTime:$mStartTime," +
                    //
                    " mSleepTimeTotal:$mSleepTimeTotal, mSleepTimeStart:$mSleepTimeStart"
        }

        return uptimeRunning
    }

    @OptIn(ExperimentalContracts::class)
    inline fun <T> doWithLock(block: (PlayState) -> T): T {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }
        return synchronized(this@PlayState) {
            block(this@PlayState)
        }
    }

}
