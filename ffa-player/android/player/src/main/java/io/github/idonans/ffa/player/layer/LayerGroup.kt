package io.github.idonans.ffa.player.layer

/**
 * 允许包含子 Layer
 */
open class LayerGroup : Layer() {

    private val mChildren = mutableListOf<Layer>()

    fun addLayer(layer: Layer, index: Int = -1) {
        addLayerInner(layer, index)
    }

    private fun addLayerInner(layer: Layer, index: Int = -1) {
        val insertIndex = if (index < 0) {
            mChildren.size
        } else {
            index
        }
        mChildren.add(insertIndex, layer)
    }

}