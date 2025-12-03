package io.github.idonans.ffa.player.layer

import android.content.Context
import io.github.idonans.ffa.player.FfaContext
import io.github.idonans.ffa.player.PlayState
import io.github.idonans.ffa.player.ffab.Ffab

/**
 * 动画层
 */
open class FlexFrameLayer {

    /**
     * 该动画层关联的 `.ffab` 资源文件
     */
    var ffab: Ffab? = null

    /**
     * 该动画层单独的 PlayState。如果这是一个 FlexFrameLayerGroup, 此 PlayState 也同时会作用于所有子 Layer。
     * 当 playState 为 null 时，会优先使用此 Layer 所属的 FlexFrameLayerGroup 中配置的 PlayState。
     */
    var playState: PlayState? = null

    /**
     * x 坐标（像素），相对于父节点
     */
    var x: Float = 0f

    /**
     * y 坐标（像素），相对于父节点
     */
    var y: Float = 0f

    /**
     * 宽度（像素）
     */
    var width: Float = 0f

    /**
     * 高度（像素）
     */
    var height: Float = 0f

    /**
     * 帧率，每秒钟播放的帧数量。当帧率为负数或 0 时，不会渲染任何内容。
     */
    var rate: Float = 0f

    /**
     * 在动画开始前是否保持绘制第一帧
     */
    var fillStart: Boolean = false

    /**
     * 在动画结束后是否保持绘制 `最后一帧` 。注意此处的最后一帧不一定是 ffab 文件中的最后一张图片，受 duration 值的
     * 影响，可能最后看见的是 ffab 文件中的任意一张图片。
     * @see duration
     */
    val fillEnd: Boolean = false

    /**
     * 动画的可运行时间长度。当此值非负时（大于或等于0）, 且动画的 uptimeRunning 超过了此时间，则基于此时间计算
     * frameIndexToDraw, 即：动画运行时间的逻辑值为 max(uptimeRunning, duration)。
     * 该值会影响 fillEnd 的效果，即 fillEnd 保持绘制的最后一帧是此时计算出来的 frameIndexToDraw, 而不是
     * ffab 文件中的序列帧的最后一张图片。
     * 在 duration 范围内，帧动画是循环的从第一帧播放到最后一帧。
     * 结合 duration、rate，可以实现控制动画的循环播放次数的效果。
     * @see visibleDuration
     */
    val duration: Long = -1L

    /**
     * 动画的可见时间长度。当此值非负时（大于或等于0），且动画的 uptimeRunning 超过了此时间，则不会绘制当前内容。
     * 即使 fillStart, fillEnd 被设置为 true, 也不会绘制内容。
     * 当此值为 0 时，表示动画总是不可见，即不会绘制任何内容。
     * 当此值为负值时，表示动画总是可见的。
     * 当 visibleDuration 超过 duration，且 fillEnd 为 true, 帧动画会固定绘制 duration 时刻的帧。
     * @see duration
     */
    val visibleDuration: Long = -1L

    /**
     * 在每次绘制时，计算最终应当绘制的帧状态。这是一个仅在帧绘制方法中可使用的参数。
     * @see drawFrame
     */
    protected var mLayerFrameState = LayerFrameState()

    internal fun getBestPlayState(parentPlayState: PlayState?): PlayState? {
        return playState ?: parentPlayState
    }

    /**
     * 绘制当前 Layer 的内容
     */
    fun drawFrame(
        context: Context,
        ffaContext: FfaContext,
        parentLayerDrawState: LayerFrameState,
    ) {
        onUpdateLayerFrameState(
            context = context,
            ffaContext = ffaContext,
            parentLayerDrawState = parentLayerDrawState,
        )

        if (mLayerFrameState.frameIndexToDraw == null) {
            // 不渲染任何内容
            return
        }

        // 绘制 mLayerFrameState.frameIndexToDraw 指向的帧
        mLayerFrameState.ffabInfo!!.frameData(
            //
            mLayerFrameState.frameIndexToDraw!!
        ).also { frameData ->
            // TODO 绘制 frameData
        }
    }

    /**
     * 根据环境信息计算当前帧应该绘制的最终参数，所有的最终绘制参数都会被设置到 mLayerFrameState。
     * 可动态调整绘制状态，实现任意特殊的动画效果，类似：View 的属性动画，Animation 动画，插值器等。
     *
     * @see mLayerFrameState
     */
    protected fun onUpdateLayerFrameState(
        context: Context,
        ffaContext: FfaContext,
        parentLayerDrawState: LayerFrameState,
    ) {
        mLayerFrameState.resetTo(
            layer = this,
            context = context,
            parentLayerDrawState = parentLayerDrawState,
        )
        mLayerFrameState.calculateFrameIndexToDraw()
    }

}