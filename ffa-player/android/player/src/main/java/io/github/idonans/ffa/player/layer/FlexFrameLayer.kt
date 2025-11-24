package io.github.idonans.ffa.player.layer

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

    fun getBestPlayState(parentPlayState: PlayState?): PlayState? {
        return playState ?: parentPlayState
    }

}