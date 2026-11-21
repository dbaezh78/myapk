package com.oukitel.wp56.screenoff

import android.content.Context

object AppPreferences {
  private const val PREFS_NAME = "dtouchoff_prefs"
  private const val KEY_TOUCH_ZONE = "touch_zone"
  private const val KEY_ACTION_MODE = "action_mode"

  // Touch zone options
  const val ZONE_ANYWHERE = 0
  const val ZONE_STATUS_BAR_ONLY = 1

  // Action mode options
  const val ACTION_TURN_OFF = 0
  const val ACTION_TURN_ON = 1
  const val ACTION_TURN_OFF_AND_ON = 2

  fun getTouchZone(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_TOUCH_ZONE, ZONE_ANYWHERE)
  }

  fun setTouchZone(context: Context, zone: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_TOUCH_ZONE, zone).apply()
  }

  fun getActionMode(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_ACTION_MODE, ACTION_TURN_OFF)
  }

  fun setActionMode(context: Context, mode: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_ACTION_MODE, mode).apply()
  }

  fun isTurnOffEnabled(context: Context): Boolean {
    val mode = getActionMode(context)
    return mode == ACTION_TURN_OFF || mode == ACTION_TURN_OFF_AND_ON
  }

  fun isTurnOnEnabled(context: Context): Boolean {
    val mode = getActionMode(context)
    return mode == ACTION_TURN_ON || mode == ACTION_TURN_OFF_AND_ON
  }
}
