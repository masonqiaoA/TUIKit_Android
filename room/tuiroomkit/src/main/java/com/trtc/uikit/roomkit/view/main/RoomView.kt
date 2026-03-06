package com.trtc.uikit.roomkit.view.main

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.constraintlayout.widget.ConstraintLayout
import com.trtc.uikit.roomkit.view.main.roomview.StandardRoomView
import com.trtc.uikit.roomkit.view.main.roomview.WebinarRoomView
import io.trtc.tuikit.atomicxcore.api.room.RoomType

/**
 * Main room view component displaying video grid.
 * Manages video rendering, layout strategies, and participant interactions in the room.
 */
class RoomView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    ConstraintLayout(context, attrs, defStyleAttr) {

    fun init(roomID: String, roomType: RoomType) {
        removeAllViews()
        val rootView = if (roomType == RoomType.WEBINAR) {
            WebinarRoomView(context).apply {
                init(roomID)
            }
        } else {
            StandardRoomView(context).apply {
                init(roomID)
            }
        }
        val params = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        addView(rootView, params)
    }
}
