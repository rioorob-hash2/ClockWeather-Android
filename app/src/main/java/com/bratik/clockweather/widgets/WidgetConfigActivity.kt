package com.bratik.clockweather.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bratik.clockweather.R
import com.bratik.clockweather.data.OpenMeteo
import com.bratik.clockweather.data.Place
import com.bratik.clockweather.data.Prefs
import com.bratik.clockweather.notify.WeatherScheduler
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Экран, который launcher показывает при первом добавлении виджета
 * (см. android:configure в res/xml/widget_*_info.xml).
 *
 * Ничего не должно падать: геокодер может быть недоступен, сети может не быть —
 * в худшем случае сохраняем введённое имя города и выходим.
 */
class WidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val found: MutableList<Place> = mutableListOf()

    private lateinit var cityInput: TextInputEditText
    private lateinit var resultsGroup: RadioGroup
    private lateinit var statusText: TextView
    private lateinit var unitsToggle: MaterialButtonToggleGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Если пользователь закроет экран — виджет не добавляем.
        setResult(RESULT_CANCELED)

        setContentView(R.layout.activity_widget_config)

        findViewById<MaterialToolbar>(R.id.toolbar).setTitle(R.string.widget_config_title)
        cityInput = findViewById(R.id.city_input)
        resultsGroup = findViewById(R.id.results_group)
        statusText = findViewById(R.id.status_text)
        unitsToggle = findViewById(R.id.units_toggle)

        val prefs = Prefs.get(this)
        if (cityInput.text.isNullOrBlank()) cityInput.setText(prefs.cityName)
        unitsToggle.check(if (prefs.fahrenheit) R.id.btn_fahrenheit else R.id.btn_celsius)

        findViewById<MaterialButton>(R.id.btn_search).setOnClickListener { search() }
        findViewById<MaterialButton>(R.id.btn_done).setOnClickListener { save() }
        findViewById<MaterialButton>(R.id.btn_cancel).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun search() {
        val query = cityInput.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) {
            showStatus(getString(R.string.hint_city_search))
            return
        }
        showStatus(getString(R.string.widget_config_searching))

        lifecycleScope.launch {
            // Геокодер и сеть — чужой код: любое исключение превращаем в «не найдено».
            val places: List<Place> = try {
                withContext(Dispatchers.IO) { OpenMeteo.geocode(query) }
            } catch (t: Throwable) {
                emptyList()
            }
            showResults(places)
        }
    }

    private fun showResults(places: List<Place>) {
        found.clear()
        found.addAll(places)
        resultsGroup.removeAllViews()

        if (found.isEmpty()) {
            showStatus(getString(R.string.city_not_found))
            return
        }
        hideStatus()

        found.forEachIndexed { index, place ->
            val button = RadioButton(this).apply {
                id = View.generateViewId()
                text = place.label
                tag = place
                setPadding(0, 4, 0, 4)
            }
            resultsGroup.addView(button)
            if (index == 0) resultsGroup.check(button.id)
        }
    }

    private fun save() {
        val prefs = Prefs.get(this)

        val place = selectedPlace()
        if (place != null) {
            prefs.cityName = place.name
            prefs.lat = place.lat
            prefs.lon = place.lon
        } else {
            val typed = cityInput.text?.toString()?.trim().orEmpty()
            if (typed.isNotEmpty()) prefs.cityName = typed
        }

        prefs.fahrenheit = unitsToggle.checkedButtonId == R.id.btn_fahrenheit

        // Планировщик и перерисовка — «best effort»: настройки уже сохранены.
        try {
            WeatherScheduler.ensurePeriodic(this)
            WeatherScheduler.refreshNow(this)
        } catch (t: Throwable) {
            // WorkManager недоступен — не страшно, обновится по расписанию системы
        }
        try {
            WidgetRender.updateAll(this)
        } catch (t: Throwable) {
            // пусть launcher сам перерисует при следующем апдейте
        }

        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, result)
        finish()
    }

    private fun selectedPlace(): Place? {
        val checkedId = resultsGroup.checkedRadioButtonId
        if (checkedId == View.NO_ID) return found.firstOrNull()
        return resultsGroup.findViewById<RadioButton>(checkedId)?.tag as? Place
    }

    private fun showStatus(text: String) {
        statusText.text = text
        statusText.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        statusText.text = ""
        statusText.visibility = View.GONE
    }
}
