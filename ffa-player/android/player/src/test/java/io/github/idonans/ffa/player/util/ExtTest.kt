package io.github.idonans.ffa.player.util

import android.util.Log
import io.github.idonans.ffa.player.Debug
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class ExtTest {

    @Before
    fun setUp() {
        println("setUp")
        // Mock android.util.Log
        mockkStatic(Log::class)
        every { Log.isLoggable(any(), any()) } returns true
        every { Log.println(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        println("tearDown")
        // Unmock android.util.Log
        unmockkStatic(Log::class)
    }

    @Test
    fun testMeasureTimeIfDebug_WhenDebugIsEnabled() {
        println("testMeasureTimeIfDebug_WhenDebugIsEnabled")
        // 设置 Debug.enable 为 true
        Debug.enable = true

        var blockExecuted = false

        // 调用被测试的函数
        measureTimeIfDebug {
            blockExecuted = true
        }

        // 验证 block 被执行
        Assert.assertTrue(
            "Block should be executed when debug is enabled",
            blockExecuted,
        )

        // 验证 Log.println 被调用
        // 由于我们使用的是 mockStatic，我们可以验证这个行为
        verify(exactly = 1) { Log.println(any(), any(), any()) }
    }

    @Test
    fun testMeasureTimeIfDebug_WhenDebugIsDisabled() {
        println("testMeasureTimeIfDebug_WhenDebugIsDisabled")
        // 设置 Debug.enable 为 false
        Debug.enable = false

        var blockExecuted = false

        // 调用被测试的函数
        measureTimeIfDebug {
            blockExecuted = true
        }

        // 验证 block 被执行
        Assert.assertTrue(
            "Block should be executed even when debug is disabled",
            blockExecuted,
        )

        // 验证 Log.println 没有被调用（因为 Debug.enable 为 false）
        // 由于我们使用的是 mockStatic，我们可以验证这个行为
        verify(exactly = 0) { Log.println(any(), any(), any()) }
    }

}
