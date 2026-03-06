package com.trtc.uikit.roomkit

import android.os.Bundle
import io.trtc.tuikit.atomicx.common.FullScreenActivity
import com.trtc.uikit.roomkit.view.RoomHomeView

/**
 * RoomHomeActivity - Home screen container activity.
 * Pure container that loads RoomHomeView with join/create room options.
 */
class RoomHomeActivity : FullScreenActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(RoomHomeView(this))
    }
}