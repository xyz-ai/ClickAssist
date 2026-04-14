package com.example.clickassist.service.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import com.example.clickassist.R
import com.example.clickassist.domain.repository.AppSettings
import com.example.clickassist.ui.overlay.OverlayAddStepPanelView
import com.example.clickassist.ui.overlay.OverlaySchemePanelView
import com.example.clickassist.ui.overlay.OverlayStepEditorView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class OverlayPanelController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val windowManager = requireNotNull(appContext.getSystemService(WindowManager::class.java))
    private val mainHandler = Handler(Looper.getMainLooper())

    private var panelRoot: LinearLayout? = null
    private var panelLayoutParams: WindowManager.LayoutParams? = null
    private var titleTextView: TextView? = null
    private var messageTextView: TextView? = null
    private var contentHost: FrameLayout? = null
    private var onCloseRequested: (() -> Unit)? = null
    private var boundsCache: Rect? = null
    private var currentSpec: OverlayPanelSpec? = null
    private var currentAppearance: OverlayAppearance =
        OverlayAppearance.fromSettings(appContext, AppSettings())

    var onBoundsChanged: ((Rect?) -> Unit)? = null

    fun hasPermission(): Boolean = Settings.canDrawOverlays(appContext)

    fun currentBounds(): Rect? = boundsCache?.let(::Rect)

    fun applySettings(settings: AppSettings) {
        currentAppearance = OverlayAppearance.fromSettings(appContext, settings)
        runOnMain {
            applyAppearanceInternal()
            currentSpec?.let(::bindPanel)
        }
    }

    suspend fun showPanel(
        spec: OverlayPanelSpec,
        onCloseRequested: () -> Unit,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        if (!hasPermission()) {
            return@withContext false
        }
        this@OverlayPanelController.onCloseRequested = onCloseRequested
        currentSpec = spec
        val view = ensurePanelRoot()
        bindPanel(spec)
        val layoutParams = panelLayoutParams ?: createLayoutParams().also { panelLayoutParams = it }
        runCatching {
            if (view.parent == null) {
                windowManager.addView(view, layoutParams)
            } else {
                windowManager.updateViewLayout(view, layoutParams)
            }
        }.onSuccess {
            updateBoundsCache()
        }.onFailure { throwable ->
            Log.e(TAG, "showPanel failed type=${spec.type}", throwable)
        }.isSuccess
    }

    suspend fun hidePanel() = withContext(Dispatchers.Main.immediate) {
        hideInternal()
    }

    fun release() {
        runOnMain {
            hideInternal()
            onCloseRequested = null
            panelRoot = null
            panelLayoutParams = null
            titleTextView = null
            messageTextView = null
            contentHost = null
            boundsCache = null
            onBoundsChanged?.invoke(null)
        }
    }

    private fun bindPanel(
        spec: OverlayPanelSpec,
    ) {
        titleTextView?.text = appContext.getString(spec.type.titleRes)
        val messageRes = spec.messageRes
        messageTextView?.apply {
            visibility = if (messageRes == null) View.GONE else View.VISIBLE
            if (messageRes != null) {
                text = appContext.getString(messageRes)
            }
        }
        contentHost?.apply {
            removeAllViews()
            addView(
                createContentView(spec),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    private fun ensurePanelRoot(): LinearLayout {
        panelRoot?.let { return it }

        val container = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            elevation = dpFloat(8)
        }

        val header = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleTextView = TextView(appContext).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, 0, dp(8), 0)
        }
        val closeView = TextView(appContext).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            text = appContext.getString(R.string.overlay_panel_close)
            isClickable = true
            isFocusable = true
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { onCloseRequested?.invoke() }
        }
        header.addView(
            titleTextView,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )
        header.addView(
            closeView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        messageTextView = TextView(appContext).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            visibility = View.GONE
        }

        val scrollContainer = ScrollView(appContext).apply {
            isFillViewport = true
        }
        contentHost = FrameLayout(appContext)
        scrollContainer.addView(
            contentHost,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        container.addView(
            header,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        container.addView(
            messageTextView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
            },
        )
        container.addView(
            scrollContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                panelHeightPx(),
            ).apply {
                topMargin = dp(10)
            },
        )

        panelRoot = container
        applyAppearanceInternal()
        return container
    }

    private fun createContentView(
        spec: OverlayPanelSpec,
    ): View {
        return when (spec) {
            is OverlayPanelSpec.Settings -> OverlaySchemePanelView(appContext).apply {
                applyAppearance(currentAppearance)
                bind(spec)
            }
            is OverlayPanelSpec.AddNode -> OverlayAddStepPanelView(appContext).apply {
                applyAppearance(currentAppearance)
                bind(spec)
            }
            is OverlayPanelSpec.StepEditor -> OverlayStepEditorView(appContext).apply {
                applyAppearance(currentAppearance)
                bind(spec)
            }
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            dp(360).coerceAtMost(screenWidth() - dp(24)),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(96)
            y = dp(48)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }

    private fun hideInternal() {
        val view = panelRoot ?: return
        if (view.parent != null) {
            runCatching { windowManager.removeView(view) }
        }
        currentSpec = null
        boundsCache = null
        onBoundsChanged?.invoke(null)
    }

    private fun updateBoundsCache() {
        val view = panelRoot
        val layoutParams = panelLayoutParams
        if (view == null || layoutParams == null) {
            boundsCache = null
            onBoundsChanged?.invoke(null)
            return
        }
        view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        boundsCache = Rect(
            layoutParams.x,
            layoutParams.y,
            layoutParams.x + view.measuredWidth,
            layoutParams.y + view.measuredHeight,
        )
        onBoundsChanged?.invoke(boundsCache?.let(::Rect))
    }

    private fun screenWidth(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds: Rect = windowManager.currentWindowMetrics.bounds
            return bounds.width().coerceAtLeast(dp(360))
        }
        return appContext.resources.displayMetrics.widthPixels.coerceAtLeast(dp(360))
    }

    private fun panelHeightPx(): Int {
        val screenHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds: Rect = windowManager.currentWindowMetrics.bounds
            bounds.height()
        } else {
            appContext.resources.displayMetrics.heightPixels
        }.coerceAtLeast(dp(320))
        return (screenHeight * 0.72f).roundToInt()
    }

    private fun dp(value: Int): Int {
        return (value * appContext.resources.displayMetrics.density).roundToInt()
    }

    private fun dpFloat(value: Int): Float {
        return value * appContext.resources.displayMetrics.density
    }

    private companion object {
        const val TAG = "OverlayToolbar"
    }

    private fun applyAppearanceInternal() {
        panelRoot?.background = GradientDrawable().apply {
            setColor(currentAppearance.panelBackgroundColor)
            cornerRadius = dpFloat(20)
            setStroke(dp(1), currentAppearance.panelBorderColor)
        }
        titleTextView?.setTextColor(currentAppearance.textPrimaryColor)
        messageTextView?.setTextColor(currentAppearance.neutralActionColor)
        contentHost?.getChildAt(0)?.let { child ->
            if (child is OverlayStylable) {
                child.applyAppearance(currentAppearance)
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
