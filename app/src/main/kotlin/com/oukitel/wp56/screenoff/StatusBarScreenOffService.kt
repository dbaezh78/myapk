package com.oukitel.wp56.screenoff

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityWindowInfo
import kotlin.math.abs

class StatusBarScreenOffService : AccessibilityService(), SensorEventListener {

  private var windowManager: WindowManager? = null
  private var statusBarOverlayView: View? = null
  private var statusBarLayoutParams: WindowManager.LayoutParams? = null
  private var outsideTouchOverlayView: View? = null

  private var lastOutsideTapTime: Long = 0L
  private var lastScrollEventTime: Long = 0L

  // Screen-On (wake via double-tap knock detection while screen is off)
  private var powerManager: PowerManager? = null
  private var sensorManager: SensorManager? = null
  private var accelerometerSensor: Sensor? = null
  private var proximitySensor: Sensor? = null
  private var partialWakeLock: PowerManager.WakeLock? = null
  private var isScreenInteractive: Boolean = true
  private var isProximityNear: Boolean = false
  private var sensorsRegistered: Boolean = false

  // High-pass filter state for accelerometer Z-axis tap detection
  private var gravityZ: Float = 0f
  private var gravityInitialized: Boolean = false
  private var firstKnockTime: Long = 0L
  private var knockSettledAfterFirst: Boolean = false
  private var lastPeakTime: Long = 0L

  private val screenStateReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      when (intent?.action) {
        Intent.ACTION_SCREEN_OFF -> {
          isScreenInteractive = false
          updateScreenOffWakeMonitor()
        }
        Intent.ACTION_SCREEN_ON -> {
          isScreenInteractive = true
          stopScreenOffWakeMonitor()
        }
      }
    }
  }

  override fun onServiceConnected() {
    super.onServiceConnected()
    instance = this
    isRunning = true

    powerManager = getSystemService(POWER_SERVICE) as? PowerManager
    sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
    accelerometerSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    isScreenInteractive = powerManager?.isInteractive ?: true

    val filter = IntentFilter().apply {
      addAction(Intent.ACTION_SCREEN_OFF)
      addAction(Intent.ACTION_SCREEN_ON)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      registerReceiver(screenStateReceiver, filter, RECEIVER_NOT_EXPORTED)
    } else {
      registerReceiver(screenStateReceiver, filter)
    }

    applyConfiguration()
    onStateChanged?.invoke(true)
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
      lastScrollEventTime = SystemClock.uptimeMillis()
      lastOutsideTapTime = 0L
    }
  }

  override fun onInterrupt() {
    // No-op
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    updateStatusBarOverlayHeight()
  }

  override fun onDestroy() {
    try {
      unregisterReceiver(screenStateReceiver)
    } catch (_: Exception) {
      // Ignored
    }
    stopScreenOffWakeMonitor()
    removeStatusBarOverlay()
    removeOutsideTouchOverlay()
    if (instance === this) {
      instance = null
    }
    isRunning = false
    onStateChanged?.invoke(false)
    super.onDestroy()
  }

  private fun applyConfiguration() {
    setupStatusBarOverlay()
    val zone = AppPreferences.getTouchZone(this)
    if (zone == AppPreferences.ZONE_ANYWHERE) {
      setupOutsideTouchOverlay()
    } else {
      removeOutsideTouchOverlay()
    }
    updateScreenOffWakeMonitor()
  }

  private fun lockScreenIfEnabled() {
    if (!AppPreferences.isTurnOffEnabled(this)) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun setupStatusBarOverlay() {
    if (statusBarOverlayView != null) return

    val wm = (windowManager ?: (getSystemService(WINDOW_SERVICE) as WindowManager)).also {
      windowManager = it
    }

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
          lockScreenIfEnabled()
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
      statusBarLayoutParams = params
    } catch (_: Exception) {
      // Ignored
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun setupOutsideTouchOverlay() {
    if (outsideTouchOverlayView != null) return

    val wm = (windowManager ?: (getSystemService(WINDOW_SERVICE) as WindowManager)).also {
      windowManager = it
    }

    val watcherView = View(this).apply {
      setBackgroundColor(Color.TRANSPARENT)
      setOnTouchListener { _, event ->
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE ||
          event.actionMasked == MotionEvent.ACTION_DOWN
        ) {
          handleScreenWideTouch()
        }
        false
      }
    }

    val params = WindowManager.LayoutParams(
      1,
      1,
      WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = 0
      y = 0
    }

    try {
      wm.addView(watcherView, params)
      outsideTouchOverlayView = watcherView
    } catch (_: Exception) {
      // Ignored
    }
  }

  private fun handleScreenWideTouch() {
    if (!AppPreferences.isTurnOffEnabled(this)) return
    if (AppPreferences.getTouchZone(this) != AppPreferences.ZONE_ANYWHERE) return
    if (isKeyboardVisible()) {
      lastOutsideTapTime = 0L
      return
    }

    val now = SystemClock.uptimeMillis()
    if (now - lastScrollEventTime < 350L) {
      lastOutsideTapTime = 0L
      return
    }

    val delta = now - lastOutsideTapTime
    if (delta in 65L..320L) {
      lastOutsideTapTime = 0L
      lockScreenIfEnabled()
    } else {
      lastOutsideTapTime = now
    }
  }

  private fun isKeyboardVisible(): Boolean {
    return try {
      windows?.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } == true
    } catch (_: Exception) {
      false
    }
  }

  private fun updateStatusBarOverlayHeight() {
    val wm = windowManager ?: return
    val view = statusBarOverlayView ?: return
    val params = statusBarLayoutParams ?: return
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
    statusBarLayoutParams = null
  }

  private fun removeOutsideTouchOverlay() {
    val wm = windowManager
    val view = outsideTouchOverlayView
    if (wm != null && view != null) {
      try {
        wm.removeView(view)
      } catch (_: Exception) {
        // Ignored
      }
    }
    outsideTouchOverlayView = null
  }

  private fun updateScreenOffWakeMonitor() {
    if (!isScreenInteractive && AppPreferences.isTurnOnEnabled(this)) {
      startScreenOffWakeMonitor()
    } else {
      stopScreenOffWakeMonitor()
    }
  }

  @SuppressLint("WakelockTimeout")
  private fun startScreenOffWakeMonitor() {
    if (sensorsRegistered) return
    val sm = sensorManager ?: return
    val accel = accelerometerSensor ?: return

    try {
      if (partialWakeLock == null) {
        partialWakeLock = powerManager?.newWakeLock(
          PowerManager.PARTIAL_WAKE_LOCK,
          "dTouchoff:ScreenOffKnockMonitor",
        )?.apply {
          setReferenceCounted(false)
        }
      }
      if (partialWakeLock?.isHeld == false) {
        partialWakeLock?.acquire()
      }
    } catch (_: Exception) {
      // Ignored
    }

    gravityInitialized = false
    firstKnockTime = 0L
    knockSettledAfterFirst = false
    isProximityNear = false

    sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
    proximitySensor?.let {
      sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
    }
    sensorsRegistered = true
  }

  private fun stopScreenOffWakeMonitor() {
    if (sensorsRegistered) {
      try {
        sensorManager?.unregisterListener(this)
      } catch (_: Exception) {
        // Ignored
      }
      sensorsRegistered = false
    }
    try {
      if (partialWakeLock?.isHeld == true) {
        partialWakeLock?.release()
      }
    } catch (_: Exception) {
      // Ignored
    }
  }

  override fun onSensorChanged(event: SensorEvent?) {
    val ev = event ?: return
    when (ev.sensor.type) {
      Sensor.TYPE_PROXIMITY -> {
        val maxRange = ev.sensor.maximumRange
        val distance = ev.values.firstOrNull() ?: maxRange
        isProximityNear = distance < minOf(maxRange, 3f)
      }
      Sensor.TYPE_ACCELEROMETER -> {
        if (isScreenInteractive || isProximityNear) return
        val z = ev.values[2]
        if (!gravityInitialized) {
          gravityZ = z
          gravityInitialized = true
          return
        }
        // High-pass filter on Z axis to isolate sharp tap/knock impulses on the screen
        gravityZ = 0.8f * gravityZ + 0.2f * z
        val linearZ = abs(z - gravityZ)
        val now = SystemClock.uptimeMillis()

        val spikeThreshold = 1.65f
        val quietThreshold = 0.65f

        if (firstKnockTime > 0L && now - firstKnockTime > 520L) {
          firstKnockTime = 0L
          knockSettledAfterFirst = false
        }

        if (firstKnockTime > 0L && !knockSettledAfterFirst) {
          if (now - lastPeakTime > 45L && linearZ < quietThreshold) {
            knockSettledAfterFirst = true
          }
        }

        if (linearZ > spikeThreshold) {
          lastPeakTime = now
          if (firstKnockTime == 0L) {
            firstKnockTime = now
            knockSettledAfterFirst = false
          } else if (knockSettledAfterFirst && (now - firstKnockTime) in 90L..500L) {
            firstKnockTime = 0L
            knockSettledAfterFirst = false
            wakeUpScreen()
          }
        }
      }
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    // No-op
  }

  @Suppress("DEPRECATION")
  private fun wakeUpScreen() {
    if (!AppPreferences.isTurnOnEnabled(this)) return
    try {
      val pm = powerManager ?: return
      val wakeLock = pm.newWakeLock(
        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
          PowerManager.ACQUIRE_CAUSES_WAKEUP or
          PowerManager.ON_AFTER_RELEASE,
        "dTouchoff:WakeUpScreen",
      )
      wakeLock.acquire(3000L)
    } catch (_: Exception) {
      // Ignored
    }
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

    fun reloadConfiguration() {
      instance?.applyConfiguration()
    }

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
