package io.github.idonans.ffa.player.layer

/**
 * 允许包含子 Layer
 */
open class FlexFrameLayerGroup : FlexFrameLayer() {

    private val mChildren = mutableListOf<FlexFrameLayer>()

    fun addLayer(index: Int = -1, layer: FlexFrameLayer) {
        addLayerInner(index, layer)
    }

    private fun addLayerInner(index: Int = -1, layer: FlexFrameLayer) {
        val insertIndex = if (index < 0) {
            mChildren.size
        } else {
            index
        }
        mChildren.add(insertIndex, layer)
    }

}