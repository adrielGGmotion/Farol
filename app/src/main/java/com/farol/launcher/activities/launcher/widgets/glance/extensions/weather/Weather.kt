/*
 * Copyright (C) 2026 Răzvan Albu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */

package com.farol.launcher.activities.launcher.widgets.glance.extensions.weather

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Address
import android.location.LocationManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebSettings
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.FloatRange
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator
import com.luckycatlabs.sunrisesunset.dto.Location
import com.farol.launcher.R
import com.farol.launcher.Farol
import com.farol.launcher.activities.launcher.widgets.glance.GlanceDialogExtension
import com.farol.launcher.activities.launcher.widgets.glance.GlanceViewExtension
import com.farol.launcher.preferences.Entry
import com.farol.launcher.ui.common.glance.GlanceConstraintLayout
import com.farol.launcher.ui.utils.UiUtils
import com.farol.launcher.utils.Utils
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import java.util.HashMap
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Future
import kotlin.math.roundToInt
import androidx.core.view.isNotEmpty
import androidx.core.view.size

open class Weather : GlanceDialogExtension() {
    private val preview = WeatherPreview()

    private lateinit var weatherPreferences: SharedPreferences
    private lateinit var geocoder: GeocoderFallback
    @Volatile private var address: Address? = null
    @Volatile private var lastUpdate = 0L
    private var weatherData: MutableList<Data> = CopyOnWriteArrayList()
    private var runningTask: Future<*>? = null
    private lateinit var recycler: RecyclerView
    private lateinit var temperature: TextView
    private lateinit var location: TextView
    private lateinit var summary: TextView
    private lateinit var direction: View
    private lateinit var speed: TextView
    private lateinit var containerView: View
    private lateinit var icon: ImageView
    private var preferencesListenerRegistered = false
    private val weatherPreferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        requestUpdate()
    }

    @JvmField
    val data = HashMap<String, Any>()

    private fun requestUpdate() {
        lastUpdate = 0
        address = null
        weatherData.clear()
        preview.update(null)

        synchronized(this@Weather) {
            if (runningTask != null && !runningTask!!.isDone) {
                runningTask!!.cancel(true)
            }
        }

        update()
    }

    override fun getTAG(): String = TAG

    override fun getViewExtensionPreview(): GlanceViewExtension = preview

    override fun inflateExpanded(inflater: LayoutInflater, container: ConstraintLayout): GlanceConstraintLayout {
        val root = inflater.inflate(R.layout.weather, container, false) as GlanceConstraintLayout
        val farol = activity.applicationContext as Farol
        weatherPreferences = farol.getSharedPreferences(Entry.WEATHER)
        geocoder = GeocoderFallback(activity)
        if (!preferencesListenerRegistered) {
            weatherPreferences.registerOnSharedPreferenceChangeListener(weatherPreferencesListener)
            preferencesListenerRegistered = true
        }

        containerView = root.findViewById(R.id.container)
        temperature = root.findViewById(R.id.temperature)
        location = root.findViewById(R.id.location)
        summary = root.findViewById(R.id.summary)
        direction = root.findViewById(R.id.direction)
        speed = root.findViewById(R.id.speed)
        recycler = root.findViewById(R.id.forecast)
        icon = root.findViewById(R.id.icon)

        recycler.layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        return root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                val index = getFirstIndexInTime()
                if (index > 0) {
                    preview.update(weatherData[index])
                }
            }
        })
    }

    override fun onDetach() {
        super.onDetach()

        if (preferencesListenerRegistered) {
            weatherPreferences.unregisterOnSharedPreferenceChangeListener(weatherPreferencesListener)
            preferencesListenerRegistered = false
        }
    }

    override fun isEnabled(): Boolean = true

    override fun updateScaling(@FloatRange(from = 0.0, to = 1.0) fraction: Float, scale: Float) {
        containerView.scaleY = scale
        containerView.alpha = fraction
    }

    @Synchronized
    override fun update() {
        if (!weatherPreferences.getBoolean(FORECAST_KEY, true) || runningTask != null && !runningTask!!.isDone) {
            return
        }

        runningTask = Utils.submitTask(Runnable {
            if (kotlin.math.abs(System.currentTimeMillis() - lastUpdate) > DEFAULT_UPDATE_INTERVAL) {
                val prefersPreciseLocation = weatherPreferences.getBoolean(PRECISE_LOCATION, false)
                var fetchedPreciseLocation = false

                if (prefersPreciseLocation) {
                    if (activity.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                        val location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                        if (location != null) {
                            lat = location.latitude
                            lon = location.longitude
                            fetchedPreciseLocation = true
                        }
                    }
                }

                if (!fetchedPreciseLocation) {
                    if (weatherPreferences.contains(LATITUDE_KEY) && weatherPreferences.contains(LONGITUDE_KEY)) {
                        lat = java.lang.Double.longBitsToDouble(weatherPreferences.getLong(LATITUDE_KEY, Long.MAX_VALUE))
                        lon = java.lang.Double.longBitsToDouble(weatherPreferences.getLong(LONGITUDE_KEY, Long.MAX_VALUE))
                    } else {
                        Utils.getPublicIPAddress()?.let(::loadApproximatedLocation)
                    }
                }

                if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                    return@Runnable
                }

                val weatherData = getWeatherInfo() ?: return@Runnable

                try {
                    val properties = weatherData.getJSONObject("properties")
                    val timeSeries = properties.getJSONArray("timeseries")
                    val entries = ArrayList<Data>()

                    var index = 0
                    while (index < timeSeries.length() && entries.size <= FORECAST_MAX_ENTRIES) {
                        try {
                            val itemContainer = timeSeries.getJSONObject(index)
                            val time = itemContainer.getString("time")
                            val details = itemContainer.getJSONObject("data").getJSONObject("instant").getJSONObject("details")
                            val temperature = details.getDouble("air_temperature")
                            val windDirection = details.getDouble("wind_from_direction")
                            val windSpeed = details.getDouble("wind_speed")
                            val iconCode = itemContainer.getJSONObject("data").getJSONObject("next_6_hours").getJSONObject("summary").getString("symbol_code")

                            entries.add(Data(Utils.parseDate(time) ?: throw JSONException("Invalid date"), iconCode, temperature, windDirection, windSpeed))
                        } catch (exception: JSONException) {
                            Log.e(TAG, "update: Parse exception for item $index.")
                        }
                        index++
                    }

                    if (entries.isNotEmpty()) {
                        this.weatherData = entries
                    }

                    val firstIndex = getFirstIndexInTime()
                    if (firstIndex > 0) {
                        UiUtils.post { preview.update(this.weatherData[firstIndex]) }
                    }

                    val addressName = weatherPreferences.getString(LOCATION_NAME, null)
                    address = if (addressName == null) {
                        geocoder.getFromLocation(lat, lon)
                    } else {
                        Address(Locale.ENGLISH).apply { locality = addressName }
                    }

                    lastUpdate = if (!fetchedPreciseLocation && prefersPreciseLocation) {
                        System.currentTimeMillis() - DEFAULT_UPDATE_INTERVAL + FALLBACK_UPDATE_INTERVAL
                    } else {
                        System.currentTimeMillis()
                    }
                } catch (exception: JSONException) {
                    Log.e(TAG, "updateWeather: ", exception)
                }
            }
        })
    }

    @SuppressLint("SetTextI18n")
    private fun updateData() {
        val index = getFirstIndexInTime()
        if (index > 0) {
            val data = weatherData[index]
            icon.setImageResource(getIcon(data.iconCode))

            temperature.text = if (weatherPreferences.getBoolean(IMPERIAL_KEY, Utils.isSystemUsingImperial(activity))) {
                Utils.toFahrenheit(data.temperature).roundToInt().toString() + "°"
            } else {
                data.temperature.roundToInt().toString() + "°"
            }

            summary.setText(getSummary(data.iconCode))

            val currentAddress = address
            if (currentAddress != null) {
                val subLocality = currentAddress.subLocality
                var locality = currentAddress.locality ?: currentAddress.subAdminArea ?: currentAddress.adminArea
                if (locality == null) {
                    locality = subLocality
                } else if (subLocality != null) {
                    locality = "$subLocality, $locality"
                }
                location.text = locality
            } else {
                location.text = null
            }

            direction.rotation = data.windDirection.toFloat() + 180f
            speed.text = if (weatherPreferences.getBoolean(IMPERIAL_KEY, Utils.isSystemUsingImperial(activity))) {
                Utils.msToMph(data.windSpeed).roundToInt().toString() + "mi/h"
            } else {
                data.windSpeed.roundToInt().toString() + "m/s"
            }

            recycler.adapter = ForecastAdapter(activity, weatherData, index)
        }
    }

    protected override fun show() {
        updateData()
        super.show()
    }

    private fun getFirstIndexInTime(): Int {
        val calendar = Calendar.getInstance()
        for (index in weatherData.indices) {
            if (weatherData[index].date.after(calendar.time)) {
                return index
            }
        }
        return -1
    }

    private fun loadApproximatedLocation(ip: String) {
        for (entry in LOCATION_APIS) {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(entry.api.replace(LOCATION_API_IP_WILDCARD, ip)).openConnection() as HttpURLConnection
                connection.readTimeout = REQUEST_TIMEOUT
                connection.connectTimeout = REQUEST_TIMEOUT
                connection.requestMethod = "GET"
                connection.addRequestProperty("User-Agent", WebSettings.getDefaultUserAgent(activity))
                connection.addRequestProperty("Content-type", "application/json")
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonObject = JSONObject(Utils.readStream(connection.inputStream))
                    for (callback in entry.callback) {
                        callback.assign(jsonObject.getDouble(callback.field))
                    }
                } else {
                    Log.w(TAG, "getWeatherInfo: Server returned non-OK status: $responseCode")
                }
            } catch (exception: Exception) {
                Log.e(TAG, "updateLocation: " + exception.message)
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun getWeatherInfo(): JSONObject? {
        var connection: HttpURLConnection? = null
        try {
            connection = URL("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=" + lat + "&lon=" + lon).openConnection() as HttpURLConnection
            connection.readTimeout = REQUEST_TIMEOUT
            connection.connectTimeout = REQUEST_TIMEOUT
            connection.requestMethod = "GET"
            connection.addRequestProperty("User-Agent", WebSettings.getDefaultUserAgent(activity))
            connection.setRequestProperty("Content-type", "application/json")
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return JSONObject(Utils.readStream(connection.inputStream))
            } else {
                Log.w(TAG, "getWeatherInfo: Server returned non-OK status: $responseCode")
            }
            return null
        } catch (exception: Exception) {
            Log.e(TAG, "getWeatherInfo: ", exception)
            return null
        } finally {
            connection?.disconnect()
        }
    }

    private class IpApiEntry(val api: String, val callback: Array<Callback>) {
        abstract class Callback(val field: String) {
            abstract fun assign(value: Double)
        }
    }

    class Data(
        @JvmField val date: Date,
        iconCode: String?,
        @JvmField val temperature: Double,
        @JvmField val windDirection: Double,
        @JvmField val windSpeed: Double,
    ) {
        @JvmField
        val iconCode: String? = iconCode?.split("[_%.-]".toRegex())?.get(0)
    }

    companion object {
        const val ACTION_REQUEST_UPDATE = "com.stario.REQUEST_UPDATE"
        const val PRECISE_LOCATION = "com.stario.PRECISE_LOCATION"
        const val FORECAST_KEY = "com.stario.WEATHER_FORECAST"
        const val IMPERIAL_KEY = "com.stario.IMPERIAL"
        const val LOCATION_NAME = "com.stario.LOCATION"
        const val LATITUDE_KEY = "com.stario.LATITUDE"
        const val LONGITUDE_KEY = "com.stario.LONGITUDE"

        private const val TAG = "com.farol.launcher.Weather"
        private const val FORECAST_MAX_ENTRIES = 20
        private const val DEFAULT_UPDATE_INTERVAL = 3_600_000
        private const val FALLBACK_UPDATE_INTERVAL = 300_000
        private const val REQUEST_TIMEOUT = 10_000
        private const val DAY = 0
        private const val NIGHT = 1
        private const val SUMMARY = 2
        private const val LOCATION_API_IP_WILDCARD = "\$"

        private val LOCATION_APIS = arrayOf(
            IpApiEntry("https://ip-api.com/json/\$", arrayOf(
                object : IpApiEntry.Callback("lat") { override fun assign(value: Double) { lat = value } },
                object : IpApiEntry.Callback("lon") { override fun assign(value: Double) { lon = value } },
            )),
            IpApiEntry("https://freeipapi.com/api/json/\$", arrayOf(
                object : IpApiEntry.Callback("latitude") { override fun assign(value: Double) { lat = value } },
                object : IpApiEntry.Callback("longitude") { override fun assign(value: Double) { lon = value } },
            )),
            IpApiEntry("https://ipapi.co/\$/json/", arrayOf(
                object : IpApiEntry.Callback("latitude") { override fun assign(value: Double) { lat = value } },
                object : IpApiEntry.Callback("longitude") { override fun assign(value: Double) { lon = value } },
            )),
        )

        private val WEATHER_RESOURCES = hashMapOf(
            "clearsky" to hashMapOf(DAY to R.drawable.clear_day, NIGHT to R.drawable.clear_night, SUMMARY to R.string.clear_sky),
            "cloudy" to hashMapOf(DAY to R.drawable.cloudy, NIGHT to R.drawable.cloudy, SUMMARY to R.string.cloudy),
            "fair" to hashMapOf(DAY to R.drawable.mostly_clear_day, NIGHT to R.drawable.mostly_clear_night, SUMMARY to R.string.fair),
            "fog" to hashMapOf(DAY to R.drawable.haze_fog_dust_smoke, NIGHT to R.drawable.haze_fog_dust_smoke, SUMMARY to R.string.fog),
            "heavyrain" to hashMapOf(DAY to R.drawable.heavy_rain, NIGHT to R.drawable.heavy_rain, SUMMARY to R.string.heavy_rain),
            "heavyrainandthunder" to hashMapOf(DAY to R.drawable.strong_thunderstorms, NIGHT to R.drawable.strong_thunderstorms, SUMMARY to R.string.heavy_rain_and_thunder),
            "heavyrainshowers" to hashMapOf(DAY to R.drawable.scattered_showers_day, NIGHT to R.drawable.scattered_showers_night, SUMMARY to R.string.heavy_rain_showers),
            "heavyrainshowersandthunder" to hashMapOf(DAY to R.drawable.isolated_scattered_thunderstorms_day, NIGHT to R.drawable.isolated_scattered_thunderstorms_night, SUMMARY to R.string.heavy_rain_showers_and_thunder),
            "heavysleet" to hashMapOf(DAY to R.drawable.sleet_hail, NIGHT to R.drawable.sleet_hail, SUMMARY to R.string.heavy_sleet),
            "heavysleetandthunder" to hashMapOf(DAY to R.drawable.sleet_hail, NIGHT to R.drawable.sleet_hail, SUMMARY to R.string.heavy_sleet_and_thunder),
            "heavysleetshowers" to hashMapOf(DAY to R.drawable.sleet_hail, NIGHT to R.drawable.sleet_hail, SUMMARY to R.string.heavy_sleet_showers),
            "heavysleetshowersandthunder" to hashMapOf(DAY to R.drawable.sleet_hail, NIGHT to R.drawable.sleet_hail, SUMMARY to R.string.heavy_sleet_showers_and_thunder),
            "heavysnow" to hashMapOf(DAY to R.drawable.heavy_snow, NIGHT to R.drawable.heavy_snow, SUMMARY to R.string.heavy_snow),
            "heavysnowandthunder" to hashMapOf(DAY to R.drawable.blowing_snow, NIGHT to R.drawable.blowing_snow, SUMMARY to R.string.heavy_snow_and_thunder),
            "heavysnowshowers" to hashMapOf(DAY to R.drawable.scattered_snow_showers_day, NIGHT to R.drawable.scattered_snow_showers_night, SUMMARY to R.string.heavy_snow_showers),
            "heavysnowshowersandthunder" to hashMapOf(DAY to R.drawable.heavy_snow, NIGHT to R.drawable.heavy_snow, SUMMARY to R.string.heavy_snow_showers_and_thunder),
            "lightrain" to hashMapOf(DAY to R.drawable.drizzle, NIGHT to R.drawable.drizzle, SUMMARY to R.string.light_rain),
            "lightrainandthunder" to hashMapOf(DAY to R.drawable.isolated_scattered_thunderstorms_day, NIGHT to R.drawable.isolated_scattered_thunderstorms_night, SUMMARY to R.string.light_rain_and_thunder),
            "lightrainshowers" to hashMapOf(DAY to R.drawable.scattered_showers_day, NIGHT to R.drawable.scattered_showers_night, SUMMARY to R.string.light_rain_showers),
            "lightrainshowersandthunder" to hashMapOf(DAY to R.drawable.isolated_scattered_thunderstorms_day, NIGHT to R.drawable.isolated_scattered_thunderstorms_night, SUMMARY to R.string.light_rain_showers_and_thunder),
            "lightsleet" to hashMapOf(DAY to R.drawable.flurries, NIGHT to R.drawable.flurries, SUMMARY to R.string.light_sleet),
            "lightsleetandthunder" to hashMapOf(DAY to R.drawable.flurries, NIGHT to R.drawable.flurries, SUMMARY to R.string.light_sleet_and_thunder),
            "lightsleetshowers" to hashMapOf(DAY to R.drawable.flurries, NIGHT to R.drawable.flurries, SUMMARY to R.string.light_sleet_showers),
            "lightsnow" to hashMapOf(DAY to R.drawable.flurries, NIGHT to R.drawable.flurries, SUMMARY to R.string.light_snow),
            "lightsnowandthunder" to hashMapOf(DAY to R.drawable.scattered_snow_showers_day, NIGHT to R.drawable.scattered_snow_showers_night, SUMMARY to R.string.light_snow_and_thunder),
            "lightsnowshowers" to hashMapOf(DAY to R.drawable.flurries, NIGHT to R.drawable.flurries, SUMMARY to R.string.light_snow_showers),
            "lightssleetshowersandthunder" to hashMapOf(DAY to R.drawable.flurries, NIGHT to R.drawable.flurries, SUMMARY to R.string.light_sleet_showers_and_thunder),
            "lightssnowshowersandthunder" to hashMapOf(DAY to R.drawable.flurries, NIGHT to R.drawable.flurries, SUMMARY to R.string.light_snow_showers_and_thunder),
            "partlycloudy" to hashMapOf(DAY to R.drawable.partly_cloudy_day, NIGHT to R.drawable.partly_cloudy_night, SUMMARY to R.string.partly_cloudy),
            "rain" to hashMapOf(DAY to R.drawable.showers_rain, NIGHT to R.drawable.showers_rain, SUMMARY to R.string.rain),
            "rainandthunder" to hashMapOf(DAY to R.drawable.isolated_thunderstorms, NIGHT to R.drawable.isolated_thunderstorms, SUMMARY to R.string.rain_and_thunder),
            "rainshowers" to hashMapOf(DAY to R.drawable.showers_rain, NIGHT to R.drawable.showers_rain, SUMMARY to R.string.rain_showers),
            "rainshowersandthunder" to hashMapOf(DAY to R.drawable.isolated_scattered_thunderstorms_day, NIGHT to R.drawable.isolated_scattered_thunderstorms_night, SUMMARY to R.string.rain_showers_and_thunder),
            "sleet" to hashMapOf(DAY to R.drawable.sleet_hail, NIGHT to R.drawable.sleet_hail, SUMMARY to R.string.sleet),
            "sleetandthunder" to hashMapOf(DAY to R.drawable.sleet_hail, NIGHT to R.drawable.sleet_hail, SUMMARY to R.string.sleet_and_thunder),
            "sleetshowers" to hashMapOf(DAY to R.drawable.sleet_hail, NIGHT to R.drawable.sleet_hail, SUMMARY to R.string.sleet_showers),
            "sleetshowersandthunder" to hashMapOf(DAY to R.drawable.sleet_hail, NIGHT to R.drawable.sleet_hail, SUMMARY to R.string.sleet_showers_and_thunder),
            "snow" to hashMapOf(DAY to R.drawable.showers_snow, NIGHT to R.drawable.showers_snow, SUMMARY to R.string.snow),
            "snowandthunder" to hashMapOf(DAY to R.drawable.heavy_snow, NIGHT to R.drawable.heavy_snow, SUMMARY to R.string.snow_and_thunder),
            "snowshowers" to hashMapOf(DAY to R.drawable.scattered_snow_showers_day, NIGHT to R.drawable.scattered_snow_showers_night, SUMMARY to R.string.snow_showers),
            "snowshowersandthunder" to hashMapOf(DAY to R.drawable.showers_snow, NIGHT to R.drawable.showers_snow, SUMMARY to R.string.snow_showers_and_thunder),
        )

        @Volatile private var lat = Double.MAX_VALUE
        @Volatile private var lon = Double.MAX_VALUE

        @JvmStatic
        fun getIcon(iconCode: String?): Int {
            val calendar = Calendar.getInstance()
            val location = Location(lat, lon)
            val calculator = SunriseSunsetCalculator(location, calendar.timeZone)
            val sunrise = calculator.getOfficialSunriseCalendarForDate(calendar)
            val sunset = calculator.getOfficialSunsetCalendarForDate(calendar)
            val dataForCode = WEATHER_RESOURCES[iconCode] ?: return R.drawable.unavailable

            val icon = if (sunrise.timeInMillis < calendar.timeInMillis && calendar.timeInMillis < sunset.timeInMillis) {
                dataForCode.getOrDefault(DAY, R.drawable.unavailable)
            } else {
                dataForCode.getOrDefault(NIGHT, R.drawable.unavailable)
            }

            return icon ?: 0
        }

        @JvmStatic
        fun getSummary(iconCode: String?): Int {
            val dataForCode = WEATHER_RESOURCES[iconCode]
            val summary = dataForCode?.getOrDefault(SUMMARY, R.string.unavailable) ?: R.string.unavailable
            return summary ?: 0
        }
    }
}
