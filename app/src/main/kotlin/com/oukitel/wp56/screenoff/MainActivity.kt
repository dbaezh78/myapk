package com.oukitel.wp56.screenoff

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

  private lateinit var statusDotView: View
  private lateinit var statusTitleText: TextView
  private lateinit var statusDetailText: TextView
  private lateinit var actionButton: Button
  private lateinit var testDoubleTapBox: LinearLayout

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(buildContentView())

    StatusBarScreenOffService.onStateChanged = {
      runOnUiThread { refreshUiState() }
    }
  }

  override fun onResume() {
    super.onResume()
    refreshUiState()
  }

  override fun onDestroy() {
    if (StatusBarScreenOffService.onStateChanged != null) {
      StatusBarScreenOffService.onStateChanged = null
    }
    super.onDestroy()
  }

  private fun refreshUiState() {
    val active = StatusBarScreenOffService.isServiceEnabled(this)
    val dotDrawable = GradientDrawable().apply {
      shape = GradientDrawable.OVAL
      setColor(if (active) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
    }
    statusDotView.background = dotDrawable

    if (active) {
      statusTitleText.text = "Servicio Activo (V1)"
      statusDetailText.text =
        "ACTIVO — Doble toque en la barra de estado superior apaga la pantalla"
      actionButton.text = "Abrir Ajustes de Accesibilidad"
      testDoubleTapBox.visibility = View.VISIBLE
    } else {
      statusTitleText.text = "Servicio Inactivo (V1)"
      statusDetailText.text =
        "INACTIVO — Activa el servicio de accesibilidad para empezar"
      actionButton.text = "Activar en Accesibilidad"
      testDoubleTapBox.visibility = View.GONE
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun buildContentView(): View {
    val rootScroll = ScrollView(this).apply {
      setBackgroundColor(Color.parseColor("#F5F7FA"))
      isFillViewport = true
    }

    val container = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(20), dp(36), dp(20), dp(24))
    }

    val headerText = TextView(this).apply {
      text = getString(R.string.app_name)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
      setTypeface(typeface, Typeface.BOLD)
      setTextColor(Color.parseColor("#1A1C1E"))
      gravity = Gravity.CENTER
      setPadding(0, dp(8), 0, dp(20))
    }
    container.addView(headerText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

    // Status Card
    val statusCard = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(18), dp(18), dp(18), dp(18))
      background = roundedRect(Color.WHITE, dp(14).toFloat(), Color.parseColor("#E0E4EB"), dp(1))
    }

    val statusHeaderRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }

    statusDotView = View(this)
    val dotParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply {
      marginEnd = dp(10)
    }
    statusHeaderRow.addView(statusDotView, dotParams)

    statusTitleText = TextView(this).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
      setTypeface(typeface, Typeface.BOLD)
      setTextColor(Color.parseColor("#1A1C1E"))
    }
    statusHeaderRow.addView(statusTitleText, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
    statusCard.addView(statusHeaderRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

    statusDetailText = TextView(this).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
      setTextColor(Color.parseColor("#44474E"))
      setPadding(0, dp(10), 0, 0)
    }
    statusCard.addView(statusDetailText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

    val cardParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
      bottomMargin = dp(16)
    }
    container.addView(statusCard, cardParams)

    // Double-Tap Test Box (shown when active)
    val gestureDetector = GestureDetector(
      this,
      object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
          StatusBarScreenOffService.lockScreenNow()
          return true
        }
      },
    )

    testDoubleTapBox = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      setPadding(dp(18), dp(24), dp(18), dp(24))
      background = roundedRect(
        Color.parseColor("#E8F0FE"),
        dp(16).toFloat(),
        Color.parseColor("#1A73E8"),
        dp(2),
      )
      setOnTouchListener { _, event ->
        gestureDetector.onTouchEvent(event)
        true
      }

      val title = TextView(this@MainActivity).apply {
        text = "Haz doble toque aquí o en la barra de estado superior"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#174EA6"))
        gravity = Gravity.CENTER
      }
      val subtitle = TextView(this@MainActivity).apply {
        text = "La pantalla de tu Oukitel WP56 se apagará al instante"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(Color.parseColor("#1A73E8"))
        gravity = Gravity.CENTER
        setPadding(0, dp(6), 0, 0)
      }
      addView(title, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
      addView(subtitle, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    val testBoxParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
      bottomMargin = dp(16)
    }
    container.addView(testDoubleTapBox, testBoxParams)

    // Action Button
    actionButton = Button(this).apply {
      isAllCaps = false
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
      setTextColor(Color.WHITE)
      background = roundedRect(Color.parseColor("#1A73E8"), dp(12).toFloat())
      setPadding(dp(16), dp(14), dp(16), dp(14))
      setOnClickListener {
        startActivity(
          Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          },
        )
      }
    }
    val buttonParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
      bottomMargin = dp(16)
    }
    container.addView(actionButton, buttonParams)

    // Instructions Card
    val instructionsCard = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(16), dp(16), dp(16), dp(16))
      background = roundedRect(Color.WHITE, dp(14).toFloat(), Color.parseColor("#E0E4EB"), dp(1))

      val title = TextView(this@MainActivity).apply {
        text = "Cómo usarlo en tu Oukitel WP56:"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#1A1C1E"))
        setPadding(0, 0, 0, dp(8))
      }
      val step1 = TextView(this@MainActivity).apply {
        text = "1. Toca «Activar en Accesibilidad» y habilita «Apagar Pantalla WP56 (Doble Toque)»."
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        setTextColor(Color.parseColor("#3C4043"))
        setPadding(0, 0, 0, dp(6))
      }
      val step2 = TextView(this@MainActivity).apply {
        text = "2. Haz doble toque en la barra de estado superior (donde están la hora y la batería) desde cualquier pantalla para apagar la pantalla."
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        setTextColor(Color.parseColor("#3C4043"))
        setPadding(0, 0, 0, dp(8))
      }
      val note = TextView(this@MainActivity).apply {
        text = "Nota: Si instalaste el APK manualmente y Android muestra «Ajustes restringidos», abre Ajustes → Aplicaciones → Apagar Pantalla WP56 → menú (⋮) arriba a la derecha → «Permitir ajustes restringidos»."
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        setTextColor(Color.parseColor("#5F6368"))
      }

      addView(title)
      addView(step1)
      addView(step2)
      addView(note)
    }
    container.addView(instructionsCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

    rootScroll.addView(container, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    return rootScroll
  }

  private fun roundedRect(
    fillColor: Int,
    radiusPx: Float,
    strokeColor: Int = Color.TRANSPARENT,
    strokeWidthPx: Int = 0,
  ): GradientDrawable {
    return GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      cornerRadius = radiusPx
      setColor(fillColor)
      if (strokeWidthPx > 0) {
        setStroke(strokeWidthPx, strokeColor)
      }
    }
  }

  private fun dp(value: Int): Int {
    return TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      value.toFloat(),
      resources.displayMetrics,
    ).toInt()
  }
}
