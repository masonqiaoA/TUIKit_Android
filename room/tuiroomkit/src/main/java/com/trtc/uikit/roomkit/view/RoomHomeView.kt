package com.trtc.uikit.roomkit.view

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.utils.widget.ImageFilterView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.RoomCreateActivity
import com.trtc.uikit.roomkit.RoomJoinActivity
import com.trtc.uikit.roomkit.base.extension.getDisplayName
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Home screen displaying user profile and room entry options.
 * Provides navigation to join existing room or create new room.
 * Subscribes to login user info from LoginStore to display avatar and username.
 */
class RoomHomeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val ivBack: ImageView by lazy { findViewById(R.id.iv_back) }
    private val ivUserAvatar: ImageFilterView by lazy { findViewById(R.id.iv_user_avatar) }
    private val tvUserName: TextView by lazy { findViewById(R.id.tv_user_name) }
    private val llJoinRoom: LinearLayout by lazy { findViewById(R.id.btn_join_room) }
    private val llCreateRoom: LinearLayout by lazy { findViewById(R.id.btn_create_room) }

    private var loginStore: LoginStore = LoginStore.shared
    private var subscribeStateJob: Job? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_home, this)
        initView()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addObserver()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeObserver()
    }

    private fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            loginStore.loginState.loginUserInfo.collect { loginUserInfo ->
                loginUserInfo?.let {
                    updateUserInfo(it)
                }
            }
        }
    }

    private fun removeObserver() {
        subscribeStateJob?.cancel()
    }

    private fun initView() {
        ivBack.setOnClickListener {
            handleBackClick()
        }

        llJoinRoom.setOnClickListener {
            handleJoinRoomClick()
        }

        llCreateRoom.setOnClickListener {
            handleCreateRoomClick()
        }
    }

    private fun updateUserInfo(userInfo: UserProfile) {
        tvUserName.text = userInfo.getDisplayName()
        if (userInfo.avatarURL.isNullOrEmpty()) {
            ivUserAvatar.setImageResource(R.drawable.roomkit_ic_default_avatar)
        } else {
            ImageLoader.load(context, ivUserAvatar, userInfo.avatarURL, R.drawable.roomkit_ic_default_avatar)
        }
    }

    private fun handleBackClick() {
        (context as? android.app.Activity)?.finish()
    }

    private fun handleJoinRoomClick() {
        val intent = Intent(context, RoomJoinActivity::class.java)
        context.startActivity(intent)
    }

    private fun handleCreateRoomClick() {
        val intent = Intent(context, RoomCreateActivity::class.java)
        context.startActivity(intent)
    }
}