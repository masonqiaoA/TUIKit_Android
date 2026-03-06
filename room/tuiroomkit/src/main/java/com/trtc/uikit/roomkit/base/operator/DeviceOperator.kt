package com.trtc.uikit.roomkit.base.operator

import android.Manifest
import android.content.Context
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import io.trtc.tuikit.atomicx.common.permission.PermissionCallback
import io.trtc.tuikit.atomicx.common.permission.PermissionRequester
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Device operator for managing camera and microphone operations with permission handling.
 */
class DeviceOperator(context: Context) {

    enum class DeviceOperatorType {
        MICROPHONE,
        CAMERA,
    }

    private val contextRef = WeakReference(context)

    private val deviceStore = DeviceStore.shared()

    suspend fun unmuteMicrophone(participantStore: RoomParticipantStore?) {
        val hasPermission = requestPermission(DeviceOperatorType.MICROPHONE)
        if (!hasPermission) return

        awaitCompletion { deviceStore.openLocalMicrophone(it) }

        participantStore?.let { store ->
            awaitCompletion { store.unmuteMicrophone(it) }
        }
    }

    fun muteMicrophone(participantStore: RoomParticipantStore?) {
        participantStore?.muteMicrophone()
    }

    suspend fun openCamera() {
        val hasPermission = requestPermission(DeviceOperatorType.CAMERA)
        if (!hasPermission) return

        awaitCompletion {
            val isFrontCamera = deviceStore.deviceState.isFrontCamera.value
            deviceStore.openLocalCamera(isFrontCamera, it)
        }
    }

    fun closeCamera() {
        deviceStore.closeLocalCamera()
    }

    suspend fun requestPermission(type: DeviceOperatorType): Boolean {
        val context = contextRef.get() ?: return false
        val appName = context.packageManager.getApplicationLabel(context.applicationInfo).toString()

        val (permission, deviceTitle, reason) = when (type) {
            DeviceOperatorType.MICROPHONE -> Triple(
                Manifest.permission.RECORD_AUDIO,
                context.getString(R.string.roomkit_permission_microphone),
                context.getString(R.string.roomkit_permission_mic_reason)
            )
            DeviceOperatorType.CAMERA -> Triple(
                Manifest.permission.CAMERA,
                context.getString(R.string.roomkit_permission_camera),
                context.getString(R.string.roomkit_permission_camera_reason)
            )
        }

        val title = context.getString(R.string.roomkit_permission_title, appName, deviceTitle)
        val settingsTip = context.getString(R.string.roomkit_permission_tips, title) + "\n" + reason

        return suspendCancellableCoroutine { continuation ->
            var isCompleted = false
            PermissionRequester.newInstance(permission)
                .title(title)
                .description(reason)
                .settingsTip(settingsTip)
                .callback(object : PermissionCallback() {
                    override fun onGranted() {
                        if (!isCompleted) {
                            isCompleted = true
                            continuation.resume(true)
                        }
                    }

                    override fun onDenied() {
                        if (!isCompleted) {
                            isCompleted = true
                            continuation.resume(false)
                        }
                    }
                })
                .request()

            continuation.invokeOnCancellation {
                isCompleted = true
            }
        }
    }

    private suspend fun awaitCompletion(block: (CompletionHandler) -> Unit) {
        suspendCancellableCoroutine { continuation ->
            var isCompleted = false
            block(object : CompletionHandler {
                override fun onSuccess() {
                    if (!isCompleted) {
                        isCompleted = true
                        continuation.resume(Unit)
                    }
                }

                override fun onFailure(code: Int, desc: String) {
                    if (!isCompleted) {
                        isCompleted = true
                        contextRef.get()?.let { ErrorLocalized.showError(it, code) }
                        continuation.resumeWithException(Exception(desc))
                    }
                }
            })

            continuation.invokeOnCancellation {
                isCompleted = true
            }
        }
    }
}
