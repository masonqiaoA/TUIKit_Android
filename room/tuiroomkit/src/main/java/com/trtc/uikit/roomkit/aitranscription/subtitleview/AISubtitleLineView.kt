package com.trtc.uikit.roomkit.aitranscription.subtitleview

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.aitranscription.config.TextStyle
import kotlin.math.ceil

/**
 * Single subtitle line view with streaming text animation and clip-from-top overflow.
 * When text exceeds maxLines, the oldest (top) content is clipped and the newest (bottom)
 * content remains visible via a fixed-height clipContainer with the TextView gravity set to BOTTOM.
 */
class AISubtitleLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val textView: TextView

    private var fullText: String = ""
    private var currentCharIndex: Int = 0
    private val handler = Handler(Looper.getMainLooper())
    private var streamRunnable: Runnable? = null
    private var streamAnimationIntervalMs: Long = 30L

    private var maxLinesValue: Int = 0
    private var lineHeightPx: Float = 0f

    /** Maximum clip height in pixels, computed from maxLines × lineHeight. */
    private var maxClipHeightPx: Int = 0
    /** Minimum height (single line) in pixels. */
    private var minLineHeightPx: Int = 0

    var onTextUpdateCompleted: (() -> Unit)? = null

    init {
        clipChildren = true
        clipToPadding = true
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_ai_subtitle_line, this, true)
        textView = findViewById(R.id.tv_subtitle_line)
    }

    // MARK: - Configuration

    fun updateMaxLines(maxLines: Int) {
        this.maxLinesValue = maxLines
        updateClipConstraints()
    }

    fun configure(style: TextStyle, animationIntervalMs: Long = 30L) {
        this.streamAnimationIntervalMs = animationIntervalMs

        textView.setTextColor(style.textColor)
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSize)
        textView.typeface = style.typeface
        textView.setShadowLayer(style.shadowRadius, style.shadowDx, style.shadowDy, style.shadowColor)

        lineHeightPx = textView.paint.fontMetrics.let { it.descent - it.ascent + it.leading }
        updateClipConstraints()
    }

    private fun updateClipConstraints() {
        if (maxLinesValue <= 0 || lineHeightPx <= 0f) {
            maxClipHeightPx = 0
            minLineHeightPx = 0
            layoutParams?.let {
                it.height = LayoutParams.WRAP_CONTENT
                layoutParams = it
            }
            minimumHeight = 0
            return
        }
        minLineHeightPx = ceil(lineHeightPx).toInt()
        maxClipHeightPx = ceil(lineHeightPx * maxLinesValue).toInt()

        // Use WRAP_CONTENT so onMeasure can determine the actual needed height,
        // then clamp it between minLineHeightPx and maxClipHeightPx.
        layoutParams?.let {
            it.height = LayoutParams.WRAP_CONTENT
            layoutParams = it
        }
        minimumHeight = minLineHeightPx
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (maxClipHeightPx <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        // Measure children with UNSPECIFIED height so the TextView reports its
        // full natural height (all lines), regardless of any parent constraints.
        // This matches iOS (textLabel with numberOfLines=0 + lessThanOrEqualTo
        // constraint) and Flutter (unconstrained child layout in
        // _RenderBottomAlignedOverflow).
        val unconstrainedHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        super.onMeasure(widthMeasureSpec, unconstrainedHeight)
        val naturalHeight = measuredHeight

        // Clamp: at least 1 line, at most maxLines lines.
        val clampedHeight = naturalHeight.coerceIn(minLineHeightPx, maxClipHeightPx)
        setMeasuredDimension(measuredWidth, clampedHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        // When the child (TextView) is taller than this container, position it
        // so its bottom edge aligns with the container's bottom edge. The top
        // portion extends above the container and is clipped by clipChildren=true.
        // This matches iOS (textLabel pinned to bottom with low-priority top) and
        // Flutter (_BottomAlignedOverflow offset = containerH - childH).
        val child = getChildAt(0) ?: run {
            super.onLayout(changed, left, top, right, bottom)
            return
        }

        val containerHeight = bottom - top
        val childWidth = child.measuredWidth
        val childHeight = child.measuredHeight

        // Bottom-align: when childHeight > containerHeight, childTop is negative → clipped from top.
        val childTop = containerHeight - childHeight
        val childLeft = paddingLeft
        child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)
    }

    // MARK: - Text Update

    fun updateText(text: String, animated: Boolean = true) {
        cancelStreamAnimation()

        val previousText = fullText
        fullText = text

        if (animated && text.isNotEmpty()) {
            if (text.startsWith(previousText) && text.length > previousText.length) {
                currentCharIndex = previousText.length
                startStreamAnimation()
            } else {
                currentCharIndex = 0
                textView.text = ""
                startStreamAnimation()
            }
        } else {
            textView.text = text
            onTextUpdateCompleted?.invoke()
        }
    }

    fun appendText(text: String, animated: Boolean = true) {
        cancelStreamAnimation()

        fullText += text
        currentCharIndex = fullText.length

        if (animated) {
            textView.animate().alpha(0.95f).setDuration(50).withEndAction {
                textView.text = fullText
                textView.alpha = 1f
            }.start()
        } else {
            textView.text = fullText
        }
    }

    fun clearText() {
        cancelStreamAnimation()
        fullText = ""
        currentCharIndex = 0
        textView.text = ""
    }

    val currentText: String get() = fullText

    // MARK: - Stream Animation

    private fun startStreamAnimation() {
        streamRunnable = object : Runnable {
            override fun run() {
                if (currentCharIndex < fullText.length) {
                    currentCharIndex++
                    val displayText = fullText.substring(0, currentCharIndex)
                    textView.text = displayText
                    handler.postDelayed(this, streamAnimationIntervalMs)
                } else {
                    streamRunnable = null
                    onTextUpdateCompleted?.invoke()
                }
            }
        }
        handler.post(streamRunnable!!)
    }

    private fun cancelStreamAnimation() {
        streamRunnable?.let { handler.removeCallbacks(it) }
        streamRunnable = null
    }

    // MARK: - Fade Animation

    fun fadeIn(durationMs: Long = 300L, completion: (() -> Unit)? = null) {
        alpha = 0f
        visibility = View.VISIBLE
        animate().alpha(1f).setDuration(durationMs).withEndAction { completion?.invoke() }.start()
    }

    fun fadeOut(durationMs: Long = 500L, completion: (() -> Unit)? = null) {
        animate().alpha(0f).setDuration(durationMs).withEndAction {
            visibility = View.GONE
            completion?.invoke()
        }.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelStreamAnimation()
    }
}
