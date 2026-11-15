package com.bratik.clockweather.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bratik.clockweather.R
import com.bratik.clockweather.data.OpenMeteo
import com.bratik.clockweather.data.Place
import com.bratik.clockweather.data.Prefs
import com.bratik.clockweather.databinding.ActivitySettingsBinding
import com.bratik.clockweather.notify.WeatherScheduler
import com.bratik.clockweather.util.Format
import com.bratik.clockweather.widgets.WidgetRender
import kotlinx.coroutines.launch
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding
    private lateinit var prefs: Prefs

    private val notifyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        updatePermButton()
        if (granted) toast("РЈРІРµРґРѕРјР»РµРЅРёСЏ СЂР°Р·СЂРµС€РµРЅС‹")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, bars.bottom)
            insets
        }

        prefs = Prefs.get(this)

        bindUnits()
        bindInterval()
        bindTransparency()
        bindSensors()
        bindAlerts()
        bindQuietHours()
        bindAntiSpam()

        b.cityEdit.setText(prefs.cityName)
        b.searchBtn.setOnClickListener { searchCity() }
        b.refreshBtn.setOnClickListener {
            WeatherScheduler.refreshNow(this)
            toast("РћР±РЅРѕРІР»РµРЅРёРµ Р·Р°РїСѓС‰РµРЅРѕ")
        }
        updateCityLabel()
        updatePermButton()
        updateUpdatedText()
    }

    override fun onResume() {
        super.onResume()
        updateUpdatedText()
        updatePermButton()
    }

    // ---------- РїСЂРёРІСЏР·РєРё ----------

    private fun bindUnits() {
        if (prefs.fahrenheit) b.unitsGroup.check(R.id.btnF) else b.unitsGroup.check(R.id.btnC)
        b.unitsGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            prefs.fahrenheit = checkedId == R.id.btnF
            pushWidgets()
        }
    }

    private fun bindInterval() {
        val id = when (prefs.intervalMin) {
            15 -> R.id.btnInt15
            60 -> R.id.btnInt60
            120 -> R.id.btnInt120
            else -> R.id.btnInt30
        }
        b.intervalGroup.check(id)
        b.intervalGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            prefs.intervalMin = when (checkedId) {
                R.id.btnInt15 -> 15
                R.id.btnInt60 -> 60
                R.id.btnInt120 -> 120
                else -> 30
            }
            WeatherScheduler.ensurePeriodic(this)
        }
    }

    private fun bindTransparency() {
        // Slider падает, если value не кратно stepSize: приводим к шагу 5 заранее.
        b.transparencySlider.value = (prefs.transparency / 5 * 5).toFloat()
        labelTransparency()
        b.transparencySlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            prefs.transparency = value.toInt()
            labelTransparency()
            pushWidgets()
        }
    }

    private fun labelTransparency() {
        b.transparencyLabel.text = getString(R.string.label_transparency) + ": ${prefs.transparency}%"
    }

    private fun bindSensors() {
        b.switchSensors.isChecked = prefs.showSensors
        b.switchSensors.setOnCheckedChangeListener { _, checked ->
            prefs.showSensors = checked
            pushWidgets()
        }
    }

    private fun bindAlerts() {
        b.switchAlerts.isChecked = prefs.alertsEnabled
        b.switchAlerts.setOnCheckedChangeListener { _, checked -> prefs.alertsEnabled = checked }

        b.rainSlider.value = (prefs.rainProbThreshold / 5 * 5).toFloat().coerceIn(40f, 100f)
        b.rainProbLabel.text = getString(R.string.label_rain_threshold) + ": ${prefs.rainProbThreshold}%"
        b.rainSlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            prefs.rainProbThreshold = value.toInt()
            b.rainProbLabel.text = getString(R.string.label_rain_threshold) + ": ${prefs.rainProbThreshold}%"
        }

        b.snowMmSlider.value = (kotlin.math.round(prefs.snowMmThreshold * 2f) / 2f).coerceIn(0.5f, 5f)
        b.snowMmLabel.text = getString(R.string.label_snow_threshold) + ": " +
            String.format(Locale.US, "%.1f", prefs.snowMmThreshold)
        b.snowMmSlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            prefs.snowMmThreshold = value
            b.snowMmLabel.text = getString(R.string.label_snow_threshold) + ": " +
                String.format(Locale.US, "%.1f", value)
        }

        b.snowProbSlider.value = (prefs.snowProbThreshold / 5 * 5).toFloat().coerceIn(50f, 100f)
        b.snowProbLabel.text = getString(R.string.label_snow_prob) + ": ${prefs.snowProbThreshold}%"
        b.snowProbSlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            prefs.snowProbThreshold = value.toInt()
            b.snowProbLabel.text = getString(R.string.label_snow_prob) + ": ${prefs.snowProbThreshold}%"
        }
    }

    private fun bindQuietHours() {
        renderQuiet()
        b.quietStartText.setOnClickListener {
            TimePickerDialog(this, { _, h, _ ->
                prefs.quietStart = h
                renderQuiet()
            }, prefs.quietStart, 0, true).show()
        }
        b.quietEndText.setOnClickListener {
            TimePickerDialog(this, { _, h, _ ->
                prefs.quietEnd = h
                renderQuiet()
            }, prefs.quietEnd, 0, true).show()
        }
    }

    private fun renderQuiet() {
        b.quietStartText.text = String.format(Locale.US, "%02d:00", prefs.quietStart)
        b.quietEndText.text = String.format(Locale.US, "%02d:00", prefs.quietEnd)
    }

    private fun bindAntiSpam() {
        val id = when (prefs.antiSpamHours) {
            1 -> R.id.btnSpam1
            2 -> R.id.btnSpam2
            6 -> R.id.btnSpam6
            else -> R.id.btnSpam3
        }
        b.antispamGroup.check(id)
        b.antispamGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            prefs.antiSpamHours = when (checkedId) {
                R.id.btnSpam1 -> 1
                R.id.btnSpam2 -> 2
                R.id.btnSpam6 -> 6
                else -> 3
            }
        }
    }

    // ---------- РіРѕСЂРѕРґ ----------

    private fun searchCity() {
        val query = b.cityEdit.text?.toString()?.trim().orEmpty()
        if (query.isBlank()) return
        hideKeyboard()
        b.searchBtn.isEnabled = false
        lifecycleScope.launch {
            runCatching { OpenMeteo.geocode(query) }
                .onSuccess { places -> renderPlaces(places) }
                .onFailure { toast(getString(R.string.network_error)) }
            b.searchBtn.isEnabled = true
        }
    }

    private fun renderPlaces(places: List<Place>) {
        b.cityResults.removeAllViews()
        if (places.isEmpty()) {
            toast(getString(R.string.city_not_found))
            return
        }
        places.forEach { place ->
            val tv = TextView(this).apply {
                text = place.label
                textSize = 15f
                setPadding(8, 22, 8, 22)
                isClickable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener { applyPlace(place) }
            }
            b.cityResults.addView(tv)
        }
    }

    private fun applyPlace(place: Place) {
        prefs.cityName = place.name
        prefs.lat = place.lat
        prefs.lon = place.lon
        b.cityEdit.setText(place.name)
        b.cityResults.removeAllViews()
        updateCityLabel()
        pushWidgets()
        WeatherScheduler.refreshNow(this)
        toast("Р“РѕСЂРѕРґ: ${place.name}")
    }

    private fun updateCityLabel() {
        b.cityCurrent.text = String.format(
            Locale.US,
            "%s · %.4f, %.4f",
            prefs.cityName, prefs.lat, prefs.lon,
        )
    }

    // ---------- РїСЂРѕС‡РµРµ ----------

    private fun pushWidgets() {
        WidgetRender.updateAll(this)
    }

    private fun updateUpdatedText() {
        val cached = prefs.cachedWeather
        b.updatedText.text = if (cached.isBlank()) {
            getString(R.string.no_data)
        } else {
            val snap = com.bratik.clockweather.data.WeatherSnapshot.fromJson(cached)
            if (snap == null) {
                getString(R.string.no_data)
            } else {
                val temp = Format.temp(snap.tempC, prefs.fahrenheit)
                getString(R.string.updated_prefix) + " " +
                    Format.updatedAgo(snap.updatedAt, System.currentTimeMillis()) +
                    " · $temp · " + com.bratik.clockweather.data.WeatherCodes.description(snap.code)
            }
        }
    }

    private fun updatePermButton() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        b.notifyPermBtn.visibility = if (granted) android.view.View.GONE else android.view.View.VISIBLE
        b.notifyPermBtn.setOnClickListener { notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(b.cityEdit.windowToken, 0)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
