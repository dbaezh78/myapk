package com.oukitel.wp56.screenoff

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import kotlin.math.abs

class StatusBarScreenOffService : AccessibilityService() {

  private var windowManager: WindowManager? = null
  private var statusBarOverlayView: View? = null
  private var overlayLayoutParams: WindowManager.LayoutParams? = null

  override fun onServiceConnected() {
    super.onServiceConnected()
    instance = this
    isRunning = true
    setupStatusBarOverlay()
    onStateChanged?.invoke(true)
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    // No accessibility events needed for V1; touch detection uses the top status bar overlay.
  }

  override fun onInterrupt() {
    // No-op
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    updateOverlayHeight()
  }

  override fun onDestroy() {
    removeStatusBarOverlay()
    if (instance === this) {
      instance = null
    }
    isRunning = false
    onStateChanged?.invoke(false)
    super.onDestroy()
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun setupStatusBarOverlay() {
    if (statusBarOverlayView != null) return

    val wm = getSystemService(WINDOW_SERVICE) as WindowManager
    windowManager = wm

    val swipeThresholdPx = dpToPx(24f)
    var touchDownRawX = 0f
    var touchDownRawY = 0f
    var shadeOpenedForCurrentGesture = false

    val gestureDetector = GestureDetector(
      this,
      object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
          touchDownRawX = e.rawX
          touchDownRawY = e.rawY
          shadeOpenedForCurrentGesture = false
          return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
          }
          return true
        }

        override fun onScroll(
          e1: MotionEvent?,
          e2: MotionEvent,
          distanceX: Float,
          distanceY: Float,
        ): Boolean {
          val deltaY = e2.rawY - touchDownRawY
          val deltaX = abs(e2.rawX - touchDownRawX)
          if (!shadeOpenedForCurrentGesture && deltaY > swipeThresholdPx && deltaY > deltaX) {
            shadeOpenedForCurrentGesture = true
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            return true
          }
          return false
        }

        override fun onFling(
          e1: MotionEvent?,
          e2: MotionEvent,
          velocityX: Float,
          velocityY: Float,
        ): Boolean {
          val deltaY = e2.rawY - touchDownRawY
          if (!shadeOpenedForCurrentGesture && deltaY > swipeThresholdPx / 2 && velocityY > 200f) {
            shadeOpenedForCurrentGesture = true
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            return true
          }
          return false
        }
      },
    )

    val overlay = View(this).apply {
      setBackgroundColor(Color.TRANSPARENT)
      setOnTouchListener { _, event ->
        gestureDetector.onTouchEvent(event)
        true
      }
    }

    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      getTouchZoneHeightPx(),
      WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = 0
      y = 0
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      }
    }

    try {
      wm.addView(overlay, params)
      statusBarOverlayView = overlay
      overlayLayoutParams = params
    } catch (_: Exception) {
      // Ignored if window manager rejects overlay during rapid service restart
    }
  }

  private fun updateOverlayHeight() {
    val wm = windowManager ?: return
    val view = statusBarOverlayView ?: return
    val params = overlayLayoutParams ?: return
    params.height = getTouchZoneHeightPx()
    try {
      wm.updateViewLayout(view, params)
    } catch (_: Exception) {
      // Ignored
    }
  }

  private fun removeStatusBarOverlay() {
    val wm = windowManager
    val view = statusBarOverlayView
    if (wm != null && view != null) {
      try {
        wm.removeView(view)
      } catch (_: Exception) {
        // Ignored
      }
    }
    statusBarOverlayView = null
    overlayLayoutParams = null
  }

  private fun getTouchZoneHeightPx(): Int {
    val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
    val systemStatusBarHeight = if (resourceId > 0) {
      resources.getDimensionPixelSize(resourceId)
    } else {
      0
    }
    val minTouchHeight = dpToPx(38f)
    return maxOf(systemStatusBarHeight, minTouchHeight)
  }

  private fun dpToPx(dp: Float): Int {
    return TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      dp,
      resources.displayMetrics,
    ).toInt()
  }

  companion object {
    @Volatile
    private var instance: StatusBarScreenOffService? = null

    @Volatile
    var isRunning: Boolean = false
      private set

    var onStateChanged: ((Boolean) -> Unit)? = null

    fun lockScreenNow(): Boolean {
      val service = instance ?: return false
      return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
      } else {
        false
      }
    }

    fun isServiceEnabled(context: Context): Boolean {
      if (isRunning && instance != null) return true

      val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
      val enabledServices =
        am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
          .orEmpty()
      val expectedComponent = ComponentName(context, StatusBarScreenOffService::class.java)

      if (enabledServices.any {
          ComponentName.unflattenFromString(it.id) == expectedComponent
        }
      ) {
        return true
      }

      val settingValue = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
      ) ?: return false

      return settingValue.split(':').any {
        ComponentName.unflattenFromString(it) == expectedComponent
      }
    }
  }
}
