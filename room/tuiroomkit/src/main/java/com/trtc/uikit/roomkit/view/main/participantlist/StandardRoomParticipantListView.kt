package com.trtc.uikit.roomkit.view.main.participantlist

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.base.ui.BaseView
import com.trtc.uikit.roomkit.base.ui.RoomAlertDialog
import com.trtc.uikit.roomkit.base.ui.RoomPopupDialog
import com.trtc.uikit.roomkit.view.main.ParticipantManagerView
import com.trtc.uikit.roomkit.view.main.ParticipantManagerView.OnParticipantActionListener
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceType
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import io.trtc.tuikit.atomicxcore.api.room.RoomType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class StandardRoomParticipantListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseView(context, attrs, defStyleAttr) {

    private val logger = RoomKitLogger.getLogger("RoomParticipantListView")

    private var subscribeStateJob: Job? = null

    private val tvTitle: TextView by lazy { findViewById(R.id.tv_title) }
    private val rvParticipants: RecyclerView by lazy { findViewById(R.id.rv_participants) }
    private val btnMuteAll: AppCompatButton by lazy { findViewById(R.id.btn_mute_all) }
    private val btnDisableAllVideo: AppCompatButton by lazy { findViewById(R.id.btn_disable_all_video) }

    private val adapter = ParticipantListAdapter(RoomType.STANDARD)
    private var participantStore: RoomParticipantStore? = null
    private var roomStore: RoomStore? = null
    private var participantManagerDialog: RoomPopupDialog? = null
    private var participantManagerView: ParticipantManagerView? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_standard_room_participant_list_view, this)
        initView()
    }

    public override fun init(roomID: String) {
        super.init(roomID)
    }

    override fun initStore(roomID: String) {
        participantStore = RoomParticipantStore.create(roomID)
        roomStore = RoomStore.shared()
    }

    override fun addObserver() {
        val participantStore = participantStore ?: return
        val roomStore = roomStore ?: return
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                participantStore.state.participantList.collect { participants ->
                    updateParticipantList(participants)
                }
            }

            launch {
                participantStore.state.localParticipant.collect { localParticipant ->
                    localParticipant?.let {
                        updateControlButtonsVisibility(it.role)
                    }
                }
            }

            launch {
                roomStore.state.currentRoom.collect { roomInfo ->
                    roomInfo?.let {
                        updateMuteAllButton(it.isAllMicrophoneDisabled)
                        updateDisableAllVideoButton(it.isAllCameraDisabled)
                        updateParticipantCount(it.participantCount)
                    }
                }
            }
        }
    }

    override fun removeObserver() {
        subscribeStateJob?.cancel()
        subscribeStateJob = null
        participantManagerDialog?.dismiss()
        participantManagerDialog = null
    }

    private fun initView() {
        rvParticipants.layoutManager = LinearLayoutManager(context)
        rvParticipants.adapter = adapter

        adapter.setOnItemClickListener { participant ->
            showParticipantActionDialog(participant)
        }

        btnMuteAll.setOnClickListener {
            handleMuteAllClick()
        }

        btnDisableAllVideo.setOnClickListener {
            handleDisableAllVideoClick()
        }
    }

    private fun updateParticipantList(participants: List<RoomParticipant>) {
        adapter.updateData(participants)
    }

    private fun updateControlButtonsVisibility(role: ParticipantRole) {
        val shouldShowControls = role == ParticipantRole.OWNER || role == ParticipantRole.ADMIN
        btnMuteAll.visibility = if (shouldShowControls) VISIBLE else GONE
        btnDisableAllVideo.visibility = if (shouldShowControls) VISIBLE else GONE
    }

    private fun showParticipantActionDialog(participant: RoomParticipant) {
        logger.info("Show action dialog for participant: ${participant.userID}")
        val localParticipant = participantStore?.state?.localParticipant?.value ?: return
        val canOperate = localParticipant.role.value < participant.role.value
        if (!canOperate) {
            return
        }

        if (participantManagerDialog == null) {
            participantManagerView = ParticipantManagerView(context).apply {
                init(roomID, RoomType.STANDARD, object : OnParticipantActionListener {
                    override fun onDismiss() {
                        participantManagerDialog?.dismiss()
                    }
                })
            }
            participantManagerDialog = RoomPopupDialog(context).apply {
                participantManagerView?.let {
                    setView(it)
                }
            }
        }
        participantManagerView?.setRoomParticipant(participant)
        participantManagerDialog?.show()
    }

    private fun updateMuteAllButton(isAllMuted: Boolean) {
        if (isAllMuted) {
            btnMuteAll.text = context.getString(R.string.roomkit_unmute_all_audio)
            btnMuteAll.setTextColor(ContextCompat.getColor(context, R.color.roomkit_color_text_red))
        } else {
            btnMuteAll.text = context.getString(R.string.roomkit_mute_all_audio)
            btnMuteAll.setTextColor(ContextCompat.getColor(context, R.color.roomkit_color_text_grey))
        }
    }

    private fun updateDisableAllVideoButton(isAllVideoDisabled: Boolean) {
        if (isAllVideoDisabled) {
            btnDisableAllVideo.text = context.getString(R.string.roomkit_enable_all_video)
            btnDisableAllVideo.setTextColor(ContextCompat.getColor(context, R.color.roomkit_color_end_room))
        } else {
            btnDisableAllVideo.text = context.getString(R.string.roomkit_disable_all_video)
            btnDisableAllVideo.setTextColor(ContextCompat.getColor(context, R.color.roomkit_color_text_grey))
        }
    }

    private fun updateParticipantCount(count: Int) {
        tvTitle.text = context.getString(R.string.roomkit_member_count, count.toString())
    }

    private fun handleMuteAllClick() {
        val roomInfo = roomStore?.state?.currentRoom?.value
        val isAllMuted = roomInfo?.isAllMicrophoneDisabled ?: false

        if (isAllMuted) {
            logger.info("Unmute all participants clicked")
            RoomAlertDialog.Builder(context)
                .setTitle(R.string.roomkit_msg_all_members_will_be_unmuted)
                .setMessage(R.string.roomkit_msg_members_can_unmute)
                .setNegativeButton(android.R.string.cancel)
                .setPositiveButton(R.string.roomkit_confirm_release) {
                    handleUnmuteAll()
                }
                .show()
        } else {
            logger.info("Mute all participants clicked")
            RoomAlertDialog.Builder(context)
                .setTitle(R.string.roomkit_msg_all_members_will_be_muted)
                .setMessage(R.string.roomkit_msg_members_cannot_unmute)
                .setNegativeButton(android.R.string.cancel)
                .setPositiveButton(R.string.roomkit_mute_all_audio) {
                    handleMuteAll()
                }
                .show()
        }
    }

    private fun handleMuteAll() {
        val store = participantStore ?: return
        logger.info("Execute mute all")
        store.disableAllDevices(DeviceType.MICROPHONE, true, object : CompletionHandler {
            override fun onSuccess() {
                logger.info("Mute all success")
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("Mute all failed: code=$code, desc=$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }

    private fun handleUnmuteAll() {
        val store = participantStore ?: return
        logger.info("Execute unmute all")
        store.disableAllDevices(DeviceType.MICROPHONE, false, object : CompletionHandler {
            override fun onSuccess() {
                logger.info("Unmute all success")
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("Unmute all failed: code=$code, desc=$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }

    private fun handleDisableAllVideoClick() {
        val roomInfo = roomStore?.state?.currentRoom?.value
        val isAllVideoDisabled = roomInfo?.isAllCameraDisabled ?: false

        if (isAllVideoDisabled) {
            logger.info("Enable all video clicked")
            RoomAlertDialog.Builder(context)
                .setTitle(R.string.roomkit_msg_all_members_video_enabled)
                .setMessage(R.string.roomkit_msg_members_can_start_video)
                .setNegativeButton(android.R.string.cancel)
                .setPositiveButton(R.string.roomkit_confirm_release) {
                    handleEnableAllVideo()
                }
                .show()
        } else {
            logger.info("Disable all video clicked")
            RoomAlertDialog.Builder(context)
                .setTitle(R.string.roomkit_msg_all_members_video_disabled)
                .setMessage(R.string.roomkit_msg_members_cannot_start_video)
                .setNegativeButton(android.R.string.cancel)
                .setPositiveButton(R.string.roomkit_disable_all_video) {
                    handleDisableAllVideo()
                }
                .show()
        }
    }

    private fun handleDisableAllVideo() {
        val store = participantStore ?: return
        logger.info("Execute disable all video")
        store.disableAllDevices(DeviceType.CAMERA, true, object : CompletionHandler {
            override fun onSuccess() {
                logger.info("Disable all video success")
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("Disable all video failed: code=$code, desc=$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }

    private fun handleEnableAllVideo() {
        val store = participantStore ?: return
        logger.info("Execute enable all video")
        store.disableAllDevices(DeviceType.CAMERA, false, object : CompletionHandler {
            override fun onSuccess() {
                logger.info("Enable all video success")
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("Enable all video failed: code=$code, desc=$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }
}