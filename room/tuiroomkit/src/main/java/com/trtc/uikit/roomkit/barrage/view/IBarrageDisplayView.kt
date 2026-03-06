package com.trtc.uikit.roomkit.barrage.view

import io.trtc.tuikit.atomicxcore.api.barrage.Barrage

interface IBarrageDisplayView {
    fun insertBarrages(vararg barrages: Barrage)
}
