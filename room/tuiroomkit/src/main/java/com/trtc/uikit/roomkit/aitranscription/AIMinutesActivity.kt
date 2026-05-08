package com.trtc.uikit.roomkit.aitranscription

import android.os.Bundle
import android.widget.FrameLayout
import io.trtc.tuikit.atomicx.common.FullScreenActivity
import com.trtc.uikit.roomkit.aitranscription.minutesview.AIMinutesView
import com.trtc.uikit.roomkit.aitranscription.minutesview.AIMinutesViewListener
import com.trtc.uikit.roomkit.aitranscription.repository.AITranscriberRepository

class AIMinutesActivity : FullScreenActivity(), AIMinutesViewListener {

    companion object {
        private var pendingRepository: AITranscriberRepository? = null

        fun bindRepository(repository: AITranscriberRepository) {
            pendingRepository = repository
        }
    }

    private lateinit var minutesView: AIMinutesView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        minutesView = AIMinutesView(this)
        minutesView.listener = this
        setContentView(minutesView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        pendingRepository?.let {
            minutesView.bindRepository(it)
        }
    }

    override fun onMinutesViewBackClicked() {
        finish()
    }
}
