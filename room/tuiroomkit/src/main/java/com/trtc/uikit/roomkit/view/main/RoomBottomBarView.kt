package com.trtc.uikit.roomkit.view.main

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.base.operator.DeviceOperator
import com.trtc.uikit.roomkit.base.ui.BaseView
import com.trtc.uikit.roomkit.base.ui.RoomPopupDialog
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import io.trtc.tuikit.atomicxcore.api.room.RoomType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * RoomBottomBarView - Bottom control bar with 3 action buttons.
 * Controls: Participants, Microphone, and Camera.
 * Subscribes to participant list and local participant device status changes.
 */
class RoomBottomBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseView(context, attrs, defStyleAttr) {

    private val logger = RoomKitLogger.getLogger("RoomBottomBarView")

    private val scope = CoroutineScope(Dispatchers.Main)
    private var subscribeJob: Job? = null
    private val deviceOperator by lazy { DeviceOperator(context) }

    private val llParticipants: LinearLayout by lazy { findViewById(R.id.ll_participants) }
    private val tvParticipants: TextView by lazy { findViewById(R.id.tv_participants) }

    private val llMicrophone: LinearLayout by lazy { findViewById(R.id.ll_microphone) }
    private val ivMicrophone: ImageView by lazy { findViewById(R.id.iv_microphone) }
    private val tvMicrophone: TextView by lazy { findViewById(R.id.tv_microphone) }

    private val llCamera: LinearLayout by lazy { findViewById(R.id.ll_camera) }
    private val ivCamera: ImageView by lazy { findViewById(R.id.iv_camera) }
    private val tvCamera: TextView by lazy { findViewById(R.id.tv_camera) }

    private var roomType = RoomType.STANDARD
    private var participantStore: RoomParticipantStore? = null
    private val roomStore = RoomStore.shared()

    private var roomParticipantListViewDialog: RoomPopupDialog? = null
    private var currentRoomID: String? = null

    fun init(roomID: String, roomType: RoomType) {
        this.roomType = roomType
        removeAllViews()
        if (roomType == RoomType.WEBINAR) {
            LayoutInflater.from(context).inflate(R.layout.roomkit_bottom_bar_webinar_view, this)
        } else {
            LayoutInflater.from(context).inflate(R.layout.roomkit_bottom_bar_standard_view, this)
        }
        initView()
        super.init(roomID)
        llCamera.visibility = if (roomType == RoomType.WEBINAR) GONE else VISIBLE
    }

    override fun initStore(roomID: String) {
        currentRoomID = roomID
        participantStore = RoomParticipantStore.create(roomID)
    }

    override fun addObserver() {
        val participantStore = participantStore ?: return

        subscribeJob = scope.launch {
            launch {
                roomStore.state.currentRoom.collect { roomInfo ->
                    roomInfo?.let {
                        updateMicrophoneStatus()
                        updateCameraStatus()
                        updateParticipantCount(roomInfo.participantCount)
                    }
                }
            }

            launch {
                participantStore.state.localParticipant.collect { localParticipant ->
                    localParticipant?.let {
                        updateMicrophoneStatus()
                        updateCameraStatus()
                    }
                }
            }

            launch {
                participantStore.state.participantList.collect { participants ->
                    updateMicrophoneStatus()
                }
            }
        }
    }

    override fun removeObserver() {
        subscribeJob?.cancel()
        subscribeJob = null
        scope.cancel()
        roomParticipantListViewDialog?.dismiss()
        roomParticipantListViewDialog = null
    }

    private fun initView() {
        llParticipants.setOnClickListener {
            handleParticipantsClick()
        }

        llMicrophone.setOnClickListener {
            handleMicrophoneClick()
        }

        llCamera.setOnClickListener {
            handleCameraClick()
        }
    }

    private fun updateParticipantCount(count: Int) {
        if (roomType == RoomType.WEBINAR) {
            return
        }
        if (count > 0) {
            tvParticipants.text = context.getString(R.string.roomkit_member_count, count.toString())
        }
    }

    private fun updateMicrophoneStatus() {
        val localParticipant = participantStore?.state?.localParticipant?.value ?: return
        val participantList = participantStore?.state?.participantList?.value ?: return
        val currentRoom = roomStore.state.currentRoom.value ?: return
        if (!isLocalUserInList(participantList)) {
            llMicrophone.visibility = INVISIBLE
            return
        }
        llMicrophone.visibility = VISIBLE
        val microphoneStatus = localParticipant.microphoneStatus
        logger.info("updateCameraStatus microphoneStatus:$microphoneStatus isAllMicrophoneDisabled:${currentRoom.isAllMicrophoneDisabled}")
        when (microphoneStatus) {
            DeviceStatus.ON -> {
                ivMicrophone.setImageResource(R.drawable.roomkit_ic_microphone_on)
                tvMicrophone.text = context.getString(R.string.roomkit_mute)
            }

            else -> {
                ivMicrophone.setImageResource(R.drawable.roomkit_ic_microphone_off)
                tvMicrophone.text = context.getString(R.string.roomkit_unmute)
            }
        }
        val isGeneralUser = localParticipant.role == ParticipantRole.GENERAL_USER
        val isAllMicrophoneDisabled = currentRoom.isAllMicrophoneDisabled
        val isButtonDisabled = microphoneStatus == DeviceStatus.OFF && isAllMicrophoneDisabled && isGeneralUser
        llMicrophone.alpha = if (isButtonDisabled) 0.5f else 1.0f
    }

    private fun updateCameraStatus() {
        if (roomType == RoomType.WEBINAR) {
            return
        }
        val localParticipant = participantStore?.state?.localParticipant?.value ?: return
        val currentRoom = roomStore.state.currentRoom.value ?: return
        val cameraStatus = localParticipant.cameraStatus
        logger.info("updateCameraStatus cameraStatus:$cameraStatus isAllCameraDisabled:${currentRoom.isAllCameraDisabled}")
        when (cameraStatus) {
            DeviceStatus.ON -> {
                ivCamera.setImageResource(R.drawable.roomkit_ic_camera_on)
                tvCamera.text = context.getString(R.string.roomkit_stop_video)
            }

            else -> {
                ivCamera.setImageResource(R.drawable.roomkit_ic_camera_off)
                tvCamera.text = context.getString(R.string.roomkit_start_video)
            }
        }
        val isGeneralUser = localParticipant.role == ParticipantRole.GENERAL_USER
        val isAllCameraDisabled = currentRoom.isAllCameraDisabled
        val isButtonDisabled = cameraStatus == DeviceStatus.OFF && isAllCameraDisabled && isGeneralUser
        llCamera.alpha = if (isButtonDisabled) 0.5f else 1.0f
    }

    private fun handleParticipantsClick() {
        logger.info("handleParticipantsClick")
        showParticipantDialog()
    }

    private fun showParticipantDialog() {
        val roomID = currentRoomID ?: return
        if (roomParticipantListViewDialog == null) {
            val view = RoomParticipantListView(context).apply {
                init(roomID, roomType)
            }
            roomParticipantListViewDialog = RoomPopupDialog(context).apply {
                setView(view)
            }
        }
        roomParticipantListViewDialog?.show()
    }

    private fun handleMicrophoneClick() {
        logger.info("handleMicrophoneClick")
        val participantStore = participantStore ?: return
        val currentStatus = participantStore.state.localParticipant.value?.microphoneStatus
        if (currentStatus == DeviceStatus.ON) {
            deviceOperator.muteMicrophone(participantStore)
        } else {
            scope.launch {
                try {
                    deviceOperator.unmuteMicrophone(participantStore)
                } catch (e: Exception) {
                    logger.error("Failed to open microphone: ${e.message}")
                }
            }
        }
    }

    private fun handleCameraClick() {
        logger.info("handleCameraClick")
        val currentStatus = participantStore?.state?.localParticipant?.value?.cameraStatus
        if (currentStatus == DeviceStatus.ON) {
            deviceOperator.closeCamera()
        } else {
            scope.launch {
                try {
                    deviceOperator.openCamera()
                } catch (e: Exception) {
                    logger.error("Failed to open camera: ${e.message}")
                }
            }
        }
    }

    fun isLocalUserInList(participants: List<RoomParticipant>): Boolean {
        val localUserId = getLocalUserId() ?: return false
        return participants.any { it.userID == localUserId }
    }

    private fun getLocalUserId(): String? {
        return participantStore?.state?.localParticipant?.value?.userID?.takeIf { it.isNotEmpty() }
    }
}
