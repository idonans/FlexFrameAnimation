package io.github.idonans.ffa.player.layer

import android.content.Context
import io.github.idonans.ffa.player.PlayState
import io.github.idonans.ffa.player.ffab.Ffab
import io.github.idonans.ffa.player.ffab.FfabInfo

/**
 * 当前帧的绘制状态。Viewport 是指当前帧所在 Surface 的区域。
 */
data class LayerFrameState(
    /**
     * 当前帧在 Viewport 中的 x 坐标
     * @see FlexFrameLayer.x
     */
    var xInViewport: Float = 0f,
    /**
     * 当前帧在 Viewport 中的 y 坐标
     * @see FlexFrameLayer.y
     */
    var yInViewport: Float = 0f,
    /**
     * 当前帧在 Viewport 中的显示宽度
     * @see FlexFrameLayer.width
     */
    var widthInViewport: Float = 0f,
    /**
     * 当前帧在 Viewport 中的显示高度
     * @see FlexFrameLayer.height
     */
    var heightInViewport: Float = 0f,
    /**
     * @see FlexFrameLayer.rate
     */
    var rate: Float = 0f,
    /**
     * @see FlexFrameLayer.fillStart
     */
    var fillStart: Boolean = false,
    /**
     * @see FlexFrameLayer.fillEnd
     */
    var fillEnd: Boolean = false,
    /**
     * @see FlexFrameLayer.duration
     */
    var duration: Long = -1L,
    /**
     * @see FlexFrameLayer.visibleDuration
     */
    var visibleDuration: Long = -1L,
    /**
     * @see FlexFrameLayer.playState
     * @see FlexFrameLayer.getBestPlayState
     */
    var playState: PlayState? = null,
    /**
     * @see PlayState.uptimeRunning
     */
    var uptimeRunning: Long? = null,
    /**
     * @see FlexFrameLayer.ffab
     * @see Ffab
     */
    var ffabInfo: FfabInfo? = null,
    /**
     * 当前应该绘制的帧 index。当为 null 时，表示不绘制任何帧。
     * 第一帧的 index 为 0。
     * 允许为负值，例如：-1 表示最后一帧，-2 表示倒数第二帧。
     *
     * `index = (imageCount + frameIndexToDraw) % imageCount`
     *
     * @see FfabInfo.imageCount
     * @see FfabInfo.frameData
     * @see uptimeRunning
     */
    var frameIndexToDraw: Int? = null,
) {

    // 内部临时变量：动画的视觉运行时长
    // 在视觉运行时长内，动画会发生变化，反之则动画内容固定在视觉上的最后时刻
    private var mTmpDuration: Double = 0.0

    // 内部临时变量：动画播放一帧需要的时长
    private var mTmpDurationPerFrame: Double = 0.0

    fun resetTo(
        layer: FlexFrameLayer,
        context: Context,
        parentLayerDrawState: LayerFrameState,
    ) {
        this.xInViewport = parentLayerDrawState.xInViewport + layer.x
        this.yInViewport = parentLayerDrawState.yInViewport + layer.y
        this.widthInViewport = layer.width
        this.heightInViewport = layer.height
        this.rate = layer.rate
        this.fillStart = layer.fillStart
        this.fillEnd = layer.fillEnd
        this.duration = layer.duration
        this.visibleDuration = layer.visibleDuration
        this.playState = layer.getBestPlayState(parentLayerDrawState.playState)
        this.uptimeRunning = this.playState?.uptimeRunning()
        this.ffabInfo = layer.ffab?.getInfo(context)

        // 注意需要设置 frameIndexToDraw 为 null, 即默认为不绘制任何帧
        this.frameIndexToDraw = null
    }

    /**
     * 计算当前应该渲染的帧 index 并赋值给 frameIndexToDraw
     */
    fun calculateFrameIndexToDraw() {
        if (this.widthInViewport <= 0 || this.heightInViewport <= 0) {
            // 没有显示区域
            return
        }
        if (this.ffabInfo == null || this.ffabInfo!!.imageCount <= 0) {
            // 没有帧动画资源
            return
        }
        if (this.visibleDuration == 0L) {
            // 动画不可见
            return
        }
        if (this.uptimeRunning == null) {
            // 没有播放参数
            return
        }
        if (this.rate <= 0f) {
            // 帧率无效，不渲染任何内容
            return
        }
        if (this.uptimeRunning!! < 0) {
            // 动画尚未开始播放
            if (this.fillStart) {
                // 在动画尚未开始播放时，绘制第一帧
                this.frameIndexToDraw = 0
            }
            return
        }

        // 此时 this.uptimeRunning >= 0
        this.mTmpDuration = (this.uptimeRunning!!).toDouble()
        if (this.visibleDuration > 0 && this.mTmpDuration > this.visibleDuration) {
            // 配置了明确的动画运行可见时间长度，且动画已经运行超过可见时间长度，动画不再渲染
            return
        }

        if (this.duration > 0L && this.mTmpDuration > this.duration) {
            // 配置了明确的动画最大运行时长，且动画的视觉运行时长超过此最大可运行时长
            // 那么：此时动画的视觉运行时长为此最大可运行时长
            // 即：在此最大可运行时长之后的时间，动画是不会变化的。
            mTmpDuration = this.duration.toDouble()

            if (!fillEnd) {
                // 无需渲染视觉上看到的 `最后一帧`
                return
            }
        }

        // 播放一帧需要的时间
        mTmpDurationPerFrame = 1000.0 / rate

        // 计算在 mTmpDuration 运行时间对应的 index
        // 注意：需要对 imageCount 取余，因为存在循环播放的情况。
        this.frameIndexToDraw =
            ((mTmpDuration / mTmpDurationPerFrame).toLong() % ffabInfo!!.imageCount).toInt()
    }

}