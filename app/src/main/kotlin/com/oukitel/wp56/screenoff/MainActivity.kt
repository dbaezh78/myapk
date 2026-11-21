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
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

class MainActivity : Activity() {

  private lateinit var statusDotView: View
  private lateinit var statusTitleText: TextView
  private lateinit var statusDetailText: TextView
  private lateinit var zoneSpinner: Spinner
  private lateinit var actionSpinner: Spinner
  private lateinit var actionButton: Button
  private lateinit var testDoubleTapBox: LinearLayout

  private val zoneOptions = arrayOf(
    "En cualquier parte de la pantalla",
    "Solo en la barra superior",
  )

  private val actionOptions = arrayOf(
    "Apagar pantalla",
    "Encender pantalla",
    "Apagar y Encender pantalla",
  )

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

    val selectedZone = zoneOptions.getOrElse(AppPreferences.getTouchZone(this)) { zoneOptions[0] }
    val selectedAction =
      actionOptions.getOrElse(AppPreferences.getActionMode(this)) { actionOptions[0] }

    if (active) {
      statusTitleText.text = "Servicio Activo"
      statusDetailText.text = "ACTIVO — $selectedAction • $selectedZone"
      actionButton.text = "Abrir Ajustes de Accesibilidad"
      testDoubleTapBox.visibility = View.VISIBLE
    } else {
      statusTitleText.text = "Servicio Inactivo"
      statusDetailText.text = "INACTIVO — Activa el servicio de accesibilidad para empezar"
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

    // Settings Card with the two Selects (Spinners)
    val settingsCard = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(18), dp(18), dp(18), dp(18))
      background = roundedRect(Color.WHITE, dp(14).toFloat(), Color.parseColor("#E0E4EB"), dp(1))
    }

    val zoneLabel = TextView(this).apply {
      text = "¿Dónde hacer el doble toque?"
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
      setTypeface(typeface, Typeface.BOLD)
      setTextColor(Color.parseColor("#1A1C1E"))
      setPadding(0, 0, 0, dp(8))
    }
    settingsCard.addView(zoneLabel, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

    zoneSpinner = buildStyledSpinner(zoneOptions).apply {
      setSelection(AppPreferences.getTouchZone(this@MainActivity).coerceIn(0, zoneOptions.lastIndex))
      onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
          AppPreferences.setTouchZone(this@MainActivity, position)
          StatusBarScreenOffService.reloadConfiguration()
          refreshUiState()
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {}
      }
    }
    val spinnerParams1 = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
      bottomMargin = dp(16)
    }
    settingsCard.addView(zoneSpinner, spinnerParams1)

    val actionLabel = TextView(this).apply {
      text = "¿Qué acción realizar con el doble toque?"
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
      setTypeface(typeface, Typeface.BOLD)
      setTextColor(Color.parseColor("#1A1C1E"))
      setPadding(0, 0, 0, dp(8))
    }
    settingsCard.addView(actionLabel, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

    actionSpinner = buildStyledSpinner(actionOptions).apply {
      setSelection(
        AppPreferences.getActionMode(this@MainActivity).coerceIn(0, actionOptions.lastIndex),
      )
      onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
          AppPreferences.setActionMode(this@MainActivity, position)
          StatusBarScreenOffService.reloadConfiguration()
          refreshUiState()
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {}
      }
    }
    settingsCard.addView(actionSpinner, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

    val settingsCardParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
      bottomMargin = dp(16)
    }
    container.addView(settingsCard, settingsCardParams)

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
      setPadding(dp(18), dp(22), dp(18), dp(22))
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
        text = "Zona de prueba: Haz doble toque aquí"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#174EA6"))
        gravity = Gravity.CENTER
      }
      val subtitle = TextView(this@MainActivity).apply {
        text = "También funciona según la zona y acción seleccionadas arriba"
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
        text = "Cómo funciona:"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#1A1C1E"))
        setPadding(0, 0, 0, dp(8))
      }
      val step1 = TextView(this@MainActivity).apply {
        text = "• En cualquier parte de la pantalla: Detecta doble toque rápido en toda la pantalla (se pausa automáticamente si el teclado está abierto para que escribas sin problema)."
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        setTextColor(Color.parseColor("#3C4043"))
        setPadding(0, 0, 0, dp(6))
      }
      val step2 = TextView(this@MainActivity).apply {
        text = "• Solo en la barra superior: Detecta el doble toque únicamente en la barra de estado superior."
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        setTextColor(Color.parseColor("#3C4043"))
        setPadding(0, 0, 0, dp(6))
      }
      val step3 = TextView(this@MainActivity).apply {
        text = "• Encender pantalla: Al estar apagada la pantalla, detecta dos toques firmes seguidos sobre la pantalla para encenderla."
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        setTextColor(Color.parseColor("#3C4043"))
      }

      addView(title)
      addView(step1)
      addView(step2)
      addView(step3)
    }
    container.addView(instructionsCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

    rootScroll.addView(container, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    return rootScroll
  }

  private fun buildStyledSpinner(items: Array<String>): Spinner {
    val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
      override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent) as TextView
        view.setTextColor(Color.parseColor("#1A1C1E"))
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        view.setPadding(dp(12), dp(12), dp(12), dp(12))
        return view
      }

      override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent) as TextView
        view.setTextColor(Color.parseColor("#1A1C1E"))
        view.setBackgroundColor(Color.WHITE)
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        view.setPadding(dp(16), dp(14), dp(16), dp(14))
        return view
      }
    }

    return Spinner(this, Spinner.MODE_DROPDOWN).apply {
      this.adapter = adapter
      background = roundedRect(
        Color.parseColor("#F8FAFD"),
        dp(10).toFloat(),
        Color.parseColor("#C4C7D0"),
        dp(1),
      )
    }
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
