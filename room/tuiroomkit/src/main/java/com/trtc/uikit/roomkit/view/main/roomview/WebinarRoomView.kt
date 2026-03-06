package com.trtc.uikit.roomkit.view.main.roomview

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.constraintlayout.widget.ConstraintLayout
import com.tencent.cloud.tuikit.engine.common.TUICommonDefine
import com.tencent.cloud.tuikit.engine.common.TUIVideoView
import com.tencent.cloud.tuikit.engine.room.TUIRoomDefine
import com.tencent.cloud.tuikit.engine.room.TUIRoomDefine.VideoStreamType
import com.tencent.cloud.tuikit.engine.room.TUIRoomEngine
import com.tencent.cloud.tuikit.engine.room.TUIRoomObserver
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class VideoView(
    var userId: String = "",
    var videoView: TUIVideoView
)

class WebinarRoomView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    private val logger = RoomKitLogger.getLogger("WebinarRoomView")
    internal val oid: Int = hashCode()
    internal var roomID: String = ""
    private var mixVideoView = VideoView(videoView = TUIVideoView(context.applicationContext))
    private var multiStreamView = VideoView(videoView = TUIVideoView(context.applicationContext))
    private val widgetView: View = inflate(context.applicationContext, R.layout.roomkit_webinar_widget_view, null)
    private val avatarPlaceholder: ImageFilterView = widgetView.findViewById(R.id.iv_avatar_placeholder)

    private val roomEngine = TUIRoomEngine.sharedInstance()
    private val roomEngineObserver = RoomEngineObserver()
    private var participantStore: RoomParticipantStore? = null
    private var isObserving = false
    private var subscribeJob: Job? = null
    private var pushVideoParticipant: RoomParticipant? = null

    init {
        addView(mixVideoView.videoView, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        addView(multiStreamView.videoView, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        addView(widgetView, LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width * 9f / 16f).toInt()

        val newHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, newHeightMeasureSpec)

        logger.info("onMeasure: width=$width, height=$height (16:9 ratio)")
    }

    fun init(roomID: String) {
        this.roomID = roomID
        participantStore = RoomParticipantStore.create(roomID)
        startObservingIfNeeded()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        roomEngine.addObserver(roomEngineObserver)
        startObservingIfNeeded()
    }

    override fun onDetachedFromWindow() {
        roomEngine.removeObserver(roomEngineObserver)
        clearVideoView()
        stopObserving()
        super.onDetachedFromWindow()
    }

    private fun addObserver() {
        val store = participantStore ?: return
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            store.state.participantList.collect { participants ->
                val seatList = roomEngine.querySeatList()
                val firstSeatUserId = seatList.firstOrNull()?.userId
                if (firstSeatUserId.isNullOrEmpty()) {
                    return@collect
                }
                val pushVideoUser = participants.firstOrNull { it.userID == firstSeatUserId }
                if (pushVideoUser != null && pushVideoUser != pushVideoParticipant) {
                    logger.info("pushVideoUser state changed:${pushVideoUser.userID} ,cameraStatus:${pushVideoUser.cameraStatus}")
                    pushVideoParticipant = pushVideoUser
                    val shouldShowAvatar = pushVideoUser.cameraStatus != DeviceStatus.ON
                    if (shouldShowAvatar) {
                        loadAvatar(pushVideoUser)
                        widgetView.visibility = VISIBLE
                    } else {
                        widgetView.visibility = GONE
                    }
                }
            }
        }
    }

    private fun removeObserver() {
        subscribeJob?.cancel()
        subscribeJob = null
    }

    private fun startObservingIfNeeded() {
        if (isObserving) {
            return
        }
        if (roomID.isEmpty()) {
            return
        }
        addObserver()
        isObserving = true
    }

    private fun stopObserving() {
        if (!isObserving) {
            return
        }
        removeObserver()
        isObserving = false
    }

    private fun clearVideoView() {
        if (mixVideoView.userId.isNotEmpty()) {
            TUIRoomEngine.sharedInstance().setRemoteVideoView(mixVideoView.userId, VideoStreamType.CAMERA_STREAM, null)
        }
        if (multiStreamView.userId.isNotEmpty()) {
            TUIRoomEngine.sharedInstance()
                .setRemoteVideoView(multiStreamView.userId, VideoStreamType.CAMERA_STREAM, null)
        }
    }

    private inner class RoomEngineObserver : TUIRoomObserver() {
        override fun onUserVideoStateChanged(
            userId: String,
            streamType: VideoStreamType,
            hasVideo: Boolean,
            reason: TUIRoomDefine.ChangeReason
        ) {
            logger.info("onUserVideoStateChanged:$oid, userId:$userId, streamType:$streamType,hasVideo:$hasVideo,reason:$reason")
            val isMixUser: Boolean = userId.contains("_feedback_")
            if (userId == LoginStore.shared.loginState.loginUserInfo.value?.userID) {
                return
            }
            if (hasVideo) {
                if (isMixUser) {
                    mixVideoView.userId = userId
                    TUIRoomEngine.sharedInstance()
                        .setRemoteVideoView(userId, VideoStreamType.CAMERA_STREAM, mixVideoView.videoView)
                } else {
                    multiStreamView.userId = userId
                    TUIRoomEngine.sharedInstance()
                        .setRemoteVideoView(userId, VideoStreamType.CAMERA_STREAM, multiStreamView.videoView)
                }
                TUIRoomEngine.sharedInstance().startPlayRemoteVideo(
                    userId,
                    VideoStreamType.CAMERA_STREAM,
                    object : TUIRoomDefine.PlayCallback {
                        override fun onPlaying(userId: String?) {
                            logger.info("oid:$oid, onPlaying:userId$userId")
                        }

                        override fun onLoading(userId: String?) {
                            logger.info("oid:$oid, onLoading:userId$userId")
                        }

                        override fun onPlayError(
                            userId: String?,
                            error: TUICommonDefine.Error?,
                            message: String?
                        ) {
                            logger.error("oid:$oid, onPlayError:userId$userId, error$error, message$message")
                        }
                    })
            } else {
                TUIRoomEngine.sharedInstance().stopPlayRemoteVideo(userId, VideoStreamType.CAMERA_STREAM)
            }
        }
    }

    private fun loadAvatar(participant: RoomParticipant) {
        if (participant.avatarURL.isEmpty()) {
            avatarPlaceholder.setImageResource(R.drawable.roomkit_ic_default_avatar)
        } else {
            ImageLoader.load(
                context,
                avatarPlaceholder,
                participant.avatarURL,
                R.drawable.roomkit_ic_default_avatar
            )
        }
    }
}