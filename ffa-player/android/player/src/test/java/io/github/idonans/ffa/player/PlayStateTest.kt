package io.github.idonans.ffa.player

import android.os.SystemClock
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class PlayStateTest {

    private var mCurrentTime = 0L

    @Before
    fun setUp() {
        println("setUp")
        // Mock SystemClock.uptimeMillis()
        mockkStatic(SystemClock::class)
        every { SystemClock.uptimeMillis() } answers { mCurrentTime }
        mCurrentTime = 1000L // 初始时间
    }

    @After
    fun tearDown() {
        println("tearDown")
        // Unmock SystemClock.uptimeMillis()
        unmockkStatic(SystemClock::class)
    }

    @Test
    fun testInitialState() {
        println("testInitialState")
        val playState = PlayState()

        // 验证初始状态
        Assert.assertFalse("Initial state should not be running", playState.isRunning())
        Assert.assertEquals("Initial uptime should be -1", -1L, playState.uptimeRunning())
    }

    @Test
    fun testStartWithReset() {
        println("testStartWithReset")
        val playState = PlayState()

        // 启动动画（默认 reset=true）
        playState.start()

        // 验证状态
        Assert.assertTrue("Should be running after start", playState.isRunning())
        Assert.assertTrue("Uptime should be positive after start", playState.uptimeRunning() >= 0)
    }

    @Test
    fun testStartWithResetAndOffset() {
        println("testStartWithResetAndOffset")
        val playState = PlayState()

        // 启动动画并设置偏移量
        val offset = 500L
        playState.start(reset = true, startTimeOffset = offset)

        // 验证状态
        Assert.assertTrue("Should be running after start", playState.isRunning())
        Assert.assertEquals("Uptime should include offset", offset, playState.uptimeRunning())
    }

    @Test
    fun testStartWithoutReset() {
        println("testStartWithoutReset")
        val playState = PlayState()

        // 首次启动动画
        playState.start()
        val firstStartTime = mCurrentTime

        // 模拟时间流逝
        mCurrentTime += 1000L

        // 暂停动画
        playState.pause()
        val pauseTime = mCurrentTime

        // 模拟时间流逝
        mCurrentTime += 500L

        // 不重置启动动画
        playState.start(reset = false)

        // 验证状态
        Assert.assertTrue("Should be running after start", playState.isRunning())

        // uptimeRunning 应该不包括暂停期间的时间
        val expectedUptime = mCurrentTime - firstStartTime - (mCurrentTime - pauseTime)
        Assert.assertEquals(
            "Uptime should exclude pause time", expectedUptime, playState.uptimeRunning()
        )
    }

    @Test
    fun testStartWithNegativeOffset() {
        println("testStartWithNegativeOffset")
        val playState = PlayState()

        // 启动动画并设置负偏移量（延迟执行）
        val offset = -500L
        playState.start(reset = true, startTimeOffset = offset)

        // 验证状态
        Assert.assertTrue("Should be running after start", playState.isRunning())
        Assert.assertEquals(
            "Uptime should be -1 with negative offset",
            -1L,
            playState.uptimeRunning(),
        )

        // 模拟时间流逝
        mCurrentTime += 500L
        Assert.assertTrue(
            "Uptime should be 0 after negative offset",
            playState.uptimeRunning() == 0L,
        )

        // 模拟时间流逝
        mCurrentTime += 100L
        Assert.assertTrue(
            "Uptime should be 100 with time pass",
            playState.uptimeRunning() == 100L,
        )
    }

    @Test
    fun testStopWithReset() {
        println("testStopWithReset")
        val playState = PlayState()

        // 启动动画
        playState.start()
        Assert.assertTrue("Should be running after start", playState.isRunning())

        // 停止动画（默认 reset=true）
        playState.stop()

        // 验证状态
        Assert.assertFalse("Should not be running after stop", playState.isRunning())
        Assert.assertEquals("Uptime should be -1 after reset stop", -1L, playState.uptimeRunning())
    }

    @Test
    fun testStopWithoutReset() {
        println("testStopWithoutReset")
        val playState = PlayState()

        // 启动动画
        playState.start()
        Assert.assertTrue("Should be running after start", playState.isRunning())

        // 模拟时间流逝
        mCurrentTime += 1200L

        // 停止动画但不重置
        playState.stop(reset = false)

        // 验证状态
        Assert.assertFalse("Should not be running after stop", playState.isRunning())

        // 模拟时间流逝
        mCurrentTime += 500L

        // 恢复动画
        playState.resume()

        // uptimeRunning 应该等于之前运行的时间（1200ms）
        Assert.assertTrue(
            "Should be running after resume",
            playState.isRunning(),
        )
        Assert.assertEquals(
            "Uptime should be 1200 after resume",
            1200L,
            playState.uptimeRunning(),
        )
    }

    @Test
    fun testPauseAndResume() {
        println("testPauseAndResume")
        val playState = PlayState()

        // 启动动画
        playState.start()
        Assert.assertTrue("Should be running after start", playState.isRunning())

        // 暂停动画
        playState.pause()
        Assert.assertFalse("Should not be running after pause", playState.isRunning())

        // 恢复动画
        playState.resume()
        Assert.assertTrue("Should be running after resume", playState.isRunning())
    }

    @Test
    fun testUptimeRunningDuringPause() {
        println("testUptimeRunningDuringPause")
        val playState = PlayState()

        // 启动动画
        playState.start()
        val startTime = mCurrentTime

        // 模拟时间流逝
        mCurrentTime += 1000L

        // 暂停动画
        playState.pause()
        val pauseTime = mCurrentTime

        // 模拟时间流逝
        mCurrentTime += 500L

        // uptimeRunning 应该不包括暂停期间的时间
        val expectedUptime = pauseTime - startTime
        Assert.assertEquals(
            "Uptime should exclude pause time", expectedUptime, playState.uptimeRunning()
        )
    }

    @Test
    fun testUptimeRunningAfterMultiplePauses() {
        println("testUptimeRunningAfterMultiplePauses")
        val playState = PlayState()

        // 启动动画
        playState.start()
        val startTime = mCurrentTime

        // 模拟时间流逝
        mCurrentTime += 500L

        // 第一次暂停
        playState.pause()
        val firstPauseTime = mCurrentTime

        // 模拟时间流逝
        mCurrentTime += 300L

        // 恢复动画
        playState.resume()
        val resumeTime = mCurrentTime

        // 模拟时间流逝
        mCurrentTime += 400L

        // 第二次暂停
        playState.pause()
        val secondPauseTime = mCurrentTime

        // 模拟时间流逝
        mCurrentTime += 200L

        // uptimeRunning 应该不包括所有暂停期间的时间
        val expectedUptime = (firstPauseTime - startTime) + (secondPauseTime - resumeTime)
        Assert.assertEquals(
            "Uptime should exclude all pause times",
            expectedUptime,
            playState.uptimeRunning(),
        )
    }

    @Test
    fun testDoWithLock() {
        println("testDoWithLock")
        val playState = PlayState()

        // 使用 doWithLock 执行操作
        val result = playState.doWithLock { state ->
            Assert.assertNotNull("State should not be null", state)
            "test result"
        }

        // 验证结果
        Assert.assertEquals("Result should match", "test result", result)
    }

}
