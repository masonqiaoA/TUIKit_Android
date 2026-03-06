package com.trtc.uikit.roomkit.barrage.view.adapter

import io.trtc.tuikit.atomicxcore.api.barrage.Barrage

interface BarrageItemTypeDelegate {
    fun getItemType(position: Int, barrage: Barrage): Int
}
