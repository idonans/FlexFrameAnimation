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
     * @see FlexFrameLayer.repeatCount
     */
    var repeatCount: Int = 0,
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
     * 当前应该绘制的帧 index
     *
     * @see FfabInfo.imageCount
     * @see FfabInfo.frameData
     * @see uptimeRunning
     */
    var frameIndexToDraw: Int? = null,
) {

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
        this.repeatCount = layer.repeatCount
        this.duration = layer.duration
        this.visibleDuration = layer.visibleDuration
        this.playState = layer.getBestPlayState(parentLayerDrawState.playState)
        this.uptimeRunning = this.playState?.uptimeRunning()
        this.ffabInfo = layer.ffab?.getInfo(context)
        this.frameIndexToDraw = null
    }

    /**
     * 计算当前应该渲染的帧 index 并赋值给 frameIndexToDraw
     */
    fun calculateFrameIndexToDraw() {

    }

}